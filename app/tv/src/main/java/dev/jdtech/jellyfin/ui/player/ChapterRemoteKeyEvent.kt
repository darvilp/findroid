package dev.jdtech.jellyfin.ui.player

import android.view.KeyEvent
import dev.jdtech.jellyfin.player.local.domain.ChapterNavigationDirection
import dev.jdtech.jellyfin.player.local.domain.ChapterNavigationState

internal fun chapterRemoteCommand(
    keyCode: Int,
    modalActive: Boolean,
    navigation: ChapterNavigationState,
): ChapterNavigationDirection? {
    if (modalActive || !navigation.hasMeaningfulChapters) return null

    val direction =
        when (keyCode) {
            KeyEvent.KEYCODE_CHANNEL_DOWN,
            KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> ChapterNavigationDirection.Previous
            KeyEvent.KEYCODE_CHANNEL_UP,
            KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> ChapterNavigationDirection.Next
            else -> return null
        }

    return direction
}
