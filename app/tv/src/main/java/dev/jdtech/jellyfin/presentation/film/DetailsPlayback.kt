package dev.jdtech.jellyfin.presentation.film

import dev.jdtech.jellyfin.PlayerRoute
import dev.jdtech.jellyfin.film.presentation.PlaybackStart
import dev.jdtech.jellyfin.film.presentation.hasMeaningfulSavedProgress
import dev.jdtech.jellyfin.film.presentation.shouldStartFromBeginning
import dev.jdtech.jellyfin.film.presentation.movie.MovieAction
import dev.jdtech.jellyfin.film.presentation.season.SeasonState
import dev.jdtech.jellyfin.film.presentation.show.ShowAction
import dev.jdtech.jellyfin.film.presentation.show.ShowState
import dev.jdtech.jellyfin.models.InitialTrackSelection
import java.util.UUID

internal fun hasMeaningfulPlaybackStart(state: ShowState): Boolean =
    hasMeaningfulPlaybackStart(state.playbackStart)

internal fun hasMeaningfulPlaybackStart(state: SeasonState): Boolean =
    hasMeaningfulPlaybackStart(state.playbackStart)

private fun hasMeaningfulPlaybackStart(playbackStart: PlaybackStart?): Boolean =
    playbackStart?.let { start ->
        !start.startFromBeginning && hasMeaningfulSavedProgress(start.episode.playbackPositionTicks)
    } == true

internal fun moviePlaybackRoute(
    movieId: UUID,
    action: MovieAction,
    initialTrackSelection: InitialTrackSelection? = null,
): PlayerRoute? =
    when (action) {
        is MovieAction.Play ->
            PlayerRoute.movie(
                itemId = movieId,
                startFromBeginning = action.startFromBeginning,
                initialTrackSelection = initialTrackSelection,
            )
        else -> null
    }

internal fun showPlaybackRoute(
    state: ShowState,
    action: ShowAction,
    initialTrackSelection: InitialTrackSelection? = null,
): PlayerRoute? =
    when (action) {
        is ShowAction.Play ->
            containerPlaybackRoute(
                playbackStart = state.playbackStart,
                startFromBeginning = action.startFromBeginning,
                initialTrackSelection = initialTrackSelection,
            )
        else -> null
    }

internal fun containerPlaybackRoute(
    playbackStart: PlaybackStart?,
    startFromBeginning: Boolean,
    initialTrackSelection: InitialTrackSelection? = null,
): PlayerRoute? =
    playbackStart?.let { start ->
        PlayerRoute.episode(
            itemId = start.episode.id,
            startFromBeginning = start.shouldStartFromBeginning(startFromBeginning),
            initialTrackSelection = initialTrackSelection,
        )
    }
