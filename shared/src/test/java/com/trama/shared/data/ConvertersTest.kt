package com.trama.shared.data

import com.trama.shared.model.Source
import org.junit.Assert.*
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `fromSource converts PHONE to string`() {
        assertEquals("PHONE", converters.fromSource(Source.PHONE))
    }

    @Test
    fun `fromSource converts WATCH to string`() {
        assertEquals("WATCH", converters.fromSource(Source.WATCH))
    }

    @Test
    fun `toSource converts PHONE string to enum`() {
        assertEquals(Source.PHONE, converters.toSource("PHONE"))
    }

    @Test
    fun `toSource converts WATCH string to enum`() {
        assertEquals(Source.WATCH, converters.toSource("WATCH"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `toSource throws for invalid string`() {
        converters.toSource("INVALID")
    }

    @Test
    fun `round-trip conversion preserves value`() {
        for (source in Source.entries) {
            val asString = converters.fromSource(source)
            val restored = converters.toSource(asString)
            assertEquals(source, restored)
        }
    }
}
