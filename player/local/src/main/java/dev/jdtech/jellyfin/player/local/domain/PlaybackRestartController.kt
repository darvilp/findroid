package dev.jdtech.jellyfin.player.local.domain

/**
 * Capability-limited restart target. Playlist replacement, track selection, and watched-state
 * mutation are intentionally outside this boundary.
 */
internal interface PlaybackRestartTarget {
    fun beginPlaybackPass()

    fun seekTo(positionMs: Long)

    fun play()
}

internal class PlaybackRestartController {
    fun isAvailable(currentPositionMs: Long, seekBackIncrementMs: Long): Boolean =
        currentPositionMs.coerceAtLeast(0L) > seekBackIncrementMs.coerceAtLeast(0L)

    fun restart(target: PlaybackRestartTarget) {
        target.beginPlaybackPass()
        target.seekTo(0L)
        target.play()
    }
}
