package com.trama.app.summary

import android.database.Cursor
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test

class CalendarReadIntegrityTest {
    @Test fun `null cursor is a failure not an empty calendar`() {
        assertTrue(runCatching { CalendarHelper.readEventCursor(null) }.isFailure)
    }

    @Test fun `complete empty cursor is a valid empty calendar`() {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToNext() } returns false
        assertTrue(CalendarHelper.readEventCursor(cursor).isEmpty())
        verify { cursor.close() }
    }

    @Test fun `failure after a row must not return a partial calendar`() {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.moveToNext() } returns true andThenThrows IllegalStateException("provider disconnected")
        assertTrue(runCatching { CalendarHelper.readEventCursor(cursor) }.isFailure)
        verify { cursor.close() }
    }
}
