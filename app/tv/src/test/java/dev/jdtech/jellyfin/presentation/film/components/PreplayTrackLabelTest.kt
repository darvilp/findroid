package dev.jdtech.jellyfin.presentation.film.components

import dev.jdtech.jellyfin.models.PlaybackTrackChoice
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class PreplayTrackLabelTest {
    @Test
    fun `audio label does not repeat metadata from a long server display title`() {
        val choice =
            track(
                displayTitle = "Japanese - AAC - 5.1 - Default",
                language = "jpn",
                codec = "aac",
                channelLayout = "5.1",
                isDefault = true,
            )

        val label = choice.preplayLabel(PreplayTrackKind.Audio, Locale.ENGLISH)

        assertEquals("Japanese · 5.1", label.compact)
        assertEquals("Japanese", label.primary)
        assertEquals("AAC · 5.1 · Default", label.secondary)
    }

    @Test
    fun `subtitle label preserves a meaningful title outside the compact value`() {
        val choice =
            track(
                title = "Signs & Songs",
                displayTitle = "English - Signs & Songs - ASS - External",
                language = "eng",
                codec = "ass",
                isExternal = true,
            )

        val label = choice.preplayLabel(PreplayTrackKind.Subtitle, Locale.ENGLISH)

        assertEquals("English · ASS", label.compact)
        assertEquals("English — Signs & Songs", label.primary)
        assertEquals("ASS · External", label.secondary)
    }

    @Test
    fun `compact subtitle label retains forced and hearing impaired distinctions`() {
        val choice =
            track(
                displayTitle = "English - SRT - Forced - SDH",
                language = "eng",
                codec = "srt",
                isForced = true,
                isHearingImpaired = true,
            )

        val label = choice.preplayLabel(PreplayTrackKind.Subtitle, Locale.ENGLISH)

        assertEquals("English · SRT · Forced · SDH", label.compact)
        assertEquals("SRT · Forced · SDH", label.secondary)
    }

    @Test
    fun `technical line omits channel metadata already present in the title`() {
        val choice =
            track(
                title = "Japanese Stereo",
                displayTitle = "Japanese Stereo - AAC - Default",
                language = "jpn",
                codec = "aac",
                channelLayout = "stereo",
                isDefault = true,
            )

        val label = choice.preplayLabel(PreplayTrackKind.Audio, Locale.ENGLISH)

        assertEquals("Japanese · Stereo", label.compact)
        assertEquals("Japanese Stereo", label.primary)
        assertEquals("AAC · Default", label.secondary)
    }

    private fun track(
        title: String? = null,
        displayTitle: String,
        language: String? = null,
        codec: String? = null,
        channelLayout: String? = null,
        isExternal: Boolean = false,
        isDefault: Boolean = false,
        isForced: Boolean = false,
        isHearingImpaired: Boolean = false,
    ) =
        PlaybackTrackChoice(
            streamIndex = 2,
            title = title,
            displayTitle = displayTitle,
            language = language,
            codec = codec,
            channelLayout = channelLayout,
            isExternal = isExternal,
            isDefault = isDefault,
            isForced = isForced,
            isHearingImpaired = isHearingImpaired,
            ordinalWithinType = 0,
        )
}
