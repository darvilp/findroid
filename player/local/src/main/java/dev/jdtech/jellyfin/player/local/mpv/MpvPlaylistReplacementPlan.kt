package dev.jdtech.jellyfin.player.local.mpv

internal data class MpvPlaylistReplacementPlan(
    val toIndex: Int,
    val currentIndex: Int,
    val replacesCurrentItem: Boolean,
    val commands: List<List<String>>,
)

internal fun planMpvPlaylistReplacementOrNull(
    sources: List<String>,
    fromIndex: Int,
    toIndex: Int,
    replacements: List<String>,
    currentIndex: Int,
): MpvPlaylistReplacementPlan? {
    require(fromIndex >= 0 && toIndex >= fromIndex)
    if (fromIndex > sources.size) return null
    val end = toIndex.coerceAtMost(sources.size)
    val removedCount = end - fromIndex
    val sameSources = sources.subList(fromIndex, end) == replacements
    val replacesCurrent = !sameSources && currentIndex in fromIndex until end
    val newSize = sources.size - removedCount + replacements.size
    val newCurrentIndex =
        when {
            sources.isEmpty() -> 0
            sameSources || currentIndex < fromIndex -> currentIndex
            replacesCurrent -> fromIndex.coerceAtMost((newSize - 1).coerceAtLeast(0))
            else -> currentIndex + replacements.size - removedCount
        }
    val commands = buildList {
        if (!sameSources) {
            // Insert after the old range before deleting it. The active native entry survives
            // queued-item changes, even when those changes shift its playlist index.
            replacements.forEachIndexed { offset, source ->
                add(listOf("loadfile", source, "insert-at", (end + offset).toString()))
            }
            for (index in end - 1 downTo fromIndex) {
                if (!replacesCurrent || index != currentIndex) {
                    add(listOf("playlist-remove", index.toString()))
                }
            }
            // Removing an active entry starts its successor. Remove it last, after all other
            // obsolete entries, so that successor is a replacement or a surviving queued item.
            if (replacesCurrent) add(listOf("playlist-remove", fromIndex.toString()))
        }
    }
    return MpvPlaylistReplacementPlan(end, newCurrentIndex, replacesCurrent, commands)
}
