package dev.jdtech.jellyfin.player.local.domain

import androidx.media3.common.C
import dev.jdtech.jellyfin.models.FindroidMediaStream
import dev.jdtech.jellyfin.models.InitialTrackSelection
import dev.jdtech.jellyfin.player.core.domain.models.PlayerItem
import dev.jdtech.jellyfin.player.core.domain.models.Track
import java.util.UUID
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.Assert.assertEquals
import org.junit.Test

class InitialTrackOverrideResolverTest {
    @Test
    fun `exact audio and subtitle off become runtime override actions`() {
        val item =
            PlayerItem(
                name = "Episode",
                itemId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                mediaSourceId = "source-1",
                playbackPosition = 0,
                initialAudioStreamIndex = 1,
                initialSubtitleStreamIndex = InitialTrackSelection.SUBTITLE_OFF,
            )
        val stream = audio(index = 1)
        val track = runtimeAudio(group = 2)

        assertEquals(
            listOf(
                InitialTrackOverride.Select(track),
                InitialTrackOverride.Disable(C.TRACK_TYPE_TEXT),
            ),
            InitialTrackOverrideResolver.resolve(
                item = item,
                jellyfinStreams = listOf(stream),
                runtimeTracks = listOf(track),
            ),
        )
    }

    @Test
    fun `a missing runtime track remains unresolved for a later callback`() {
        val item =
            PlayerItem(
                name = "Episode",
                itemId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                mediaSourceId = "source-1",
                playbackPosition = 0,
                initialAudioStreamIndex = 1,
                initialSubtitleStreamIndex = 3,
            )

        assertEquals(
            listOf(InitialTrackOverride.Select(runtimeAudio(group = 2))),
            InitialTrackOverrideResolver.resolve(
                item = item,
                jellyfinStreams = listOf(audio(index = 1), subtitle(index = 3)),
                runtimeTracks = listOf(runtimeAudio(group = 2)),
            ),
        )
    }

    private fun audio(index: Int) =
        FindroidMediaStream(
            title = "Japanese",
            displayTitle = "Japanese",
            language = "jpn",
            type = MediaStreamType.AUDIO,
            codec = "aac",
            isExternal = false,
            path = null,
            channelLayout = "stereo",
            videoRangeType = null,
            height = null,
            width = null,
            videoDoViTitle = null,
            index = index,
        )

    private fun subtitle(index: Int) =
        FindroidMediaStream(
            title = "English",
            displayTitle = "English",
            language = "eng",
            type = MediaStreamType.SUBTITLE,
            codec = "ass",
            isExternal = false,
            path = null,
            channelLayout = null,
            videoRangeType = null,
            height = null,
            width = null,
            videoDoViTitle = null,
            index = index,
        )

    private fun runtimeAudio(group: Int) =
        Track(
            type = C.TRACK_TYPE_AUDIO,
            groupIndex = group,
            trackIndex = 0,
            label = "Japanese",
            language = "Japanese",
            codec = "audio/aac",
            selected = false,
            supported = true,
            rawLanguage = "jpn",
        )
}
