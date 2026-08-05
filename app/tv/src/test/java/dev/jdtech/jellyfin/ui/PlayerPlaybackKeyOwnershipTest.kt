package dev.jdtech.jellyfin.ui

import dev.jdtech.jellyfin.ui.components.player.VideoPlayerOverlayMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPlaybackKeyOwnershipTest {
    @Test
    fun `focused skip prompt owns playback keys while controls are hidden or peeking`() {
        assertFalse(
            playerRootOwnsPlaybackKeys(
                overlayMode = VideoPlayerOverlayMode.Hidden,
                skipPromptFocused = true,
            )
        )
        assertFalse(
            playerRootOwnsPlaybackKeys(
                overlayMode = VideoPlayerOverlayMode.Peek,
                skipPromptFocused = true,
            )
        )
    }

    @Test
    fun `root owns playback keys when no interactive child is focused`() {
        assertTrue(
            playerRootOwnsPlaybackKeys(
                overlayMode = VideoPlayerOverlayMode.Hidden,
                skipPromptFocused = false,
            )
        )
        assertTrue(
            playerRootOwnsPlaybackKeys(
                overlayMode = VideoPlayerOverlayMode.Peek,
                skipPromptFocused = false,
            )
        )
    }

    @Test
    fun `visible controls own their playback keys`() {
        assertFalse(
            playerRootOwnsPlaybackKeys(
                overlayMode = VideoPlayerOverlayMode.Controls,
                skipPromptFocused = false,
            )
        )
    }
}
