package dev.jdtech.jellyfin.player.local.domain

import androidx.media3.common.C
import java.util.UUID
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialTrackApplicationStateTest {
    @Test
    fun `returning to a rebuilt previous item can apply its track again`() {
        val first = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
        val second = UUID.fromString("11111111-2222-3333-4444-555555555555")
        val state = InitialTrackApplicationState()

        state.markApplied(first, C.TRACK_TYPE_AUDIO)
        state.markApplied(second, C.TRACK_TYPE_AUDIO)
        state.reset(first)

        assertFalse(state.isApplied(first, C.TRACK_TYPE_AUDIO))
        assertTrue(state.isApplied(second, C.TRACK_TYPE_AUDIO))
    }
}
