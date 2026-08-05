package dev.jdtech.jellyfin.ui.components.player

import androidx.annotation.IntRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow

enum class VideoPlayerOverlayMode {
    Hidden,
    Peek,
    Controls,
}

class VideoPlayerState
internal constructor(@param:IntRange(from = 0) private val hideSeconds: Int) {
    private var _mode by mutableStateOf(VideoPlayerOverlayMode.Controls)
    val mode: VideoPlayerOverlayMode
        get() = _mode

    val controlsVisible: Boolean
        get() = mode != VideoPlayerOverlayMode.Hidden

    private val requests = Channel<VisibilityRequest>(CONFLATED)
    private var autoHide = true

    fun onPlaybackIntentChanged(shouldAutoHide: Boolean) {
        autoHide = shouldAutoHide
        if (shouldAutoHide) showControls() else showControlsIndefinitely()
    }

    fun showPeek(seconds: Int = hideSeconds) {
        show(VideoPlayerOverlayMode.Peek, seconds)
    }

    fun showControls(seconds: Int = hideSeconds) {
        show(VideoPlayerOverlayMode.Controls, seconds)
    }

    fun showControlsIndefinitely() {
        _mode = VideoPlayerOverlayMode.Controls
        requests.trySend(VisibilityRequest(VideoPlayerOverlayMode.Controls, null))
    }

    fun hideControls() {
        _mode = VideoPlayerOverlayMode.Hidden
        requests.trySend(VisibilityRequest(VideoPlayerOverlayMode.Hidden, null))
    }

    private fun show(mode: VideoPlayerOverlayMode, seconds: Int) {
        _mode = mode
        val hideAfterMillis =
            if (!autoHide || seconds == Int.MAX_VALUE) {
                null
            } else {
                seconds.coerceAtLeast(0).toLong() * 1000L
            }
        requests.trySend(VisibilityRequest(mode, hideAfterMillis))
    }

    suspend fun observe() {
        requests.receiveAsFlow().collectLatest { request ->
            val hideAfterMillis = request.hideAfterMillis ?: return@collectLatest
            delay(hideAfterMillis)
            if (_mode == request.mode) _mode = VideoPlayerOverlayMode.Hidden
        }
    }

    private data class VisibilityRequest(
        val mode: VideoPlayerOverlayMode,
        val hideAfterMillis: Long?,
    )
}

@Composable
fun rememberVideoPlayerState(@IntRange(from = 0) hideSeconds: Int = 2) =
    remember { VideoPlayerState(hideSeconds = hideSeconds) }
        .also { LaunchedEffect(it) { it.observe() } }
