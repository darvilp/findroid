package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.film.presentation.home.HomeAction
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.HomeSection
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.ui.components.Direction
import dev.jdtech.jellyfin.ui.components.ItemCard

@Composable
internal fun HomeSection(
    section: HomeSection,
    itemsPadding: PaddingValues,
    onAction: (HomeAction) -> Unit,
    row: HomeRow,
    modifier: Modifier = Modifier,
    fallbackFocusRequester: FocusRequester? = null,
) {
    Column(modifier = modifier) {
        Text(
            text = section.name.asString(),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(itemsPadding),
        )
        Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
            contentPadding = itemsPadding,
            modifier = Modifier.focusRestorer(),
        ) {
            itemsIndexed(section.items, key = { _, item -> item.id }) { index, item ->
                HomeSectionItem(
                    item = item,
                    onAction = onAction,
                    row = row,
                    fallbackFocusRequester = fallbackFocusRequester.takeIf { index == 0 },
                )
            }
        }
    }
}

@Composable
private fun HomeSectionItem(
    item: FindroidItem,
    onAction: (HomeAction) -> Unit,
    row: HomeRow,
    fallbackFocusRequester: FocusRequester?,
) {
    val itemFocusRequester = remember { FocusRequester() }
    val focusRequester = fallbackFocusRequester ?: itemFocusRequester
    val playedAction = row.playedActionFor(item)
    var showActions by remember { mutableStateOf(false) }
    var restoreFocusOnDismiss by remember { mutableStateOf(false) }

    LaunchedEffect(showActions) {
        if (!showActions && restoreFocusOnDismiss) {
            focusRequester.requestFocus()
            restoreFocusOnDismiss = false
        }
    }

    ItemCard(
        item = item,
        direction = Direction.HORIZONTAL,
        onClick = { onAction(HomeAction.OnItemClick(it)) },
        onLongClick =
            if (playedAction != null) {
                {
                    restoreFocusOnDismiss = true
                    showActions = true
                }
            } else {
                null
            },
        surfaceModifier = Modifier.focusRequester(focusRequester),
    )

    if (showActions && playedAction != null) {
        HomeItemActionsDialog(
            item = item,
            onMarkAsPlayed = {
                showActions = false
                onAction(playedAction)
            },
            onDismissRequest = { showActions = false },
        )
    }
}

internal enum class HomeRow {
    Suggestions,
    Resume,
    NextUp,
    Latest,
    ;

    fun playedActionFor(item: FindroidItem): HomeAction.MarkAsPlayed? =
        when (this) {
            Resume,
            NextUp -> HomeAction.MarkAsPlayed(item.id)
            Suggestions,
            Latest -> null
        }
}
