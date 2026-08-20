package dev.jdtech.jellyfin.models

data class PlaybackTrackReport(
    val mediaSourceId: String,
    val audioStreamIndex: Int?,
    val subtitleStreamIndex: Int?,
)
