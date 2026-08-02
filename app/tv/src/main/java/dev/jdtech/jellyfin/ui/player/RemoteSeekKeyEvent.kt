package dev.jdtech.jellyfin.ui.player

import android.view.KeyEvent

internal fun KeyEvent.remoteSeekDirectionOrNull(): RemoteSeekDirection? =
    when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> RemoteSeekDirection.Backward
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> RemoteSeekDirection.Forward
        else -> null
    }
