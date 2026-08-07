package dev.jdtech.jellyfin.presentation.film

import dev.jdtech.jellyfin.PlayerRoute
import dev.jdtech.jellyfin.film.presentation.PlaybackStart
import dev.jdtech.jellyfin.film.presentation.hasMeaningfulSavedProgress
import dev.jdtech.jellyfin.film.presentation.shouldStartFromBeginning
import dev.jdtech.jellyfin.film.presentation.movie.MovieAction
import dev.jdtech.jellyfin.film.presentation.season.SeasonState
import dev.jdtech.jellyfin.film.presentation.show.ShowAction
import dev.jdtech.jellyfin.film.presentation.show.ShowState
import java.util.UUID

internal fun hasMeaningfulPlaybackStart(state: ShowState): Boolean =
    hasMeaningfulPlaybackStart(state.playbackStart)

internal fun hasMeaningfulPlaybackStart(state: SeasonState): Boolean =
    hasMeaningfulPlaybackStart(state.playbackStart)

private fun hasMeaningfulPlaybackStart(playbackStart: PlaybackStart?): Boolean =
    playbackStart?.let { start ->
        !start.startFromBeginning && hasMeaningfulSavedProgress(start.episode.playbackPositionTicks)
    } == true

internal fun moviePlaybackRoute(movieId: UUID, action: MovieAction): PlayerRoute? =
    when (action) {
        is MovieAction.Play ->
            PlayerRoute.movie(
                itemId = movieId,
                startFromBeginning = action.startFromBeginning,
            )
        else -> null
    }

internal fun showPlaybackRoute(state: ShowState, action: ShowAction): PlayerRoute? =
    when (action) {
        is ShowAction.Play ->
            containerPlaybackRoute(state.playbackStart, action.startFromBeginning)
        else -> null
    }

internal fun containerPlaybackRoute(
    playbackStart: PlaybackStart?,
    startFromBeginning: Boolean,
): PlayerRoute? =
    playbackStart?.let { start ->
        PlayerRoute.episode(
            itemId = start.episode.id,
            startFromBeginning = start.shouldStartFromBeginning(startFromBeginning),
        )
    }
