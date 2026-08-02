package dev.jdtech.jellyfin.film.presentation

import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressTest {
    @Test
    fun `saved progress becomes meaningful at one minute`() {
        assertFalse(hasMeaningfulSavedProgress(playbackPositionTicks = -1L))
        assertFalse(hasMeaningfulSavedProgress(playbackPositionTicks = 0L))
        assertFalse(hasMeaningfulSavedProgress(playbackPositionTicks = 599_999_999L))
        assertTrue(hasMeaningfulSavedProgress(playbackPositionTicks = 600_000_000L))
    }

    @Test
    fun `series playback start prefers next up episode`() {
        val nextUp = dummyEpisode.copy(id = java.util.UUID.randomUUID())
        val fallback = dummyEpisode.copy(id = java.util.UUID.randomUUID())

        assertEquals(
            nextUp,
            seriesPlaybackStartEpisode(
                nextUp = nextUp,
                firstSeasonEpisodes = listOf(fallback),
            ),
        )
    }

    @Test
    fun `series playback start falls back to first non-missing episode`() {
        val missing = dummyEpisode.copy(id = java.util.UUID.randomUUID(), missing = true)
        val playable = dummyEpisode.copy(id = java.util.UUID.randomUUID(), missing = false)

        assertEquals(
            playable,
            seriesPlaybackStartEpisode(
                nextUp = null,
                firstSeasonEpisodes = listOf(missing, playable),
            ),
        )
    }

    @Test
    fun `fallback discovery failure is nonfatal`() = runBlocking {
        val failure = IllegalStateException("episode lookup failed")
        var reportedFailure: Exception? = null

        val episode =
            resolveSeriesPlaybackStartEpisode(
                nextUp = null,
                loadFirstSeasonEpisodes = { throw failure },
                onFailure = { reportedFailure = it },
            )

        assertEquals(null, episode)
        assertEquals(failure, reportedFailure)
    }
}
