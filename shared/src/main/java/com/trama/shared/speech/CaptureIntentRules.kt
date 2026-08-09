package com.trama.shared.speech

/** User-facing precision profile for the lightweight capture gate. */
enum class CaptureProfile {
    STRICT,
    BALANCED,
    SENSITIVE
}

/** Compact grammatical rules used instead of expanding hundreds of literal phrases. */
object CaptureIntentRules {
    data class RuleMatch(
        val intentId: String,
        val label: String,
        val trigger: String,
        val matchEndExclusive: Int,
        val confidence: Float,
        val weakOwnership: Boolean,
        val reasons: List<String>
    )

    data class Classification(val id: String, val label: String)

    val ACTION_VERBS: Set<String> = setOf(
        "abrir", "actualizar", "anadir", "anotar", "apagar", "apuntar", "arreglar",
        "avisar", "bajar", "bloquear", "borrar", "buscar", "cambiar", "cancelar",
        "cargar", "cerrar", "cobrar", "coger", "comprar", "comprobar", "confirmar",
        "contestar", "copiar", "corregir", "crear", "dejar", "descargar", "devolver",
        "enviar", "escribir", "felicitar", "firmar", "guardar", "hacer", "imprimir",
        "instalar", "ir", "limpiar", "llamar", "llevar", "mandar", "mirar", "pagar", "hablar",
        "pasar", "pedir", "preparar", "programar", "recoger", "reclamar", "recordar",
        "renovar", "reparar", "responder", "reservar", "revisar", "sacar", "subir",
        "terminar", "traer", "tramitar", "validar", "verificar"
    )

    private val OWNED_ACTION = Regex("(?:^|\\s)(tengo|tenemos)\\s+que\\s+([\\p{L}]+)(?=\\s|$)")
    private val NEED_ACTION = Regex("(?:^|\\s)(necesito|necesitamos)\\s+([\\p{L}]+)(?=\\s|$)")
    private val IMPERSONAL_ACTION = Regex("(?:^|\\s)hay\\s+que\\s+([\\p{L}]+)(?=\\s|$)")
    private val DIRECTED_ACTION = Regex("(?:^|\\s)tienes\\s+que\\s+([\\p{L}]+)(?=\\s|$)")
    private val THIRD_PERSON_COMMITMENT = Regex(
        "(?:^|\\s)tiene\\s+(?:la\\s+)?(cita|reunion|itv|medico|dentista)(?=\\s|$)"
    )
    private val REMEMBER_ACTION = Regex("(?:^|\\s)recordar(?:\\s+(?:que|de))?\\s+([\\p{L}]+)(?=\\s|$)")
    private val FORGOT_ACTION = Regex("(?:^|\\s)me\\s+olvide\\s+([\\p{L}]+)(?=\\s|$)")
    private val COMMUNICATION = Regex("(?:^|\\s)(llamar|escribir|enviar|mandar|contestar|responder|avisar|felicitar|hablar)(?=\\s|$)")
    private val COMMITMENT = Regex(
        "(?:^|\\s)(?:(?:tengo|tenemos)\\s+(?:la\\s+)?(?:cita|reunion|itv|medico|dentista)|he\\s+quedado)(?=\\s|$)"
    )
    private val REMINDER = Regex("(?:^|\\s)(recuerdame|acordarme|acordarnos|olvidar|nota mental)(?=\\s|$)")

    fun find(text: String, profile: CaptureProfile): RuleMatch? {
        findAction(text, OWNED_ACTION, profile, baseConfidence = 0.88f, weak = false)?.let { return it }
        findAction(text, NEED_ACTION, profile, baseConfidence = 0.86f, weak = false)?.let { return it }
        findRememberAction(text, profile)?.let { return it }
        findForgotAction(text, profile)?.let { return it }
        if (profile != CaptureProfile.STRICT) {
            findAction(text, IMPERSONAL_ACTION, profile, baseConfidence = 0.74f, weak = true)?.let { return it }
        }
        if (profile == CaptureProfile.SENSITIVE) {
            findAction(text, DIRECTED_ACTION, profile, baseConfidence = 0.72f, weak = true)?.let { return it }
            THIRD_PERSON_COMMITMENT.find(text)?.let { match ->
                if (hasComplement(text, match.range.last + 1)) {
                    return RuleMatch(
                        intentId = "compromisos",
                        label = "Compromisos",
                        trigger = match.value.trim(),
                        matchEndExclusive = match.range.last + 1,
                        confidence = 0.72f,
                        weakOwnership = true,
                        reasons = listOf("third_person_commitment", "weak_ownership", "has_complement")
                    )
                }
            }
        }
        return null
    }

    private fun findAction(
        text: String,
        regex: Regex,
        profile: CaptureProfile,
        baseConfidence: Float,
        weak: Boolean
    ): RuleMatch? {
        regex.findAll(text).forEach { match ->
            if (isNegated(text, match.range.first)) return@forEach
            if (isReportedOrHypothetical(text, match.range.first)) return@forEach
            val rawVerb = match.groupValues.last()
            val normalizedVerb = normalizeVerb(rawVerb, profile) ?: return@forEach
            if (!hasComplement(text, match.range.last + 1)) return@forEach
            val drifted = normalizedVerb != rawVerb && rawVerb + "r" == normalizedVerb
            val cliticNormalized = normalizedVerb != rawVerb && !drifted
            return RuleMatch(
                intentId = "tareas",
                label = "Tareas",
                trigger = match.value.trim(),
                matchEndExclusive = match.range.last + 1,
                confidence = (baseConfidence - if (drifted) 0.06f else 0f).coerceAtLeast(0f),
                weakOwnership = weak || drifted,
                reasons = buildList {
                    add("structural_action")
                    add("action_verb:$normalizedVerb")
                    if (weak) add("weak_ownership") else add("owned_structure")
                    if (drifted) {
                        add("asr_missing_final_r")
                        add("weak_ownership")
                    }
                    if (cliticNormalized) add("verb_with_clitic:$rawVerb")
                    add("has_complement")
                }
            )
        }
        return null
    }

    private fun findRememberAction(text: String, profile: CaptureProfile): RuleMatch? {
        REMEMBER_ACTION.findAll(text).forEach { match ->
            if (isNegated(text, match.range.first)) return@forEach
            val rawVerb = match.groupValues[1]
            val normalizedVerb = normalizeVerb(rawVerb, profile) ?: return@forEach
            if (!hasComplement(text, match.range.last + 1)) return@forEach
            return RuleMatch(
                intentId = "recordatorios",
                label = "Recordatorios",
                trigger = match.value.trim(),
                matchEndExclusive = match.range.last + 1,
                confidence = 0.88f,
                weakOwnership = false,
                reasons = listOf("structural_reminder", "action_verb:$normalizedVerb", "has_complement")
            )
        }
        return null
    }

    private fun findForgotAction(text: String, profile: CaptureProfile): RuleMatch? {
        FORGOT_ACTION.findAll(text).forEach { match ->
            val rawVerb = match.groupValues[1]
            val normalizedVerb = normalizeVerb(rawVerb, profile) ?: return@forEach
            if (!hasComplement(text, match.range.last + 1)) return@forEach
            return RuleMatch(
                intentId = "recordatorios",
                label = "Recordatorios",
                trigger = match.value.trim(),
                matchEndExclusive = match.range.last + 1,
                confidence = 0.84f,
                weakOwnership = false,
                reasons = listOf("structural_forgot", "action_verb:$normalizedVerb", "has_complement")
            )
        }
        return null
    }

    private fun normalizeVerb(token: String, profile: CaptureProfile): String? {
        if (token in ACTION_VERBS) return token
        listOf("les", "las", "los", "nos", "me", "te", "se", "lo", "la", "le").forEach { suffix ->
            if (token.endsWith(suffix)) {
                val base = token.removeSuffix(suffix)
                if (base in ACTION_VERBS) return base
            }
        }
        if (profile != CaptureProfile.STRICT) {
            val restored = "${token}r"
            if (restored in ACTION_VERBS) return restored
        }
        return null
    }

    private fun hasComplement(text: String, endExclusive: Int): Boolean = text
        .substring(endExclusive.coerceAtMost(text.length))
        .trim()
        .split(" ")
        .any { it.length >= 2 }

    private fun isNegated(text: String, matchStart: Int): Boolean {
        val prefix = text.substring(0, matchStart.coerceAtLeast(0)).trimEnd()
        return Regex("(?:^|\\s)no(?:\\s+(?:tengo|tenemos)\\s+que)?$").containsMatchIn(prefix)
    }

    private fun isReportedOrHypothetical(text: String, matchStart: Int): Boolean {
        val prefix = text.substring(0, matchStart.coerceAtLeast(0)).trimEnd()
        return Regex(
            "(?:^|\\s)(?:si|segun|dice|dicen|dijo|dijeron|decia|comento|comentaron)(?:\\s+que)?$"
        ).containsMatchIn(prefix)
    }

    /** Classification runs after final transcription and does not affect gate activation. */
    fun classify(text: String, fallback: Classification): Classification {
        val normalized = IntentPattern.normalizeTrigger(text)
        return when {
            COMMUNICATION.containsMatchIn(normalized) -> Classification("comunicacion", "Comunicación")
            COMMITMENT.containsMatchIn(normalized) -> Classification("compromisos", "Compromisos")
            REMINDER.containsMatchIn(normalized) -> Classification("recordatorios", "Recordatorios")
            else -> fallback
        }
    }
}
