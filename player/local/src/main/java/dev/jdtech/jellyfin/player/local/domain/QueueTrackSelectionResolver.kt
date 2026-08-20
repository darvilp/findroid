package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.models.FindroidMediaStream
import org.jellyfin.sdk.model.api.MediaStreamType

data class QueueTrackFingerprint(
    val type: MediaStreamType,
    val language: String?,
    val displayTitle: String?,
    val codec: String?,
    val channelLayout: String?,
    val isExternal: Boolean,
    val isForced: Boolean?,
    val isHearingImpaired: Boolean?,
    val ordinalWithinType: Int,
) {
    companion object {
        fun fromStream(
            selectedStreamIndex: Int,
            streams: List<FindroidMediaStream>,
        ): QueueTrackFingerprint? {
            val stream = streams.singleOrNull { it.index == selectedStreamIndex } ?: return null
            return fromStream(stream, streams)
        }

        fun fromStream(
            stream: FindroidMediaStream,
            streams: List<FindroidMediaStream>,
        ): QueueTrackFingerprint? {
            if (stream.index == null) return null
            val sameType = streams.filter { it.type == stream.type }
            val ordinal = sameType.indexOf(stream)
            if (ordinal < 0) return null
            return QueueTrackFingerprint(
                type = stream.type,
                language = stream.language.normalizedMetadata(),
                displayTitle = stream.preferredTitle().normalizedMetadata(),
                codec = stream.codec.normalizedMetadata(),
                channelLayout = stream.channelLayout.normalizedMetadata(),
                isExternal = stream.isExternal,
                isForced = stream.isForced,
                isHearingImpaired = stream.isHearingImpaired,
                ordinalWithinType = ordinal,
            )
        }
    }
}

object QueueTrackSelectionResolver {
    private const val MINIMUM_SCORE = 3

    fun resolve(
        fingerprint: QueueTrackFingerprint,
        candidates: List<FindroidMediaStream>,
    ): Int? =
        candidates
            .asSequence()
            .filter { it.index != null && it.type == fingerprint.type }
            .mapIndexedNotNull { _, candidate ->
                candidate.scoreAgainst(fingerprint, candidates)
            }
            .filter { it.score >= MINIMUM_SCORE }
            .sortedWith(
                compareByDescending<ScoredCandidate> { it.score }
                    .thenByDescending { it.exactTitle }
                    .thenByDescending { it.sameOrdinal }
                    .thenBy { it.streamIndex }
            )
            .firstOrNull()
            ?.streamIndex

    private fun FindroidMediaStream.scoreAgainst(
        fingerprint: QueueTrackFingerprint,
        candidates: List<FindroidMediaStream>,
    ): ScoredCandidate? {
        if (knownMismatch(isForced, fingerprint.isForced)) return null
        if (knownMismatch(isHearingImpaired, fingerprint.isHearingImpaired)) return null

        val title = preferredTitle().normalizedMetadata()
        if (
            type == MediaStreamType.AUDIO &&
                title.isCommentaryTitle() != fingerprint.displayTitle.isCommentaryTitle()
        ) {
            return null
        }

        val ordinal = candidates.filter { it.type == type }.indexOf(this)
        val exactTitle = title != null && title == fingerprint.displayTitle
        val sameOrdinal = ordinal == fingerprint.ordinalWithinType
        var score = 0
        val language = language.normalizedMetadata()
        if (language != null && language != "und" && language == fingerprint.language) score += 2
        if (exactTitle) score += 2
        if (codec.normalizedMetadata()?.equals(fingerprint.codec) == true) score += 1
        if (sameOrdinal) score += 1
        if (
            channelLayout.normalizedMetadata()?.equals(fingerprint.channelLayout) == true
        ) {
            score += 1
        }
        if (type == MediaStreamType.SUBTITLE && isExternal == fingerprint.isExternal) score += 1

        return ScoredCandidate(
            streamIndex = requireNotNull(index),
            score = score,
            exactTitle = exactTitle,
            sameOrdinal = sameOrdinal,
        )
    }
}

private data class ScoredCandidate(
    val streamIndex: Int,
    val score: Int,
    val exactTitle: Boolean,
    val sameOrdinal: Boolean,
)

private fun knownMismatch(candidate: Boolean?, previous: Boolean?): Boolean =
    candidate != null && previous != null && candidate != previous

private fun FindroidMediaStream.preferredTitle(): String? = displayTitle ?: title

private fun String?.normalizedMetadata(): String? =
    this?.trim()?.replace(Regex("\\s+"), " ")?.lowercase()?.takeIf { it.isNotEmpty() }

private fun String?.isCommentaryTitle(): Boolean = this?.contains("commentary") == true
