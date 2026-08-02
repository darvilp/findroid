package dev.jdtech.jellyfin.film.presentation

import dev.jdtech.jellyfin.models.FindroidEpisode
import kotlinx.coroutines.CancellationException

private const val MEANINGFUL_SAVED_PROGRESS_TICKS = 600_000_000L

fun hasMeaningfulSavedProgress(playbackPositionTicks: Long): Boolean =
    playbackPositionTicks.div(MEANINGFUL_SAVED_PROGRESS_TICKS) > 0L

internal fun firstPlayableEpisode(episodes: List<FindroidEpisode>): FindroidEpisode? =
    episodes.firstOrNull { !it.missing }

internal fun seriesPlaybackStartEpisode(
    nextUp: FindroidEpisode?,
    firstSeasonEpisodes: List<FindroidEpisode>,
): FindroidEpisode? = nextUp ?: firstPlayableEpisode(firstSeasonEpisodes)

internal suspend fun resolveSeriesPlaybackStartEpisode(
    nextUp: FindroidEpisode?,
    loadFirstSeasonEpisodes: suspend () -> List<FindroidEpisode>,
    onFailure: (Exception) -> Unit = {},
): FindroidEpisode? {
    if (nextUp != null) return seriesPlaybackStartEpisode(nextUp, emptyList())

    return try {
        seriesPlaybackStartEpisode(nextUp = null, firstSeasonEpisodes = loadFirstSeasonEpisodes())
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        onFailure(exception)
        null
    }
}
