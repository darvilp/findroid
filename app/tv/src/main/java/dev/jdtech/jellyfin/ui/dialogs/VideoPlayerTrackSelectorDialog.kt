package dev.jdtech.jellyfin.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.player.core.domain.models.Track
import dev.jdtech.jellyfin.player.local.R as PlayerLocalR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun VideoPlayerTrackSelectorDialog(
    trackType: @C.TrackType Int,
    tracks: List<Track>,
    onSelect: (Track?) -> Unit,
    onSynchronization: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val dialogTitle =
        when (trackType) {
            C.TRACK_TYPE_AUDIO -> PlayerLocalR.string.select_audio_track
            C.TRACK_TYPE_TEXT -> PlayerLocalR.string.select_subtitle_track
            else -> CoreR.string.unknown_error
        }
    val selectedListIndex = (tracks.indexOfFirst { it.selected } + 1).coerceAtLeast(0)
    val synchronizationEntryCount =
        if (trackDialogShowsSynchronization(trackType, onSynchronization != null)) 1 else 0
    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = selectedListIndex + synchronizationEntryCount
        )
    val selectedFocusRequester = remember { FocusRequester() }

    LaunchedEffect(selectedListIndex) {
        listState.scrollToItem(selectedListIndex + synchronizationEntryCount)
        selectedFocusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 420.dp, max = 720.dp).heightIn(max = 560.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(MaterialTheme.spacings.medium)) {
                Text(
                    text = stringResource(id = dialogTitle),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                LazyColumn(
                    state = listState,
                    verticalArrangement =
                        Arrangement.spacedBy(
                            MaterialTheme.spacings.medium - MaterialTheme.spacings.extraSmall
                        ),
                    contentPadding =
                        PaddingValues(vertical = MaterialTheme.spacings.extraSmall),
                ) {
                    if (trackDialogShowsSynchronization(trackType, onSynchronization != null)) {
                        item(key = "synchronization") {
                            ActionOption(
                                text = stringResource(id = dev.jdtech.jellyfin.R.string.synchronization),
                                onClick = checkNotNull(onSynchronization),
                            )
                        }
                    }
                    item(key = "none") {
                        TrackOption(
                            text = stringResource(id = PlayerLocalR.string.none),
                            selected = tracks.none { it.selected },
                            supported = true,
                            modifier =
                                if (selectedListIndex == 0) {
                                    Modifier.focusRequester(selectedFocusRequester)
                                } else {
                                    Modifier
                                },
                            onClick = { onSelect(null) },
                        )
                    }
                    items(
                        items = tracks,
                        key = { track -> "${track.groupIndex}-${track.trackIndex}" },
                    ) { track ->
                        val listIndex = tracks.indexOf(track) + 1
                        TrackOption(
                            text =
                                listOfNotNull(track.label, track.language, track.codec)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" - ")
                                    .ifEmpty { stringResource(id = CoreR.string.unknown_error) },
                            selected = track.selected,
                            supported = track.supported,
                            modifier =
                                if (listIndex == selectedListIndex) {
                                    Modifier.focusRequester(selectedFocusRequester)
                                } else {
                                    Modifier
                                },
                            onClick = { onSelect(track) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionOption(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(4.dp)),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(4.dp, Color.White), shape = RoundedCornerShape(10.dp))),
        scale = ClickableSurfaceScale.None,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(MaterialTheme.spacings.medium))
    }
}

@Composable
private fun TrackOption(
    text: String,
    selected: Boolean,
    supported: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = supported,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(4.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
            ),
        border =
            ClickableSurfaceDefaults.border(
                focusedBorder =
                    Border(
                        BorderStroke(4.dp, Color.White),
                        shape = RoundedCornerShape(10.dp),
                    )
            ),
        scale = ClickableSurfaceScale.None,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(MaterialTheme.spacings.extraSmall),
        ) {
            RadioButton(selected = selected, onClick = null, enabled = supported)
            Spacer(modifier = Modifier.width(MaterialTheme.spacings.medium))
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Preview
@Composable
private fun VideoPlayerTrackSelectorDialogPreview() {
    FindroidTheme {
        VideoPlayerTrackSelectorDialog(
            trackType = C.TRACK_TYPE_AUDIO,
            tracks =
                listOf(
                    Track(
                        type = C.TRACK_TYPE_AUDIO,
                        groupIndex = 0,
                        trackIndex = 0,
                        label = null,
                        language = "English",
                        codec = "flac",
                        selected = true,
                        supported = true,
                    ),
                    Track(
                        type = C.TRACK_TYPE_AUDIO,
                        groupIndex = 1,
                        trackIndex = 0,
                        label = null,
                        language = "Japanese",
                        codec = "truehd",
                        selected = false,
                        supported = false,
                    ),
                ),
            onSelect = {},
            onDismiss = {},
        )
    }
}
