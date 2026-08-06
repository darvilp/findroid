package dev.jdtech.jellyfin.player.local.domain

enum class PlaylistNavigationDirection {
    Previous,
    Next,
}

data class PlaylistNavigationState(
    val canGoPrevious: Boolean,
    val canGoNext: Boolean,
) {
    fun isAvailable(direction: PlaylistNavigationDirection): Boolean =
        when (direction) {
            PlaylistNavigationDirection.Previous -> canGoPrevious
            PlaylistNavigationDirection.Next -> canGoNext
        }
}

internal interface PlaylistNavigationTarget {
    fun hasPreviousMediaItem(): Boolean

    fun hasNextMediaItem(): Boolean

    fun isPreviousMediaItemCommandAvailable(): Boolean

    fun isNextMediaItemCommandAvailable(): Boolean

    fun seekToPreviousMediaItem()

    fun seekToNextMediaItem()

    fun playWhenReady(): Boolean

    fun play()

    fun pause()
}

internal class PlaylistNavigationController {
    fun state(target: PlaylistNavigationTarget): PlaylistNavigationState =
        PlaylistNavigationState(
            canGoPrevious =
                target.hasPreviousMediaItem() &&
                    target.isPreviousMediaItemCommandAvailable(),
            canGoNext =
                target.hasNextMediaItem() && target.isNextMediaItemCommandAvailable(),
        )

    fun navigate(
        direction: PlaylistNavigationDirection,
        target: PlaylistNavigationTarget,
    ): Boolean {
        if (!state(target).isAvailable(direction)) return false

        val playWhenReady = target.playWhenReady()
        when (direction) {
            PlaylistNavigationDirection.Previous -> target.seekToPreviousMediaItem()
            PlaylistNavigationDirection.Next -> target.seekToNextMediaItem()
        }
        if (target.playWhenReady() != playWhenReady) {
            if (playWhenReady) target.play() else target.pause()
        }
        return true
    }
}
