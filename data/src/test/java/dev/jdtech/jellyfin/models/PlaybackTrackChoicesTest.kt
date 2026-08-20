package dev.jdtech.jellyfin.models

import java.util.UUID
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackTrackChoicesTest {
    @Test
    fun `remote source exposes indexed audio and subtitle choices with per-type ordinals`() {
        val source =
            FindroidSource(
                id = "source-1",
                name = "1080p",
                type = FindroidSourceType.REMOTE,
                path = "",
                size = 100,
                mediaStreams =
                    listOf(
                        stream(index = 0, type = MediaStreamType.VIDEO),
                        stream(index = 2, type = MediaStreamType.AUDIO, language = "jpn"),
                        stream(index = 4, type = MediaStreamType.SUBTITLE, language = "eng"),
                        stream(index = 7, type = MediaStreamType.AUDIO, language = "eng"),
                    ),
                defaultAudioStreamIndex = 2,
                defaultSubtitleStreamIndex = 4,
            )

        val choices =
            listOf(source).playbackTrackChoices(
                itemId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
            )

        assertEquals("source-1", choices?.mediaSourceId)
        assertEquals(listOf(2, 7), choices?.audio?.map { it.streamIndex })
        assertEquals(listOf(0, 1), choices?.audio?.map { it.ordinalWithinType })
        assertEquals(listOf("Track 2", "Track 7"), choices?.audio?.map { it.title })
        assertEquals(listOf(4), choices?.subtitles?.map { it.streamIndex })
        assertEquals(2, choices?.defaultAudioStreamIndex)
        assertEquals(4, choices?.defaultSubtitleStreamIndex)
    }

    @Test
    fun `offline sources do not expose pre-playback choices`() {
        val source =
            FindroidSource(
                id = "offline",
                name = "download",
                type = FindroidSourceType.LOCAL,
                path = "/download/video.mkv",
                size = 100,
                mediaStreams = emptyList(),
            )

        assertNull(listOf(source).playbackTrackChoices(UUID.randomUUID()))
    }

    private fun stream(
        index: Int,
        type: MediaStreamType,
        language: String = "",
    ) =
        FindroidMediaStream(
            title = "Track $index",
            displayTitle = "Track $index",
            language = language,
            type = type,
            codec = "codec",
            isExternal = false,
            path = null,
            channelLayout = null,
            videoRangeType = null,
            height = null,
            width = null,
            videoDoViTitle = null,
            index = index,
            isDefault = false,
            isForced = false,
            isHearingImpaired = false,
        )
}
