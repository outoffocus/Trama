package com.trama.shared.speech

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import java.text.Normalizer

/** A user-visible capture category and its explicit high-intent phrases. */
@Serializable
data class IntentPattern(
    val id: String,
    val label: String,
    val triggers: List<String>,
    val captureAll: Boolean = true,
    val enabled: Boolean = true,
    val isCustom: Boolean = false,
    /** Built-in preset revision. Zero identifies settings written by older releases. */
    val presetVersion: Int = 0
) {
    @Transient
    val regex: Regex = buildRegex(triggers)

    @Transient
    val normalizedTriggers: List<String> = triggers
        .map(::normalizeTrigger)
        .filter { it.isNotBlank() }
        .distinct()

    companion object {
        const val CURRENT_PRESET_VERSION = 2

        private val json = Json { ignoreUnknownKeys = true }

        fun buildRegex(triggers: List<String>): Regex {
            if (triggers.none { it.isNotBlank() }) return Regex("(?!)")
            val pattern = triggers
                .filter { it.isNotBlank() }
                .sortedByDescending { it.length }
                .joinToString("|") { trigger ->
                    trigger.trim()
                        .split("\\s+".toRegex())
                        .joinToString("\\s+") { word -> Regex.escape(word) }
                }
            return runCatching {
                Regex("(?<![\\p{L}\\p{N}])($pattern)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
            }.getOrElse { Regex("(?!)") }
        }

        fun serialize(patterns: List<IntentPattern>): String = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(serializer()),
            patterns
        )

        /**
         * Reads user settings and performs a one-way migration from the old expanded preset.
         * Once migrated, built-in trigger lists are authoritative: removing a phrase stays removed.
         */
        fun deserialize(jsonStr: String): List<IntentPattern> = runCatching {
            mergeWithDefaults(json.decodeFromString<List<IntentPattern>>(jsonStr))
        }.getOrElse { DEFAULTS }

        private fun mergeWithDefaults(stored: List<IntentPattern>): List<IntentPattern> {
            val defaultIds = DEFAULTS.mapTo(mutableSetOf()) { it.id }
            val storedById = stored.associateBy { it.id }
            return buildList {
                DEFAULTS.forEach { default ->
                    add(storedById[default.id]?.let { migrateBuiltIn(default, it) } ?: default)
                }
                stored.filterTo(this) { it.isCustom && it.id !in defaultIds }
            }
        }

        private fun migrateBuiltIn(default: IntentPattern, stored: IntentPattern): IntentPattern {
            if (stored.presetVersion >= CURRENT_PRESET_VERSION) {
                return stored.copy(
                    label = stored.label.ifBlank { default.label },
                    triggers = stored.triggers.distinctBy(::normalizeTrigger)
                )
            }

            val knownLegacy = legacyTriggersFor(default.id)
            val userAdditions = stored.triggers.filter { trigger ->
                trigger.isNotBlank() && normalizeTrigger(trigger) !in knownLegacy
            }
            return stored.copy(
                label = stored.label.ifBlank { default.label },
                triggers = (default.triggers + userAdditions).distinctBy(::normalizeTrigger),
                presetVersion = CURRENT_PRESET_VERSION
            )
        }

        /** Compact preset: grammar and action vocabulary live in CaptureIntentRules. */
        val DEFAULTS: List<IntentPattern> = listOf(
            IntentPattern(
                id = "recordatorios",
                label = "Recordatorios",
                triggers = listOf(
                    "recuérdame",
                    "acordarme de",
                    "acordarnos de",
                    "me tengo que acordar de",
                    "tengo que acordarme de",
                    "no olvidar",
                    "no olvidarme de",
                    "no olvidarnos de",
                    "nota mental"
                ),
                presetVersion = CURRENT_PRESET_VERSION
            ),
            IntentPattern(
                id = "tareas",
                label = "Tareas",
                triggers = listOf(
                    "tengo pendiente",
                    "queda pendiente",
                    "pendiente de",
                    "falta por"
                ),
                presetVersion = CURRENT_PRESET_VERSION
            ),
            IntentPattern(
                id = "compromisos",
                label = "Compromisos",
                triggers = listOf(
                    "tengo cita",
                    "tenemos cita",
                    "tengo reunión",
                    "tenemos reunión",
                    "tengo la itv",
                    "tengo itv",
                    "tengo médico",
                    "tengo dentista",
                    "he quedado con"
                ),
                presetVersion = CURRENT_PRESET_VERSION
            )
        )

        private val LEGACY_ACTION_VERBS = setOf(
            "abrir", "actualizar", "añadir", "anotar", "apagar", "apuntar", "arreglar",
            "avisar", "bajar", "bloquear", "borrar", "buscar", "cambiar", "cancelar",
            "cargar", "cerrar", "cobrar", "coger", "comprar", "comprobar", "confirmar",
            "contestar", "copiar", "corregir", "crear", "dejar", "descargar", "devolver",
            "enviar", "escribir", "felicitar", "firmar", "guardar", "hacer", "imprimir",
            "instalar", "limpiar", "llamar", "llevar", "mandar", "mirar", "pagar", "pasar",
            "pedir", "preparar", "programar", "recoger", "reclamar", "recordar", "renovar",
            "reparar", "responder", "reservar", "revisar", "sacar", "subir", "terminar",
            "traer", "tramitar", "validar", "verificar"
        )

        private val LEGACY_SHARED = setOf(
            "tengo que", "tenemos que", "tienes que", "hay que", "he de", "debo", "debemos",
            "necesito", "necesitamos", "quiero", "queremos", "voy a", "vamos a",
            "recordar", "recordar que", "recordatorio", "apunta", "apúntame", "anota",
            "recuerdame", "recuérdame", "acuerdate de", "acuérdate de", "me olvide",
            "me olvidé", "tengo que acordarme que", "tenemos que acordarnos de",
            "tienes que acordarte de", "no te olvides de",
            "recordar que", "recuerdame que", "recuérdame que", "acordarme de",
            "acordarnos de", "me tengo que acordar de", "no olvidar", "no olvidarme de",
            "no olvidarnos de", "se me fue la olla", "me acuerdo", "apuntame", "apúntame",
            "apuntar", "anotar", "nota mental", "mañana tengo que", "mañana hay que",
            "esta tarde tengo que", "esta noche tengo que", "el lunes tengo que",
            "el martes tengo que", "el miercoles tengo que", "el miércoles tengo que",
            "el jueves tengo que", "el viernes tengo que",
            "llamar a", "escribir a", "mandar mensaje a", "contestar a", "responder a",
            "avisar a", "tiene cita", "tiene reunión", "tiene medico", "tiene médico",
            "tiene dentista", "tiene itv", "tiene la itv", "quedé con", "quede con",
            "ha quedado con"
        ).mapTo(mutableSetOf(), ::normalizeTrigger)

        private fun legacyTriggersFor(id: String): Set<String> = buildSet {
            addAll(LEGACY_SHARED)
            DEFAULTS.firstOrNull { it.id == id }?.triggers?.mapTo(this, ::normalizeTrigger)
            if (id == "tareas") {
                LEGACY_ACTION_VERBS.forEach { verb ->
                    val drift = verb.dropLast(1)
                    listOf(
                        "tengo que $verb", "tenemos que $verb",
                        "tengo de $drift", "tenemos de $drift",
                        "tengo que $drift", "tenemos que $drift",
                        "hay que $verb", "necesito $verb",
                        "tengo que $verb a", "tenemos que $verb a",
                        "hay que $verb a", "necesito $verb a"
                    ).mapTo(this, ::normalizeTrigger)
                }
            }
        }

        fun normalizeTrigger(trigger: String): String {
            val decomposed = Normalizer.normalize(trigger.lowercase(), Normalizer.Form.NFD)
            return decomposed
                .replace("\\p{M}+".toRegex(), "")
                .replace("[^\\p{L}\\p{N}\\s]".toRegex(), " ")
                .replace("\\s+".toRegex(), " ")
                .trim()
        }
    }
}
