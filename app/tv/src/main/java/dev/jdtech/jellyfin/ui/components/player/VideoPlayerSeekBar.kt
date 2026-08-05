package dev.jdtech.jellyfin.ui.components.player

import android.view.KeyEvent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme

@Composable
fun VideoPlayerSeekBar(
    progress: Float,
    chapterMarkers: List<Float>,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    state: VideoPlayerState,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val color by rememberUpdatedState(MaterialTheme.colorScheme.onSurface)
    val animatedHeight by animateDpAsState(targetValue = 8.dp.times(if (isFocused) 2f else 1f))
    val safeProgress = progress.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f

    LaunchedEffect(isFocused) {
        if (isFocused) state.showControls()
    }

    Canvas(
        modifier =
            Modifier.fillMaxWidth()
                .height(animatedHeight)
                .padding(horizontal = 4.dp)
                .onPreviewKeyEvent { event ->
                    when (event.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> {
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                onSeekBack()
                                state.showControls()
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT,
                        KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> {
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                                onSeekForward()
                                state.showControls()
                            }
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                                onPlayPauseToggle()
                                state.showControls()
                            }
                            true
                        }
                        else -> false
                    }
                }
                .focusable(interactionSource = interactionSource)
    ) {
        val yOffset = size.height / 2f
        drawLine(
            color = color.copy(alpha = 0.24f),
            start = Offset(x = 0f, y = yOffset),
            end = Offset(x = size.width, y = yOffset),
            strokeWidth = size.height / 2f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(x = 0f, y = yOffset),
            end = Offset(x = size.width * safeProgress, y = yOffset),
            strokeWidth = size.height / 2f,
            cap = StrokeCap.Round,
        )
        chapterMarkers
            .asSequence()
            .filter { it.isFinite() && it in 0f..1f }
            .forEach { marker ->
                val x = size.width * marker
                drawLine(
                    color = Color.White.copy(alpha = 0.8f),
                    start = Offset(x = x, y = 0f),
                    end = Offset(x = x, y = size.height),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        drawCircle(
            color = Color.White,
            radius = size.height / 2f,
            center = Offset(x = size.width * safeProgress, y = yOffset),
        )
    }
}

@Preview
@Composable
fun VideoPlayerSeekBarPreview() {
    FindroidTheme {
        VideoPlayerSeekBar(
            progress = 0.4f,
            chapterMarkers = listOf(0.2f, 0.7f),
            onSeekBack = {},
            onSeekForward = {},
            onPlayPauseToggle = {},
            state = rememberVideoPlayerState(),
        )
    }
}
