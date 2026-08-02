package dev.jdtech.jellyfin.presentation.film

import dev.jdtech.jellyfin.PlayerRoute
import dev.jdtech.jellyfin.film.presentation.hasMeaningfulSavedProgress
import dev.jdtech.jellyfin.film.presentation.movie.MovieAction
import dev.jdtech.jellyfin.film.presentation.season.SeasonState
import dev.jdtech.jellyfin.film.presentation.show.ShowAction
import dev.jdtech.jellyfin.film.presentation.show.ShowState
import java.util.UUID

internal fun hasMeaningfulPlaybackStart(state: ShowState): Boolean =
    hasMeaningfulSavedProgress(state.playbackStartEpisode?.playbackPositionTicks ?: 0L)

internal fun hasMeaningfulPlaybackStart(state: SeasonState): Boolean =
    hasMeaningfulSavedProgress(state.playbackStartEpisode?.playbackPositionTicks ?: 0L)

internal fun moviePlaybackRoute(movieId: UUID, action: MovieAction): PlayerRoute? =
    when (action) {
        is MovieAction.Play ->
            PlayerRoute.movie(
                itemId = movieId,
                startFromBeginning = action.startFromBeginning,
            )
        else -> null
    }

internal fun showPlaybackRoute(showId: UUID, action: ShowAction): PlayerRoute? =
    when (action) {
        is ShowAction.Play ->
            PlayerRoute.series(
                itemId = showId,
                startFromBeginning = action.startFromBeginning,
            )
        else -> null
    }
