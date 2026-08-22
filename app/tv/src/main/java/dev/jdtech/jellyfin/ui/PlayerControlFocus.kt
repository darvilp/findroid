package dev.jdtech.jellyfin.ui

import dev.jdtech.jellyfin.ui.components.player.VideoPlayerOverlayMode

internal enum class PlayerControl {
    Details,
    Restart,
    PreviousChapter,
    NextChapter,
    Audio,
    Subtitles,
    HardwareDecoder,
    PreviousEpisode,
    PlayPause,
    NextEpisode,
    SeekBar,
}

internal sealed interface PlayerFocusRequest {
    data object Root : PlayerFocusRequest

    data object SkipPrompt : PlayerFocusRequest

    data class Control(val control: PlayerControl) : PlayerFocusRequest
}

internal fun playerFocusRequest(
    overlayMode: VideoPlayerOverlayMode,
    modalActive: Boolean,
    skipPromptMayTakeFocus: Boolean,
    focusedControl: PlayerControl?,
    availableControls: Set<PlayerControl>,
): PlayerFocusRequest? =
    when {
        modalActive -> null
        skipPromptMayTakeFocus -> PlayerFocusRequest.SkipPrompt
        overlayMode != VideoPlayerOverlayMode.Controls -> PlayerFocusRequest.Root
        focusedControl != null && focusedControl !in availableControls ->
            PlayerFocusRequest.Control(PlayerControl.PlayPause)
        else -> null
    }

internal fun restoredPlayerControl(
    lastFocusedControl: PlayerControl?,
    availableControls: Set<PlayerControl>,
): PlayerControl =
    lastFocusedControl?.takeIf { it in availableControls } ?: PlayerControl.PlayPause

internal fun focusAfterSkipPromptRemoval(
    skipPromptWasFocused: Boolean,
    skipPromptAvailable: Boolean,
    overlayMode: VideoPlayerOverlayMode,
    lastFocusedBottomControl: PlayerControl?,
    availableControls: Set<PlayerControl>,
): PlayerControl? =
    if (
        skipPromptWasFocused &&
            !skipPromptAvailable &&
            overlayMode == VideoPlayerOverlayMode.Controls
    ) {
        restoredPlayerControl(lastFocusedBottomControl, availableControls)
    } else {
        null
    }

internal fun previousPlayerControl(
    control: PlayerControl,
    orderedControls: List<PlayerControl>,
): PlayerControl? {
    val index = orderedControls.indexOf(control)
    return if (index > 0) orderedControls[index - 1] else null
}

internal fun nextPlayerControl(
    control: PlayerControl,
    orderedControls: List<PlayerControl>,
): PlayerControl? {
    val index = orderedControls.indexOf(control)
    return if (index >= 0) orderedControls.getOrNull(index + 1) else null
}
