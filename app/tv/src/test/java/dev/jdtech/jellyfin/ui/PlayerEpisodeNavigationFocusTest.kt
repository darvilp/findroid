package dev.jdtech.jellyfin.ui

import dev.jdtech.jellyfin.player.local.domain.PlaylistNavigationDirection
import dev.jdtech.jellyfin.player.local.domain.PlaylistNavigationState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerEpisodeNavigationFocusTest {
    @Test
    fun `focus returns to play when the focused episode direction disappears`() {
        assertTrue(
            shouldRestoreEpisodeNavigationFocus(
                focusedDirection = PlaylistNavigationDirection.Next,
                navigation = PlaylistNavigationState(canGoPrevious = true, canGoNext = false),
            )
        )
    }

    @Test
    fun `focus stays put while the focused episode direction remains available`() {
        assertFalse(
            shouldRestoreEpisodeNavigationFocus(
                focusedDirection = PlaylistNavigationDirection.Previous,
                navigation = PlaylistNavigationState(canGoPrevious = true, canGoNext = false),
            )
        )
    }

    @Test
    fun `availability changes do not steal focus from another control`() {
        assertFalse(
            shouldRestoreEpisodeNavigationFocus(
                focusedDirection = null,
                navigation = PlaylistNavigationState(canGoPrevious = true, canGoNext = false),
            )
        )
    }
}
