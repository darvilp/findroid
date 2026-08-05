package dev.jdtech.jellyfin.presentation.film

import dev.jdtech.jellyfin.film.presentation.season.SeasonAction
import dev.jdtech.jellyfin.film.presentation.show.ShowAction
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackActivityRequestTest {
    @Test
    fun `show playback request retains both start intents`() {
        val showId = UUID.fromString("11111111-2222-3333-4444-555555555555")

        val resume = showPlaybackActivityRequest(showId, ShowAction.Play(false))
        val restart = showPlaybackActivityRequest(showId, ShowAction.Play(true))

        assertEquals(showId.toString(), resume.itemId)
        assertEquals("Series", resume.itemKind)
        assertFalse(resume.startFromBeginning)
        assertTrue(restart.startFromBeginning)
    }

    @Test
    fun `season playback request retains both start intents`() {
        val seasonId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")

        val resume = seasonPlaybackActivityRequest(seasonId, SeasonAction.Play(false))
        val restart = seasonPlaybackActivityRequest(seasonId, SeasonAction.Play(true))

        assertEquals(seasonId.toString(), resume.itemId)
        assertEquals("Season", resume.itemKind)
        assertFalse(resume.startFromBeginning)
        assertTrue(restart.startFromBeginning)
    }
}
