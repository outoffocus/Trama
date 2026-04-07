package com.trama.app.summary

import android.content.Context
import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.Source
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecordingProcessorTest {

    private lateinit var processor: RecordingProcessor

    @Before
    fun setUp() {
        processor = RecordingProcessor(mockk<Context>(relaxed = true))
    }

    @Test
    fun `validateActionType normalizes unsupported values`() {
        assertEquals("CALL", callPrivate<String>("validateActionType", "call"))
        assertEquals("GENERIC", callPrivate<String>("validateActionType", "archive"))
    }

    @Test
    fun `validatePriority normalizes unsupported values`() {
        assertEquals("LOW", callPrivate<String>("validatePriority", "low"))
        assertEquals("NORMAL", callPrivate<String>("validatePriority", "critical"))
    }

    @Test
    fun `parseSimpleActions reads valid json array`() {
        val actions = callPrivate<List<Any>>(
            "parseSimpleActions",
            """[{"text":"Comprar leche","type":"BUY"},{"text":"Llamar a Juan","type":"CALL"}]"""
        )
        assertEquals(2, actions.size)
        assertEquals("Comprar leche", readField(actions[0], "text"))
        assertEquals("BUY", readField(actions[0], "actionType"))
        assertEquals("Llamar a Juan", readField(actions[1], "text"))
    }

    @Test
    fun `parseSimpleActions returns empty list for invalid payload`() {
        val actions = callPrivate<List<Any>>("parseSimpleActions", "not-json")
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `parseDedupResponse returns matching existing id only`() {
        val existing = listOf(
            diaryEntry(id = 1, text = "Llamar a Juan"),
            diaryEntry(id = 2, text = "Comprar leche")
        )
        val found = callPrivate<Long?>("parseDedupResponse", """{"duplicateOfId":2}""", existing)
        val missing = callPrivate<Long?>("parseDedupResponse", """{"duplicateOfId":99}""", existing)
        val invalid = callPrivate<Long?>("parseDedupResponse", "oops", existing)

        assertEquals(2L, found)
        assertNull(missing)
        assertNull(invalid)
    }

    @Test
    fun `buildPrompt contains transcription and structured keys`() {
        val prompt = callPrivate<String>("buildPrompt", "hoy hable con el cliente")
        assertTrue(prompt.contains("hoy hable con el cliente"))
        assertTrue(prompt.contains("\"title\""))
        assertTrue(prompt.contains("\"summary\""))
        assertTrue(prompt.contains("\"actionItems\""))
    }

    private fun diaryEntry(id: Long, text: String) = DiaryEntry(
        id = id,
        text = text,
        keyword = "test",
        category = "Test",
        confidence = 0.8f,
        source = Source.PHONE,
        duration = 0,
        cleanText = text
    )

    @Suppress("UNCHECKED_CAST")
    private fun <T> callPrivate(name: String, vararg args: Any?): T {
        val method = RecordingProcessor::class.java.declaredMethods.first {
            it.name == name && it.parameterCount == args.size
        }
        method.isAccessible = true
        return method.invoke(processor, *args) as T
    }

    private fun readField(target: Any, name: String): Any? {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(target)
    }
}
