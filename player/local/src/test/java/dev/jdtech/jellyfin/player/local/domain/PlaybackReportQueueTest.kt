package dev.jdtech.jellyfin.player.local.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class PlaybackReportQueueTest {
    @Test
    fun `pending progress finishes before departure and next start even when closed`() = runTest {
        val events = mutableListOf<String>()
        val progress = CompletableDeferred<Unit>()
        val queue = PlaybackReportQueue(backgroundScope) { throw it }

        queue.enqueue {
            progress.await()
            events.add("episode 1 progress")
        }
        runCurrent()
        queue.enqueue { events.add("episode 1 stop") }
        queue.enqueue { events.add("episode 2 start") }
        queue.close()
        assertEquals(emptyList<String>(), events)

        progress.complete(Unit)
        runCurrent()
        assertEquals(listOf("episode 1 progress", "episode 1 stop", "episode 2 start"), events)
    }
}
