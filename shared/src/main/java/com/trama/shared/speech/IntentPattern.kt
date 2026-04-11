package com.trama.shared.speech

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import java.text.Normalizer

/**
 * A capture category that groups multiple trigger phrases under one intent.
 *
 * Users can edit the trigger phrases (add, remove) and the regex is auto-compiled.
 * For example, the "recordatorios" category can include phrases like "recordar"
 * or "acordarme de".
 *
 * Shared between phone and watch modules.
 */
@Serializable
data class IntentPattern(
    /** Unique identifier, e.g. "recordatorios", "trabajo", "salud" */
    val id: String,
    /** Human-readable label shown in UI and notifications */
    val label: String,
    /** Trigger phrases — matched with flexible whitespace */
    val triggers: List<String>,
    /** If true, capture the full utterance. If false, capture from the match onward */
    val captureAll: Boolean = true,
    /** Whether this pattern is enabled by the user */
    val enabled: Boolean = true,
    /** Whether this is a user-created pattern (vs built-in) */
    val isCustom: Boolean = false
) {
    /** Compiled regex from triggers. Cached per instance. */
    @Transient
    val regex: Regex = buildRegex(triggers)

    @Transient
    val normalizedTriggers: List<String> = triggers.map(::normalizeTrigger).filter { it.isNotBlank() }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * Build a regex from trigger phrases.
         * Each phrase is matched with flexible whitespace between words.
         * Sorted longest-first so "tengo que ir" matches before "tengo que".
         */
        fun buildRegex(triggers: List<String>): Regex {
            if (triggers.isEmpty()) return Regex("(?!)")  // Never matches

            val pattern = triggers
                .filter { it.isNotBlank() }
                .sortedByDescending { it.length }
                .joinToString("|") { trigger ->
                    trigger.trim()
                        .split("\\s+".toRegex())
                        .joinToString("\\s+") { word -> Regex.escape(word) }
                }

            return try {
                Regex("($pattern)", RegexOption.IGNORE_CASE)
            } catch (_: Exception) {
                Regex("(?!)") // Fallback: never matches if regex is invalid
            }
        }

        /**
         * Serialize a list of patterns to JSON for storage/sync.
         */
        fun serialize(patterns: List<IntentPattern>): String {
            return json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(serializer()),
                patterns
            )
        }

        /**
         * Deserialize categories from JSON.
         * Keeps current built-in categories and preserves any user-created ones.
         */
        fun deserialize(jsonStr: String): List<IntentPattern> {
            return try {
                val stored = json.decodeFromString<List<IntentPattern>>(jsonStr)
                mergeWithDefaults(stored)
            } catch (_: Exception) {
                DEFAULTS
            }
        }

        /**
         * Merge stored categories with defaults.
         * - Keeps user modifications for current built-in categories
         * - Drops removed built-in categories from older versions
         * - Preserves user-created categories
         */
        private fun mergeWithDefaults(stored: List<IntentPattern>): List<IntentPattern> {
            val defaultIds = DEFAULTS.map { it.id }.toSet()
            val storedById = stored.associateBy { it.id }
            val result = mutableListOf<IntentPattern>()

            for (default in DEFAULTS) {
                val storedPattern = storedById[default.id]
                result += if (storedPattern != null) {
                    mergeBuiltInPattern(default, storedPattern)
                } else {
                    default
                }
            }

            stored.filterTo(result) { it.isCustom && it.id !in defaultIds }
            return result
        }

        private fun mergeBuiltInPattern(default: IntentPattern, stored: IntentPattern): IntentPattern {
            val legacyDefaults = LEGACY_DEFAULT_TRIGGER_SETS[default.id].orEmpty()
            val mergedTriggers = linkedSetOf<String>()
            default.triggers.forEach { trigger ->
                if (trigger.isNotBlank()) mergedTriggers += trigger
            }
            stored.triggers.forEach { trigger ->
                val normalized = normalizeTrigger(trigger)
                if (trigger.isNotBlank() && normalized !in legacyDefaults) {
                    mergedTriggers += trigger
                }
            }

            return stored.copy(
                label = stored.label.ifBlank { default.label },
                triggers = mergedTriggers.toList()
            )
        }

        // ── Default patterns ────────────────────────────────────────────────

        val DEFAULTS: List<IntentPattern> = listOf(
            IntentPattern(
                id = "recordatorios",
                label = "Recordatorios",
                triggers = listOf(
                    "recordar",
                    "recuerdame",
                    "recuérdame",
                    "acordarme de",
                    "acordarnos de",
                    "no olvidar"
                )
            ),
            IntentPattern(
                id = "tareas",
                label = "Tareas",
                triggers = listOf(
                    "tengo que",
                    "tenemos que",
                    "hay que",
                    "debo",
                    "debemos",
                    "deberia",
                    "debería",
                    "deberiamos",
                    "deberíamos",
                    "necesito",
                    "necesitamos",
                    "necesitaria",
                    "necesitaría",
                    "necesitariamos",
                    "necesitaríamos"
                )
            ),
            IntentPattern(
                id = "comunicacion",
                label = "Comunicacion",
                triggers = listOf(
                    "llamar a",
                    "escribir a",
                    "mandar mensaje a"
                )
            )
        )

        private val LEGACY_DEFAULT_TRIGGER_SETS: Map<String, Set<String>> = mapOf(
            "recordatorios" to listOf(
                "recordar",
                "recuerdame",
                "recuérdame",
                "acordarme de",
                "acordarnos de",
                "me tengo que acordar de",
                "tengo que",
                "tenemos que",
                "tengo q",
                "tenemos q",
                "hay que",
                "hay q",
                "he de",
                "debo",
                "debemos",
                "deberia",
                "debería",
                "deberiamos",
                "deberíamos",
                "necesito",
                "necesitamos",
                "no olvidar",
                "no olvidarme de",
                "no olvidarnos de",
                "no te olvides de",
                "mañana tengo que",
                "mañana hay que",
                "esta tarde tengo que",
                "esta noche tengo que",
                "el lunes tengo que",
                "el martes tengo que",
                "el miércoles tengo que",
                "el miercoles tengo que",
                "el jueves tengo que",
                "el viernes tengo que",
                "me olvidé",
                "se me fue la olla"
            ).map(::normalizeTrigger).toSet()
        )

        private fun normalizeTrigger(trigger: String): String {
            val decomposed = Normalizer.normalize(trigger.lowercase(), Normalizer.Form.NFD)
            return decomposed
                .replace("\\p{M}+".toRegex(), "")
                .replace("[^\\p{L}\\p{N}\\s]".toRegex(), " ")
                .replace("\\s+".toRegex(), " ")
                .trim()
        }
    }
}
