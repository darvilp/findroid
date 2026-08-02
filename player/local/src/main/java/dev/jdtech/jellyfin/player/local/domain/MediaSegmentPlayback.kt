package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidSegmentType
import java.util.UUID

enum class MediaSegmentAutoSkipMode {
    ALWAYS,
    PICTURE_IN_PICTURE,
}

data class MediaSegmentPlaybackPreferences(
    val autoSkipEnabled: Boolean,
    val autoSkipTypes: Set<FindroidSegmentType>,
    val autoSkipMode: MediaSegmentAutoSkipMode,
    val manualSkipEnabled: Boolean,
    val manualSkipTypes: Set<FindroidSegmentType>,
)

sealed interface MediaSegmentPlaybackDecision {
    data object None : MediaSegmentPlaybackDecision

    data class AutoSkip(val segment: FindroidSegment) : MediaSegmentPlaybackDecision

    data class ManualPrompt(val segment: FindroidSegment) : MediaSegmentPlaybackDecision
}

class MediaSegmentPlayback {
    private var itemId: UUID? = null
    private var segments: List<FindroidSegment> = emptyList()
    private val consumedSegments = mutableSetOf<FindroidSegment>()

    fun beginPlaybackPass() {
        consumedSegments.clear()
    }

    fun beginPlaybackPass(itemId: UUID) {
        if (this.itemId != itemId) {
            segments = emptyList()
        }
        this.itemId = itemId
        beginPlaybackPass()
    }

    fun updateSegments(itemId: UUID, segments: List<FindroidSegment>) {
        if (this.itemId == itemId) {
            this.segments = segments
        }
    }

    fun decisionAt(
        positionMs: Long,
        isInPictureInPictureMode: Boolean,
        preferences: MediaSegmentPlaybackPreferences,
    ): MediaSegmentPlaybackDecision {
        val segment = segments.segmentAt(positionMs) ?: return MediaSegmentPlaybackDecision.None
        val shouldAutoSkip =
            preferences.autoSkipEnabled &&
                segment.type in preferences.autoSkipTypes &&
                (preferences.autoSkipMode == MediaSegmentAutoSkipMode.ALWAYS ||
                    (preferences.autoSkipMode == MediaSegmentAutoSkipMode.PICTURE_IN_PICTURE &&
                        isInPictureInPictureMode))

        if (shouldAutoSkip && consumedSegments.add(segment)) {
            return MediaSegmentPlaybackDecision.AutoSkip(segment)
        }

        return if (
            preferences.manualSkipEnabled && segment.type in preferences.manualSkipTypes
        ) {
            MediaSegmentPlaybackDecision.ManualPrompt(segment)
        } else {
            MediaSegmentPlaybackDecision.None
        }
    }
}
