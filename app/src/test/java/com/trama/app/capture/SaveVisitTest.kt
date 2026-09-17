package com.trama.app.capture

import org.junit.Test

class SaveVisitTest {
    @Test
    fun `completed historical visit is valid`() {
        validateVisitInterval(start = 10, end = 20, now = 30)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `departure before arrival is rejected`() {
        validateVisitInterval(start = 20, end = 10, now = 30)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `future visit cannot be recorded as a memory`() {
        validateVisitInterval(start = 20, end = 40, now = 30)
    }
}
