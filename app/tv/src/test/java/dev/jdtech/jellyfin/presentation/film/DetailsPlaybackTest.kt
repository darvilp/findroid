package dev.jdtech.jellyfin.presentation.film

import dev.jdtech.jellyfin.film.presentation.movie.MovieAction
import dev.jdtech.jellyfin.film.presentation.season.SeasonState
import dev.jdtech.jellyfin.film.presentation.show.ShowAction
import dev.jdtech.jellyfin.film.presentation.show.ShowState
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailsPlaybackTest {
    @Test
    fun `show details eligibility follows the episode series playback will start`() {
        val state =
            ShowState(
                playbackStartEpisode =
                    dummyEpisode.copy(playbackPositionTicks = 600_000_000L)
            )

        assertTrue(hasMeaningfulPlaybackStart(state))
    }

    @Test
    fun `season details eligibility follows the episode season playback will start`() {
        val state =
            SeasonState(
                playbackStartEpisode =
                    dummyEpisode.copy(playbackPositionTicks = 599_999_999L)
            )

        assertFalse(hasMeaningfulPlaybackStart(state))
    }

    @Test
    fun `movie details carry play from beginning intent into typed route`() {
        val movieId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

        val route =
            requireNotNull(
                moviePlaybackRoute(
                    movieId = movieId,
                    action = MovieAction.Play(startFromBeginning = true),
                )
            )

        assertEquals(movieId.toString(), route.itemId)
        assertEquals("Movie", route.itemKind)
        assertTrue(route.startFromBeginning)
    }

    @Test
    fun `show details carry play from beginning intent into typed route`() {
        val showId = UUID.fromString("12345678-1234-5678-9abc-123456789abc")

        val route =
            requireNotNull(
                showPlaybackRoute(
                    showId = showId,
                    action = ShowAction.Play(startFromBeginning = true),
                )
            )

        assertEquals(showId.toString(), route.itemId)
        assertEquals("Series", route.itemKind)
        assertTrue(route.startFromBeginning)
    }
}
