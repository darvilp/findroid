package dev.jdtech.jellyfin.player.local.mpv

import dev.jdtech.jellyfin.settings.domain.MpvSynchronizationKind
import org.junit.Assert.assertEquals
import org.junit.Test

class MpvSynchronizationTest {
    @Test
    fun `audio and subtitle synchronization map to the correct mpv properties`() {
        assertEquals("audio-delay", mpvSynchronizationProperty(MpvSynchronizationKind.AUDIO))
        assertEquals("sub-delay", mpvSynchronizationProperty(MpvSynchronizationKind.SUBTITLE))
    }

    @Test
    fun `mpv boundary preserves exact signed milliseconds`() {
        assertEquals("0.125", mpvSynchronizationSeconds(125L))
        assertEquals("-0.050", mpvSynchronizationSeconds(-50L))
        assertEquals(125L, mpvSynchronizationMilliseconds("0.125"))
        assertEquals(-50L, mpvSynchronizationMilliseconds("-0.050"))
        assertEquals(125L, mpvSynchronizationMilliseconds("0.125000"))
        assertEquals(-50L, mpvSynchronizationMilliseconds("-0.050000"))
        assertEquals(null, mpvSynchronizationMilliseconds("0.125001"))
    }
}
