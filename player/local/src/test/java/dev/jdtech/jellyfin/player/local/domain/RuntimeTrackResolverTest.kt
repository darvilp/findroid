package dev.jdtech.jellyfin.player.local.domain

import androidx.media3.common.C
import dev.jdtech.jellyfin.models.FindroidMediaStream
import dev.jdtech.jellyfin.player.core.domain.models.Track
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimeTrackResolverTest {
    @Test
    fun `external subtitle resolves by its explicit Jellyfin stream id`() {
        val streams = listOf(subtitle(index = 4, title = "Full Subtitles"))
        val runtimeTracks =
            listOf(
                runtimeTrack(group = 0, id = "9", title = "Full Subtitles"),
                runtimeTrack(group = 1, id = "4", title = "Different runtime label"),
            )

        val resolved =
            RuntimeTrackResolver.resolve(
                jellyfinStreamIndex = 4,
                jellyfinStreams = streams,
                runtimeTracks = runtimeTracks,
            )

        assertEquals(runtimeTracks[1], resolved)
    }

    @Test
    fun `external subtitle never falls back to a semantic runtime match`() {
        val streams = listOf(subtitle(index = 4, title = "Full Subtitles"))
        val runtimeTracks = listOf(runtimeTrack(group = 0, id = "9", title = "Full Subtitles"))

        assertNull(
            RuntimeTrackResolver.resolve(
                jellyfinStreamIndex = 4,
                jellyfinStreams = streams,
                runtimeTracks = runtimeTracks,
            )
        )
    }

    private fun subtitle(index: Int, title: String) =
        FindroidMediaStream(
            title = title,
            displayTitle = title,
            language = "eng",
            type = MediaStreamType.SUBTITLE,
            codec = "ass",
            isExternal = true,
            path = "https://example.invalid/subtitle",
            channelLayout = null,
            videoRangeType = null,
            height = null,
            width = null,
            videoDoViTitle = null,
            index = index,
        )

    private fun runtimeTrack(group: Int, id: String, title: String) =
        Track(
            type = C.TRACK_TYPE_TEXT,
            groupIndex = group,
            trackIndex = 0,
            label = title,
            language = "English",
            codec = "text/x-ssa",
            selected = false,
            supported = true,
            id = id,
            rawLanguage = "eng",
        )
}
