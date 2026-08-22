package dev.jdtech.jellyfin.ui.components.player

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.Text

@Composable
fun VideoPlayerMediaButton(
    icon: Painter,
    state: VideoPlayerState,
    contentDescription: String,
    enabled: Boolean = true,
    onFocusChanged: (Boolean) -> Unit = {},
    onClick: () -> Unit = {},
) {
    VideoPlayerMediaButtonContent(
        state = state,
        contentDescription = contentDescription,
        enabled = enabled,
        onFocusChanged = onFocusChanged,
        onClick = onClick,
    ) {
        Icon(painter = icon, contentDescription = null)
    }
}

@Composable
fun VideoPlayerMediaTextButton(
    label: String,
    state: VideoPlayerState,
    contentDescription: String,
    enabled: Boolean = true,
    onFocusChanged: (Boolean) -> Unit = {},
    onClick: () -> Unit = {},
) {
    VideoPlayerMediaButtonContent(
        state = state,
        contentDescription = contentDescription,
        enabled = enabled,
        onFocusChanged = onFocusChanged,
        onClick = onClick,
    ) {
        Text(text = label)
    }
}

@Composable
private fun VideoPlayerMediaButtonContent(
    state: VideoPlayerState,
    contentDescription: String,
    enabled: Boolean,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        onFocusChanged(isFocused)
        if (isFocused) state.showControls()
    }

    IconButton(
        modifier = Modifier.semantics { this.contentDescription = contentDescription },
        enabled = enabled,
        onClick = {
            state.showControls()
            onClick()
        },
        interactionSource = interactionSource,
    ) {
        content()
    }
}
