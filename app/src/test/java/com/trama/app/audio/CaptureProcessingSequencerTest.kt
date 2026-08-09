package com.trama.app.audio

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureProcessingSequencerTest {
    @Test
    fun `overlapping captures are processed in arrival order without overlap`() = runTest {
        val sequencer = CaptureProcessingSequencer()
        val releaseFirst = CompletableDeferred<Unit>()
        val firstEntered = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        val first = async {
            sequencer.process {
                events += "first-start"
                firstEntered.complete(Unit)
                releaseFirst.await()
                events += "first-end"
            }
        }
        firstEntered.await()
        val second = async {
            sequencer.process {
                events += "second-start"
                events += "second-end"
            }
        }
        yield()
        assertEquals(listOf("first-start"), events)

        releaseFirst.complete(Unit)
        first.await()
        second.await()
        assertEquals(
            listOf("first-start", "first-end", "second-start", "second-end"),
            events
        )
    }

    @Test
    fun `cancelling owner releases pipeline for next capture`() = runTest {
        val sequencer = CaptureProcessingSequencer()
        val entered = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()

        val first = async {
            sequencer.process {
                entered.complete(Unit)
                never.await()
            }
        }
        entered.await()
        val second = async { sequencer.process { "processed" } }

        first.cancelAndJoin()

        assertEquals("processed", second.await())
    }
}
