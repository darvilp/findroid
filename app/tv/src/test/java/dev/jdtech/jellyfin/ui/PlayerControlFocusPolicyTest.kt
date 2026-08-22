package dev.jdtech.jellyfin.ui

import dev.jdtech.jellyfin.ui.components.player.VideoPlayerOverlayMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerControlFocusPolicyTest {
    private val stableControls =
        setOf(
            PlayerControl.Details,
            PlayerControl.Audio,
            PlayerControl.Subtitles,
            PlayerControl.PlayPause,
            PlayerControl.SeekBar,
        )

    @Test
    fun `segment updates preserve a surviving seek bar focus`() {
        assertNull(
            playerFocusRequest(
                overlayMode = VideoPlayerOverlayMode.Controls,
                modalActive = false,
                skipPromptMayTakeFocus = false,
                focusedControl = PlayerControl.SeekBar,
                availableControls = stableControls,
            )
        )
    }

    @Test
    fun `removing the focused conditional control falls back to play pause`() {
        assertEquals(
            PlayerFocusRequest.Control(PlayerControl.PlayPause),
            playerFocusRequest(
                overlayMode = VideoPlayerOverlayMode.Controls,
                modalActive = false,
                skipPromptMayTakeFocus = false,
                focusedControl = PlayerControl.Restart,
                availableControls = stableControls,
            ),
        )
    }

    @Test
    fun `removing an unfocused conditional control preserves current focus`() {
        assertNull(
            playerFocusRequest(
                overlayMode = VideoPlayerOverlayMode.Controls,
                modalActive = false,
                skipPromptMayTakeFocus = false,
                focusedControl = PlayerControl.SeekBar,
                availableControls = stableControls,
            )
        )
    }

    @Test
    fun `hidden controls return focus to the player root`() {
        assertEquals(
            PlayerFocusRequest.Root,
            playerFocusRequest(
                overlayMode = VideoPlayerOverlayMode.Hidden,
                modalActive = false,
                skipPromptMayTakeFocus = false,
                focusedControl = null,
                availableControls = stableControls,
            ),
        )
    }

    @Test
    fun `an available skip prompt takes focus only outside full controls`() {
        assertEquals(
            PlayerFocusRequest.SkipPrompt,
            playerFocusRequest(
                overlayMode = VideoPlayerOverlayMode.Peek,
                modalActive = false,
                skipPromptMayTakeFocus = true,
                focusedControl = null,
                availableControls = stableControls,
            ),
        )
    }

    @Test
    fun `modal content owns focus`() {
        assertNull(
            playerFocusRequest(
                overlayMode = VideoPlayerOverlayMode.Controls,
                modalActive = true,
                skipPromptMayTakeFocus = false,
                focusedControl = PlayerControl.Audio,
                availableControls = stableControls,
            )
        )
    }

    @Test
    fun `controls restore the last available control`() {
        assertEquals(
            PlayerControl.SeekBar,
            restoredPlayerControl(PlayerControl.SeekBar, stableControls),
        )
    }

    @Test
    fun `controls fall back to play pause when the last control disappeared`() {
        assertEquals(
            PlayerControl.PlayPause,
            restoredPlayerControl(PlayerControl.Restart, stableControls),
        )
    }

    @Test
    fun `removing a focused skip prompt restores the last bottom control`() {
        assertEquals(
            PlayerControl.SeekBar,
            focusAfterSkipPromptRemoval(
                skipPromptWasFocused = true,
                skipPromptAvailable = false,
                overlayMode = VideoPlayerOverlayMode.Controls,
                lastFocusedBottomControl = PlayerControl.SeekBar,
                availableControls = stableControls,
            ),
        )
    }

    @Test
    fun `directional neighbors follow the available control order`() {
        val actions =
            listOf(
                PlayerControl.Details,
                PlayerControl.Audio,
                PlayerControl.Subtitles,
            )

        assertEquals(
            PlayerControl.Audio,
            previousPlayerControl(PlayerControl.Subtitles, actions),
        )
        assertEquals(PlayerControl.Audio, nextPlayerControl(PlayerControl.Details, actions))
        assertNull(previousPlayerControl(PlayerControl.Details, actions))
        assertNull(nextPlayerControl(PlayerControl.Subtitles, actions))
    }
}
