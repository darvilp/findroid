package dev.jdtech.jellyfin.models

import java.util.UUID
import org.jellyfin.sdk.model.api.MediaStreamType

data class PlaybackTrackChoices(
    val itemId: UUID,
    val mediaSourceId: String,
    val defaultAudioStreamIndex: Int?,
    val defaultSubtitleStreamIndex: Int?,
    val audio: List<PlaybackTrackChoice>,
    val subtitles: List<PlaybackTrackChoice>,
)

data class PlaybackTrackChoice(
    val streamIndex: Int,
    val displayTitle: String,
    val language: String?,
    val codec: String?,
    val channelLayout: String?,
    val isExternal: Boolean,
    val isDefault: Boolean,
    val isForced: Boolean,
    val isHearingImpaired: Boolean,
    val ordinalWithinType: Int,
    val title: String? = null,
)

fun List<FindroidSource>.playbackTrackChoices(itemId: UUID): PlaybackTrackChoices? {
    val source = firstOrNull { it.type == FindroidSourceType.REMOTE } ?: return null
    return PlaybackTrackChoices(
        itemId = itemId,
        mediaSourceId = source.id,
        defaultAudioStreamIndex = source.defaultAudioStreamIndex,
        defaultSubtitleStreamIndex = source.defaultSubtitleStreamIndex,
        audio = source.mediaStreams.choicesOfType(MediaStreamType.AUDIO),
        subtitles = source.mediaStreams.choicesOfType(MediaStreamType.SUBTITLE),
    )
}

private fun List<FindroidMediaStream>.choicesOfType(
    type: MediaStreamType
): List<PlaybackTrackChoice> =
    filter { it.type == type && it.index != null }.mapIndexed { ordinal, stream ->
        PlaybackTrackChoice(
            streamIndex = requireNotNull(stream.index),
            displayTitle = stream.displayTitle ?: stream.title,
            language = stream.language.takeIf(String::isNotBlank),
            codec = stream.codec.takeIf(String::isNotBlank),
            channelLayout = stream.channelLayout,
            isExternal = stream.isExternal,
            isDefault = stream.isDefault == true,
            isForced = stream.isForced == true,
            isHearingImpaired = stream.isHearingImpaired == true,
            ordinalWithinType = ordinal,
            title = stream.title.takeIf(String::isNotBlank),
        )
    }
