package dev.jdtech.jellyfin.ui.components.player

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoPlayerSeekerTest {
    @Test
    fun `backward seek floors at zero`() {
        assertEquals(
            0L,
            seekTarget(
                positionMs = 3_000L,
                durationMs = 60_000L,
                incrementMs = 10_000L,
                forward = false,
            ),
        )
    }

    @Test
    fun `forward seek caps at known duration`() {
        assertEquals(
            60_000L,
            seekTarget(
                positionMs = 58_000L,
                durationMs = 60_000L,
                incrementMs = 10_000L,
                forward = true,
            ),
        )
    }

    @Test
    fun `chapter markers omit start end and invalid duration`() {
        assertEquals(
            listOf(0.25f, 0.5f),
            chapterMarkerProgress(
                chapterStartPositions = listOf(0L, 15_000L, 30_000L, 60_000L),
                durationMs = 60_000L,
            ),
        )
        assertEquals(emptyList<Float>(), chapterMarkerProgress(listOf(10_000L), -1L))
    }

    @Test
    fun `playback progress handles unknown duration`() {
        assertEquals(0f, playbackProgress(positionMs = 5_000L, durationMs = -1L))
    }
}
