package dev.jdtech.jellyfin.models

import kotlinx.serialization.Serializable

@Serializable
data class InitialTrackSelection(
    val mediaSourceId: String,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
) {
    init {
        require(mediaSourceId.isNotBlank()) { "mediaSourceId must not be blank" }
        require(audioStreamIndex == null || audioStreamIndex >= 0) {
            "audioStreamIndex must be null or non-negative"
        }
        require(subtitleStreamIndex == null || subtitleStreamIndex >= SUBTITLE_OFF) {
            "subtitleStreamIndex must be null, -1, or non-negative"
        }
    }

    companion object {
        const val SUBTITLE_OFF = -1
    }
}
