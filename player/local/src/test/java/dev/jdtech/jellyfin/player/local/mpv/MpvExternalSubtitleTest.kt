package dev.jdtech.jellyfin.player.local.mpv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MpvExternalSubtitleTest {
    @Test
    fun `a unique file URI matches the decoded path but ambiguous matches are rejected`() {
        val subtitle = "file:///tmp/Full%20Subtitles.srt" to 42
        assertEquals(42, externalSubtitleStreamIndex("/tmp/Full Subtitles.srt", listOf(subtitle)))
        assertNull(
            externalSubtitleStreamIndex(
                "/tmp/Full Subtitles.srt",
                listOf(subtitle, subtitle.first to 43),
            ),
        )
    }

    @Test
    fun `remote sidecars match by exact URI including query`() {
        val subtitle = "https://example.invalid/sub.srt?id=1" to 42
        assertEquals(42, externalSubtitleStreamIndex(subtitle.first, listOf(subtitle)))
        assertNull(
            externalSubtitleStreamIndex("https://example.invalid/sub.srt?id=2", listOf(subtitle)),
        )
    }
}
