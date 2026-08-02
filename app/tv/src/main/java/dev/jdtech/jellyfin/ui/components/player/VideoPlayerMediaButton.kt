package dev.jdtech.jellyfin.ui.components.player

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton

@Composable
fun VideoPlayerMediaButton(
    icon: Painter,
    state: VideoPlayerState,
    contentDescription: String,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) {
        if (isFocused) state.showControls()
    }

    IconButton(
        enabled = enabled,
        onClick = {
            state.showControls()
            onClick()
        },
        interactionSource = interactionSource,
    ) {
        Icon(painter = icon, contentDescription = contentDescription)
    }
}
