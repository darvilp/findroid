package dev.jdtech.jellyfin.presentation.film

import android.content.Context
import android.content.Intent
import dev.jdtech.jellyfin.PlayerActivity
import dev.jdtech.jellyfin.film.presentation.season.SeasonAction
import dev.jdtech.jellyfin.film.presentation.show.ShowAction
import java.util.UUID
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
    showId: UUID,
    action: ShowAction.Play,
): PlaybackActivityRequest =
    PlaybackActivityRequest(
        itemId = showId.toString(),
        itemKind = BaseItemKind.SERIES.serialName,
        startFromBeginning = action.startFromBeginning,
    )

internal fun seasonPlaybackActivityRequest(
    seasonId: UUID,
    action: SeasonAction.Play,
): PlaybackActivityRequest =
    PlaybackActivityRequest(
        itemId = seasonId.toString(),
        itemKind = BaseItemKind.SEASON.serialName,
        startFromBeginning = action.startFromBeginning,
    )
