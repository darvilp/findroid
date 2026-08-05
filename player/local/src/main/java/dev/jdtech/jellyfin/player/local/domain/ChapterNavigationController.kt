package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter

enum class ChapterNavigationDirection {
    Previous,
    Next,
}

data class ChapterNavigationState(
    val chapters: List<PlayerChapter>,
    val previousChapter: PlayerChapter?,
    val nextChapter: PlayerChapter?,
) {
    val hasMeaningfulChapters: Boolean
        get() = chapters.size >= 2

    fun target(direction: ChapterNavigationDirection): PlayerChapter? =
        when (direction) {
            ChapterNavigationDirection.Previous -> previousChapter
            ChapterNavigationDirection.Next -> nextChapter
        }
}

internal fun interface ChapterSeekTarget {
    fun seekTo(positionMs: Long)
}

internal class ChapterNavigationController {
    private val seekPositionTracker = ChapterSeekPositionTracker()

    fun state(
        chapters: List<PlayerChapter>,
        currentPositionMs: Long,
        durationMs: Long?,
    ): ChapterNavigationState {
        val normalizedChapters = normalize(chapters, durationMs)
        if (normalizedChapters.size < 2) {
            return ChapterNavigationState(
                chapters = normalizedChapters,
                previousChapter = null,
                nextChapter = null,
            )
        }

        val reportedPositionMs = currentPositionMs.coerceAtLeast(0L)
        val positionMs =
            seekPositionTracker.positionForNavigation(
                reportedPositionMs = reportedPositionMs,
                validChapterPositions = normalizedChapters.mapTo(mutableSetOf()) { chapter ->
                    chapter.startPosition
                },
            )
        val currentChapterIndex =
            normalizedChapters.indexOfLast { chapter -> chapter.startPosition <= positionMs }
        val previousChapter =
            if (currentChapterIndex < 0) {
                null
            } else {
                val currentChapter = normalizedChapters[currentChapterIndex]
                if (positionMs - currentChapter.startPosition > RESTART_CURRENT_CHAPTER_AFTER_MS) {
                    currentChapter
                } else {
                    normalizedChapters.getOrNull(currentChapterIndex - 1)
                }
            }
        val nextChapter =
            normalizedChapters.firstOrNull { chapter -> chapter.startPosition > positionMs }

        return ChapterNavigationState(
            chapters = normalizedChapters,
            previousChapter = previousChapter,
            nextChapter = nextChapter,
        )
    }

    fun seek(
        direction: ChapterNavigationDirection,
        navigation: ChapterNavigationState,
        target: ChapterSeekTarget,
    ): PlayerChapter? =
        navigation.target(direction)?.also { chapter ->
            seekPositionTracker.onChapterSeekRequested(chapter.startPosition)
            target.seekTo(chapter.startPosition)
        }

    fun onPositionDiscontinuity(isSeek: Boolean) {
        seekPositionTracker.onPositionDiscontinuity(isSeek)
    }

    fun reset() {
        seekPositionTracker.reset()
    }

    private fun normalize(
        chapters: List<PlayerChapter>,
        durationMs: Long?,
    ): List<PlayerChapter> {
        val duration = durationMs?.takeIf { it > 0L } ?: return emptyList()
        return chapters
            .asSequence()
            .filter { chapter -> chapter.startPosition in 0 until duration }
            .sortedBy(PlayerChapter::startPosition)
            .distinctBy(PlayerChapter::startPosition)
            .toList()
    }

    private companion object {
        const val RESTART_CURRENT_CHAPTER_AFTER_MS = 5_000L
    }
}

private class ChapterSeekPositionTracker {
    private val pendingPositionsMs = ArrayDeque<Long>()
    private var settlingPositionMs: Long? = null
    private var lastLandedPositionMs: Long? = null

    fun onChapterSeekRequested(positionMs: Long) {
        pendingPositionsMs.addLast(positionMs)
    }

    fun onPositionDiscontinuity(isSeek: Boolean) {
        if (!isSeek) {
            reset()
            return
        }

        val acknowledgedPositionMs = pendingPositionsMs.removeFirstOrNull()
        if (acknowledgedPositionMs == null) {
            reset()
        } else {
            settlingPositionMs = acknowledgedPositionMs
        }
    }

    fun positionForNavigation(
        reportedPositionMs: Long,
        validChapterPositions: Set<Long>,
    ): Long {
        val pendingPositionMs = pendingPositionsMs.lastOrNull()
        if (pendingPositionMs != null) {
            if (pendingPositionMs !in validChapterPositions) {
                reset()
                return reportedPositionMs
            }
            return pendingPositionMs
        }

        val settlingPositionMs = settlingPositionMs
        if (settlingPositionMs != null) {
            if (settlingPositionMs !in validChapterPositions) {
                reset()
                return reportedPositionMs
            }
            if (!positionsAreNear(reportedPositionMs, settlingPositionMs)) {
                return settlingPositionMs
            }

            this.settlingPositionMs = null
            lastLandedPositionMs = settlingPositionMs
        }

        val landedPositionMs = lastLandedPositionMs
        return if (
            landedPositionMs != null &&
                landedPositionMs in validChapterPositions &&
                landedPositionMs > reportedPositionMs &&
                landedPositionMs - reportedPositionMs <= CHAPTER_SEEK_LANDING_TOLERANCE_MS
        ) {
            landedPositionMs
        } else {
            reportedPositionMs
        }
    }

    fun reset() {
        pendingPositionsMs.clear()
        settlingPositionMs = null
        lastLandedPositionMs = null
    }

    private fun positionsAreNear(firstPositionMs: Long, secondPositionMs: Long): Boolean =
        if (firstPositionMs >= secondPositionMs) {
            firstPositionMs - secondPositionMs <= CHAPTER_SEEK_LANDING_TOLERANCE_MS
        } else {
            secondPositionMs - firstPositionMs <= CHAPTER_SEEK_LANDING_TOLERANCE_MS
        }

    private companion object {
        // mpv observes time-pos as whole seconds, so a successful seek can be reported up to one
        // second on either side of the requested millisecond position.
        const val CHAPTER_SEEK_LANDING_TOLERANCE_MS = 1_000L
    }
}
