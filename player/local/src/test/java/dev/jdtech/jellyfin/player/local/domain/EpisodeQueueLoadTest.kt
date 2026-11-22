package dev.jdtech.jellyfin.player.local.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EpisodeQueueLoadTest {
    @Test
    fun `delayed neighbor cannot insert after leaving and returning to the same episode`() =
        runTest {
            val loader = EpisodeQueueLoad()
            val timeline = mutableListOf(1, 2)
            val delayed = CompletableDeferred<Int>()
            val original = loader.begin()
            var currentEpisode = 2
            val previous = PlaylistNavigationDirection.Previous
            val next = PlaylistNavigationDirection.Next

            launch {
                loader.load(
                    original,
                    isAnchorCurrent = { currentEpisode == 2 },
                    fetch = { if (it == next) delayed.await() else null },
                    insert = { _, episode ->
                        timeline.add(timeline.indexOf(currentEpisode) + 1, episode)
                    },
                )
            }
            runCurrent()

            currentEpisode = 1
            loader.begin()
            delayed.complete(3)
            runCurrent()
            assertEquals(listOf(1, 2), timeline)

            currentEpisode = 2
            val delayedAgain = CompletableDeferred<Int>()
            val second = loader.begin()
            launch {
                loader.load(
                    second,
                    isAnchorCurrent = { currentEpisode == 2 },
                    fetch = { if (it == next) delayedAgain.await() else null },
                    insert = { _, episode ->
                        timeline.add(timeline.indexOf(currentEpisode) + 1, episode)
                    },
                )
            }
            runCurrent()
            currentEpisode = 1
            loader.begin()
            currentEpisode = 2
            val latest = loader.begin()
            loader.load(
                latest,
                isAnchorCurrent = { currentEpisode == 2 },
                fetch = { if (it == previous) null else 3 },
                insert = { _, episode ->
                    timeline.add(timeline.indexOf(currentEpisode) + 1, episode)
                },
            )
            delayedAgain.complete(3)
            runCurrent()
            assertEquals(listOf(1, 2, 3), timeline)
        }
}
