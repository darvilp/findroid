package dev.jdtech.jellyfin.presentation.film

import android.content.Context
import android.content.Intent
import dev.jdtech.jellyfin.PlayerActivity
import dev.jdtech.jellyfin.film.presentation.PlaybackStart
import dev.jdtech.jellyfin.film.presentation.shouldStartFromBeginning
import dev.jdtech.jellyfin.film.presentation.season.SeasonAction
import dev.jdtech.jellyfin.film.presentation.season.SeasonState
import dev.jdtech.jellyfin.film.presentation.show.ShowAction
import dev.jdtech.jellyfin.film.presentation.show.ShowState
import org.jellyfin.sdk.model.api.BaseItemKind

internal data class PlaybackActivityRequest(
    val itemId: String,
    val itemKind: String,
    val startFromBeginning: Boolean,
) {
    fun toIntent(context: Context): Intent =
        Intent(context, PlayerActivity::class.java).apply {
            putExtra("itemId", itemId)
            putExtra("itemKind", itemKind)
            putExtra("startFromBeginning", startFromBeginning)
        }
}

internal fun showPlaybackActivityRequest(
    state: ShowState,
    action: ShowAction.Play,
): PlaybackActivityRequest? {
    return playbackActivityRequest(state.playbackStart, action.startFromBeginning)
}

internal fun seasonPlaybackActivityRequest(
    state: SeasonState,
    action: SeasonAction.Play,
): PlaybackActivityRequest? {
    return playbackActivityRequest(state.playbackStart, action.startFromBeginning)
}

private fun playbackActivityRequest(
    playbackStart: PlaybackStart?,
    startFromBeginning: Boolean,
): PlaybackActivityRequest? =
    playbackStart?.let { start ->
        PlaybackActivityRequest(
            itemId = start.episode.id.toString(),
            itemKind = BaseItemKind.EPISODE.serialName,
            startFromBeginning = start.shouldStartFromBeginning(startFromBeginning),
        )
    }
