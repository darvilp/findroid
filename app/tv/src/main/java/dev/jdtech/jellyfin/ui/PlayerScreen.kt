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
import androidx.compose.ui.focus.focusProperties
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
import dev.jdtech.jellyfin.models.InitialTrackSelection
import dev.jdtech.jellyfin.player.local.domain.ChapterNavigationDirection
import dev.jdtech.jellyfin.player.local.domain.ChapterNavigationState
import dev.jdtech.jellyfin.player.local.domain.PlaylistNavigationState
import dev.jdtech.jellyfin.player.local.domain.PlaybackDetailsTarget
import dev.jdtech.jellyfin.player.local.presentation.PlayerViewModel
import dev.jdtech.jellyfin.player.local.presentation.PlayerEvents
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.ui.components.player.VideoPlayerControlsLayout
import dev.jdtech.jellyfin.ui.components.player.VideoPlayerMediaButton
import dev.jdtech.jellyfin.ui.components.player.VideoPlayerMediaTextButton
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
    initialTrackSelection: InitialTrackSelection?,
    navigateBack: () -> Unit,
    navigateToDetails: (PlaybackDetailsTarget) -> Unit,
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
    val skipButtonFocusRequester = remember { FocusRequester() }
    val controlFocusRequesters =
        remember { PlayerControl.entries.associateWith { FocusRequester() } }

    var currentPosition by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(viewModel.player.isPlaying) }
    var playWhenReady by remember { mutableStateOf(viewModel.player.playWhenReady) }
    var hardwareDecodingActive by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(lifecycleOwner, viewModel.player) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                currentPosition = viewModel.player.currentPosition
                isPlaying = viewModel.player.isPlaying
                playWhenReady = viewModel.player.playWhenReady
                viewModel.isHardwareDecodingActive()?.let { hardwareDecodingActive = it }
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
    var trackDialogReturnControl by remember { mutableStateOf<PlayerControl?>(null) }
    var dismissedSkipSegment by remember { mutableStateOf<FindroidSegment?>(null) }
    var skipButtonFocused by remember { mutableStateOf(false) }
    var focusedControl by remember { mutableStateOf<PlayerControl?>(null) }
    var lastFocusedControl by remember { mutableStateOf<PlayerControl?>(PlayerControl.PlayPause) }
    var lastFocusedAction by remember { mutableStateOf<PlayerControl?>(null) }
    var lastFocusedBottomControl by remember {
        mutableStateOf<PlayerControl?>(PlayerControl.PlayPause)
    }
    var lastFocusWasSkipPrompt by remember { mutableStateOf(false) }
    var pendingControlFocus by remember { mutableStateOf<PlayerControl?>(null) }
    val segment = uiState.currentSegment
    val chapterNavigation = viewModel.chapterNavigationState(currentPosition)
    val restartAvailable = viewModel.isRestartCurrentItemAvailable(currentPosition)
    val actionControls =
        buildList {
            if (uiState.currentDetailsTarget != null) add(PlayerControl.Details)
            if (restartAvailable) add(PlayerControl.Restart)
            if (chapterNavigation.previousChapter != null) add(PlayerControl.PreviousChapter)
            if (chapterNavigation.nextChapter != null) add(PlayerControl.NextChapter)
            add(PlayerControl.Audio)
            add(PlayerControl.Subtitles)
            if (hardwareDecodingActive != null) add(PlayerControl.HardwareDecoder)
        }
    val bottomControls =
        buildList {
            if (uiState.playlistNavigation.canGoPrevious) add(PlayerControl.PreviousEpisode)
            add(PlayerControl.PlayPause)
            if (uiState.playlistNavigation.canGoNext) add(PlayerControl.NextEpisode)
            add(PlayerControl.SeekBar)
        }
    val availableControls = (actionControls + bottomControls).toSet()

    val onControlFocused = { control: PlayerControl ->
        focusedControl = control
        lastFocusedControl = control
        lastFocusWasSkipPrompt = false
        if (control in actionControls) lastFocusedAction = control
        if (control in bottomControls) lastFocusedBottomControl = control
    }

    LaunchedEffect(segment) {
        if (segment == null) dismissedSkipSegment = null
    }

    LaunchedEffect(segment, lastFocusWasSkipPrompt, videoPlayerState.mode) {
        val control =
            focusAfterSkipPromptRemoval(
                skipPromptWasFocused = lastFocusWasSkipPrompt,
                skipPromptAvailable = segment != null,
                overlayMode = videoPlayerState.mode,
                lastFocusedBottomControl = lastFocusedBottomControl,
                availableControls = bottomControls.toSet(),
            )
        if (control != null) {
            lastFocusWasSkipPrompt = false
            pendingControlFocus = control
        }
    }

    val skipPromptMayTakeFocus =
        segment != null &&
            segment != dismissedSkipSegment &&
            selectedTrackType == null &&
            videoPlayerState.mode != VideoPlayerOverlayMode.Controls

    LaunchedEffect(videoPlayerState.mode) {
        if (videoPlayerState.mode == VideoPlayerOverlayMode.Controls) {
            delay(50L)
            val control = restoredPlayerControl(lastFocusedControl, availableControls)
            controlFocusRequesters.getValue(control).requestFocus()
        }
    }

    LaunchedEffect(pendingControlFocus, videoPlayerState.mode) {
        val control = pendingControlFocus ?: return@LaunchedEffect
        if (videoPlayerState.mode == VideoPlayerOverlayMode.Controls) {
            delay(50L)
            controlFocusRequesters.getValue(control).requestFocus()
            pendingControlFocus = null
        }
    }

    LaunchedEffect(
        segment,
        dismissedSkipSegment,
        selectedTrackType,
        videoPlayerState.mode,
        focusedControl,
        availableControls,
    ) {
        when (
            val request =
                playerFocusRequest(
                    overlayMode = videoPlayerState.mode,
                    modalActive = selectedTrackType != null,
                    skipPromptMayTakeFocus = skipPromptMayTakeFocus,
                    focusedControl = focusedControl,
                    availableControls = availableControls,
                )
        ) {
            PlayerFocusRequest.Root -> {
                focusedControl = null
                lastFocusWasSkipPrompt = false
                rootFocusRequester.requestFocus()
            }
            PlayerFocusRequest.SkipPrompt -> {
                focusedControl = null
                lastFocusWasSkipPrompt = true
                skipButtonFocusRequester.requestFocus()
            }
            is PlayerFocusRequest.Control -> {
                focusedControl = null
                pendingControlFocus = request.control
            }
            null -> Unit
        }
    }

    val closeTrackDialog = {
        selectedTrackType = null
        trackDialogReturnControl?.let { control ->
            pendingControlFocus = control
            videoPlayerState.showControls()
        }
        trackDialogReturnControl = null
    }

    BackHandler(
        enabled =
            selectedTrackType != null ||
                videoPlayerState.mode != VideoPlayerOverlayMode.Hidden ||
                skipButtonFocused
    ) {
        when {
            selectedTrackType != null -> closeTrackDialog()
            videoPlayerState.mode != VideoPlayerOverlayMode.Hidden -> {
                videoPlayerState.hideControls()
                focusedControl = null
                rootFocusRequester.requestFocus()
            }
            skipButtonFocused && segment != null -> {
                dismissedSkipSegment = segment
                focusedControl = null
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
                            initialTrackSelection = initialTrackSelection,
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
                state = videoPlayerState,
                shouldAutoHide = playWhenReady,
                controls = {
                    VideoPlayerControls(
                        title = uiState.currentItemTitle,
                        detailsTarget = uiState.currentDetailsTarget,
                        isPlaying = isPlaying,
                        contentCurrentPosition = currentPosition,
                        chapterNavigation = chapterNavigation,
                        playlistNavigation = uiState.playlistNavigation,
                        showChapterMarkers = viewModel.chapterMarkersEnabled,
                        player = viewModel.player,
                        state = videoPlayerState,
                        controlFocusRequesters = controlFocusRequesters,
                        actionControls = actionControls,
                        bottomControls = bottomControls,
                        lastFocusedAction = lastFocusedAction,
                        onControlFocused = onControlFocused,
                        skipButtonFocusRequester = skipButtonFocusRequester,
                        skipPromptAvailable = segment != null,
                        remoteSeekController = remoteSeekController,
                        restartAvailable = restartAvailable,
                        onRestart = viewModel::restartCurrentItem,
                        onViewDetails = { target ->
                            viewModel.player.pause()
                            navigateToDetails(target)
                        },
                        onPreviousChapter = viewModel::seekToPreviousChapter,
                        onNextChapter = viewModel::seekToNextChapter,
                        onPreviousEpisode = viewModel::goToPreviousEpisode,
                        onNextEpisode = viewModel::goToNextEpisode,
                        onSelectAudio = {
                            focusedControl = null
                            trackDialogReturnControl = PlayerControl.Audio
                            selectedTrackType = C.TRACK_TYPE_AUDIO
                        },
                        onSelectSubtitles = {
                            focusedControl = null
                            trackDialogReturnControl = PlayerControl.Subtitles
                            selectedTrackType = C.TRACK_TYPE_TEXT
                        },
                        hardwareDecodingActive = hardwareDecodingActive,
                        onSetHardwareDecodingEnabled = { enabled ->
                            hardwareDecodingActive = enabled
                            viewModel.setHardwareDecodingEnabled(enabled)
                        },
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
                    if (focused) {
                        focusedControl = null
                        lastFocusWasSkipPrompt = true
                    }
                    if (focused && videoPlayerState.mode == VideoPlayerOverlayMode.Controls) {
                        videoPlayerState.showControls()
                    }
                },
                onNavigateFocus = { target ->
                    when (target) {
                        PlayerFocusTarget.DefaultControls -> {
                            pendingControlFocus =
                                restoredPlayerControl(
                                    lastFocusedBottomControl,
                                    bottomControls.toSet(),
                                )
                            videoPlayerState.showControls()
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
                closeTrackDialog()
            },
            onDismiss = closeTrackDialog,
        )
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerControls(
    title: String,
    detailsTarget: PlaybackDetailsTarget?,
    isPlaying: Boolean,
    contentCurrentPosition: Long,
    chapterNavigation: ChapterNavigationState,
    playlistNavigation: PlaylistNavigationState,
    showChapterMarkers: Boolean,
    player: Player,
    state: VideoPlayerState,
    controlFocusRequesters: Map<PlayerControl, FocusRequester>,
    actionControls: List<PlayerControl>,
    bottomControls: List<PlayerControl>,
    lastFocusedAction: PlayerControl?,
    onControlFocused: (PlayerControl) -> Unit,
    skipButtonFocusRequester: FocusRequester,
    skipPromptAvailable: Boolean,
    remoteSeekController: RemoteSeekController,
    restartAvailable: Boolean,
    onRestart: () -> Unit,
    onViewDetails: (PlaybackDetailsTarget) -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onSelectAudio: () -> Unit,
    onSelectSubtitles: () -> Unit,
    hardwareDecodingActive: Boolean?,
    onSetHardwareDecodingEnabled: (Boolean) -> Unit,
) {
    fun requester(control: PlayerControl?): FocusRequester =
        control?.let(controlFocusRequesters::getValue) ?: FocusRequester.Cancel

    fun controlModifier(
        control: PlayerControl,
        left: FocusRequester,
        right: FocusRequester,
        up: FocusRequester,
        down: FocusRequester,
    ): Modifier =
        Modifier.focusRequester(controlFocusRequesters.getValue(control))
            .focusProperties {
                this.left = left
                this.right = right
                this.up = up
                this.down = down
            }
            .onFocusChanged { focusState ->
                if (focusState.isFocused) onControlFocused(control)
            }

    fun actionModifier(control: PlayerControl): Modifier =
        controlModifier(
            control = control,
            left = requester(previousPlayerControl(control, actionControls)),
            right = requester(nextPlayerControl(control, actionControls)),
            up = FocusRequester.Cancel,
            down = requester(PlayerControl.SeekBar),
        )

    val upAction = lastFocusedAction?.takeIf { it in actionControls } ?: actionControls.firstOrNull()
    val bottomDownRequester =
        if (skipPromptAvailable) skipButtonFocusRequester else FocusRequester.Cancel

    fun bottomModifier(control: PlayerControl): Modifier =
        controlModifier(
            control = control,
            left = requester(previousPlayerControl(control, bottomControls)),
            right = requester(nextPlayerControl(control, bottomControls)),
            up = requester(upAction),
            down = bottomDownRequester,
        )

    val seekBarModifier =
        controlModifier(
            control = PlayerControl.SeekBar,
            left = FocusRequester.Cancel,
            right = FocusRequester.Cancel,
            up = requester(upAction),
            down = bottomDownRequester,
        )

    val playPauseFocusRequester = controlFocusRequesters.getValue(PlayerControl.PlayPause)

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
                playPauseFocusRequester = playPauseFocusRequester,
                state = state,
                isPlaying = isPlaying,
                onPlayPauseToggle = onPlayPauseToggle,
                playlistNavigation = playlistNavigation,
                previousEpisodeModifier = bottomModifier(PlayerControl.PreviousEpisode),
                playPauseModifier = bottomModifier(PlayerControl.PlayPause),
                nextEpisodeModifier = bottomModifier(PlayerControl.NextEpisode),
                seekBarModifier = seekBarModifier,
                onPreviousEpisode = onPreviousEpisode,
                onNextEpisode = onNextEpisode,
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
                VideoPlayerMediaButton(
                    icon = painterResource(id = R.drawable.ic_info),
                    state = state,
                    contentDescription = stringResource(id = R.string.view_details),
                    modifier = actionModifier(PlayerControl.Details),
                    enabled = detailsTarget != null,
                    onClick = { detailsTarget?.let(onViewDetails) },
                )
                if (restartAvailable) {
                    VideoPlayerMediaButton(
                        icon = painterResource(id = R.drawable.ic_rotate_ccw),
                        state = state,
                        contentDescription = stringResource(id = R.string.restart_current_item),
                        modifier = actionModifier(PlayerControl.Restart),
                        onClick = {
                            playPauseFocusRequester.requestFocus()
                            onRestart()
                        },
                    )
                }
                if (chapterNavigation.hasMeaningfulChapters) {
                    VideoPlayerMediaButton(
                        icon = painterResource(id = R.drawable.ic_skip_back),
                        state = state,
                        contentDescription = stringResource(id = R.string.previous_chapter),
                        modifier = actionModifier(PlayerControl.PreviousChapter),
                        enabled = chapterNavigation.previousChapter != null,
                        onClick = onPreviousChapter,
                    )
                    VideoPlayerMediaButton(
                        icon = painterResource(id = R.drawable.ic_skip_forward),
                        state = state,
                        contentDescription = stringResource(id = R.string.next_chapter),
                        modifier = actionModifier(PlayerControl.NextChapter),
                        enabled = chapterNavigation.nextChapter != null,
                        onClick = onNextChapter,
                    )
                }
                VideoPlayerMediaButton(
                    icon = painterResource(id = R.drawable.ic_speaker),
                    state = state,
                    contentDescription = stringResource(id = R.string.audio),
                    modifier = actionModifier(PlayerControl.Audio),
                    onClick = onSelectAudio,
                )
                VideoPlayerMediaButton(
                    icon = painterResource(id = R.drawable.ic_closed_caption),
                    state = state,
                    contentDescription = stringResource(id = R.string.subtitle),
                    modifier = actionModifier(PlayerControl.Subtitles),
                    onClick = onSelectSubtitles,
                )
                hardwareDecodingActive?.let { active ->
                    val enableHardwareDecoding = !active
                    VideoPlayerMediaTextButton(
                        label = stringResource(if (active) R.string.hw_decoder else R.string.sw_decoder),
                        state = state,
                        modifier = actionModifier(PlayerControl.HardwareDecoder),
                        contentDescription =
                            stringResource(
                                if (enableHardwareDecoding) {
                                    R.string.switch_to_hardware_decoding
                                } else {
                                    R.string.switch_to_software_decoding
                                }
                            ),
                        onClick = {
                            onSetHardwareDecodingEnabled(enableHardwareDecoding)
                        },
                    )
                }
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
