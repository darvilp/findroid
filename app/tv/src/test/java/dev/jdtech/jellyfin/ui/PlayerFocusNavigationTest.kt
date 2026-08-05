package dev.jdtech.jellyfin.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerFocusNavigationTest {
    @Test
    fun `playback controls and an available skip prompt are vertical neighbors`() {
        assertEquals(
            PlayerFocusTarget.SkipPrompt,
            playerFocusDestination(
                source = PlayerFocusTarget.DefaultControls,
                direction = PlayerFocusDirection.Down,
                skipPromptAvailable = true,
            ),
        )
        assertEquals(
            PlayerFocusTarget.DefaultControls,
            playerFocusDestination(
                source = PlayerFocusTarget.SkipPrompt,
                direction = PlayerFocusDirection.Up,
                skipPromptAvailable = true,
            ),
        )
    }

    @Test
    fun `playback controls retain normal down navigation without a skip prompt`() {
        assertNull(
            playerFocusDestination(
                source = PlayerFocusTarget.DefaultControls,
                direction = PlayerFocusDirection.Down,
                skipPromptAvailable = false,
            )
        )
    }

    @Test
    fun `down from the bottom skip prompt has no focus destination`() {
        assertNull(
            playerFocusDestination(
                source = PlayerFocusTarget.SkipPrompt,
                direction = PlayerFocusDirection.Down,
                skipPromptAvailable = true,
            )
        )
    }
}
