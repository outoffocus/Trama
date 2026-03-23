package com.mydiary.shared.speech

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json

/**
 * A semantic intent pattern that groups multiple trigger phrases under one intent.
 *
 * Users can edit the trigger phrases (add, remove) and the regex is auto-compiled.
 * For example, the "pendiente" pattern has triggers: "tengo que", "tendría que",
 * "hay que", "debería", "necesito", etc.
 *
 * Shared between phone and watch modules.
 */
@Serializable
data class IntentPattern(
    /** Unique identifier, e.g. "pendiente", "cita", "contacto" */
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
         * Deserialize patterns from JSON.
         * Merges with defaults: keeps user customizations, adds any new built-in patterns.
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
         * Merge stored patterns with defaults.
         * - Keeps user modifications (triggers, label, enabled state)
         * - Adds new built-in patterns that weren't in storage
         * - Preserves custom user patterns
         */
        private fun mergeWithDefaults(stored: List<IntentPattern>): List<IntentPattern> {
            val storedIds = stored.map { it.id }.toSet()
            val result = stored.toMutableList()

            for (default in DEFAULTS) {
                if (default.id !in storedIds) {
                    result.add(default)
                }
            }

            return result
        }

        // ── Default patterns ────────────────────────────────────────────────

        val DEFAULTS: List<IntentPattern> = listOf(

            IntentPattern(
                id = "pendiente",
                label = "Pendientes",
                triggers = listOf(
                    "tengo que", "tendría que", "tenemos que", "tendríamos que",
                    "hay que", "habría que",
                    "debo", "debería", "deberíamos", "deberías",
                    "necesito", "necesitamos", "necesitaría",
                    "me falta", "falta", "faltaría",
                    "me toca", "toca", "tocaría"
                )
            ),

            IntentPattern(
                id = "recordar",
                label = "Recordar",
                triggers = listOf(
                    "no olvidar", "no te olvides de", "no se me olvide",
                    "que no se me olvide", "que no se me pase",
                    "acuérdate de", "acuérdate", "acordarme de",
                    "recuerda", "recordar",
                    "se me olvidó", "se me fue", "se me pasó", "se me escapó",
                    "casi se me pasa", "casi se me olvida",
                    "no me acordé de", "se me fue de la cabeza",
                    "tengo pendiente",
                    "antes de que se me olvide"
                )
            ),

            IntentPattern(
                id = "cita",
                label = "Citas",
                triggers = listOf(
                    "tengo cita", "cita con", "cita en", "cita para",
                    "he quedado", "quedamos", "quedo con", "quedo a",
                    "me han dado hora",
                    "tengo reunión", "reunión con", "reunión de", "reunión a",
                    "reserva para", "reserva a", "reserva en"
                )
            ),

            IntentPattern(
                id = "horario",
                label = "Agenda",
                triggers = listOf(
                    "mañana a las", "mañana tengo", "mañana hay", "mañana me", "mañana voy",
                    "pasado mañana",
                    "el lunes", "el martes", "el miércoles", "el jueves",
                    "el viernes", "el sábado", "el domingo",
                    "esta semana tengo", "esta semana hay",
                    "este fin de semana"
                )
            ),

            IntentPattern(
                id = "contacto",
                label = "Contactar",
                triggers = listOf(
                    "llama a", "llamar a",
                    "escríbele a", "escríbele", "escribirle a",
                    "dile a", "decirle a",
                    "mándale", "mandarle",
                    "envíale", "enviarle",
                    "habla con", "hablar con",
                    "pregúntale a", "preguntar a",
                    "avisa a", "avisar a",
                    "contacta con", "contactar con"
                )
            ),

            IntentPattern(
                id = "urgente",
                label = "Urgente",
                triggers = listOf(
                    "es para ya", "es urgente",
                    "corre prisa", "cuanto antes",
                    "no puede esperar", "es prioritario",
                    "de manera urgente", "lo antes posible"
                )
            ),

            IntentPattern(
                id = "decision",
                label = "Decisiones",
                triggers = listOf(
                    "vale pues", "lo dejamos en",
                    "al final vamos a", "al final hacemos", "al final quedamos",
                    "entonces hacemos", "entonces quedamos", "entonces vamos",
                    "quedamos en que",
                    "hemos decidido", "he decidido", "decidimos",
                    "la decisión es"
                )
            ),

            IntentPattern(
                id = "idea",
                label = "Ideas",
                triggers = listOf(
                    "oye y si", "y si hacemos", "y si probamos", "y si intentamos",
                    "yo creo que", "igual podríamos",
                    "a lo mejor",
                    "se me ocurre", "se me ha ocurrido",
                    "qué tal si", "podríamos",
                    "estaría bien si", "he pensado que"
                )
            ),

            IntentPattern(
                id = "problema",
                label = "Situaciones",
                triggers = listOf(
                    "el tema es que", "lo que pasa es",
                    "resulta que", "es que no",
                    "la cosa es que", "el problema es",
                    "ha pasado que", "sucede que", "pasa que"
                )
            ),

            IntentPattern(
                id = "nota",
                label = "Notas",
                triggers = listOf(
                    "por cierto", "que se me olvidaba",
                    "una cosa", "oye mira",
                    "ah sí", "otra cosa",
                    "antes de que se me olvide"
                )
            ),

            IntentPattern(
                id = "compra",
                label = "Compras",
                triggers = listOf(
                    "comprar", "hay que comprar", "necesito comprar",
                    "lista de la compra",
                    "ir al super", "ir a comprar"
                )
            ),

            IntentPattern(
                id = "hecho",
                label = "Completado",
                triggers = listOf(
                    "ya está", "ya lo hice", "ya lo hicimos", "ya lo terminé",
                    "ya queda",
                    "listo", "hecho", "terminado", "completado",
                    "pues nada", "ya quedó"
                )
            ),
        )
    }
}
