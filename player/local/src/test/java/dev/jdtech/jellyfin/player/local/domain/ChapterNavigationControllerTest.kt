package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterNavigationControllerTest {
    private val controller = ChapterNavigationController()

    @Test
    fun `normalization sorts distinct valid starts within the item duration`() {
        val navigation =
            controller.state(
                chapters =
                    listOf(
                        chapter(120_000L, "third"),
                        chapter(-1L, "invalid"),
                        chapter(60_000L, "second"),
                        chapter(60_000L, "duplicate"),
                        chapter(180_000L, "at end"),
                        chapter(0L, "first"),
                    ),
                currentPositionMs = 0L,
                durationMs = 180_000L,
            )

        assertEquals(listOf(0L, 60_000L, 120_000L), navigation.chapters.map { it.startPosition })
        assertEquals(listOf("first", "second", "third"), navigation.chapters.map { it.name })
        assertTrue(navigation.hasMeaningfulChapters)
    }

    @Test
    fun `a lone chapter marker does not enable navigation`() {
        val navigation =
            controller.state(
                chapters = listOf(chapter(0L)),
                currentPositionMs = 20_000L,
                durationMs = 180_000L,
            )

        assertFalse(navigation.hasMeaningfulChapters)
        assertNull(navigation.previousChapter)
        assertNull(navigation.nextChapter)
    }

    @Test
    fun `unknown duration does not expose unvalidated chapter starts`() {
        val navigation =
            controller.state(
                chapters = listOf(chapter(0L), chapter(60_000L)),
                currentPositionMs = 0L,
                durationMs = null,
            )

        assertEquals(emptyList<PlayerChapter>(), navigation.chapters)
        assertFalse(navigation.hasMeaningfulChapters)
    }

    @Test
    fun `previous restarts the current chapter after more than five seconds`() {
        val navigation = navigationAt(66_000L)

        assertEquals(60_000L, navigation.previousChapter?.startPosition)
    }

    @Test
    fun `previous moves back when exactly five seconds or less into the current chapter`() {
        assertEquals(0L, navigationAt(65_000L).previousChapter?.startPosition)
        assertEquals(0L, navigationAt(62_000L).previousChapter?.startPosition)
    }

    @Test
    fun `next selects only a strictly following chapter`() {
        assertEquals(120_000L, navigationAt(60_000L).nextChapter?.startPosition)
        assertEquals(120_000L, navigationAt(119_000L).nextChapter?.startPosition)
        assertNull(navigationAt(120_000L).nextChapter)
    }

    @Test
    fun `ordinary playback just before a marker still selects that following chapter`() {
        val navigation = navigationAt(59_900L)

        assertEquals(60_000L, navigation.nextChapter?.startPosition)
    }

    @Test
    fun `next does not stick when a requested chapter seek lands a few milliseconds early`() {
        seekChapter(ChapterNavigationDirection.Next, navigationAt(0L))
        controller.onPositionDiscontinuity(isSeek = true)
        val navigation = navigationAt(59_900L)

        assertEquals(120_000L, navigation.nextChapter?.startPosition)
    }

    @Test
    fun `rapid next survives seek acknowledgement before the reported position catches up`() {
        seekChapter(ChapterNavigationDirection.Next, navigationAt(0L))
        controller.onPositionDiscontinuity(isSeek = true)
        val stalePositionNavigation = navigationAt(0L)

        assertEquals(120_000L, stalePositionNavigation.nextChapter?.startPosition)
    }

    @Test
    fun `rapid previous survives seek acknowledgement before the reported position catches up`() {
        seekChapter(ChapterNavigationDirection.Previous, navigationAt(66_000L))
        controller.onPositionDiscontinuity(isSeek = true)
        val stalePositionNavigation = navigationAt(66_000L)

        assertEquals(0L, stalePositionNavigation.previousChapter?.startPosition)
    }

    @Test
    fun `external seek clears the last chapter seek correction`() {
        seekChapter(ChapterNavigationDirection.Next, navigationAt(0L))
        controller.onPositionDiscontinuity(isSeek = true)
        controller.onPositionDiscontinuity(isSeek = true)

        assertEquals(
            60_000L,
            navigationAt(59_900L).nextChapter?.startPosition,
        )
    }

    @Test
    fun `first and last boundaries expose safe unavailable directions`() {
        assertNull(navigationAt(0L).previousChapter)
        assertNull(navigationAt(180_000L).nextChapter)
    }

    @Test
    fun `chapter seeks cannot change the active playlist item`() {
        val target = RecordingChapterSeekTarget()
        val playlistIndexBefore = target.playlistIndex

        val soughtChapter =
            controller.seek(
                direction = ChapterNavigationDirection.Next,
                navigation = navigationAt(60_000L),
                target = target,
            )

        assertEquals(120_000L, soughtChapter?.startPosition)
        assertEquals(listOf(120_000L), target.seekPositions)
        assertEquals(playlistIndexBefore, target.playlistIndex)

        val boundaryResult =
            controller.seek(
                direction = ChapterNavigationDirection.Next,
                navigation = navigationAt(180_000L),
                target = target,
            )
        assertNull(boundaryResult)
        assertEquals(listOf(120_000L), target.seekPositions)
        assertEquals(playlistIndexBefore, target.playlistIndex)
    }

    private fun navigationAt(positionMs: Long): ChapterNavigationState =
        controller.state(
            chapters = listOf(chapter(0L), chapter(60_000L), chapter(120_000L)),
            currentPositionMs = positionMs,
            durationMs = 180_000L,
        )

    private fun seekChapter(
        direction: ChapterNavigationDirection,
        navigation: ChapterNavigationState,
    ) {
        controller.seek(direction, navigation, RecordingChapterSeekTarget())
    }

    private fun chapter(startPosition: Long, name: String? = null) =
        PlayerChapter(startPosition = startPosition, name = name)

    private class RecordingChapterSeekTarget : ChapterSeekTarget {
        val seekPositions = mutableListOf<Long>()
        val playlistIndex = 1

        override fun seekTo(positionMs: Long) {
            seekPositions += positionMs
        }
    }
}
