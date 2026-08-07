package dev.jdtech.jellyfin.presentation.film

import dev.jdtech.jellyfin.film.presentation.movie.MovieAction
import dev.jdtech.jellyfin.film.presentation.PlaybackStart
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
                playbackStart =
                    PlaybackStart(
                        episode = dummyEpisode.copy(playbackPositionTicks = 600_000_000L),
                        startFromBeginning = false,
                    )
            )

        assertTrue(hasMeaningfulPlaybackStart(state))
    }

    @Test
    fun `season details eligibility follows the episode season playback will start`() {
        val state =
            SeasonState(
                playbackStart =
                    PlaybackStart(
                        episode = dummyEpisode.copy(playbackPositionTicks = 599_999_999L),
                        startFromBeginning = false,
                    )
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
    fun `show details target the selected episode and carry play from beginning intent`() {
        val episode =
            dummyEpisode.copy(id = UUID.fromString("12345678-1234-5678-9abc-123456789abc"))
        val state = ShowState(playbackStart = PlaybackStart(episode, startFromBeginning = false))

        val route =
            requireNotNull(
                showPlaybackRoute(
                    state = state,
                    action = ShowAction.Play(startFromBeginning = true),
                )
            )

        assertEquals(episode.id.toString(), route.itemId)
        assertEquals("Episode", route.itemKind)
        assertTrue(route.startFromBeginning)
    }

    @Test
    fun `forced restart playback does not expose a redundant restart action`() {
        val state =
            ShowState(
                playbackStart =
                    PlaybackStart(
                        episode = dummyEpisode.copy(playbackPositionTicks = 900_000_000L),
                        startFromBeginning = true,
                    )
            )

        assertFalse(hasMeaningfulPlaybackStart(state))
    }
}
