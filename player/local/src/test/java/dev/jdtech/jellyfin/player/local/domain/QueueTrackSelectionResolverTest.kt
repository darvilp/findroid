package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.models.FindroidMediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueTrackSelectionResolverTest {
    @Test
    fun `selected subtitle follows the same semantic track when stream indices change`() {
        val previousStreams =
            listOf(
                subtitle(index = 2, language = "jpn", title = "Signs & Songs", codec = "ass"),
                subtitle(index = 3, language = "eng", title = "Full Subtitles", codec = "ass"),
            )
        val nextStreams =
            listOf(
                subtitle(index = 7, language = "eng", title = "Full Subtitles", codec = "ass"),
                subtitle(index = 8, language = "jpn", title = "Signs & Songs", codec = "ass"),
            )

        val fingerprint =
            QueueTrackFingerprint.fromStream(
                selectedStreamIndex = 2,
                streams = previousStreams,
            )

        assertEquals(8, QueueTrackSelectionResolver.resolve(fingerprint!!, nextStreams))
    }

    @Test
    fun `forced subtitle does not continue onto a non-forced track`() {
        val previous = subtitle(index = 2, language = "eng", title = "Forced", codec = "ass", forced = true)
        val candidate = subtitle(index = 8, language = "eng", title = "Forced", codec = "ass", forced = false)
        val fingerprint = QueueTrackFingerprint.fromStream(previous, listOf(previous))

        assertNull(QueueTrackSelectionResolver.resolve(fingerprint!!, listOf(candidate)))
    }

    @Test
    fun `commentary audio does not continue onto the main audio track`() {
        val previous = audio(index = 1, title = "Director Commentary")
        val candidate = audio(index = 5, title = "Main Audio")
        val fingerprint = QueueTrackFingerprint.fromStream(previous, listOf(previous))

        assertNull(QueueTrackSelectionResolver.resolve(fingerprint!!, listOf(candidate)))
    }

    @Test
    fun `hearing impaired subtitle does not continue onto a standard track`() {
        val previous =
            subtitle(index = 2, language = "eng", title = "English", codec = "ass", hearingImpaired = true)
        val candidate =
            subtitle(index = 8, language = "eng", title = "English", codec = "ass", hearingImpaired = false)

        assertNull(
            QueueTrackSelectionResolver.resolve(
                QueueTrackFingerprint.fromStream(previous, listOf(previous))!!,
                listOf(candidate),
            )
        )
    }

    @Test
    fun `matching ass codec wins when other attributes are equal`() {
        val previous = subtitle(index = 2, language = "eng", title = "English", codec = "ass")
        val previousStreams =
            listOf(
                subtitle(index = 0, language = "jpn", title = "Signs", codec = "ass"),
                subtitle(index = 1, language = "spa", title = "Spanish", codec = "ass"),
                previous,
            )
        val candidates =
            listOf(
                subtitle(index = 8, language = "eng", title = "English", codec = "subrip"),
                subtitle(index = 9, language = "eng", title = "English", codec = "ass"),
            )

        assertEquals(
            9,
            QueueTrackSelectionResolver.resolve(
                QueueTrackFingerprint.fromStream(previous, previousStreams)!!,
                candidates,
            ),
        )
    }

    @Test
    fun `weak candidate falls back to the new item default`() {
        val previous = subtitle(index = 2, language = "jpn", title = "Signs", codec = "ass")
        val candidate = subtitle(index = 8, language = "eng", title = "Full", codec = "subrip")

        assertNull(
            QueueTrackSelectionResolver.resolve(
                QueueTrackFingerprint.fromStream(previous, listOf(previous))!!,
                listOf(candidate),
            )
        )
    }

    @Test
    fun `ties resolve to the lower Jellyfin stream index`() {
        val previous = subtitle(index = 2, language = "eng", title = "English", codec = "ass")
        val previousStreams =
            listOf(
                subtitle(index = 0, language = "jpn", title = "Signs", codec = "ass"),
                subtitle(index = 1, language = "spa", title = "Spanish", codec = "ass"),
                previous,
            )
        val candidates =
            listOf(
                subtitle(index = 9, language = "eng", title = "English", codec = "ass"),
                subtitle(index = 7, language = "eng", title = "English", codec = "ass"),
            )

        assertEquals(
            7,
            QueueTrackSelectionResolver.resolve(
                QueueTrackFingerprint.fromStream(previous, previousStreams)!!,
                candidates,
            ),
        )
    }

    private fun subtitle(
        index: Int,
        language: String,
        title: String,
        codec: String,
        forced: Boolean = false,
        hearingImpaired: Boolean = false,
    ) =
        FindroidMediaStream(
            title = title,
            displayTitle = title,
            language = language,
            type = MediaStreamType.SUBTITLE,
            codec = codec,
            isExternal = false,
            path = null,
            channelLayout = null,
            videoRangeType = null,
            height = null,
            width = null,
            videoDoViTitle = null,
            index = index,
            isDefault = false,
            isForced = forced,
            isHearingImpaired = hearingImpaired,
        )

    private fun audio(index: Int, title: String) =
        FindroidMediaStream(
            title = title,
            displayTitle = title,
            language = "eng",
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
            isDefault = false,
            isForced = false,
            isHearingImpaired = false,
        )
}
