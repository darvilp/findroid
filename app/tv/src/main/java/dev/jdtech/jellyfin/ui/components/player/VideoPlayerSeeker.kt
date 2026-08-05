package dev.jdtech.jellyfin.ui.components.player

import android.view.KeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.ui.player.RemoteSeekDirection

@Composable
fun VideoPlayerSeeker(
    focusRequester: FocusRequester,
    state: VideoPlayerState,
    isPlaying: Boolean,
    onPlayPauseToggle: (Boolean) -> Unit,
    onSeekKeyEvent: (KeyEvent, RemoteSeekDirection) -> Unit,
    onNavigateDown: (() -> Unit)? = null,
    contentProgress: Long,
    contentDuration: Long,
    chapterMarkers: List<Float>,
) {
    Row(
        modifier =
            Modifier.onPreviewKeyEvent { event ->
                val keyEvent = event.nativeKeyEvent
                val isDown =
                    keyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
                        keyEvent.keyCode == KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN
                if (isDown && onNavigateDown != null) {
                    if (keyEvent.action == KeyEvent.ACTION_DOWN) onNavigateDown()
                    true
                } else {
                    false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = {
                onPlayPauseToggle(!isPlaying)
                state.showControls()
            },
            modifier = Modifier.focusRequester(focusRequester),
        ) {
            Icon(
                painter =
                    painterResource(
                        id =
                            if (isPlaying) {
                                CoreR.drawable.ic_pause
                            } else {
                                CoreR.drawable.ic_play
                            }
                    ),
                contentDescription = null,
            )
        }
        Spacer(modifier = Modifier.width(MaterialTheme.spacings.medium))
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatPlaybackTime(contentProgress),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                Text(
                    text = formatPlaybackTime(contentDuration),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
            }
            Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
            VideoPlayerSeekBar(
                progress = playbackProgress(contentProgress, contentDuration),
                chapterMarkers = chapterMarkers,
                onSeekKeyEvent = onSeekKeyEvent,
                onPlayPauseToggle = { onPlayPauseToggle(!isPlaying) },
                state = state,
            )
        }
    }
}

internal fun playbackProgress(positionMs: Long, durationMs: Long): Float =
    if (durationMs > 0L) {
        positionMs.toFloat().div(durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

internal fun chapterMarkerProgress(
    chapterStartPositions: List<Long>,
    durationMs: Long,
): List<Float> =
    if (durationMs > 0L) {
        chapterStartPositions
            .asSequence()
            .filter { it in 1 until durationMs }
            .map { it.toFloat() / durationMs }
            .toList()
    } else {
        emptyList()
    }

internal fun seekTarget(
    positionMs: Long,
    durationMs: Long,
    incrementMs: Long,
    forward: Boolean,
): Long {
    val position = positionMs.coerceAtLeast(0L)
    val increment = incrementMs.coerceAtLeast(0L)
    if (!forward) return (position - increment).coerceAtLeast(0L)

    val target =
        if (position > Long.MAX_VALUE - increment) Long.MAX_VALUE else position + increment
    return if (durationMs > 0L) target.coerceAtMost(durationMs) else target
}

private fun formatPlaybackTime(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "$hours:${minutes.padStartWith0()}:${seconds.padStartWith0()}"
    } else {
        "${minutes.padStartWith0()}:${seconds.padStartWith0()}"
    }
}

@Preview
@Composable
private fun VideoPlayerSeekerPreview() {
    FindroidTheme {
        VideoPlayerSeeker(
            focusRequester = FocusRequester(),
            state = rememberVideoPlayerState(),
            isPlaying = false,
            onPlayPauseToggle = {},
            onSeekKeyEvent = { _, _ -> },
            contentProgress = 471_000L,
            contentDuration = 1_420_000L,
            chapterMarkers = listOf(0.2f, 0.7f),
        )
    }
}

private fun Number.padStartWith0() = toString().padStart(2, '0')
