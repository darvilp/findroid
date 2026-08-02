package dev.jdtech.jellyfin.presentation.film

import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.film.presentation.season.SeasonAction
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonPlaybackRouteTest {
    @Test
    fun `season play creates a season target and carries playback intent`() {
        val seasonId = UUID.fromString("12345678-aaaa-bbbb-cccc-123456789abc")

        val route =
            requireNotNull(
                seasonPlaybackRoute(
                    seasonId = seasonId,
                    action = SeasonAction.Play(startFromBeginning = true),
                )
            )

        assertEquals(seasonId.toString(), route.itemId)
        assertEquals("Season", route.itemKind)
        assertTrue(route.startFromBeginning)
    }

    @Test
    fun `season list episode creates an episode playback target`() {
        val route =
            requireNotNull(
                seasonPlaybackRoute(
                    seasonId = UUID.fromString("aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb"),
                    action = SeasonAction.NavigateToItem(dummyEpisode),
                )
            )

        assertEquals(dummyEpisode.id.toString(), route.itemId)
        assertEquals("Episode", route.itemKind)
        assertFalse(route.startFromBeginning)
    }
}
