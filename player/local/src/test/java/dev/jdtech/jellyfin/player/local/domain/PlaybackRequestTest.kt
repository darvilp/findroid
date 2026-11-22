package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.models.InitialTrackSelection
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlaybackRequestTest {
    @Test
    fun `retry starts the failed episode at the captured position`() {
        val show = UUID.randomUUID()
        val episode = UUID.randomUUID()
        val selection = InitialTrackSelection("episode-media", 1, 42)
        val request = PlaybackRequest(show, BaseItemKind.SERIES, true, null)

        val retry = request.forRetry(episode, BaseItemKind.EPISODE, 25_000, selection)

        assertEquals(episode, retry.itemId)
        assertEquals(BaseItemKind.EPISODE, retry.kind)
        assertEquals(25_000L, retry.positionMs)
        assertEquals(selection, retry.trackSelection)
        assertFalse(retry.startFromBeginning)
    }
}
