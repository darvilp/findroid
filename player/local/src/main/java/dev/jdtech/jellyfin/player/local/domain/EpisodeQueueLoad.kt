package dev.jdtech.jellyfin.player.local.domain

/** Invalidates an adjacent-item lookup even when playback returns to the same item. */
internal class EpisodeQueueLoad {
    private var generation = 0L

    fun begin(): Long = ++generation

    fun isCurrent(expected: Long): Boolean = generation == expected

    suspend fun <T> load(
        expected: Long,
        isAnchorCurrent: () -> Boolean,
        fetch: suspend (PlaylistNavigationDirection) -> T?,
        insert: (PlaylistNavigationDirection, T) -> Unit,
    ) {
        for (direction in PlaylistNavigationDirection.entries) {
            if (!isCurrent(expected) || !isAnchorCurrent()) return
            val item = fetch(direction) ?: continue
            if (!isCurrent(expected) || !isAnchorCurrent()) return
            insert(direction, item)
        }
    }
}
