package com.trama.app.ambient

import com.trama.shared.model.TimelineEvent
import java.text.Normalizer
import java.util.Locale

/** Coarse, local-only context. Raw transcripts are never part of this model. */
enum class AmbientContextCategory(
    val title: String,
    val description: String
) {
    MUSIC("Música", "Se detectó música en el ambiente"),
    TELEVISION("Televisión o radio", "Se detectó contenido emitido en el ambiente"),
    CONVERSATION("Conversación", "Se detectó una conversación en el ambiente"),
    MEETING("Reunión", "Se detectó una reunión o conversación de trabajo")
}

data class AmbientContextSignal(
    val category: AmbientContextCategory,
    val confidence: Float,
    val evidence: String
)

/**
 * Conservative text classifier for environmental ASR results.
 *
 * It deliberately returns null for first-person/action phrases. Context capture must
 * never steal an intentional task from the normal intent pipeline.
 */
object AmbientContextClassifier {

    private val personalAction = Regex(
        """\b(?:recu[eé]rdame|recordar|an[oó]ta(?:lo|la|esto)?|ap[uú]nta(?:lo|la|esto)?|guarda(?:r|lo|la|esto)?|tengo\s+que|tenemos\s+que|debo|debemos|necesito|necesitamos|quiero\s+recordar|no\s+olvidar|tengo\s+(?:la\s+)?(?:cita|reuni[oó]n|itv|m[eé]dico|dentista)|tenemos\s+(?:la\s+)?(?:cita|reuni[oó]n))\b""",
        RegexOption.IGNORE_CASE
    )
    private val music = Regex(
        """\b(?:m[uú]sica|canci[oó]n|instrumental|melod[ií]a|aplausos|cantando|coro)\b""",
        RegexOption.IGNORE_CASE
    )
    private val broadcast = Regex(
        """\b(?:suscr[ií]bete|nuestro\s+canal|este\s+canal|las\s+noticias|informativo|publicidad|anuncios|audiencia|espectadores|presentador|presentadora|pr[oó]ximo\s+programa|despu[eé]s\s+de\s+la\s+pausa|buenas\s+noches[, ]+son\s+las)\b""",
        RegexOption.IGNORE_CASE
    )
    private val meeting = Regex(
        """\b(?:orden\s+del\s+d[ií]a|siguiente\s+punto|acta\s+de\s+la\s+reuni[oó]n|compartir\s+pantalla|equipo\s+de\s+trabajo|asistentes|cerramos\s+la\s+reuni[oó]n|reuni[oó]n\s+de\s+equipo)\b""",
        RegexOption.IGNORE_CASE
    )
    private val dialogueLine = Regex("""(?m)^\s*[-–—]\s+\S+""")

    fun classify(rawText: String): AmbientContextSignal? {
        val text = rawText.trim()
        if (text.isBlank() || personalAction.containsMatchIn(text)) return null

        val normalized = normalize(text)
        if (normalized.isBlank()) return null

        if (music.containsMatchIn(normalized)) {
            return AmbientContextSignal(AmbientContextCategory.MUSIC, 0.96f, "music_tag")
        }
        if (broadcast.containsMatchIn(normalized)) {
            return AmbientContextSignal(AmbientContextCategory.TELEVISION, 0.94f, "broadcast_cue")
        }
        if (meeting.containsMatchIn(normalized)) {
            return AmbientContextSignal(AmbientContextCategory.MEETING, 0.90f, "meeting_cue")
        }

        val dialogueTurns = dialogueLine.findAll(text).count()
        if (dialogueTurns >= 2) {
            return AmbientContextSignal(AmbientContextCategory.CONVERSATION, 0.88f, "dialogue_turns")
        }
        if (text.startsWith("¿") && text.endsWith("?") && normalized.split(' ').size >= 4) {
            return AmbientContextSignal(AmbientContextCategory.CONVERSATION, 0.84f, "environmental_question")
        }
        return null
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace(Regex("[^\\p{L}\\p{N}\\s¿?¡!.,:;\\-–—]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}

data class AmbientContextConfig(
    val enabled: Boolean,
    val activeStartHour: Int,
    val activeEndHour: Int,
    val excludeHome: Boolean,
    val excludeWork: Boolean
)

enum class AmbientPlaceKind { HOME, WORK, OTHER, UNKNOWN }

object AmbientContextPolicy {
    sealed interface Decision {
        data object Allow : Decision
        data class Suppress(val reason: String) : Decision
    }

    fun evaluate(
        config: AmbientContextConfig,
        hourOfDay: Int,
        place: AmbientPlaceKind,
        deviceMediaPlaying: Boolean
    ): Decision {
        if (!config.enabled) return Decision.Suppress("disabled")
        if (deviceMediaPlaying) return Decision.Suppress("device_media")
        if (!isHourActive(hourOfDay, config.activeStartHour, config.activeEndHour)) {
            return Decision.Suppress("outside_schedule")
        }
        if (config.excludeHome && place == AmbientPlaceKind.HOME) {
            return Decision.Suppress("excluded_home")
        }
        if (config.excludeWork && place == AmbientPlaceKind.WORK) {
            return Decision.Suppress("excluded_work")
        }
        return Decision.Allow
    }

    internal fun isHourActive(hourOfDay: Int, startHour: Int, endHour: Int): Boolean {
        val hour = hourOfDay.coerceIn(0, 23)
        val start = startHour.coerceIn(0, 23)
        val end = endHour.coerceIn(0, 23)
        if (start == end) return true
        return if (start < end) hour in start until end else hour >= start || hour < end
    }
}

object AmbientContextAggregation {
    const val MERGE_GAP_MS = 45L * 60L * 1000L
    const val CHANGE_COOLDOWN_MS = 15L * 60L * 1000L
    const val MAX_BLOCKS_PER_DAY = 12

    sealed interface Decision {
        data object Insert : Decision
        data class Merge(val event: TimelineEvent) : Decision
        data class Suppress(val reason: String) : Decision
    }

    fun decide(
        latest: TimelineEvent?,
        category: AmbientContextCategory,
        nowMs: Long,
        blocksToday: Int
    ): Decision {
        if (latest == null) {
            return if (blocksToday >= MAX_BLOCKS_PER_DAY) {
                Decision.Suppress("daily_limit")
            } else {
                Decision.Insert
            }
        }
        val latestEnd = latest.endTimestamp ?: latest.timestamp
        val elapsed = (nowMs - latestEnd).coerceAtLeast(0L)
        val latestCategory = categoryFromData(latest.dataJson)
        if (latestCategory == category && elapsed <= MERGE_GAP_MS) {
            return Decision.Merge(latest)
        }
        if (elapsed < CHANGE_COOLDOWN_MS) return Decision.Suppress("change_cooldown")
        if (blocksToday >= MAX_BLOCKS_PER_DAY) return Decision.Suppress("daily_limit")
        return Decision.Insert
    }

    fun dataJson(category: AmbientContextCategory, samples: Int): String =
        "{\"category\":\"${category.name}\",\"samples\":${samples.coerceAtLeast(1)}}"

    fun categoryFromData(dataJson: String?): AmbientContextCategory? {
        val raw = dataJson
            ?.let { CATEGORY_REGEX.find(it)?.groupValues?.getOrNull(1) }
            ?: return null
        return runCatching { AmbientContextCategory.valueOf(raw) }.getOrNull()
    }

    fun samplesFromData(dataJson: String?): Int =
        dataJson?.let { SAMPLES_REGEX.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            ?.coerceAtLeast(1)
            ?: 1

    private val CATEGORY_REGEX = Regex("\\\"category\\\":\\\"([A-Z_]+)\\\"")
    private val SAMPLES_REGEX = Regex("\\\"samples\\\":(\\d+)")
}
