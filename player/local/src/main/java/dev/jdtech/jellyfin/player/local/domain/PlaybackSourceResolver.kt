package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.models.FindroidSource
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.InitialTrackSelection
import org.jellyfin.sdk.model.api.MediaStreamType

data class ResolvedPlaybackSource(
    val source: FindroidSource,
    val initialTrackSelection: InitialTrackSelection?,
)

object PlaybackSourceResolver {
    fun resolve(
        sources: List<FindroidSource>,
        selection: InitialTrackSelection?,
    ): ResolvedPlaybackSource {
        require(sources.isNotEmpty()) { "No media sources available" }

        if (selection != null) {
            val selectedSource = sources.firstOrNull { it.id == selection.mediaSourceId }
            if (selectedSource != null && selection.isValidFor(selectedSource)) {
                return ResolvedPlaybackSource(selectedSource, selection)
            }
        }

        val defaultSource =
            sources.firstOrNull { it.type == FindroidSourceType.LOCAL } ?: sources.first()
        return ResolvedPlaybackSource(defaultSource, null)
    }

    private fun InitialTrackSelection.isValidFor(source: FindroidSource): Boolean {
        val validAudio =
            audioStreamIndex == null ||
                source.mediaStreams.any {
                    it.index == audioStreamIndex && it.type == MediaStreamType.AUDIO
                }
        val validSubtitle =
            subtitleStreamIndex == null ||
                subtitleStreamIndex == InitialTrackSelection.SUBTITLE_OFF ||
                source.mediaStreams.any { stream ->
                    stream.index == subtitleStreamIndex &&
                        stream.type == MediaStreamType.SUBTITLE &&
                        (!stream.isExternal || !stream.path.isNullOrBlank())
                }
        return validAudio && validSubtitle
    }
}
