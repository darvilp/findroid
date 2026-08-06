package dev.jdtech.jellyfin.player.local.mpv

internal data class MpvPlaylistInsertionPlan(
    val insertionIndex: Int,
    val currentMediaItemIndex: Int,
    val commandIndices: List<Int>,
    val awaitedPlaylistCurrentPosition: Int?,
) {
    fun acceptsPlaylistCurrentPosition(position: Int): Boolean =
        awaitedPlaylistCurrentPosition == null || position == awaitedPlaylistCurrentPosition
}

internal fun planMpvPlaylistInsertion(
    requestedIndex: Int,
    playlistSize: Int,
    currentMediaItemIndex: Int,
    insertedItemCount: Int,
): MpvPlaylistInsertionPlan {
    val insertionIndex = requestedIndex.coerceAtMost(playlistSize)
    val shiftsCurrentItem =
        playlistSize > 0 &&
            insertedItemCount > 0 &&
            insertionIndex <= currentMediaItemIndex
    val currentMediaItemIndexAfterInsertion =
        if (shiftsCurrentItem) {
            currentMediaItemIndex + insertedItemCount
        } else {
            currentMediaItemIndex
        }
    return MpvPlaylistInsertionPlan(
        insertionIndex = insertionIndex,
        currentMediaItemIndex = currentMediaItemIndexAfterInsertion,
        commandIndices = List(insertedItemCount) { insertionIndex + it },
        awaitedPlaylistCurrentPosition =
            currentMediaItemIndexAfterInsertion.takeIf { shiftsCurrentItem },
    )
}
