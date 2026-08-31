package dev.jdtech.jellyfin.ui.dialogs

import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.R
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.settings.domain.MpvSynchronizationKind

@Composable
fun SynchronizationDialog(
    synchronization: PlayerViewModel.PlaybackSynchronizationUiState,
    initialKind: MpvSynchronizationKind,
    onSet: (MpvSynchronizationKind, Long) -> Unit,
    onReset: (MpvSynchronizationKind) -> Unit,
    onUseCurrentAsDefault: (MpvSynchronizationKind) -> Unit,
    onDismiss: () -> Unit,
) {
    var editingKind by remember { mutableStateOf<MpvSynchronizationKind?>(null) }
    val dialogPolicy = remember(initialKind) { SynchronizationDialogPolicy(initialKind) }
    val audioRequester = remember { FocusRequester() }
    val subtitleRequester = remember { FocusRequester() }
    var returnKind by remember { mutableStateOf<MpvSynchronizationKind?>(null) }

    val initialFocusTarget =
        initialSynchronizationFocusTarget(initialKind, synchronization.canEditSubtitle)
    LaunchedEffect(initialFocusTarget) {
        (if (initialFocusTarget == MpvSynchronizationKind.AUDIO) audioRequester
            else subtitleRequester)
            .requestFocus()
    }
    LaunchedEffect(returnKind) {
        val kind = returnKind ?: return@LaunchedEffect
        (if (kind == MpvSynchronizationKind.AUDIO) audioRequester else subtitleRequester).requestFocus()
        returnKind = null
    }
    BackHandler(enabled = editingKind == null, onBack = onDismiss)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.widthIn(min = 520.dp, max = 760.dp), shape = MaterialTheme.shapes.medium) {
            Column(modifier = Modifier.padding(MaterialTheme.spacings.medium), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.synchronization), style = MaterialTheme.typography.headlineMedium)
                SyncRow(stringResource(R.string.synchronization_audio), localizedSynchronizationValueLabel(synchronization.audioEffectiveMs), true, Modifier.focusRequester(audioRequester)) { dialogPolicy.openEditor(MpvSynchronizationKind.AUDIO); editingKind = MpvSynchronizationKind.AUDIO }
                SyncRow(stringResource(R.string.synchronization_subtitles), localizedSynchronizationValueLabel(synchronization.subtitleEffectiveMs), synchronization.canEditSubtitle, Modifier.focusRequester(subtitleRequester)) { dialogPolicy.openEditor(MpvSynchronizationKind.SUBTITLE); editingKind = MpvSynchronizationKind.SUBTITLE }
                SyncRow(stringResource(R.string.synchronization_reset_audio), localizedSynchronizationValueLabel(synchronization.audioBaselineMs), true) { onReset(MpvSynchronizationKind.AUDIO) }
                SyncRow(stringResource(R.string.synchronization_reset_subtitles), localizedSynchronizationValueLabel(synchronization.subtitleBaselineMs), synchronization.canEditSubtitle) { onReset(MpvSynchronizationKind.SUBTITLE) }
                SyncRow(stringResource(R.string.synchronization_use_audio_default), null, true) { onUseCurrentAsDefault(MpvSynchronizationKind.AUDIO) }
                SyncRow(stringResource(R.string.synchronization_use_subtitles_default), null, synchronization.canEditSubtitle) { onUseCurrentAsDefault(MpvSynchronizationKind.SUBTITLE) }
            }
        }
    }

    editingKind?.let { kind ->
        val value = if (kind == MpvSynchronizationKind.AUDIO) synchronization.audioEffectiveMs else synchronization.subtitleEffectiveMs
        SynchronizationValueEditor(
            valueMs = value,
            onValue = { onSet(kind, it) },
            onDone = {
                dialogPolicy.closeEditor()
                editingKind = null
                returnKind = dialogPolicy.takeFocusReturn()
            },
        )
    }
}

@Composable
private fun localizedSynchronizationValueLabel(valueMs: Long): String =
    synchronizationValueLabel(
        valueMs = valueMs,
        synchronizedLabel = stringResource(R.string.synchronization_synchronized),
        earlierLabel = stringResource(R.string.synchronization_earlier),
        laterLabel = stringResource(R.string.synchronization_later),
    )

@Composable
private fun SyncRow(label: String, value: String?, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = MaterialTheme.shapes.small)),
        scale = ClickableSurfaceScale.None,
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label)
            if (value != null) Text(value)
        }
    }
}

@Composable
internal fun SynchronizationValueEditor(valueMs: Long, onValue: (Long) -> Unit, onDone: () -> Unit) {
    var state by remember { mutableStateOf(SynchronizationEditorState.from(valueMs)) }
    var editorUiState by remember {
        mutableStateOf(SynchronizationEditorUiState(exactEntry = false, valueMs = valueMs))
    }
    var okDownTime by remember { mutableStateOf<Long?>(null) }
    var backState by remember { mutableStateOf(SynchronizationEditorBackState()) }
    val keyGuard = remember { SynchronizationKeyGuard() }
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { requester.requestFocus() }
    LaunchedEffect(valueMs) {
        if (state.valueMs != valueMs) state = state.copy(valueMs = valueMs)
    }
    BackHandler {
        if (editorUiState.exactEntry) {
            editorUiState = editorUiState.dismissExactEntry()
        } else {
            onDone()
        }
    }

    Dialog(onDismissRequest = onDone, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(min = 620.dp).focusRequester(requester).focusable().onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                val owned = keyGuard.consume(native.keyCode, native.action == KeyEvent.ACTION_DOWN)
                val backTransition =
                    backState.onKey(
                        keyCode = native.keyCode,
                        isDown = native.action == KeyEvent.ACTION_DOWN,
                        repeatCount = native.repeatCount,
                    )
                backState = backTransition.state
                if (backTransition.action == SynchronizationEditorAction.DONE) {
                    onDone()
                }
                if (!editorUiState.exactEntry && native.action == KeyEvent.ACTION_DOWN) {
                    when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> state = state.move(-1)
                        KeyEvent.KEYCODE_DPAD_RIGHT -> state = state.move(1)
                        KeyEvent.KEYCODE_DPAD_UP ->
                            synchronizationValueAfterEdit(state, increase = true)?.let {
                                state = it
                                onValue(it.valueMs)
                            }
                        KeyEvent.KEYCODE_DPAD_DOWN ->
                            synchronizationValueAfterEdit(state, increase = false)?.let {
                                state = it
                                onValue(it.valueMs)
                            }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER ->
                            if (native.repeatCount == 0) okDownTime = native.eventTime
                    }
                }
                if (
                    !editorUiState.exactEntry &&
                        native.action == KeyEvent.ACTION_UP &&
                        native.keyCode in
                            setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER) &&
                        okDownTime != null
                ) {
                    val wasShortPress =
                        native.eventTime - checkNotNull(okDownTime) <
                            ViewConfiguration.getLongPressTimeout()
                    okDownTime = null
                    if (wasShortPress) editorUiState = editorUiState.copy(exactEntry = true)
                }
                owned
            },
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(localizedSynchronizationValueLabel(state.valueMs), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(20.dp))
                val magnitudeParts = synchronizationMagnitudeParts(state.valueMs)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    EditorField(if (state.valueMs < 0) stringResource(R.string.synchronization_earlier) else stringResource(R.string.synchronization_later), state.selectedField == SynchronizationField.DIRECTION)
                    EditorField(magnitudeParts.seconds, state.selectedField == SynchronizationField.SECONDS)
                    Text(".")
                    EditorField(magnitudeParts.hundreds, state.selectedField == SynchronizationField.HUNDRED_MILLISECONDS)
                    EditorField(magnitudeParts.tens, state.selectedField == SynchronizationField.TEN_MILLISECONDS)
                    EditorField(magnitudeParts.ones, state.selectedField == SynchronizationField.ONE_MILLISECOND)
                }
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.synchronization_done))
            }
        }
    }
    if (editorUiState.exactEntry) ExactEntryDialog(state.valueMs < 0, state.valueMs) { parsed ->
        if (parsed != null) {
            state = SynchronizationEditorState.from(parsed)
            editorUiState = SynchronizationEditorUiState(exactEntry = false, valueMs = parsed)
            onValue(parsed)
        } else {
            editorUiState = editorUiState.dismissExactEntry()
        }
    }
}

@Composable
private fun EditorField(text: String, selected: Boolean) {
    Surface(border = if (selected) Border(BorderStroke(3.dp, Color.White), shape = MaterialTheme.shapes.small) else Border.None, shape = MaterialTheme.shapes.small) {
        Text(text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

@Composable
private fun ExactEntryDialog(initialEarlier: Boolean, currentMs: Long, onResult: (Long?) -> Unit) {
    var earlier by remember { mutableStateOf(initialEarlier) }
    var text by remember { mutableStateOf(dev.jdtech.jellyfin.settings.domain.MpvSynchronizationValue.formatMpvMagnitudeSeconds(currentMs).trimEnd('0').trimEnd('.')) }
    var invalid by remember { mutableStateOf(false) }
    val keyGuard = remember { SynchronizationKeyGuard(openingKeyCode = null) }
    Dialog(onDismissRequest = { onResult(null) }) {
        Surface(
            modifier =
                Modifier.onPreviewKeyEvent { event ->
                    val native = event.nativeKeyEvent
                    val owned =
                        keyGuard.consume(
                            keyCode = native.keyCode,
                            isDown = native.action == KeyEvent.ACTION_DOWN,
                        )
                    if (
                        synchronizationEditorAction(
                            keyCode = native.keyCode,
                            isDown = native.action == KeyEvent.ACTION_DOWN,
                            repeatCount = native.repeatCount,
                        ) == SynchronizationEditorAction.DONE
                    ) {
                        onResult(null)
                    }
                    owned
                },
            shape = MaterialTheme.shapes.medium,
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.synchronization_exact_entry), style = MaterialTheme.typography.headlineMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { earlier = true }) { Text(stringResource(R.string.synchronization_earlier)) }
                    Button(onClick = { earlier = false }) { Text(stringResource(R.string.synchronization_later)) }
                }
                BasicTextField(value = text, onValueChange = { text = it; invalid = false }, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.headlineSmall.copy(color = Color.White))
                if (invalid) Text(stringResource(R.string.synchronization_invalid_value), color = Color.Red)
                Button(onClick = {
                    val value = exactSynchronizationValue(earlier, text)
                    if (value == null) invalid = true else onResult(value)
                }) { Text(stringResource(android.R.string.ok)) }
            }
        }
    }
}
