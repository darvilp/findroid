package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.models.FindroidSegment

fun List<FindroidSegment>.segmentAt(
    positionMs: Long,
    endPaddingMs: Long = 100L,
): FindroidSegment? =
    firstOrNull { segment ->
        val eligibleEnd =
            (segment.endTicks - endPaddingMs.coerceAtLeast(0L)).coerceAtLeast(segment.startTicks)
        positionMs >= segment.startTicks && positionMs < eligibleEnd
    }
