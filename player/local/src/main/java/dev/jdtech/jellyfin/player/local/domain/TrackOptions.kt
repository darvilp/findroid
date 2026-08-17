package dev.jdtech.jellyfin.player.local.domain

import androidx.media3.common.Tracks
import dev.jdtech.jellyfin.player.core.domain.models.Track
import java.util.Locale

/** Enumerate every format in every Media3 track group while retaining its raw identity. */
fun Tracks.toTrackOptions(type: Int): List<Track> =
    groups.flatMapIndexed { groupIndex, group ->
        if (group.type != type) {
            emptyList()
        } else {
            (0 until group.mediaTrackGroup.length).map { trackIndex ->
                val format = group.mediaTrackGroup.getFormat(trackIndex)
                Track(
                    type = type,
                    groupIndex = groupIndex,
                    trackIndex = trackIndex,
                    label = format.label,
                    language = format.language?.toDisplayLanguage(),
                    codec = format.codecs ?: format.sampleMimeType,
                    selected = group.isTrackSelected(trackIndex),
                    supported = group.isTrackSupported(trackIndex),
                    id = format.id,
                    rawLanguage = format.language,
                    channelCount = format.channelCount.takeIf { it != androidx.media3.common.Format.NO_VALUE },
                )
            }
        }
    }

private fun String.toDisplayLanguage(): String =
    Locale.forLanguageTag(this).displayLanguage.ifBlank { this }
