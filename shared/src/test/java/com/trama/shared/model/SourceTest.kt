package com.trama.shared.model

import org.junit.Assert.*
import org.junit.Test

class SourceTest {

    @Test
    fun `enum contains known sources`() {
        val values = Source.entries
        assertEquals(3, values.size)
        assertTrue(values.contains(Source.PHONE))
        assertTrue(values.contains(Source.WATCH))
        assertTrue(values.contains(Source.SCREENSHOT))
    }

    @Test
    fun `valueOf resolves PHONE`() {
        assertEquals(Source.PHONE, Source.valueOf("PHONE"))
    }

    @Test
    fun `valueOf resolves WATCH`() {
        assertEquals(Source.WATCH, Source.valueOf("WATCH"))
    }

    @Test
    fun `valueOf resolves SCREENSHOT`() {
        assertEquals(Source.SCREENSHOT, Source.valueOf("SCREENSHOT"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `valueOf throws for invalid value`() {
        Source.valueOf("TABLET")
    }

    @Test
    fun `name returns correct string`() {
        assertEquals("PHONE", Source.PHONE.name)
        assertEquals("WATCH", Source.WATCH.name)
        assertEquals("SCREENSHOT", Source.SCREENSHOT.name)
    }

    @Test
    fun `ordinal values are sequential`() {
        assertEquals(0, Source.PHONE.ordinal)
        assertEquals(1, Source.WATCH.ordinal)
        assertEquals(2, Source.SCREENSHOT.ordinal)
    }
}
