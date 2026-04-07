package com.trama.app.summary

/**
 * Attempts to fix common JSON syntax errors produced by LLMs.
 */
object JsonRepair {

    /**
     * Extract a JSON object or array from a response that may contain
     * markdown code fences, explanatory text, or other non-JSON content.
     * Then apply structural repairs.
     */
    fun extractAndRepair(raw: String): String {
        val extracted = extractJson(raw)
        return repair(extracted)
    }

    /**
     * Extract the first JSON object {...} or array [...] from text,
     * ignoring markdown fences, leading text, etc.
     */
    fun extractJson(raw: String): String {
        // Step 1: Strip markdown code fences
        var s = raw.trim()
        // Handle ```json ... ``` or ``` ... ```
        val fencePattern = Regex("```(?:json)?\\s*\n?(.*?)```", RegexOption.DOT_MATCHES_ALL)
        val fenceMatch = fencePattern.find(s)
        if (fenceMatch != null) {
            s = fenceMatch.groupValues[1].trim()
        }

        // Step 2: Find the first { or [ and extract from there
        val jsonStart = s.indexOfFirst { it == '{' || it == '[' }
        if (jsonStart > 0) {
            s = s.substring(jsonStart)
        }

        return s.trim()
    }

    fun repair(raw: String): String {
        var s = raw

        // Fix ) used instead of ] — e.g. "text")  →  "text"]
        s = s.replace(Regex(""""(\s*)\)"""), "\"$1]")

        // Remove trailing commas before ] or }
        s = s.replace(Regex(",\\s*]"), "]")
        s = s.replace(Regex(",\\s*\\}"), "}")

        // Close unclosed brackets/braces (truncated output)
        val openBraces = s.count { it == '{' }
        val closeBraces = s.count { it == '}' }
        val openBrackets = s.count { it == '[' }
        val closeBrackets = s.count { it == ']' }
        repeat(openBrackets - closeBrackets) { s += "]" }
        repeat(openBraces - closeBraces) { s += "}" }

        return s
    }
}
