package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.models.FindroidMediaStream
import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.InitialTrackSelection
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSourceResolverTest {
    private val remote =
        source(
            id = "remote",
            type = FindroidSourceType.REMOTE,
            streams =
                listOf(
                    stream(1, MediaStreamType.AUDIO),
                    stream(3, MediaStreamType.SUBTITLE),
                ),
        )
    private val local = source(id = "local", type = FindroidSourceType.LOCAL)

    @Test
    fun `valid selection resolves its exact remote source and indices`() {
        val selection =
            InitialTrackSelection(
                mediaSourceId = "remote",
                audioStreamIndex = 1,
                subtitleStreamIndex = 3,
            )

        val resolved = PlaybackSourceResolver.resolve(listOf(local, remote), selection)

        assertEquals(remote, resolved.source)
        assertEquals(selection, resolved.initialTrackSelection)
    }

    @Test
    fun `stale stream index falls back to normal source defaults`() {
        val selection =
            InitialTrackSelection(
                mediaSourceId = "remote",
                audioStreamIndex = 99,
            )

        val resolved = PlaybackSourceResolver.resolve(listOf(local, remote), selection)

        assertEquals(local, resolved.source)
        assertNull(resolved.initialTrackSelection)
    }

    @Test
    fun `external subtitle without a delivery path falls back to defaults`() {
        val unusableRemote =
            source(
                id = "remote",
                type = FindroidSourceType.REMOTE,
                streams =
                    listOf(
                        stream(1, MediaStreamType.AUDIO),
                        stream(3, MediaStreamType.SUBTITLE, external = true),
                    ),
            )
        val selection =
            InitialTrackSelection(mediaSourceId = "remote", subtitleStreamIndex = 3)

        val resolved = PlaybackSourceResolver.resolve(listOf(local, unusableRemote), selection)

        assertEquals(local, resolved.source)
        assertNull(resolved.initialTrackSelection)
    }

    private fun source(
        id: String,
        type: FindroidSourceType,
        streams: List<FindroidMediaStream> = emptyList(),
    ) =
        FindroidSource(
            id = id,
            name = id,
            type = type,
            path = id,
            size = 1,
            mediaStreams = streams,
        )

    private fun stream(index: Int, type: MediaStreamType, external: Boolean = false) =
        FindroidMediaStream(
            title = "Track $index",
            displayTitle = "Track $index",
            language = "eng",
            type = type,
            codec = "codec",
            isExternal = external,
            path = null,
            channelLayout = null,
            videoRangeType = null,
            height = null,
            width = null,
            videoDoViTitle = null,
            index = index,
        )
}
