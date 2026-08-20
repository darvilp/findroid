package dev.jdtech.jellyfin.player.local.domain

import androidx.media3.common.C
import dev.jdtech.jellyfin.models.FindroidMediaStream
import dev.jdtech.jellyfin.models.InitialTrackSelection
import dev.jdtech.jellyfin.player.core.domain.models.PlayerItem
import dev.jdtech.jellyfin.player.core.domain.models.Track

sealed interface InitialTrackOverride {
    data class Select(val track: Track) : InitialTrackOverride

    data class Disable(val trackType: Int) : InitialTrackOverride
}

object InitialTrackOverrideResolver {
    fun resolve(
        item: PlayerItem,
        jellyfinStreams: List<FindroidMediaStream>,
        runtimeTracks: List<Track>,
    ): List<InitialTrackOverride> = buildList {
        item.initialAudioStreamIndex?.let { streamIndex ->
            RuntimeTrackResolver.resolve(streamIndex, jellyfinStreams, runtimeTracks)?.let {
                add(InitialTrackOverride.Select(it))
            }
        }
        when (val streamIndex = item.initialSubtitleStreamIndex) {
            InitialTrackSelection.SUBTITLE_OFF ->
                add(InitialTrackOverride.Disable(C.TRACK_TYPE_TEXT))
            null -> Unit
            else ->
                RuntimeTrackResolver.resolve(streamIndex, jellyfinStreams, runtimeTracks)?.let {
                    add(InitialTrackOverride.Select(it))
                }
        }
    }
}
