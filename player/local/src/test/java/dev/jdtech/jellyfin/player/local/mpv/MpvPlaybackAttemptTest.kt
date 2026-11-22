package dev.jdtech.jellyfin.player.local.mpv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvPlaybackAttemptTest {
    @Test
    fun `only one error for the current native load is accepted`() {
        val attempts = MpvPlaybackAttempt()
        attempts.requestFile()
        val original = attempts.onNativeStart()
        assertFalse(attempts.claimFailure(original, 0)) // EOF
        assertFalse(attempts.claimFailure(original, 2)) // Stop
        assertFalse(attempts.claimFailure(original, 5)) // Redirect
        assertTrue(attempts.claimFailure(original, 4))
        assertFalse(attempts.claimFailure(original, 4))

        attempts.requestFile()
        assertFalse(attempts.claimFailure(original, 4))
        val retry = attempts.onNativeStart()
        assertTrue(attempts.claimFailure(retry, 4))
    }
}
