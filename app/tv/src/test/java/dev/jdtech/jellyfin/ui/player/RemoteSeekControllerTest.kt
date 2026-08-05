package dev.jdtech.jellyfin.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteSeekControllerTest {
    @Test
    fun `initial forward press uses configured increment`() {
        val controller = RemoteSeekController()

        val target =
            controller.onKeyDown(
                direction = RemoteSeekDirection.Forward,
                eventTimeMs = 10_000L,
                playback =
                    RemoteSeekPlayback(
                        positionMs = 30_000L,
                        durationMs = 120_000L,
                        seekBackIncrementMs = 5_000L,
                        seekForwardIncrementMs = 10_000L,
                    ),
            )

        assertEquals(40_000L, target)
    }

    @Test
    fun `continuous hold advances through every acceleration tier`() {
        val controller = RemoteSeekController()
        val playback = playback(positionMs = 100_000L)

        assertEquals(
            105_000L,
            controller.onKeyDown(RemoteSeekDirection.Forward, 10_000L, playback),
        )
        assertEquals(
            105_000L,
            controller.onKeyDown(RemoteSeekDirection.Forward, 10_999L, playback),
        )
        assertEquals(
            110_000L,
            controller.onKeyDown(RemoteSeekDirection.Forward, 11_000L, playback),
        )
        assertEquals(
            120_000L,
            controller.onKeyDown(RemoteSeekDirection.Forward, 12_000L, playback),
        )
        assertEquals(
            140_000L,
            controller.onKeyDown(RemoteSeekDirection.Forward, 13_500L, playback),
        )
    }

    @Test
    fun `backward press uses backward increment`() {
        val controller = RemoteSeekController()
        val playback = playback(positionMs = 100_000L, seekBackIncrementMs = 7_000L)

        assertEquals(
            93_000L,
            controller.onKeyDown(
                direction = RemoteSeekDirection.Backward,
                eventTimeMs = 0L,
                playback = playback,
            ),
        )
        assertEquals(
            44_000L,
            controller.onKeyDown(RemoteSeekDirection.Backward, 4_000L, playback),
        )
    }

    @Test
    fun `release resets acceleration`() {
        val controller = RemoteSeekController()
        val playback = playback(positionMs = 100_000L)
        controller.onKeyDown(RemoteSeekDirection.Forward, 0L, playback)
        assertEquals(
            140_000L,
            controller.onKeyDown(RemoteSeekDirection.Forward, 4_000L, playback),
        )

        controller.onKeyUp(RemoteSeekDirection.Forward)

        assertEquals(
            105_000L,
            controller.onKeyDown(RemoteSeekDirection.Forward, 5_000L, playback),
        )
    }

    @Test
    fun `direction change resets acceleration`() {
        val controller = RemoteSeekController()
        val playback = playback(positionMs = 100_000L, seekBackIncrementMs = 7_000L)
        controller.onKeyDown(RemoteSeekDirection.Forward, 0L, playback)
        controller.onKeyDown(RemoteSeekDirection.Forward, 4_000L, playback)

        assertEquals(
            93_000L,
            controller.onKeyDown(RemoteSeekDirection.Backward, 4_100L, playback),
        )
        controller.onKeyUp(RemoteSeekDirection.Forward)
        assertEquals(
            86_000L,
            controller.onKeyDown(RemoteSeekDirection.Backward, 5_100L, playback),
        )
    }

    @Test
    fun `accelerated step is capped at sixty seconds`() {
        val controller = RemoteSeekController()
        val playback = playback(positionMs = 100_000L, seekForwardIncrementMs = 10_000L)
        controller.onKeyDown(RemoteSeekDirection.Forward, 0L, playback)

        assertEquals(
            160_000L,
            controller.onKeyDown(RemoteSeekDirection.Forward, 4_000L, playback),
        )
    }

    @Test
    fun `configured increment above cap is preserved`() {
        val controller = RemoteSeekController()
        val playback = playback(positionMs = 100_000L, seekForwardIncrementMs = 90_000L)
        controller.onKeyDown(RemoteSeekDirection.Forward, 0L, playback)

        assertEquals(
            190_000L,
            controller.onKeyDown(RemoteSeekDirection.Forward, 4_000L, playback),
        )
    }

    @Test
    fun `targets stay within known playback bounds`() {
        val controller = RemoteSeekController()

        assertEquals(
            0L,
            controller.onKeyDown(
                RemoteSeekDirection.Backward,
                0L,
                playback(positionMs = 2_000L, seekBackIncrementMs = 5_000L),
            ),
        )
        controller.onKeyUp(RemoteSeekDirection.Backward)
        assertEquals(
            120_000L,
            controller.onKeyDown(
                RemoteSeekDirection.Forward,
                1_000L,
                playback(positionMs = 118_000L, durationMs = 120_000L),
            ),
        )
        controller.onKeyUp(RemoteSeekDirection.Forward)
        assertEquals(
            115_000L,
            controller.onKeyDown(
                RemoteSeekDirection.Backward,
                2_000L,
                playback(positionMs = Long.MAX_VALUE, durationMs = 120_000L),
            ),
        )
    }

    @Test
    fun `unknown duration allows a safe forward target`() {
        val target =
            RemoteSeekController()
                .onKeyDown(
                    RemoteSeekDirection.Forward,
                    0L,
                    playback(positionMs = 30_000L, durationMs = null),
                )

        assertEquals(35_000L, target)
    }

    @Test
    fun `position and step arithmetic saturate instead of overflowing`() {
        val controller = RemoteSeekController()
        val nearMaximum = playback(positionMs = Long.MAX_VALUE - 2L, durationMs = null)

        assertEquals(
            Long.MAX_VALUE,
            controller.onKeyDown(RemoteSeekDirection.Forward, 0L, nearMaximum),
        )
        controller.onKeyUp(RemoteSeekDirection.Forward)
        val hugeIncrement =
            playback(
                positionMs = 0L,
                durationMs = null,
                seekForwardIncrementMs = Long.MAX_VALUE,
            )
        controller.onKeyDown(RemoteSeekDirection.Forward, 1_000L, hugeIncrement)
        assertEquals(
            Long.MAX_VALUE,
            controller.onKeyDown(RemoteSeekDirection.Forward, 5_000L, hugeIncrement),
        )
    }

    private fun playback(
        positionMs: Long,
        durationMs: Long? = 300_000L,
        seekBackIncrementMs: Long = 5_000L,
        seekForwardIncrementMs: Long = 5_000L,
    ) =
        RemoteSeekPlayback(
            positionMs = positionMs,
            durationMs = durationMs,
            seekBackIncrementMs = seekBackIncrementMs,
            seekForwardIncrementMs = seekForwardIncrementMs,
        )
}
