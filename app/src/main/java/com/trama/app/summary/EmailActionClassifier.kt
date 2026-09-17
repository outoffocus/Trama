package com.trama.app.summary

internal object EmailActionClassifier {
    private val emailAddress = Regex(
        "[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}",
        RegexOption.IGNORE_CASE
    )
    private val emailWords = Regex(
        "\\b(?:email|e-mail|correo(?: electrónico)?)\\b",
        RegexOption.IGNORE_CASE
    )

    fun isEmail(text: String): Boolean =
        emailAddress.containsMatchIn(text) || emailWords.containsMatchIn(text)

    fun recipient(text: String): String? = emailAddress.find(text)?.value
}
