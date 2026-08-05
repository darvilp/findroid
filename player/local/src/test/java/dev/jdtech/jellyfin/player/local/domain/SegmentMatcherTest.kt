package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidSegmentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SegmentMatcherTest {
    private val intro = FindroidSegment(FindroidSegmentType.INTRO, 1_000L, 5_000L)
    private val outro = FindroidSegment(FindroidSegmentType.OUTRO, 9_000L, 10_000L)
    private val segments = listOf(intro, outro)

    @Test
    fun `segment start is inclusive`() {
        assertEquals(intro, segments.segmentAt(1_000L))
    }

    @Test
    fun `final padding is excluded to prevent a stale prompt`() {
        assertEquals(intro, segments.segmentAt(4_899L))
        assertNull(segments.segmentAt(4_900L))
    }

    @Test
    fun `position outside all segments has no match`() {
        assertNull(segments.segmentAt(7_000L))
    }
}
