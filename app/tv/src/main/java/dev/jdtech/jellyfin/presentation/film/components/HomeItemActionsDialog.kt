package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem

@Composable
fun HomeItemActionsDialog(
    item: FindroidItem,
    onMarkAsPlayed: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    PlayedActionDialog(
        title =
            when (item) {
                is FindroidEpisode ->
                    stringResource(
                        CoreR.string.episode_name,
                        item.indexNumber,
                        item.name,
                    )
                else -> item.name
            },
        actionLabel = stringResource(CoreR.string.mark_as_played),
        onAction = onMarkAsPlayed,
        onDismissRequest = onDismissRequest,
    )
}
