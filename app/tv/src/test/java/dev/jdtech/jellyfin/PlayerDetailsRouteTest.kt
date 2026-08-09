package dev.jdtech.jellyfin

import dev.jdtech.jellyfin.player.local.domain.PlaybackDetailsTarget
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerDetailsRouteTest {
    @Test
    fun `episode playback details navigate to the containing season`() {
        val seasonId = UUID.fromString("20000000-0000-0000-0000-000000000000")

        assertEquals(
            SeasonRoute(seasonId.toString()),
            playerDetailsRoute(PlaybackDetailsTarget.Season(seasonId)),
        )
    }

    @Test
    fun `movie playback details navigate to the movie`() {
        val movieId = UUID.fromString("30000000-0000-0000-0000-000000000000")

        assertEquals(
            MovieRoute(movieId.toString()),
            playerDetailsRoute(PlaybackDetailsTarget.Movie(movieId)),
        )
    }
}
