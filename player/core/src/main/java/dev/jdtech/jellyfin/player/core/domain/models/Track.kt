package dev.jdtech.jellyfin.player.core.domain.models

data class Track(
    val type: Int,
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String?,
    val language: String?,
    val codec: String?,
    val selected: Boolean,
    val supported: Boolean,
)

/** Resolve an exact track identity from a possibly stale set of UI options. */
fun List<Track>.findTrack(
    type: Int,
    groupIndex: Int,
    trackIndex: Int,
): Track? =
    singleOrNull {
        it.type == type && it.groupIndex == groupIndex && it.trackIndex == trackIndex
    }

fun List<Track>.withSelectedTrack(groupIndex: Int?, trackIndex: Int?): List<Track> =
    map { track ->
        track.copy(
            selected = track.groupIndex == groupIndex && track.trackIndex == trackIndex
        )
    }
