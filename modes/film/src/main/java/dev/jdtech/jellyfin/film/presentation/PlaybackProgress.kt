package dev.jdtech.jellyfin.film.presentation

import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.isAvailableForPlayback

private const val MEANINGFUL_SAVED_PROGRESS_TICKS = 600_000_000L

data class PlaybackStart(
    val episode: FindroidEpisode,
    val startFromBeginning: Boolean,
)

fun PlaybackStart.shouldStartFromBeginning(requested: Boolean): Boolean =
    requested || startFromBeginning

fun hasMeaningfulSavedProgress(playbackPositionTicks: Long): Boolean =
    playbackPositionTicks.div(MEANINGFUL_SAVED_PROGRESS_TICKS) > 0L

internal fun selectPlaybackStartEpisode(
    scopedResumeEpisodes: List<FindroidEpisode>,
    nextUp: FindroidEpisode?,
    canonicalEpisodes: List<FindroidEpisode>,
): PlaybackStart? =
    selectResumePlaybackStart(scopedResumeEpisodes)
        ?: selectResumePlaybackStart(canonicalEpisodes)
        ?: nextUp
            ?.takeIf(FindroidEpisode::isUnplayedAndAvailable)
            ?.let { episode -> PlaybackStart(episode = episode, startFromBeginning = false) }
        ?: canonicalEpisodes.firstPlaybackStart(isCandidate = FindroidEpisode::isUnplayedAndAvailable)
        ?: canonicalEpisodes.firstPlaybackStart(
            startFromBeginning = true,
            isCandidate = FindroidEpisode::isAvailableForPlayback,
        )

internal fun selectResumePlaybackStart(episodes: List<FindroidEpisode>): PlaybackStart? =
    episodes.firstPlaybackStart(isCandidate = FindroidEpisode::isResumable)

private fun FindroidEpisode.isUnplayedAndAvailable(): Boolean =
    isAvailableForPlayback() && !played

private fun FindroidEpisode.isResumable(): Boolean =
    isUnplayedAndAvailable() && playbackPositionTicks > 0L

private fun List<FindroidEpisode>.firstPlaybackStart(
    startFromBeginning: Boolean = false,
    isCandidate: (FindroidEpisode) -> Boolean,
): PlaybackStart? =
    firstOrNull(isCandidate)?.let { episode ->
        PlaybackStart(episode = episode, startFromBeginning = startFromBeginning)
    }
