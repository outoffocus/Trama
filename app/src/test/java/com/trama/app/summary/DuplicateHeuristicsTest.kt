package com.trama.app.summary

import com.trama.shared.model.DiaryEntry
import com.trama.shared.model.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DuplicateHeuristicsTest {

    @Test
    fun `finds duplicate across conversational prefix and modal typo variants`() {
        val existing = listOf(
            entry(1, "Comprar una cafetera nueva")
        )

        val duplicate = DuplicateHeuristics.findLikelyDuplicate(
            text = "Hoy estoy hablando con Elena y tenemso que comprar una cafetera nueva",
            existing = existing
        )

        assertEquals(1L, duplicate?.id)
    }

    @Test
    fun `does not collapse different purchase objects`() {
        val existing = listOf(
            entry(1, "Comprar una cafetera nueva")
        )

        val duplicate = DuplicateHeuristics.findLikelyDuplicate(
            text = "Comprar una mesa nueva",
            existing = existing
        )

        assertNull(duplicate)
    }

    private fun entry(id: Long, text: String) = DiaryEntry(
        id = id,
        text = text,
        keyword = "test",
        category = "Test",
        confidence = 1.0f,
        source = Source.PHONE,
        duration = 0,
        cleanText = text
    )
}
