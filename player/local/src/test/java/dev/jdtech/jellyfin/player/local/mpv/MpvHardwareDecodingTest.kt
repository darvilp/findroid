package dev.jdtech.jellyfin.player.local.mpv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvHardwareDecodingTest {
    @Test
    fun `reports the decoder actually in use`() {
        assertTrue(isHardwareDecodingActive("mediacodec") == true)
        assertTrue(isHardwareDecodingActive("mediacodec-copy") == true)
        assertEquals(false, isHardwareDecodingActive("no"))
        assertNull(isHardwareDecodingActive(null))
    }

    @Test
    fun `disabling hardware decoding selects software decoding`() {
        assertEquals("no", requestedHwdec(enabled = false, configuredHwdec = "mediacodec"))
    }

    @Test
    fun `enabling hardware decoding restores the configured decoder`() {
        assertEquals(
            "mediacodec-copy",
            requestedHwdec(enabled = true, configuredHwdec = "mediacodec-copy"),
        )
    }

    @Test
    fun `enabling uses mediacodec when hardware decoding is disabled by default`() {
        assertEquals("mediacodec", requestedHwdec(enabled = true, configuredHwdec = "no"))
    }
}
