package dev.jdtech.jellyfin.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import dev.jdtech.jellyfin.core.R
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.player.local.domain.ChapterNavigationDirection
import dev.jdtech.jellyfin.player.local.domain.ChapterNavigationState
import dev.jdtech.jellyfin.player.local.domain.PlaylistNavigationDirection
import dev.jdtech.jellyfin.player.local.domain.PlaylistNavigationState
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.player.local.presentation.PlayerEvents
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.ui.components.player.VideoPlayerControlsLayout
import dev.jdtech.jellyfin.ui.components.player.VideoPlayerMediaButton
import dev.jdtech.jellyfin.ui.components.player.VideoPlayerMediaTitle
import dev.jdtech.jellyfin.ui.components.player.VideoPlayerOverlay
import dev.jdtech.jellyfin.ui.components.player.VideoPlayerOverlayMode
import dev.jdtech.jellyfin.ui.components.player.VideoPlayerSeeker
import dev.jdtech.jellyfin.ui.components.player.VideoPlayerState
import dev.jdtech.jellyfin.ui.components.player.chapterMarkerProgress
import dev.jdtech.jellyfin.ui.components.player.rememberVideoPlayerState
import dev.jdtech.jellyfin.ui.dialogs.VideoPlayerTrackSelectorDialog
import dev.jdtech.jellyfin.ui.player.RemoteSeekController
import dev.jdtech.jellyfin.ui.player.RemoteSeekDirection
import dev.jdtech.jellyfin.ui.player.RemoteSeekPlayback
import dev.jdtech.jellyfin.ui.player.chapterRemoteCommand
import dev.jdtech.jellyfin.ui.player.remoteSeekDirectionOrNull
import java.util.UUID
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    itemId: UUID,
    itemKind: String,
    startFromBeginning: Boolean,
    navigateBack: () -> Unit,
) {
    val viewModel = hiltViewModel<PlayerViewModel>()
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val currentView = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner, viewModel, navigateBack) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.eventsChannelFlow.collect { event ->
                when (event) {
                    PlayerEvents.NavigateBack -> navigateBack()
                    is PlayerEvents.IsPlayingChanged -> Unit
                }
            }
        }
    }

    DisposableEffect(Unit) {
        currentView.keepScreenOn = true
        onDispose { currentView.keepScreenOn = false }
    }

    var lifecycle by remember { mutableStateOf(Lifecycle.Event.ON_CREATE) }
    var mediaSession by remember { mutableStateOf<MediaSession?>(null) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            lifecycle = event
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    mediaSession?.release()
                    mediaSession = null
                }
                Lifecycle.Event.ON_START -> {
                    mediaSession?.release()
                    mediaSession = MediaSession.Builder(context, viewModel.player).build()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mediaSession?.release()
            mediaSession = null
        }
    }

    val videoPlayerState = rememberVideoPlayerState()
    val remoteSeekController = remember { RemoteSeekController() }
    val rootFocusRequester = remember { FocusRequester() }
    val controlsFocusRequester = remember { FocusRequester() }
    val skipButtonFocusRequester = remember { FocusRequester() }

    var currentPosition by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(viewModel.player.isPlaying) }
    var playWhenReady by remember { mutableStateOf(viewModel.player.playWhenReady) }
    LaunchedEffect(lifecycleOwner, viewModel.player) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                currentPosition = viewModel.player.currentPosition
                isPlaying = viewModel.player.isPlaying
                playWhenReady = viewModel.player.playWhenReady
                delay(300L)
            }
        }
    }

    LaunchedEffect(
        lifecycleOwner,
        viewModel.segmentsSkipButton,
        viewModel.segmentsAutoSkip,
    ) {
        if (viewModel.segmentsSkipButton || viewModel.segmentsAutoSkip) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    viewModel.updateCurrentSegment()
                    delay(1_000L)
                }
            }
        }
    }

    var selectedTrackType by remember { mutableStateOf<Int?>(null) }
    var dismissedSkipSegment by remember { mutableStateOf<FindroidSegment?>(null) }
    var skipButtonFocused by remember { mutableStateOf(false) }
    val segment = uiState.currentSegment
    val chapterNavigation = viewModel.chapterNavigationState(currentPosition)

    LaunchedEffect(segment) {
        if (segment == null) dismissedSkipSegment = null
    }

    val skipPromptMayTakeFocus =
        segment != null &&
            segment != dismissedSkipSegment &&
            selectedTrackType == null &&
            videoPlayerState.mode != VideoPlayerOverlayMode.Controls

    LaunchedEffect(
        segment,
        dismissedSkipSegment,
        selectedTrackType,
        videoPlayerState.mode,
    ) {
        if (selectedTrackType == null) {
            when {
                skipPromptMayTakeFocus -> skipButtonFocusRequester.requestFocus()
                videoPlayerState.mode == VideoPlayerOverlayMode.Controls -> {
                    // Let AnimatedVisibility place the controls before moving focus off a
                    // segment prompt. An immediate request can race the enter animation.
                    delay(50L)
                    controlsFocusRequester.requestFocus()
                }
                else -> rootFocusRequester.requestFocus()
            }
        }
    }

    var chapterActionsAvailable by remember {
        mutableStateOf(chapterNavigation.hasMeaningfulChapters)
    }
    LaunchedEffect(chapterNavigation.hasMeaningfulChapters) {
        val chapterActionsRemoved =
            chapterActionsAvailable && !chapterNavigation.hasMeaningfulChapters
        chapterActionsAvailable = chapterNavigation.hasMeaningfulChapters
        if (
            chapterActionsRemoved &&
                selectedTrackType == null &&
                videoPlayerState.mode == VideoPlayerOverlayMode.Controls
        ) {
            delay(50L)
            controlsFocusRequester.requestFocus()
        }
    }

    BackHandler(
        enabled =
            selectedTrackType != null ||
                videoPlayerState.mode != VideoPlayerOverlayMode.Hidden ||
                skipButtonFocused
    ) {
        when {
            selectedTrackType != null -> selectedTrackType = null
            videoPlayerState.mode != VideoPlayerOverlayMode.Hidden -> {
                videoPlayerState.hideControls()
                rootFocusRequester.requestFocus()
            }
            skipButtonFocused && segment != null -> {
                dismissedSkipSegment = segment
                rootFocusRequester.requestFocus()
            }
        }
    }

    Box(
        modifier =
            Modifier.fillMaxSize()
                .playerDPadEvents(
                    player = viewModel.player,
                    videoPlayerState = videoPlayerState,
                    remoteSeekController = remoteSeekController,
                    chapterNavigation = viewModel::chapterNavigationState,
                    modalActive = selectedTrackType != null,
                    skipPromptFocused = skipButtonFocused,
                    onChapterCommand = { direction ->
                        when (direction) {
                            ChapterNavigationDirection.Previous ->
                                viewModel.seekToPreviousChapter()
                            ChapterNavigationDirection.Next -> viewModel.seekToNextChapter()
                        }
                    },
                )
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .focusRequester(rootFocusRequester)
                    .focusable()
        ) {
            AndroidView(
                factory = { playerViewContext ->
                    PlayerView(playerViewContext).also { playerView ->
                        playerView.player = viewModel.player
                        playerView.useController = false
                        viewModel.initializePlayer(
                            itemId = itemId,
                            itemKind = itemKind,
                            startFromBeginning = startFromBeginning,
                        )
                        playerView.setBackgroundColor(
                            playerViewContext.resources.getColor(
                                android.R.color.black,
                                playerViewContext.theme,
                            )
                        )
                    }
                },
                update = { playerView ->
                    when (lifecycle) {
                        Lifecycle.Event.ON_PAUSE -> {
                            playerView.onPause()
                            playerView.player?.pause()
                        }
                        Lifecycle.Event.ON_RESUME -> playerView.onResume()
                        else -> Unit
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            VideoPlayerOverlay(
                modifier = Modifier.align(Alignment.BottomCenter),
                focusRequester = controlsFocusRequester,
                state = videoPlayerState,
                shouldAutoHide = playWhenReady,
                controls = {
                    VideoPlayerControls(
                        title = uiState.currentItemTitle,
                        isPlaying = isPlaying,
                        contentCurrentPosition = currentPosition,
                        chapterNavigation = chapterNavigation,
                        playlistNavigation = uiState.playlistNavigation,
                        showChapterMarkers = viewModel.chapterMarkersEnabled,
                        player = viewModel.player,
                        state = videoPlayerState,
                        focusRequester = controlsFocusRequester,
                        skipButtonFocusRequester = skipButtonFocusRequester,
                        skipPromptAvailable = segment != null,
                        remoteSeekController = remoteSeekController,
                        restartAvailable =
                            viewModel.isRestartCurrentItemAvailable(currentPosition),
                        onRestart = viewModel::restartCurrentItem,
                        onPreviousChapter = viewModel::seekToPreviousChapter,
                        onNextChapter = viewModel::seekToNextChapter,
                        onPreviousEpisode = viewModel::goToPreviousEpisode,
                        onNextEpisode = viewModel::goToNextEpisode,
                        onSelectAudio = { selectedTrackType = C.TRACK_TYPE_AUDIO },
                        onSelectSubtitles = { selectedTrackType = C.TRACK_TYPE_TEXT },
                    )
                },
            )
        }

        if (segment != null) {
            SkipButton(
                stringRes = uiState.currentSkipButtonStringRes,
                onClick = { viewModel.skipSegment(segment) },
                skipButtonFocusRequester = skipButtonFocusRequester,
                onFocusChanged = { focused ->
                    skipButtonFocused = focused
                    if (focused && videoPlayerState.mode == VideoPlayerOverlayMode.Controls) {
                        videoPlayerState.showControls()
                    }
                },
                onNavigateFocus = { target ->
                    when (target) {
                        PlayerFocusTarget.DefaultControls -> {
                            if (videoPlayerState.mode == VideoPlayerOverlayMode.Controls) {
                                controlsFocusRequester.requestFocus()
                            } else {
                                videoPlayerState.showControls()
                            }
                        }
                        PlayerFocusTarget.SkipPrompt -> skipButtonFocusRequester.requestFocus()
                    }
                },
                onSeekKeyEvent = { keyEvent, direction ->
                    viewModel.player.handleRemoteSeekKeyEvent(
                        remoteSeekController,
                        keyEvent,
                        direction,
                    )
                    if (keyEvent.action == KeyEvent.ACTION_DOWN) videoPlayerState.showPeek()
                },
            )
        }
    }

    selectedTrackType?.let { trackType ->
        val tracks =
            if (trackType == C.TRACK_TYPE_AUDIO) uiState.audioTracks else uiState.subtitleTracks
        VideoPlayerTrackSelectorDialog(
            trackType = trackType,
            tracks = tracks,
            onSelect = { track ->
                viewModel.switchToTrack(
                    trackType = trackType,
                    groupIndex = track?.groupIndex,
                    trackIndex = track?.trackIndex,
                )
                selectedTrackType = null
            },
            onDismiss = { selectedTrackType = null },
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerControls(
    title: String,
    isPlaying: Boolean,
    contentCurrentPosition: Long,
    chapterNavigation: ChapterNavigationState,
    playlistNavigation: PlaylistNavigationState,
    showChapterMarkers: Boolean,
    player: Player,
    state: VideoPlayerState,
    focusRequester: FocusRequester,
    skipButtonFocusRequester: FocusRequester,
    skipPromptAvailable: Boolean,
    remoteSeekController: RemoteSeekController,
    restartAvailable: Boolean,
    onRestart: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onSelectAudio: () -> Unit,
    onSelectSubtitles: () -> Unit,
) {
    var focusedEpisodeDirection by remember {
        mutableStateOf<PlaylistNavigationDirection?>(null)
    }
    LaunchedEffect(playlistNavigation, focusedEpisodeDirection, state.mode) {
        if (
            state.mode == VideoPlayerOverlayMode.Controls &&
                shouldRestoreEpisodeNavigationFocus(
                    focusedDirection = focusedEpisodeDirection,
                    navigation = playlistNavigation,
                )
        ) {
            delay(50L)
            focusRequester.requestFocus()
            focusedEpisodeDirection = null
        }
    }
    val clearEpisodeNavigationFocus = { focused: Boolean ->
        if (focused) focusedEpisodeDirection = null
    }

    val onPlayPauseToggle = { shouldPlay: Boolean ->
        if (shouldPlay) player.play() else player.pause()
    }
    val chapterMarkers =
        if (showChapterMarkers) {
            chapterMarkerProgress(
                chapterNavigation.chapters.map { it.startPosition },
                player.duration,
            )
        } else {
            emptyList()
        }

    VideoPlayerControlsLayout(
        mediaTitle = { VideoPlayerMediaTitle(title = title, subtitle = null) },
        seeker = {
            VideoPlayerSeeker(
                focusRequester = focusRequester,
                state = state,
                isPlaying = isPlaying,
                onPlayPauseToggle = onPlayPauseToggle,
                playlistNavigation = playlistNavigation,
                onPreviousEpisode = onPreviousEpisode,
                onNextEpisode = onNextEpisode,
                onEpisodeNavigationFocusChanged = { direction ->
                    focusedEpisodeDirection = direction
                },
                onSeekKeyEvent = { keyEvent, direction ->
                    player.handleRemoteSeekKeyEvent(remoteSeekController, keyEvent, direction)
                },
                onNavigateDown =
                    if (
                        playerFocusDestination(
                            source = PlayerFocusTarget.DefaultControls,
                            direction = PlayerFocusDirection.Down,
                            skipPromptAvailable = skipPromptAvailable,
                        ) == PlayerFocusTarget.SkipPrompt
                    ) {
                        {
                            focusedEpisodeDirection = null
                            state.showControls()
                            skipButtonFocusRequester.requestFocus()
                        }
                    } else {
                        null
                    },
                contentProgress = contentCurrentPosition,
                contentDuration = player.duration,
                chapterMarkers = chapterMarkers,
            )
        },
        mediaActions = {
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium)) {
                if (restartAvailable) {
                    VideoPlayerMediaButton(
                        icon = painterResource(id = R.drawable.ic_rotate_ccw),
                        state = state,
                        contentDescription = stringResource(id = R.string.restart_current_item),
                        onFocusChanged = clearEpisodeNavigationFocus,
                        onClick = {
                            focusRequester.requestFocus()
                            onRestart()
                        },
                    )
                }
                if (chapterNavigation.hasMeaningfulChapters) {
                    VideoPlayerMediaButton(
                        icon = painterResource(id = R.drawable.ic_skip_back),
                        state = state,
                        contentDescription = stringResource(id = R.string.previous_chapter),
                        enabled = chapterNavigation.previousChapter != null,
                        onFocusChanged = clearEpisodeNavigationFocus,
                        onClick = onPreviousChapter,
                    )
                    VideoPlayerMediaButton(
                        icon = painterResource(id = R.drawable.ic_skip_forward),
                        state = state,
                        contentDescription = stringResource(id = R.string.next_chapter),
                        enabled = chapterNavigation.nextChapter != null,
                        onFocusChanged = clearEpisodeNavigationFocus,
                        onClick = onNextChapter,
                    )
                }
                VideoPlayerMediaButton(
                    icon = painterResource(id = R.drawable.ic_speaker),
                    state = state,
                    contentDescription = stringResource(id = R.string.audio),
                    onFocusChanged = clearEpisodeNavigationFocus,
                    onClick = onSelectAudio,
                )
                VideoPlayerMediaButton(
                    icon = painterResource(id = R.drawable.ic_closed_caption),
                    state = state,
                    contentDescription = stringResource(id = R.string.subtitle),
                    onFocusChanged = clearEpisodeNavigationFocus,
                    onClick = onSelectSubtitles,
                )
            }
        },
    )
}

@Composable
private fun SkipButton(
    stringRes: Int,
    onClick: () -> Unit,
    skipButtonFocusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onNavigateFocus: (PlayerFocusTarget) -> Unit,
    onSeekKeyEvent: (KeyEvent, RemoteSeekDirection) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().padding(MaterialTheme.spacings.large).zIndex(1f),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Button(
            onClick = onClick,
            modifier =
                Modifier.focusRequester(skipButtonFocusRequester)
                    .onFocusChanged { onFocusChanged(it.isFocused) }
                    .onPreviewKeyEvent { event ->
                        val keyEvent = event.nativeKeyEvent
                        val remoteSeekDirection = keyEvent.remoteSeekDirectionOrNull()
                        if (remoteSeekDirection != null) {
                            onSeekKeyEvent(keyEvent, remoteSeekDirection)
                            return@onPreviewKeyEvent true
                        }

                        val focusDirection = keyEvent.playerFocusDirectionOrNull()
                        if (focusDirection != null) {
                            val destination =
                                playerFocusDestination(
                                    source = PlayerFocusTarget.SkipPrompt,
                                    direction = focusDirection,
                                    skipPromptAvailable = true,
                                )
                            if (keyEvent.action == KeyEvent.ACTION_DOWN && destination != null) {
                                onNavigateFocus(destination)
                            }
                            return@onPreviewKeyEvent true
                        }

                        false
                    },
            glow =
                ButtonDefaults.glow(
                    focusedGlow = Glow(elevationColor = Color.Gray, elevation = 20.dp)
                ),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_forward),
                contentDescription = null,
            )
            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(text = stringResource(stringRes), color = Color.Black)
        }
    }
}

private fun Modifier.playerDPadEvents(
    player: Player,
    videoPlayerState: VideoPlayerState,
    remoteSeekController: RemoteSeekController,
    chapterNavigation: () -> ChapterNavigationState,
    modalActive: Boolean,
    skipPromptFocused: Boolean,
    onChapterCommand: (ChapterNavigationDirection) -> Unit,
): Modifier =
    onPreviewKeyEvent { event ->
        val keyEvent = event.nativeKeyEvent
        val chapterCommand =
            chapterRemoteCommand(
                keyCode = keyEvent.keyCode,
                modalActive = modalActive,
                navigation = chapterNavigation(),
            )
        if (chapterCommand != null) {
            if (keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.repeatCount == 0) {
                onChapterCommand(chapterCommand)
                if (videoPlayerState.mode == VideoPlayerOverlayMode.Controls) {
                    videoPlayerState.showControls()
                } else {
                    videoPlayerState.showPeek()
                }
            }
            return@onPreviewKeyEvent true
        }

        if (
            !playerRootOwnsPlaybackKeys(
                overlayMode = videoPlayerState.mode,
                skipPromptFocused = skipPromptFocused,
            )
        ) {
            return@onPreviewKeyEvent false
        }

        val remoteSeekDirection = keyEvent.remoteSeekDirectionOrNull()
        if (remoteSeekDirection != null) {
            player.handleRemoteSeekKeyEvent(
                remoteSeekController,
                keyEvent,
                remoteSeekDirection,
            )
            if (keyEvent.action == KeyEvent.ACTION_DOWN) videoPlayerState.showPeek()
            return@onPreviewKeyEvent true
        }

        when (keyEvent.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN -> {
                if (keyEvent.action == KeyEvent.ACTION_DOWN) videoPlayerState.showControls()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (keyEvent.action == KeyEvent.ACTION_UP) {
                    if (player.isPlaying) player.pause() else player.play()
                    videoPlayerState.showControls()
                }
                true
            }
            else -> false
        }
    }

internal fun playerRootOwnsPlaybackKeys(
    overlayMode: VideoPlayerOverlayMode,
    skipPromptFocused: Boolean,
): Boolean =
    overlayMode != VideoPlayerOverlayMode.Controls &&
        !skipPromptFocused

internal fun shouldRestoreEpisodeNavigationFocus(
    focusedDirection: PlaylistNavigationDirection?,
    navigation: PlaylistNavigationState,
): Boolean = focusedDirection?.let { direction -> !navigation.isAvailable(direction) } ?: false

internal enum class PlayerFocusTarget {
    DefaultControls,
    SkipPrompt,
}

internal enum class PlayerFocusDirection {
    Up,
    Down,
}

internal fun playerFocusDestination(
    source: PlayerFocusTarget,
    direction: PlayerFocusDirection,
    skipPromptAvailable: Boolean,
): PlayerFocusTarget? =
    when {
        !skipPromptAvailable -> null
        source == PlayerFocusTarget.DefaultControls && direction == PlayerFocusDirection.Down ->
            PlayerFocusTarget.SkipPrompt
        source == PlayerFocusTarget.SkipPrompt && direction == PlayerFocusDirection.Up ->
            PlayerFocusTarget.DefaultControls
        else -> null
    }

private fun KeyEvent.playerFocusDirectionOrNull(): PlayerFocusDirection? =
    when (keyCode) {
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_SYSTEM_NAVIGATION_UP -> PlayerFocusDirection.Up
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_SYSTEM_NAVIGATION_DOWN -> PlayerFocusDirection.Down
        else -> null
    }

private fun Player.handleRemoteSeekKeyEvent(
    controller: RemoteSeekController,
    keyEvent: KeyEvent,
    direction: RemoteSeekDirection,
) {
    when (keyEvent.action) {
        KeyEvent.ACTION_DOWN ->
            seekTo(
                controller.onKeyDown(
                    direction = direction,
                    eventTimeMs = keyEvent.eventTime,
                    playback =
                        RemoteSeekPlayback(
                            positionMs = currentPosition,
                            durationMs = duration.takeIf { it >= 0L },
                            seekBackIncrementMs = seekBackIncrement,
                            seekForwardIncrementMs = seekForwardIncrement,
                        ),
                )
            )
        KeyEvent.ACTION_UP -> controller.onKeyUp(direction)
    }
}
