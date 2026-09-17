package com.trama.shared.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecordingKeyPointsTest {
    @Test
    fun `round trip preserves normalized points`() {
        val encoded = RecordingKeyPoints.encode(listOf(" Decisión uno ", "Acción dos"))

        assertEquals(listOf("Decisión uno", "Acción dos"), RecordingKeyPoints.decode(encoded))
    }

    @Test
    fun `old newline storage remains visible`() {
        assertEquals(
            listOf("Primer punto", "Segundo punto"),
            RecordingKeyPoints.decode("Primer punto\n- Segundo punto")
        )
    }

    @Test
    fun `empty points are not stored`() {
        assertNull(RecordingKeyPoints.encode(listOf(" ")))
        assertEquals(emptyList<String>(), RecordingKeyPoints.decode(null))
    }
}
