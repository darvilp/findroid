package dev.jdtech.jellyfin.presentation.film.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.RadioButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.R
import dev.jdtech.jellyfin.models.InitialTrackSelection
import dev.jdtech.jellyfin.models.PlaybackTrackChoice
import dev.jdtech.jellyfin.presentation.film.PreplayTrackSelectionState
import dev.jdtech.jellyfin.presentation.theme.spacings

private enum class SelectorType {
    Audio,
    Subtitle,
}

@Composable
fun PreplayTrackControls(
    state: PreplayTrackSelectionState,
    referenceLabel: String? = null,
    onSelectAudio: (Int?) -> Unit,
    onSelectSubtitle: (Int?) -> Unit,
    onRetry: () -> Unit,
) {
    var selectorType by remember { mutableStateOf<SelectorType?>(null) }
    val choices = state.choices

    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.extraSmall)) {
        referenceLabel?.let {
            Text(
                text = stringResource(R.string.preplay_tracks_for, it),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
        ) {
            Button(
                onClick = { selectorType = SelectorType.Audio },
                enabled = choices?.audio?.isNotEmpty() == true,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.preplay_audio_value,
                            selectedLabel(
                                choices = choices?.audio.orEmpty(),
                                selectedIndex = state.selectedAudioStreamIndex,
                                defaultIndex = choices?.defaultAudioStreamIndex,
                                kind = PreplayTrackKind.Audio,
                            ),
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = { selectorType = SelectorType.Subtitle },
                enabled = choices != null,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text =
                        stringResource(
                            R.string.preplay_subtitle_value,
                            if (
                                state.selectedSubtitleStreamIndex ==
                                    InitialTrackSelection.SUBTITLE_OFF
                            ) {
                                stringResource(R.string.preplay_off)
                            } else {
                                selectedLabel(
                                    choices = choices?.subtitles.orEmpty(),
                                    selectedIndex = state.selectedSubtitleStreamIndex,
                                    defaultIndex = choices?.defaultSubtitleStreamIndex,
                                    kind = PreplayTrackKind.Subtitle,
                                )
                            },
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.loading) {
                Text(
                    text = stringResource(R.string.preplay_loading),
                    modifier = Modifier.align(Alignment.CenterVertically),
                    style = MaterialTheme.typography.labelMedium,
                )
            } else if (state.error != null) {
                Button(onClick = onRetry) {
                    Text(stringResource(dev.jdtech.jellyfin.core.R.string.retry))
                }
            }
        }
    }

    when (selectorType) {
        SelectorType.Audio ->
            PreplayTrackDialog(
                title = stringResource(dev.jdtech.jellyfin.core.R.string.audio),
                choices = choices?.audio.orEmpty(),
                selectedIndex = state.selectedAudioStreamIndex,
                includeOff = false,
                kind = PreplayTrackKind.Audio,
                onSelect = {
                    onSelectAudio(it)
                    selectorType = null
                },
                onDismiss = { selectorType = null },
            )
        SelectorType.Subtitle ->
            PreplayTrackDialog(
                title = stringResource(dev.jdtech.jellyfin.core.R.string.subtitle),
                choices = choices?.subtitles.orEmpty(),
                selectedIndex = state.selectedSubtitleStreamIndex,
                includeOff = true,
                kind = PreplayTrackKind.Subtitle,
                onSelect = {
                    onSelectSubtitle(it)
                    selectorType = null
                },
                onDismiss = { selectorType = null },
            )
        null -> Unit
    }
}

@Composable
private fun PreplayTrackDialog(
    title: String,
    choices: List<PlaybackTrackChoice>,
    selectedIndex: Int?,
    includeOff: Boolean,
    kind: PreplayTrackKind,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedListIndex =
        when {
            selectedIndex == null -> 0
            selectedIndex == InitialTrackSelection.SUBTITLE_OFF && includeOff -> 1
            else -> choices.indexOfFirst { it.streamIndex == selectedIndex } + if (includeOff) 2 else 1
        }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedListIndex)
    val selectedFocusRequester = remember { FocusRequester() }

    LaunchedEffect(selectedListIndex) {
        listState.scrollToItem(selectedListIndex)
        selectedFocusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 420.dp, max = 760.dp).heightIn(max = 560.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(MaterialTheme.spacings.medium)) {
                Text(text = title, style = MaterialTheme.typography.headlineMedium)
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.small),
                    contentPadding = PaddingValues(vertical = MaterialTheme.spacings.small),
                ) {
                    item(key = "default") {
                        TrackChoiceRow(
                            label =
                                PreplayTrackLabel(
                                    compact = stringResource(R.string.preplay_use_default),
                                    primary = stringResource(R.string.preplay_use_default),
                                    secondary = null,
                                ),
                            selected = selectedIndex == null,
                            modifier = selectedModifier(selectedListIndex == 0, selectedFocusRequester),
                            onClick = { onSelect(null) },
                        )
                    }
                    if (includeOff) {
                        item(key = "off") {
                            TrackChoiceRow(
                                label =
                                    PreplayTrackLabel(
                                        compact = stringResource(R.string.preplay_off),
                                        primary = stringResource(R.string.preplay_off),
                                        secondary = null,
                                    ),
                                selected =
                                    selectedIndex == InitialTrackSelection.SUBTITLE_OFF,
                                modifier = selectedModifier(selectedListIndex == 1, selectedFocusRequester),
                                onClick = {
                                    onSelect(InitialTrackSelection.SUBTITLE_OFF)
                                },
                            )
                        }
                    }
                    items(choices, key = PlaybackTrackChoice::streamIndex) { choice ->
                        val offset = if (includeOff) 2 else 1
                        val listIndex = choices.indexOf(choice) + offset
                        TrackChoiceRow(
                            label = choice.preplayLabel(kind),
                            selected = selectedIndex == choice.streamIndex,
                            modifier =
                                selectedModifier(
                                    listIndex == selectedListIndex,
                                    selectedFocusRequester,
                                ),
                            onClick = { onSelect(choice.streamIndex) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackChoiceRow(
    label: PreplayTrackLabel,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(4.dp)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
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
            RadioButton(selected = selected, onClick = null)
            Spacer(modifier = Modifier.width(MaterialTheme.spacings.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label.primary,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                label.secondary?.let { secondary ->
                    Text(
                        text = secondary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = .7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun selectedModifier(selected: Boolean, focusRequester: FocusRequester): Modifier =
    if (selected) Modifier.focusRequester(focusRequester) else Modifier

@Composable
private fun selectedLabel(
    choices: List<PlaybackTrackChoice>,
    selectedIndex: Int?,
    defaultIndex: Int?,
    kind: PreplayTrackKind,
): String {
    if (selectedIndex != null) {
        return choices.firstOrNull { it.streamIndex == selectedIndex }?.preplayLabel(kind)?.compact
            ?: stringResource(dev.jdtech.jellyfin.core.R.string.unknown_error)
    }
    val default = choices.firstOrNull { it.streamIndex == defaultIndex }
    return if (default == null) {
        stringResource(R.string.preplay_use_default)
    } else {
        "${stringResource(R.string.preplay_use_default)} — ${default.preplayLabel(kind).compact}"
    }
}
