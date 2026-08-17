package dev.jdtech.jellyfin.player.local.domain

import androidx.media3.common.C
import dev.jdtech.jellyfin.models.FindroidMediaStream
import dev.jdtech.jellyfin.player.core.domain.models.Track
import java.util.Locale
import org.jellyfin.sdk.model.api.MediaStreamType

object RuntimeTrackResolver {
    fun resolve(
        jellyfinStreamIndex: Int,
        jellyfinStreams: List<FindroidMediaStream>,
        runtimeTracks: List<Track>,
    ): Track? {
        val stream = jellyfinStreams.singleOrNull { it.index == jellyfinStreamIndex } ?: return null
        val supported = runtimeTracks.filter { it.supported && it.type == stream.runtimeTrackType() }
        if (stream.isExternal) {
            return supported.singleOrNull { it.id == jellyfinStreamIndex.toString() }
        }

        val fingerprint = QueueTrackFingerprint.fromStream(stream, jellyfinStreams) ?: return null
        return supported
            .mapIndexed { ordinal, track ->
                RuntimeCandidate(
                    track = track,
                    score = track.scoreAgainst(fingerprint, ordinal),
                    exactTitle =
                        track.label.normalizedRuntimeMetadata() == fingerprint.displayTitle,
                    sameOrdinal = ordinal == fingerprint.ordinalWithinType,
                )
            }
            .filter { it.score >= 3 }
            .sortedWith(
                compareByDescending<RuntimeCandidate> { it.score }
                    .thenByDescending { it.exactTitle }
                    .thenByDescending { it.sameOrdinal }
                    .thenBy { it.track.groupIndex }
                    .thenBy { it.track.trackIndex }
            )
            .firstOrNull()
            ?.track
    }

    fun resolveSourceStream(
        runtimeTrack: Track,
        jellyfinStreams: List<FindroidMediaStream>,
        runtimeTracks: List<Track>,
    ): FindroidMediaStream? =
        jellyfinStreams
            .filter { it.index != null && it.runtimeTrackType() == runtimeTrack.type }
            .firstOrNull { stream ->
                resolve(requireNotNull(stream.index), jellyfinStreams, runtimeTracks)?.sameIdentity(
                    runtimeTrack
                ) == true
            }

    private fun Track.scoreAgainst(fingerprint: QueueTrackFingerprint, ordinal: Int): Int {
        var score = 0
        val language = rawLanguage.normalizedLanguage()
        if (language != null && language != "und" && language == fingerprint.language.normalizedLanguage()) {
            score += 2
        }
        if (label.normalizedRuntimeMetadata() == fingerprint.displayTitle) score += 2
        if (codec.normalizedCodec() == fingerprint.codec.normalizedCodec()) score += 1
        if (ordinal == fingerprint.ordinalWithinType) score += 1
        return score
    }
}

private data class RuntimeCandidate(
    val track: Track,
    val score: Int,
    val exactTitle: Boolean,
    val sameOrdinal: Boolean,
)

private fun FindroidMediaStream.runtimeTrackType(): Int =
    when (type) {
        MediaStreamType.AUDIO -> C.TRACK_TYPE_AUDIO
        MediaStreamType.SUBTITLE -> C.TRACK_TYPE_TEXT
        else -> C.TRACK_TYPE_UNKNOWN
    }

private fun Track.sameIdentity(other: Track): Boolean =
    type == other.type && groupIndex == other.groupIndex && trackIndex == other.trackIndex

private fun String?.normalizedRuntimeMetadata(): String? =
    this?.trim()?.replace(Regex("\\s+"), " ")?.lowercase()?.takeIf(String::isNotEmpty)

private fun String?.normalizedCodec(): String? =
    normalizedRuntimeMetadata()?.substringAfterLast('/')?.let { codec ->
        when (codec) {
            "x-ssa", "ssa" -> "ass"
            else -> codec
        }
    }

private fun String?.normalizedLanguage(): String? {
    val value = normalizedRuntimeMetadata() ?: return null
    if (value == "und") return value
    return try {
        Locale.forLanguageTag(value).isO3Language.lowercase()
    } catch (_: Exception) {
        value
    }
}
