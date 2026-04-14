package com.trama.app.chat

import com.trama.shared.data.DiaryRepository
import com.trama.shared.model.DailyPage
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.Place
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Builds the full diary context to inject into the AI assistant.
 *
 * Covers ALL available history with two levels of detail:
 *
 *   Recent (last 90 days):
 *     - Completed tasks listed individually with date and text.
 *     - DailyPage full markdown (rich: tasks, places, events, summary).
 *
 *   Older (everything before 90 days, no upper limit):
 *     - Completed tasks grouped by month (compact but searchable).
 *     - DailyPage briefSummary only (lightweight narrative per day).
 *
 *   Always included:
 *     - All visited places with ratings and opinions.
 *     - All current pending tasks.
 *
 * Token estimates for a 4-year power user:
 *   Places (50):              ~5 K tokens
 *   Tasks recent (90 d):      ~18 K tokens
 *   Tasks older grouped:      ~10 K tokens   (48 months × ~200 t)
 *   Pages recent full (90 d): ~72 K tokens   (90 days × ~800 t)
 *   Pages older briefSummary: ~205 K tokens  (1 370 days × ~150 t)
 *   ─────────────────────────────────────────────────────────────
 *   Total (4 years):          ~310 K tokens  → fine for Gemini 1 M
 *                                              ~2 years fit in Gemma 128 K
 *
 * Context is ordered newest-first so that, on models with a sliding-window
 * truncation, the most recently relevant data always stays in context.
 *
 * Cached for CONTEXT_TTL_MS; call invalidate() to force a rebuild.
 */
class DiaryContextBuilder(private val repository: DiaryRepository) {

    private var cached: String? = null
    private var builtAt: Long = 0L

    private val dayFormat = SimpleDateFormat("dd/MM EEE", Locale("es"))
    private val monthFormat = SimpleDateFormat("MMMM yyyy", Locale("es"))

    /** Returns the full diary context (same for both Gemini and Gemma). */
    suspend fun getContext(): String {
        val now = System.currentTimeMillis()
        if (cached != null && now - builtAt <= CONTEXT_TTL_MS) return cached!!
        cached = buildContext(now)
        builtAt = now
        return cached!!
    }

    /** Force context rebuild on next call (e.g. after "Nuevo chat"). */
    fun invalidate() { cached = null }

    // ── Build ─────────────────────────────────────────────────────────────────

    private suspend fun buildContext(now: Long): String = withContext(Dispatchers.IO) {
        val pages = repository.getAllDailyPagesOnce()          // all history, newest first
        val places = repository.getAllPlacesOnce()
        val allCompleted = repository.getCompletedSince(0L)    // ALL time, no cutoff
        val currentPending = repository.getPendingOnce()

        val recentCutoff = now - RECENT_WINDOW_MS

        buildString {
            appendLine("=== DIARIO PERSONAL ===")
            appendLine()
            appendPlacesSection(places)
            appendTasksSection(allCompleted, currentPending, recentCutoff)
            appendDailyPagesSection(pages, recentCutoff)
        }
    }

    /**
     * Returns a version of the context safe for Gemma's KV cache.
     * Gemma 4 E4B supports 128K tokens but maxNumTokens in the engine is capped at 32K
     * for on-device RAM reasons. At ~4 chars/token, 28K tokens ≈ 112K chars — we leave
     * ~4K tokens headroom for the conversation itself.
     */
    suspend fun getContextForLocalModel(): String {
        val full = getContext()
        return if (full.length <= MAX_LOCAL_CONTEXT_CHARS) full
        else full.take(MAX_LOCAL_CONTEXT_CHARS) + "\n\n[...historial anterior omitido por límite de contexto del modelo local]"
    }

    // ── Sections ──────────────────────────────────────────────────────────────

    private fun StringBuilder.appendPlacesSection(places: List<Place>) {
        if (places.isEmpty()) return
        appendLine("--- LUGARES VISITADOS ---")
        places.forEach { place ->
            append("• ${place.name}")
            place.rating?.let { append(" [${it}/5★]") }
            val summary = place.opinionSummary?.takeIf { it.isNotBlank() }
            val opinion = place.opinionText?.takeIf { it.isNotBlank() && it != summary }
            summary?.let { append(" — $it") }
            opinion?.let { append(" (nota: $it)") }
            appendLine()
        }
        appendLine()
    }

    private fun StringBuilder.appendTasksSection(
        allCompleted: List<DiaryEntry>,
        pending: List<DiaryEntry>,
        recentCutoff: Long
    ) {
        appendLine("--- TAREAS COMPLETADAS ---")

        if (allCompleted.isEmpty()) {
            appendLine("Ninguna registrada.")
        } else {
            val (recent, older) = allCompleted.partition {
                (it.completedAt ?: it.createdAt) >= recentCutoff
            }

            // Recent: one line per task
            if (recent.isNotEmpty()) {
                appendLine("[Últimos 90 días]")
                recent.forEach { entry ->
                    val date = dayFormat.format(Date(entry.completedAt ?: entry.createdAt))
                    val text = (entry.cleanText ?: entry.displayText).take(120)
                    appendLine("• [$date] $text")
                }
            }

            // Older: grouped by month — compact but searchable
            if (older.isNotEmpty()) {
                appendLine("[Historial anterior — agrupado por mes]")
                older
                    .groupBy { monthKey(it.completedAt ?: it.createdAt) }
                    .entries
                    .sortedByDescending { it.key }
                    .forEach { (month, tasks) ->
                        val sample = tasks.take(6)
                            .joinToString("; ") { (it.cleanText ?: it.displayText).take(60) }
                        val more = if (tasks.size > 6) " … +${tasks.size - 6} más" else ""
                        appendLine("• [$month] ${tasks.size} completadas: $sample$more")
                    }
            }
        }
        appendLine()

        appendLine("--- TAREAS PENDIENTES AHORA ---")
        if (pending.isEmpty()) {
            appendLine("Ninguna.")
        } else {
            pending.forEach { entry ->
                val created = dayFormat.format(Date(entry.createdAt))
                val prio = when (entry.priority) {
                    "URGENT" -> " [URGENTE]"
                    "HIGH"   -> " [ALTA]"
                    else     -> ""
                }
                val text = (entry.cleanText ?: entry.displayText).take(120)
                appendLine("• [$created]$prio $text")
            }
        }
        appendLine()
    }

    private fun StringBuilder.appendDailyPagesSection(
        pages: List<DailyPage>,
        recentCutoff: Long
    ) {
        if (pages.isEmpty()) {
            appendLine("--- RESÚMENES DIARIOS ---")
            appendLine("No hay resúmenes generados todavía.")
            return
        }
        appendLine("--- RESÚMENES DIARIOS (historial completo, del más reciente al más antiguo) ---")
        pages.forEach { page ->
            // Recent pages: full markdown (tasks, places, events, narrative)
            // Older pages: briefSummary only (lightweight)
            val useFullMarkdown = page.markdown.isNotBlank() && page.dayStartMillis >= recentCutoff
            if (useFullMarkdown) {
                appendLine(page.markdown.trim())
            } else {
                appendLine("# ${page.date}")
                appendLine(page.briefSummary?.takeIf { it.isNotBlank() } ?: "Sin resumen.")
            }
            appendLine()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun monthKey(millis: Long): String =
        monthFormat.format(Date(millis)).replaceFirstChar { it.uppercase() }

    companion object {
        /** Full markdown for pages within this window; older pages use briefSummary. */
        private val RECENT_WINDOW_MS = 90L * 86_400_000L
        /** Context cache TTL — re-query DB after 5 minutes. */
        private val CONTEXT_TTL_MS = 5L * 60_000L
        /**
         * Max chars sent to local model (embedded in user message, not ConversationConfig).
         * maxNumTokens=4096 total; context(~1500 tok) + history(~300) + response(1024) = ~2824 < 4096.
         * 1500 tokens × 4 chars/token ≈ 6000 chars.
         */
        const val MAX_LOCAL_CONTEXT_CHARS = 6_000
    }
}
