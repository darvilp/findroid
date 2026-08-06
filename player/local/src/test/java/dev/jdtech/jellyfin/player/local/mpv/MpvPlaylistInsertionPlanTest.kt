package dev.jdtech.jellyfin.player.local.mpv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvPlaylistInsertionPlanTest {
    @Test
    fun `inserting before the current item preserves its media identity`() {
        val plan =
            planMpvPlaylistInsertion(
                requestedIndex = 0,
                playlistSize = 1,
                currentMediaItemIndex = 0,
                insertedItemCount = 1,
            )

        assertEquals(
            MpvPlaylistInsertionPlan(
                insertionIndex = 0,
                currentMediaItemIndex = 1,
                commandIndices = listOf(0),
                awaitedPlaylistCurrentPosition = 1,
            ),
            plan,
        )
    }

    @Test
    fun `inserting after the current item leaves its index unchanged`() {
        val plan =
            planMpvPlaylistInsertion(
                requestedIndex = 2,
                playlistSize = 3,
                currentMediaItemIndex = 1,
                insertedItemCount = 1,
            )

        assertEquals(
            MpvPlaylistInsertionPlan(
                insertionIndex = 2,
                currentMediaItemIndex = 1,
                commandIndices = listOf(2),
                awaitedPlaylistCurrentPosition = null,
            ),
            plan,
        )
    }

    @Test
    fun `an index beyond the playlist appends the items`() {
        val plan =
            planMpvPlaylistInsertion(
                requestedIndex = 4,
                playlistSize = 2,
                currentMediaItemIndex = 0,
                insertedItemCount = 1,
            )

        assertEquals(
            MpvPlaylistInsertionPlan(
                insertionIndex = 2,
                currentMediaItemIndex = 0,
                commandIndices = listOf(2),
                awaitedPlaylistCurrentPosition = null,
            ),
            plan,
        )
    }

    @Test
    fun `inserting into an empty playlist keeps the initial index`() {
        val plan =
            planMpvPlaylistInsertion(
                requestedIndex = 0,
                playlistSize = 0,
                currentMediaItemIndex = 0,
                insertedItemCount = 1,
            )

        assertEquals(
            MpvPlaylistInsertionPlan(
                insertionIndex = 0,
                currentMediaItemIndex = 0,
                commandIndices = listOf(0),
                awaitedPlaylistCurrentPosition = null,
            ),
            plan,
        )
    }

    @Test
    fun `inserting several items preserves their order and shifts by their count`() {
        val plan =
            planMpvPlaylistInsertion(
                requestedIndex = 1,
                playlistSize = 3,
                currentMediaItemIndex = 2,
                insertedItemCount = 2,
            )

        assertEquals(
            MpvPlaylistInsertionPlan(
                insertionIndex = 1,
                currentMediaItemIndex = 4,
                commandIndices = listOf(1, 2),
                awaitedPlaylistCurrentPosition = 4,
            ),
            plan,
        )
    }

    @Test
    fun `intermediate native positions do not replace the preserved current item`() {
        val plan =
            planMpvPlaylistInsertion(
                requestedIndex = 1,
                playlistSize = 3,
                currentMediaItemIndex = 2,
                insertedItemCount = 2,
            )

        assertFalse(plan.acceptsPlaylistCurrentPosition(3))
        assertTrue(plan.acceptsPlaylistCurrentPosition(4))
    }

    @Test
    fun `inserting no items leaves playlist state unchanged`() {
        val plan =
            planMpvPlaylistInsertion(
                requestedIndex = 1,
                playlistSize = 3,
                currentMediaItemIndex = 1,
                insertedItemCount = 0,
            )

        assertEquals(
            MpvPlaylistInsertionPlan(
                insertionIndex = 1,
                currentMediaItemIndex = 1,
                commandIndices = emptyList(),
                awaitedPlaylistCurrentPosition = null,
            ),
            plan,
        )
    }
}
