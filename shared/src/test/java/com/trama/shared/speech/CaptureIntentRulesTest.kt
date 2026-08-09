package com.trama.shared.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureIntentRulesTest {
    @Test
    fun `strict grammar covers the complete action vocabulary without expanded phrases`() {
        val detector = IntentDetector()
        assertTrue(CaptureIntentRules.ACTION_VERBS.size >= 60)

        CaptureIntentRules.ACTION_VERBS.forEach { verb ->
            val result = detector.detect("tengo que $verb algo mañana")
            assertEquals(verb, "tareas", result?.pattern?.id)
            assertTrue(verb, result?.scoreReasons?.contains("structural_action") == true)
        }
    }

    @Test
    fun `strict grammar rejects third person negated and incomplete action matrices`() {
        val detector = IntentDetector()
        val thirdPersonSubjects = listOf("Pablo", "Lara", "mi jefe", "la presentadora", "el profesor", "alguien")
        val reportedPrefixes = listOf("Pablo dijo que", "la presentadora dice que", "en la reunión comentaron que")

        CaptureIntentRules.ACTION_VERBS.forEach { verb ->
            thirdPersonSubjects.forEach { subject ->
                assertNull("third person $subject $verb", detector.detect("$subject tiene que $verb algo mañana"))
            }
            reportedPrefixes.forEach { prefix ->
                assertNull("reported $prefix $verb", detector.detect("$prefix tengo que $verb algo mañana"))
            }
            assertNull("negated $verb", detector.detect("no tengo que $verb nada mañana"))
            assertNull("incomplete $verb", detector.detect("tengo que $verb"))
        }
    }

    @Test
    fun `profiles deliberately widen recall and mark weak ownership`() {
        val strict = IntentDetector()
        val balanced = IntentDetector().apply { setCaptureProfile(CaptureProfile.BALANCED) }
        val sensitive = IntentDetector().apply { setCaptureProfile(CaptureProfile.SENSITIVE) }

        assertNull(strict.detect("hay que comprar pan mañana"))
        assertTrue(balanced.detect("hay que comprar pan mañana")?.scoreReasons?.contains("weak_ownership") == true)
        assertNull(balanced.detect("tiene dentista el lunes"))
        assertTrue(sensitive.detect("tiene dentista el lunes")?.scoreReasons?.contains("weak_ownership") == true)
    }

    @Test
    fun `classification is independent from gate category`() {
        val fallback = CaptureIntentRules.Classification("tareas", "Tareas")

        assertEquals(
            CaptureIntentRules.Classification("comunicacion", "Comunicación"),
            CaptureIntentRules.classify("tengo que llamar a Elena mañana", fallback)
        )
        assertEquals(
            CaptureIntentRules.Classification("compromisos", "Compromisos"),
            CaptureIntentRules.classify("tengo cita con Ana mañana", fallback)
        )
        assertEquals(
            fallback,
            CaptureIntentRules.classify("tengo que cancelar la cita de mañana", fallback)
        )
    }

    @Test
    fun `strict grammar rejects reported and hypothetical actions`() {
        val detector = IntentDetector()

        assertNull(detector.detect("Pablo dijo que tengo que comprar pan"))
        assertNull(detector.detect("no sé si tengo que llamar a Ana"))
        assertNull(detector.detect("en la reunión dijeron que tenemos que cambiar el formato"))
    }
}
