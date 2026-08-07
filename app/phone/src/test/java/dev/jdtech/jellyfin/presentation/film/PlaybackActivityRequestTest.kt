package dev.jdtech.jellyfin.presentation.film

import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.film.presentation.PlaybackStart
import dev.jdtech.jellyfin.film.presentation.season.SeasonAction
import dev.jdtech.jellyfin.film.presentation.season.SeasonState
import dev.jdtech.jellyfin.film.presentation.show.ShowAction
import dev.jdtech.jellyfin.film.presentation.show.ShowState
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackActivityRequestTest {
    @Test
    fun `show playback request targets the selected episode and retains both start intents`() {
        val episode =
            dummyEpisode.copy(id = UUID.fromString("11111111-2222-3333-4444-555555555555"))
        val state = ShowState(playbackStart = PlaybackStart(episode, startFromBeginning = false))

        val resume = requireNotNull(showPlaybackActivityRequest(state, ShowAction.Play(false)))
        val restart = requireNotNull(showPlaybackActivityRequest(state, ShowAction.Play(true)))

        assertEquals(episode.id.toString(), resume.itemId)
        assertEquals("Episode", resume.itemKind)
        assertFalse(resume.startFromBeginning)
        assertTrue(restart.startFromBeginning)
    }

    @Test
    fun `season playback request honors a selector-forced restart`() {
        val episode =
            dummyEpisode.copy(id = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"))
        val state = SeasonState(playbackStart = PlaybackStart(episode, startFromBeginning = true))

        val request = requireNotNull(seasonPlaybackActivityRequest(state, SeasonAction.Play(false)))

        assertEquals(episode.id.toString(), request.itemId)
        assertEquals("Episode", request.itemKind)
        assertTrue(request.startFromBeginning)
    }

    @Test
    fun `container playback request is absent without a selected episode`() {
        assertNull(showPlaybackActivityRequest(ShowState(), ShowAction.Play(false)))
        assertNull(seasonPlaybackActivityRequest(SeasonState(), SeasonAction.Play(false)))
    }
}
