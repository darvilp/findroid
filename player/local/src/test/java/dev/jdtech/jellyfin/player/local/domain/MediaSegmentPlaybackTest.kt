package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidSegmentType
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSegmentPlaybackTest {
    private val itemId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val intro =
        FindroidSegment(
            type = FindroidSegmentType.INTRO,
            startTicks = 1_000L,
            endTicks = 5_000L,
        )
    private val outro =
        FindroidSegment(
            type = FindroidSegmentType.OUTRO,
            startTicks = 9_000L,
            endTicks = 10_000L,
        )

    private fun playbackWith(vararg segments: FindroidSegment): MediaSegmentPlayback =
        MediaSegmentPlayback().also { playback ->
            playback.beginPlaybackPass(itemId = itemId)
            playback.updateSegments(itemId = itemId, segments = segments.toList())
        }

    @Test
    fun `automatic skip consumes the segment before the next poll`() {
        val playback = playbackWith(intro)
        val preferences =
            MediaSegmentPlaybackPreferences(
                autoSkipEnabled = true,
                autoSkipTypes = setOf(FindroidSegmentType.INTRO),
                autoSkipMode = MediaSegmentAutoSkipMode.ALWAYS,
                manualSkipEnabled = true,
                manualSkipTypes = setOf(FindroidSegmentType.INTRO),
            )

        assertEquals(
            MediaSegmentPlaybackDecision.AutoSkip(intro),
            playback.decisionAt(
                positionMs = 1_000L,
                isInPictureInPictureMode = false,
                preferences = preferences,
            ),
        )
        assertEquals(
            MediaSegmentPlaybackDecision.ManualPrompt(intro),
            playback.decisionAt(
                positionMs = 1_000L,
                isInPictureInPictureMode = false,
                preferences = preferences,
            ),
        )
    }

    @Test
    fun `picture in picture mode waits to consume until picture in picture is active`() {
        val playback = playbackWith(intro)
        val preferences =
            MediaSegmentPlaybackPreferences(
                autoSkipEnabled = true,
                autoSkipTypes = setOf(FindroidSegmentType.INTRO),
                autoSkipMode = MediaSegmentAutoSkipMode.PICTURE_IN_PICTURE,
                manualSkipEnabled = false,
                manualSkipTypes = emptySet(),
            )

        assertEquals(
            MediaSegmentPlaybackDecision.None,
            playback.decisionAt(
                positionMs = 1_500L,
                isInPictureInPictureMode = false,
                preferences = preferences,
            ),
        )
        assertEquals(
            MediaSegmentPlaybackDecision.AutoSkip(intro),
            playback.decisionAt(
                positionMs = 1_500L,
                isInPictureInPictureMode = true,
                preferences = preferences,
            ),
        )
    }

    @Test
    fun `new item clears old segments and preserves automatic outro handling after loading`() {
        val playback = playbackWith(intro)
        val nextItemId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
        val preferences =
            MediaSegmentPlaybackPreferences(
                autoSkipEnabled = true,
                autoSkipTypes = setOf(FindroidSegmentType.INTRO, FindroidSegmentType.OUTRO),
                autoSkipMode = MediaSegmentAutoSkipMode.ALWAYS,
                manualSkipEnabled = false,
                manualSkipTypes = emptySet(),
            )
        assertEquals(
            MediaSegmentPlaybackDecision.AutoSkip(intro),
            playback.decisionAt(1_500L, false, preferences),
        )

        playback.beginPlaybackPass(itemId = nextItemId)
        assertEquals(
            MediaSegmentPlaybackDecision.None,
            playback.decisionAt(1_500L, false, preferences),
        )

        playback.updateSegments(itemId = nextItemId, segments = listOf(outro))
        assertEquals(
            MediaSegmentPlaybackDecision.AutoSkip(outro),
            playback.decisionAt(9_000L, false, preferences),
        )
    }

    @Test
    fun `ordinary seeks and picture in picture transitions do not rearm a consumed segment`() {
        val playback = playbackWith(intro)
        val preferences =
            MediaSegmentPlaybackPreferences(
                autoSkipEnabled = true,
                autoSkipTypes = setOf(FindroidSegmentType.INTRO),
                autoSkipMode = MediaSegmentAutoSkipMode.ALWAYS,
                manualSkipEnabled = true,
                manualSkipTypes = setOf(FindroidSegmentType.INTRO),
            )
        assertEquals(
            MediaSegmentPlaybackDecision.AutoSkip(intro),
            playback.decisionAt(1_500L, false, preferences),
        )

        assertEquals(
            MediaSegmentPlaybackDecision.None,
            playback.decisionAt(7_000L, false, preferences),
        )
        assertEquals(
            MediaSegmentPlaybackDecision.ManualPrompt(intro),
            playback.decisionAt(1_500L, true, preferences),
        )
    }

    @Test
    fun `consumed segment reentry is silent when manual skip is disabled`() {
        val playback = playbackWith(intro)
        val preferences =
            MediaSegmentPlaybackPreferences(
                autoSkipEnabled = true,
                autoSkipTypes = setOf(FindroidSegmentType.INTRO),
                autoSkipMode = MediaSegmentAutoSkipMode.ALWAYS,
                manualSkipEnabled = false,
                manualSkipTypes = setOf(FindroidSegmentType.INTRO),
            )
        assertEquals(
            MediaSegmentPlaybackDecision.AutoSkip(intro),
            playback.decisionAt(1_500L, false, preferences),
        )

        assertEquals(
            MediaSegmentPlaybackDecision.None,
            playback.decisionAt(1_500L, false, preferences),
        )
    }

    @Test
    fun `explicitly beginning a playback pass rearms retained segments`() {
        val playback = playbackWith(intro)
        val preferences =
            MediaSegmentPlaybackPreferences(
                autoSkipEnabled = true,
                autoSkipTypes = setOf(FindroidSegmentType.INTRO),
                autoSkipMode = MediaSegmentAutoSkipMode.ALWAYS,
                manualSkipEnabled = false,
                manualSkipTypes = emptySet(),
            )
        assertEquals(
            MediaSegmentPlaybackDecision.AutoSkip(intro),
            playback.decisionAt(1_500L, false, preferences),
        )

        playback.beginPlaybackPass()

        assertEquals(
            MediaSegmentPlaybackDecision.AutoSkip(intro),
            playback.decisionAt(1_500L, false, preferences),
        )
    }

    @Test
    fun `segment type excluded from auto skip can still show an enabled manual prompt`() {
        val playback = playbackWith(outro)
        val preferences =
            MediaSegmentPlaybackPreferences(
                autoSkipEnabled = true,
                autoSkipTypes = setOf(FindroidSegmentType.INTRO),
                autoSkipMode = MediaSegmentAutoSkipMode.ALWAYS,
                manualSkipEnabled = true,
                manualSkipTypes = setOf(FindroidSegmentType.OUTRO),
            )

        assertEquals(
            MediaSegmentPlaybackDecision.ManualPrompt(outro),
            playback.decisionAt(9_000L, false, preferences),
        )
    }

    @Test
    fun `manual prompt type filter suppresses a consumed segment on reentry`() {
        val playback = playbackWith(intro)
        val preferences =
            MediaSegmentPlaybackPreferences(
                autoSkipEnabled = true,
                autoSkipTypes = setOf(FindroidSegmentType.INTRO),
                autoSkipMode = MediaSegmentAutoSkipMode.ALWAYS,
                manualSkipEnabled = true,
                manualSkipTypes = setOf(FindroidSegmentType.OUTRO),
            )
        assertEquals(
            MediaSegmentPlaybackDecision.AutoSkip(intro),
            playback.decisionAt(1_500L, false, preferences),
        )

        assertEquals(
            MediaSegmentPlaybackDecision.None,
            playback.decisionAt(1_500L, false, preferences),
        )
    }

    @Test
    fun `reopening the same item rearms consumption without discarding its segments`() {
        val playback = playbackWith(intro)
        val preferences =
            MediaSegmentPlaybackPreferences(
                autoSkipEnabled = true,
                autoSkipTypes = setOf(FindroidSegmentType.INTRO),
                autoSkipMode = MediaSegmentAutoSkipMode.ALWAYS,
                manualSkipEnabled = false,
                manualSkipTypes = emptySet(),
            )
        assertEquals(
            MediaSegmentPlaybackDecision.AutoSkip(intro),
            playback.decisionAt(1_500L, false, preferences),
        )

        playback.beginPlaybackPass(itemId = itemId)

        assertEquals(
            MediaSegmentPlaybackDecision.AutoSkip(intro),
            playback.decisionAt(1_500L, false, preferences),
        )
    }

    @Test
    fun `activating a new item clears old segments and ignores the old item's late response`() {
        val firstItemId = UUID.fromString("11111111-1111-1111-1111-111111111111")
        val secondItemId = UUID.fromString("22222222-2222-2222-2222-222222222222")
        val playback = MediaSegmentPlayback()
        val preferences =
            MediaSegmentPlaybackPreferences(
                autoSkipEnabled = true,
                autoSkipTypes = setOf(FindroidSegmentType.INTRO, FindroidSegmentType.OUTRO),
                autoSkipMode = MediaSegmentAutoSkipMode.ALWAYS,
                manualSkipEnabled = false,
                manualSkipTypes = emptySet(),
            )
        playback.beginPlaybackPass(itemId = firstItemId)
        playback.updateSegments(itemId = firstItemId, segments = listOf(intro))
        assertEquals(
            MediaSegmentPlaybackDecision.AutoSkip(intro),
            playback.decisionAt(1_500L, false, preferences),
        )

        playback.beginPlaybackPass(itemId = secondItemId)
        playback.updateSegments(itemId = firstItemId, segments = listOf(outro))
        assertEquals(
            MediaSegmentPlaybackDecision.None,
            playback.decisionAt(9_000L, false, preferences),
        )

        playback.updateSegments(itemId = secondItemId, segments = listOf(outro))
        assertEquals(
            MediaSegmentPlaybackDecision.AutoSkip(outro),
            playback.decisionAt(9_000L, false, preferences),
        )
    }
}
