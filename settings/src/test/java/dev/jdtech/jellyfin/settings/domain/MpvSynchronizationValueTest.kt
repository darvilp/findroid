package dev.jdtech.jellyfin.settings.domain

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MpvSynchronizationValueTest {
    @Test
    fun `formats signed milliseconds as exact mpv seconds`() {
        assertEquals("0.000", MpvSynchronizationValue.formatMpvSeconds(0L))
        assertEquals("0.125", MpvSynchronizationValue.formatMpvSeconds(125L))
        assertEquals("-0.050", MpvSynchronizationValue.formatMpvSeconds(-50L))
        assertEquals("12.345", MpvSynchronizationValue.formatMpvSeconds(12_345L))
    }

    @Test
    fun `formatting is independent of the device locale`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1.005", MpvSynchronizationValue.formatMpvSeconds(1_005L))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `parses exact seconds with up to three fractional digits`() {
        assertEquals(0L, MpvSynchronizationValue.parseMagnitudeSeconds("0"))
        assertEquals(125L, MpvSynchronizationValue.parseMagnitudeSeconds("0.125"))
        assertEquals(1_500L, MpvSynchronizationValue.parseMagnitudeSeconds("1.5"))
        assertEquals(12_345L, MpvSynchronizationValue.parseMagnitudeSeconds("12.345"))
    }

    @Test
    fun `rejects invalid negative overly precise and overflowing magnitudes`() {
        assertNull(MpvSynchronizationValue.parseMagnitudeSeconds("-1"))
        assertNull(MpvSynchronizationValue.parseMagnitudeSeconds("1.0001"))
        assertNull(MpvSynchronizationValue.parseMagnitudeSeconds("seconds"))
        assertNull(MpvSynchronizationValue.parseMagnitudeSeconds("9223372036854776"))
    }

    @Test
    fun `parses signed mpv seconds only when they represent exact milliseconds`() {
        assertEquals(-50L, MpvSynchronizationValue.parseSignedMpvSeconds("-0.050"))
        assertEquals(1_500L, MpvSynchronizationValue.parseSignedMpvSeconds("1.5"))
        assertEquals(Long.MAX_VALUE, MpvSynchronizationValue.parseSignedMpvSeconds("9223372036854775.807"))
        assertEquals(Long.MIN_VALUE, MpvSynchronizationValue.parseSignedMpvSeconds("-9223372036854775.808"))
        assertNull(MpvSynchronizationValue.parseSignedMpvSeconds("0.0005"))
        assertNull(MpvSynchronizationValue.parseSignedMpvSeconds("seconds"))
        assertNull(MpvSynchronizationValue.parseSignedMpvSeconds("9223372036854775.808"))
        assertEquals(125L, MpvSynchronizationValue.parseSignedMpvSeconds("0.125000"))
    }

    @Test
    fun `formats the complete signed long range exactly`() {
        assertEquals("9223372036854775.807", MpvSynchronizationValue.formatMpvSeconds(Long.MAX_VALUE))
        assertEquals("-9223372036854775.808", MpvSynchronizationValue.formatMpvSeconds(Long.MIN_VALUE))
    }
}
