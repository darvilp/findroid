package dev.jdtech.jellyfin.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FindroidMediaStreamTest {
    @Test
    fun `missing delivery URL remains missing`() {
        assertNull(null.toAbsoluteDeliveryUrl("https://example.invalid"))
    }

    @Test
    fun `relative delivery URL is resolved against the server`() {
        assertEquals(
            "https://example.invalid/Videos/stream.vtt",
            "/Videos/stream.vtt".toAbsoluteDeliveryUrl("https://example.invalid"),
        )
    }
}
