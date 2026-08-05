package dev.jdtech.jellyfin.player.core.domain.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackTest {
    private val tracks =
        listOf(
            track(type = 1, groupIndex = 2, trackIndex = 0),
            track(type = 1, groupIndex = 2, trackIndex = 1),
            track(type = 3, groupIndex = 4, trackIndex = 0),
        )

    @Test
    fun `findTrack resolves the exact type group and format`() {
        assertEquals(tracks[1], tracks.findTrack(type = 1, groupIndex = 2, trackIndex = 1))
    }

    @Test
    fun `findTrack rejects stale or mismatched identities`() {
        assertNull(tracks.findTrack(type = 3, groupIndex = 2, trackIndex = 1))
        assertNull(tracks.findTrack(type = 1, groupIndex = 9, trackIndex = 0))
    }

    @Test
    fun `withSelectedTrack selects one exact option or none`() {
        assertEquals(
            listOf(false, true, false),
            tracks.withSelectedTrack(groupIndex = 2, trackIndex = 1).map { it.selected },
        )
        assertEquals(
            listOf(false, false, false),
            tracks.withSelectedTrack(groupIndex = null, trackIndex = null).map { it.selected },
        )
    }

    private fun track(type: Int, groupIndex: Int, trackIndex: Int) =
        Track(
            type = type,
            groupIndex = groupIndex,
            trackIndex = trackIndex,
            label = "Track",
            language = "English",
            codec = "codec",
            selected = false,
            supported = true,
        )
}
