package dev.jdtech.jellyfin.ui.components.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun VideoPlayerOverlay(
    shouldAutoHide: Boolean,
    modifier: Modifier = Modifier,
    state: VideoPlayerState = rememberVideoPlayerState(),
    controls: @Composable () -> Unit = {},
) {
    LaunchedEffect(shouldAutoHide) { state.onPlaybackIntentChanged(shouldAutoHide) }

    AnimatedVisibility(visible = state.controlsVisible, enter = fadeIn(), exit = fadeOut()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Spacer(
                modifier =
                    modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.4f),
                                    Color.Black.copy(alpha = 0.8f),
                                )
                            )
                        )
            )

            Column(Modifier.padding(MaterialTheme.spacings.default * 2)) { controls() }
        }
    }
}

@Preview(device = "id:tv_4k")
@Composable
private fun VideoPlayerOverlayPreview() {
    FindroidTheme {
        Box(Modifier.fillMaxSize()) {
            VideoPlayerOverlay(
                modifier = Modifier.align(Alignment.BottomCenter),
                shouldAutoHide = true,
                controls = { Box(Modifier.fillMaxWidth().height(120.dp).background(Color.Blue)) },
            )
        }
    }
}
