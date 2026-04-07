package com.trama.shared.model

import org.junit.Assert.*
import org.junit.Test

class SourceTest {

    @Test
    fun `enum contains PHONE and WATCH`() {
        val values = Source.entries
        assertEquals(2, values.size)
        assertTrue(values.contains(Source.PHONE))
        assertTrue(values.contains(Source.WATCH))
    }

    @Test
    fun `valueOf resolves PHONE`() {
        assertEquals(Source.PHONE, Source.valueOf("PHONE"))
    }

    @Test
    fun `valueOf resolves WATCH`() {
        assertEquals(Source.WATCH, Source.valueOf("WATCH"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `valueOf throws for invalid value`() {
        Source.valueOf("TABLET")
    }

    @Test
    fun `name returns correct string`() {
        assertEquals("PHONE", Source.PHONE.name)
        assertEquals("WATCH", Source.WATCH.name)
    }

    @Test
    fun `ordinal values are sequential`() {
        assertEquals(0, Source.PHONE.ordinal)
        assertEquals(1, Source.WATCH.ordinal)
    }
}
