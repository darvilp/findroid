package dev.jdtech.jellyfin.ui.player

import android.view.KeyEvent
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.local.domain.ChapterNavigationDirection
import dev.jdtech.jellyfin.player.local.domain.ChapterNavigationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ChapterRemoteKeyEventTest {
    @Test
    fun `channel down and up map to previous and next chapter`() {
        val navigation = meaningfulNavigation()

        assertEquals(
            ChapterNavigationDirection.Previous,
            chapterRemoteCommand(KeyEvent.KEYCODE_CHANNEL_DOWN, false, navigation),
        )
        assertEquals(
            ChapterNavigationDirection.Next,
            chapterRemoteCommand(KeyEvent.KEYCODE_CHANNEL_UP, false, navigation),
        )
    }

    @Test
    fun `generic media skip keys are chapter aliases`() {
        val navigation = meaningfulNavigation()

        assertEquals(
            ChapterNavigationDirection.Previous,
            chapterRemoteCommand(KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD, false, navigation),
        )
        assertEquals(
            ChapterNavigationDirection.Next,
            chapterRemoteCommand(KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD, false, navigation),
        )
    }

    @Test
    fun `media previous and next remain available to playlist handling`() {
        val navigation = meaningfulNavigation()

        assertNull(chapterRemoteCommand(KeyEvent.KEYCODE_MEDIA_PREVIOUS, false, navigation))
        assertNull(chapterRemoteCommand(KeyEvent.KEYCODE_MEDIA_NEXT, false, navigation))
    }

    @Test
    fun `active modal retains chapter key ownership`() {
        assertNull(
            chapterRemoteCommand(KeyEvent.KEYCODE_CHANNEL_UP, true, meaningfulNavigation())
        )
    }

    @Test
    fun `meaningful chapter boundary is consumed as a no op`() {
        val command =
            chapterRemoteCommand(
                keyCode = KeyEvent.KEYCODE_CHANNEL_DOWN,
                modalActive = false,
                navigation = meaningfulNavigation(),
            )

        assertEquals(ChapterNavigationDirection.Previous, command)
    }

    @Test
    fun `chapter keys are unhandled when the item has no meaningful chapter set`() {
        val navigation =
            ChapterNavigationState(
                chapters = listOf(PlayerChapter(0L)),
                previousChapter = null,
                nextChapter = null,
            )

        assertFalse(navigation.hasMeaningfulChapters)
        assertNull(chapterRemoteCommand(KeyEvent.KEYCODE_CHANNEL_UP, false, navigation))
    }

    private fun meaningfulNavigation() =
        ChapterNavigationState(
            chapters = listOf(PlayerChapter(0L), PlayerChapter(60_000L)),
            previousChapter = null,
            nextChapter = null,
        )
}
