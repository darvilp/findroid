package dev.jdtech.jellyfin.player.local.mpv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class MpvPlaylistReplacementPlanTest {
    private val sources = listOf("previous", "playing", "next")

    @Test
    fun `refreshing queued track metadata keeps native playback untouched`() {
        for (index in listOf(0, 2)) {
            val plan = planMpvPlaylistReplacement(sources, index, index + 1, listOf(sources[index]), 1)
            assertEquals(1, plan.currentIndex)
            assertTrue(plan.commands.isEmpty())
            assertFalse(plan.replacesCurrentItem)
        }
    }

    @Test
    fun `new next episode URL replaces its native entry without restarting current`() {
        val plan = planMpvPlaylistReplacement(sources, 2, 3, listOf("new-next"), 1)
        assertEquals(listOf(listOf("loadfile", "new-next", "insert-at", "3"), listOf("playlist-remove", "2")), plan.commands)
        assertEquals(1, plan.currentIndex)
        assertFalse(plan.replacesCurrentItem)
    }

    @Test
    fun `new previous episode URL preserves current index after intermediate shifts`() {
        val plan = planMpvPlaylistReplacement(sources, 0, 1, listOf("new-previous"), 1)
        assertEquals(listOf(listOf("loadfile", "new-previous", "insert-at", "1"), listOf("playlist-remove", "0")), plan.commands)
        assertEquals(1, plan.currentIndex)
        assertFalse(plan.replacesCurrentItem)
    }

    @Test
    fun `larger replacement preserves order and shifts current by net size change`() {
        val plan = planMpvPlaylistReplacement(sources, 0, 1, listOf("a", "b"), 1)
        assertEquals(listOf(listOf("loadfile", "a", "insert-at", "1"), listOf("loadfile", "b", "insert-at", "2"), listOf("playlist-remove", "0")), plan.commands)
        assertEquals(2, plan.currentIndex)
        assertFalse(plan.replacesCurrentItem)
    }

    @Test
    fun `removing earlier entries shifts current without removing its native entry`() {
        val plan = planMpvPlaylistReplacement(sources, 0, 2, emptyList(), 2)
        assertEquals(listOf(listOf("playlist-remove", "1"), listOf("playlist-remove", "0")), plan.commands)
        assertEquals(0, plan.currentIndex)
        assertFalse(plan.replacesCurrentItem)
    }

    @Test
    fun `metadata only replacement of current source does not restart playback`() {
        val plan = planMpvPlaylistReplacement(sources, 1, 2, listOf("playing"), 1)
        assertTrue(plan.commands.isEmpty())
        assertFalse(plan.replacesCurrentItem)
    }

    @Test
    fun `current is removed last so it cannot start an entry being deleted`() {
        val plan = planMpvPlaylistReplacement(sources, 0, 3, listOf("new"), 1)
        assertEquals(listOf(listOf("loadfile", "new", "insert-at", "3"), listOf("playlist-remove", "2"), listOf("playlist-remove", "0"), listOf("playlist-remove", "0")), plan.commands)
        assertEquals(0, plan.currentIndex)
        assertTrue(plan.replacesCurrentItem)
    }

    @Test
    fun `out of bounds start is ignored and end is clipped`() {
        assertEquals(null, planMpvPlaylistReplacementOrNull(sources, 4, 5, listOf("new"), 1))
        val plan = planMpvPlaylistReplacement(sources, 2, 99, listOf("new"), 1)
        assertEquals(3, plan.toIndex)
        assertEquals(1, plan.currentIndex)
    }

    @Test
    fun `insertion at end and empty playlist are valid ranges`() {
        val append = planMpvPlaylistReplacement(sources, 3, 3, listOf("new"), 1)
        assertEquals(listOf(listOf("loadfile", "new", "insert-at", "3")), append.commands)
        val empty = planMpvPlaylistReplacement(emptyList(), 0, 0, listOf("new"), 0)
        assertEquals(0, empty.currentIndex)
        assertFalse(empty.replacesCurrentItem)
    }

    @Test
    fun `negative and inverted ranges are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { planMpvPlaylistReplacement(sources, -1, 1, emptyList(), 1) }
        assertThrows(IllegalArgumentException::class.java) { planMpvPlaylistReplacement(sources, 2, 1, emptyList(), 1) }
    }

    @Test
    fun `commands produce the requested queue and preserve every surviving current entry`() {
        // Exercise shrinking, expanding, insertion, removal and current-item replacement ranges.
        // This interpreter models only MPV's documented insert-at and playlist-remove commands.
        for (size in 1..5) {
            val original = List(size) { "old-$it" }
            for (from in 0..size) for (to in from..size) for (count in 0..3) for (current in original.indices) {
                val replacements = List(count) { "new-$it" }
                val plan = planMpvPlaylistReplacement(original, from, to, replacements, current)
                val nativeQueue = original.toMutableList()
                plan.commands.forEach { command ->
                    when (command[0]) {
                        "loadfile" -> nativeQueue.add(command[3].toInt(), command[1])
                        "playlist-remove" -> nativeQueue.removeAt(command[1].toInt())
                        else -> error("Unexpected command: $command")
                    }
                }
                assertEquals(original.take(from) + replacements + original.drop(to), nativeQueue)
                if (current !in from until to) {
                    assertFalse(plan.replacesCurrentItem)
                    assertEquals(original[current], nativeQueue[plan.currentIndex])
                } else {
                    assertTrue(plan.replacesCurrentItem)
                    if (from < nativeQueue.size) assertEquals(from, plan.currentIndex)
                }
            }
        }
    }

    private fun planMpvPlaylistReplacement(sources: List<String>, from: Int, to: Int, replacements: List<String>, current: Int) =
        requireNotNull(planMpvPlaylistReplacementOrNull(sources, from, to, replacements, current))
}
