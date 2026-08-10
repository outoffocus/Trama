package com.trama.app.ambient

import com.trama.shared.model.TimelineEvent
import com.trama.shared.model.TimelineEventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientContextTest {

    @Test
    fun `classifies only grounded environmental cues`() {
        assertEquals(
            AmbientContextCategory.MUSIC,
            AmbientContextClassifier.classify("[Música]")?.category
        )
        assertEquals(
            AmbientContextCategory.TELEVISION,
            AmbientContextClassifier.classify("Suscríbete a nuestro canal")?.category
        )
        assertEquals(
            AmbientContextCategory.CONVERSATION,
            AmbientContextClassifier.classify("- Hola, buenas tardes\n- Hola, ¿cómo estás?")?.category
        )
        assertEquals(
            AmbientContextCategory.MEETING,
            AmbientContextClassifier.classify("Pasamos al siguiente punto del orden del día")?.category
        )
        assertNull(AmbientContextClassifier.classify("Hoy ha sido un día bastante largo"))
    }

    @Test
    fun `never takes a personal action away from task capture`() {
        assertNull(
            AmbientContextClassifier.classify(
                "Tengo que preparar el orden del día de la reunión de equipo"
            )
        )
        assertNull(AmbientContextClassifier.classify("Recuérdame suscribirme al canal"))
        assertNull(AmbientContextClassifier.classify("Tengo reunión de equipo mañana a las diez"))
        assertNull(AmbientContextClassifier.classify("Anótalo en el acta de la reunión"))
    }

    @Test
    fun `policy is opt in and supports schedule media and place exclusions`() {
        val base = config()
        assertTrue(
            AmbientContextPolicy.evaluate(base.copy(enabled = false), 12, AmbientPlaceKind.OTHER, false)
                is AmbientContextPolicy.Decision.Suppress
        )
        assertEquals(
            AmbientContextPolicy.Decision.Allow,
            AmbientContextPolicy.evaluate(base, 12, AmbientPlaceKind.OTHER, false)
        )
        assertEquals(
            AmbientContextPolicy.Decision.Suppress("outside_schedule"),
            AmbientContextPolicy.evaluate(base, 2, AmbientPlaceKind.OTHER, false)
        )
        assertEquals(
            AmbientContextPolicy.Decision.Suppress("device_media"),
            AmbientContextPolicy.evaluate(base, 12, AmbientPlaceKind.OTHER, true)
        )
        assertEquals(
            AmbientContextPolicy.Decision.Suppress("excluded_home"),
            AmbientContextPolicy.evaluate(base.copy(excludeHome = true), 12, AmbientPlaceKind.HOME, false)
        )
    }

    @Test
    fun `overnight and all-day schedules are well defined`() {
        assertTrue(AmbientContextPolicy.isHourActive(23, 22, 6))
        assertTrue(AmbientContextPolicy.isHourActive(4, 22, 6))
        assertTrue(!AmbientContextPolicy.isHourActive(12, 22, 6))
        assertTrue(AmbientContextPolicy.isHourActive(12, 0, 0))
    }

    @Test
    fun `nearby equal categories merge without creating phrase spam`() {
        val now = 1_000_000L
        val existing = ambientEvent(
            timestamp = now - 20 * 60_000L,
            endTimestamp = now - 10 * 60_000L,
            category = AmbientContextCategory.TELEVISION
        )
        val decision = AmbientContextAggregation.decide(
            latest = existing,
            category = AmbientContextCategory.TELEVISION,
            nowMs = now,
            blocksToday = 3
        )
        assertTrue(decision is AmbientContextAggregation.Decision.Merge)
        assertEquals(1, AmbientContextAggregation.samplesFromData(existing.dataJson))
    }

    @Test
    fun `category changes are cooled down and daily blocks are capped`() {
        val now = 2_000_000L
        val recent = ambientEvent(
            timestamp = now - 5 * 60_000L,
            endTimestamp = now - 5 * 60_000L,
            category = AmbientContextCategory.MUSIC
        )
        assertEquals(
            AmbientContextAggregation.Decision.Suppress("change_cooldown"),
            AmbientContextAggregation.decide(recent, AmbientContextCategory.CONVERSATION, now, 2)
        )
        assertEquals(
            AmbientContextAggregation.Decision.Suppress("daily_limit"),
            AmbientContextAggregation.decide(
                recent.copy(timestamp = 1L, endTimestamp = 1L),
                AmbientContextCategory.CONVERSATION,
                now,
                AmbientContextAggregation.MAX_BLOCKS_PER_DAY
            )
        )
    }

    @Test
    fun `persisted ambient metadata contains no transcript field`() {
        assertEquals(
            "{\"category\":\"TELEVISION\",\"samples\":2}",
            AmbientContextAggregation.dataJson(AmbientContextCategory.TELEVISION, 2)
        )
    }

    private fun config() = AmbientContextConfig(
        enabled = true,
        activeStartHour = 7,
        activeEndHour = 23,
        excludeHome = false,
        excludeWork = false
    )

    private fun ambientEvent(
        timestamp: Long,
        endTimestamp: Long,
        category: AmbientContextCategory
    ) = TimelineEvent(
        id = 1,
        type = TimelineEventType.AMBIENT_CONTEXT,
        timestamp = timestamp,
        endTimestamp = endTimestamp,
        title = category.title,
        dataJson = AmbientContextAggregation.dataJson(category, 1)
    )
}
