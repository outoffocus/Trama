package com.trama.app.summary

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DailyPageMarkdownStore(
    context: Context
) {
    private val appContext = context.applicationContext
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val rootDir: File by lazy {
        File(appContext.filesDir, "daily-pages").apply { mkdirs() }
    }

    fun fileFor(dayStartMillis: Long): File =
        File(rootDir, "${dateFormat.format(Date(dayStartMillis))}.md")

    fun write(dayStartMillis: Long, markdown: String): String {
        val file = fileFor(dayStartMillis)
        file.parentFile?.mkdirs()
        file.writeText(markdown)
        return file.absolutePath
    }
}
