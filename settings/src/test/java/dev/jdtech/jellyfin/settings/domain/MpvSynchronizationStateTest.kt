package dev.jdtech.jellyfin.settings.domain

import java.math.BigInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MpvSynchronizationStateTest {
    @Test
    fun `effective values combine independent configured and temporary values`() {
        val state = MpvSynchronizationState(audioBaselineMs = 100L, subtitleBaselineMs = -50L)

        state.setTemporary(MpvSynchronizationKind.AUDIO, 25L)
        state.setTemporary(MpvSynchronizationKind.SUBTITLE, -10L)

        assertEquals(125L, state.effective(MpvSynchronizationKind.AUDIO))
        assertEquals(-60L, state.effective(MpvSynchronizationKind.SUBTITLE))
    }

    @Test
    fun `reset clears only the selected temporary adjustment`() {
        val state = MpvSynchronizationState(audioBaselineMs = 100L, subtitleBaselineMs = 200L)
        state.setTemporary(MpvSynchronizationKind.AUDIO, 50L)
        state.setTemporary(MpvSynchronizationKind.SUBTITLE, 75L)

        state.reset(MpvSynchronizationKind.AUDIO)

        assertEquals(100L, state.effective(MpvSynchronizationKind.AUDIO))
        assertEquals(275L, state.effective(MpvSynchronizationKind.SUBTITLE))
    }

    @Test
    fun `promotion makes the effective value the baseline without changing playback`() {
        val state = MpvSynchronizationState(audioBaselineMs = 100L, subtitleBaselineMs = 0L)
        state.setTemporary(MpvSynchronizationKind.AUDIO, -150L)

        state.promote(MpvSynchronizationKind.AUDIO)

        assertEquals(-50L, state.baseline(MpvSynchronizationKind.AUDIO))
        assertEquals(BigInteger.ZERO, state.temporary(MpvSynchronizationKind.AUDIO))
        assertEquals(-50L, state.effective(MpvSynchronizationKind.AUDIO))
    }

    @Test
    fun `place arithmetic carries borrows and clamps magnitude at zero`() {
        assertEquals(109L, adjustSynchronizationMagnitude(99L, 10L))
        assertEquals(1_000L, adjustSynchronizationMagnitude(999L, 1L))
        assertEquals(999L, adjustSynchronizationMagnitude(1_000L, -1L))
        assertEquals(0L, adjustSynchronizationMagnitude(0L, -1L))
    }

    @Test
    fun `updating one configured default preserves the other default`() {
        val defaults = MpvSynchronizationDefaults(audioMs = 100L, subtitleMs = -200L)

        assertEquals(
            MpvSynchronizationDefaults(audioMs = 300L, subtitleMs = -200L),
            defaults.withValue(MpvSynchronizationKind.AUDIO, 300L),
        )
        assertEquals(
            MpvSynchronizationDefaults(audioMs = 100L, subtitleMs = 400L),
            defaults.withValue(MpvSynchronizationKind.SUBTITLE, 400L),
        )
    }

    @Test
    fun `opposite long endpoints retain an exact wider temporary delta`() {
        val state =
            MpvSynchronizationState(
                audioBaselineMs = Long.MIN_VALUE,
                subtitleBaselineMs = Long.MAX_VALUE,
            )

        state.setEffective(MpvSynchronizationKind.AUDIO, Long.MAX_VALUE)
        state.setEffective(MpvSynchronizationKind.SUBTITLE, Long.MIN_VALUE)

        assertEquals(Long.MAX_VALUE, state.effective(MpvSynchronizationKind.AUDIO))
        assertEquals(Long.MIN_VALUE, state.effective(MpvSynchronizationKind.SUBTITLE))
        assertEquals(
            BigInteger("18446744073709551615"),
            state.temporary(MpvSynchronizationKind.AUDIO),
        )
        assertEquals(
            BigInteger("-18446744073709551615"),
            state.temporary(MpvSynchronizationKind.SUBTITLE),
        )
    }

    @Test
    fun `effective baseline plus temporary rejects overflow past either endpoint`() {
        val upper = MpvSynchronizationState(audioBaselineMs = Long.MAX_VALUE)
        val lower = MpvSynchronizationState(audioBaselineMs = Long.MIN_VALUE)

        assertThrows(ArithmeticException::class.java) {
            upper.setTemporary(MpvSynchronizationKind.AUDIO, 1L)
        }
        assertThrows(ArithmeticException::class.java) {
            lower.setTemporary(MpvSynchronizationKind.AUDIO, -1L)
        }
        assertEquals(Long.MAX_VALUE, upper.effective(MpvSynchronizationKind.AUDIO))
        assertEquals(Long.MIN_VALUE, lower.effective(MpvSynchronizationKind.AUDIO))
    }
}
