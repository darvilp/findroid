package dev.jdtech.jellyfin.presentation.film

import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.film.presentation.PlaybackStart
import dev.jdtech.jellyfin.film.presentation.season.SeasonAction
import dev.jdtech.jellyfin.film.presentation.season.SeasonState
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonPlaybackRouteTest {
    @Test
    fun `season play creates the selected episode target and carries playback intent`() {
        val episode =
            dummyEpisode.copy(id = UUID.fromString("12345678-aaaa-bbbb-cccc-123456789abc"))
        val state = SeasonState(playbackStart = PlaybackStart(episode, startFromBeginning = false))

        val route =
            requireNotNull(
                seasonPlaybackRoute(
                    state = state,
                    action = SeasonAction.Play(startFromBeginning = true),
                )
            )

        assertEquals(episode.id.toString(), route.itemId)
        assertEquals("Episode", route.itemKind)
        assertTrue(route.startFromBeginning)
    }

    @Test
    fun `season list episode creates an episode playback target`() {
        val route =
            requireNotNull(
                seasonPlaybackRoute(
                    state = SeasonState(),
                    action = SeasonAction.NavigateToItem(dummyEpisode),
                )
            )

        assertEquals(dummyEpisode.id.toString(), route.itemId)
        assertEquals("Episode", route.itemKind)
        assertFalse(route.startFromBeginning)
    }

    @Test
    fun `unplayed episode requests marking the same episode as played`() {
        val episodeId = UUID.fromString("cccccccc-1111-2222-3333-dddddddddddd")

        assertEquals(
            SeasonAction.SetEpisodePlayed(episodeId = episodeId, played = true),
            episodePlayedAction(dummyEpisode.copy(id = episodeId, played = false)),
        )
    }

    @Test
    fun `played episode requests marking the same episode as unplayed`() {
        val episodeId = UUID.fromString("eeeeeeee-1111-2222-3333-ffffffffffff")

        assertEquals(
            SeasonAction.SetEpisodePlayed(episodeId = episodeId, played = false),
            episodePlayedAction(dummyEpisode.copy(id = episodeId, played = true)),
        )
    }

    @Test
    fun `episode played state mutation never creates a playback route`() {
        assertNull(
            seasonPlaybackRoute(
                state = SeasonState(),
                action =
                    SeasonAction.SetEpisodePlayed(
                        episodeId = UUID.fromString("cccccccc-1111-2222-3333-dddddddddddd"),
                        played = true,
                    ),
            )
        )
    }

    @Test
    fun `season play has no route without a selected episode`() {
        assertNull(seasonPlaybackRoute(state = SeasonState(), action = SeasonAction.Play(false)))
    }
}
