package dev.jdtech.jellyfin.presentation.film

import dev.jdtech.jellyfin.models.PlaybackTrackChoice
import dev.jdtech.jellyfin.models.PlaybackTrackChoices
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreplayTrackSelectionStateTest {
    private val choices =
        PlaybackTrackChoices(
            itemId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
            mediaSourceId = "source-1",
            defaultAudioStreamIndex = 1,
            defaultSubtitleStreamIndex = 3,
            audio = emptyList(),
            subtitles = emptyList(),
        )

    @Test
    fun `untouched selectors preserve normal defaults`() {
        assertNull(PreplayTrackSelectionState(choices = choices).initialTrackSelection())
    }

    @Test
    fun `explicit selector creates a source-bound initial selection`() {
        val selection =
            PreplayTrackSelectionState(
                    choices = choices,
                    selectedAudioStreamIndex = 2,
                    selectedSubtitleStreamIndex = -1,
                )
                .initialTrackSelection()

        assertEquals("source-1", selection?.mediaSourceId)
        assertEquals(2, selection?.audioStreamIndex)
        assertEquals(-1, selection?.subtitleStreamIndex)
    }

    @Test
    fun `selection snapshot is rejected for a different reference episode`() {
        val state =
            PreplayTrackSelectionState(choices = choices, selectedAudioStreamIndex = 2)

        assertNull(
            state.initialTrackSelection(
                UUID.fromString("11111111-2222-3333-4444-555555555555")
            )
        )
    }

    @Test
    fun `reference change preserves one exact semantic match and subtitle off`() {
        val oldTrack = track(streamIndex = 2)
        val newTrack = track(streamIndex = 7)
        val oldChoices = choices.copy(audio = listOf(oldTrack))
        val newChoices =
            choices.copy(
                itemId = UUID.fromString("11111111-2222-3333-4444-555555555555"),
                mediaSourceId = "source-2",
                audio = listOf(newTrack),
            )

        val updated =
            PreplayTrackSelectionState(
                    referenceItemId = oldChoices.itemId,
                    choices = oldChoices,
                    selectedAudioStreamIndex = oldTrack.streamIndex,
                    selectedSubtitleStreamIndex = -1,
                )
                .withChoices(newChoices)

        assertEquals(7, updated.selectedAudioStreamIndex)
        assertEquals(-1, updated.selectedSubtitleStreamIndex)
    }

    private fun track(streamIndex: Int) =
        PlaybackTrackChoice(
            streamIndex = streamIndex,
            displayTitle = "Japanese Stereo",
            language = "jpn",
            codec = "aac",
            channelLayout = "stereo",
            isExternal = false,
            isDefault = true,
            isForced = false,
            isHearingImpaired = false,
            ordinalWithinType = 0,
        )
}
