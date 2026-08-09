package dev.jdtech.jellyfin.player.local.presentation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.FindroidSegment
import dev.jdtech.jellyfin.models.FindroidSegmentType
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.core.domain.models.PlayerItem
import dev.jdtech.jellyfin.player.core.domain.models.Track
import dev.jdtech.jellyfin.player.core.domain.models.Trickplay
import dev.jdtech.jellyfin.player.core.domain.models.findTrack
import dev.jdtech.jellyfin.player.core.domain.models.withSelectedTrack
import dev.jdtech.jellyfin.player.local.R
import dev.jdtech.jellyfin.player.local.domain.ChapterNavigationController
import dev.jdtech.jellyfin.player.local.domain.ChapterNavigationDirection
import dev.jdtech.jellyfin.player.local.domain.ChapterNavigationState
import dev.jdtech.jellyfin.player.local.domain.ChapterSeekTarget
import dev.jdtech.jellyfin.player.local.domain.MediaSegmentAutoSkipMode
import dev.jdtech.jellyfin.player.local.domain.MediaSegmentPlayback
import dev.jdtech.jellyfin.player.local.domain.MediaSegmentPlaybackDecision
import dev.jdtech.jellyfin.player.local.domain.MediaSegmentPlaybackPreferences
import dev.jdtech.jellyfin.player.local.domain.PlaylistManager
import dev.jdtech.jellyfin.player.local.domain.PlaybackCompletionCoordinator
import dev.jdtech.jellyfin.player.local.domain.PlaybackDetailsTarget
import dev.jdtech.jellyfin.player.local.domain.PlaybackRestartController
import dev.jdtech.jellyfin.player.local.domain.PlaybackRestartTarget
import dev.jdtech.jellyfin.player.local.domain.PlaybackStopReport
import dev.jdtech.jellyfin.player.local.domain.PlaylistNavigationController
import dev.jdtech.jellyfin.player.local.domain.PlaylistNavigationDirection
import dev.jdtech.jellyfin.player.local.domain.PlaylistNavigationState
import dev.jdtech.jellyfin.player.local.domain.PlaylistNavigationTarget
import dev.jdtech.jellyfin.player.local.domain.toTrackOptions
import dev.jdtech.jellyfin.player.local.mpv.MPVPlayer
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.settings.domain.Constants
import java.util.UUID
import javax.inject.Inject
import kotlin.math.ceil
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber

@HiltViewModel
class PlayerViewModel
@Inject
constructor(
    private val application: Application,
    private val playlistManager: PlaylistManager,
    private val repository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel(), Player.Listener {
    val player: Player

    private val _uiState =
        MutableStateFlow(
            UiState(
                currentItemTitle = "",
                currentDetailsTarget = null,
                currentSegment = null,
                currentSkipButtonStringRes = R.string.player_controls_skip_intro,
                currentTrickplay = null,
                currentChapters = emptyList(),
                playlistNavigation = PlaylistNavigationState(false, false),
                audioTracks = emptyList(),
                subtitleTracks = emptyList(),
                fileLoaded = false,
            )
        )
    val uiState = _uiState.asStateFlow()

    private val eventsChannel = Channel<PlayerEvents>()
    val eventsChannelFlow = eventsChannel.receiveAsFlow()

    data class UiState(
        val currentItemTitle: String,
        val currentDetailsTarget: PlaybackDetailsTarget?,
        val currentSegment: FindroidSegment?,
        val currentSkipButtonStringRes: Int,
        val currentTrickplay: Trickplay?,
        val currentChapters: List<PlayerChapter>,
        val playlistNavigation: PlaylistNavigationState,
        val audioTracks: List<Track>,
        val subtitleTracks: List<Track>,
        val fileLoaded: Boolean,
    )

    private var items: MutableList<PlayerItem> = mutableListOf()

    private val trackSelector = DefaultTrackSelector(application)
    var playWhenReady = true
    private var currentMediaItemIndex = savedStateHandle["mediaItemIndex"] ?: 0
    private var playbackPosition: Long = savedStateHandle["position"] ?: 0
    private val mediaSegmentPlayback = MediaSegmentPlayback()
    private val playbackCompletionCoordinator = PlaybackCompletionCoordinator()
    private val playbackRestartController = PlaybackRestartController()
    private val chapterNavigationController = ChapterNavigationController()
    private val playlistNavigationController = PlaylistNavigationController()

    // Segments preferences
    var segmentsSkipButton: Boolean = false
    private var segmentsSkipButtonTypes: Set<FindroidSegmentType> = emptySet()
    var segmentsSkipButtonDuration: Long = 0L
    var segmentsAutoSkip: Boolean = false
    private var segmentsAutoSkipTypes: Set<FindroidSegmentType> = emptySet()
    private var segmentsAutoSkipMode: MediaSegmentAutoSkipMode? = null
    val chapterMarkersEnabled: Boolean =
        appPreferences.getValue(appPreferences.playerChapterMarkers)

    var playbackSpeed: Float = 1f

    var isInPictureInPictureMode: Boolean = false

    init {
        segmentsSkipButton = appPreferences.getValue(appPreferences.playerMediaSegmentsSkipButton)
        segmentsSkipButtonTypes =
            appPreferences
                .getValue(appPreferences.playerMediaSegmentsSkipButtonType)
                .toFindroidSegmentTypes()
        segmentsSkipButtonDuration =
            appPreferences.getValue(appPreferences.playerMediaSegmentsSkipButtonDuration)
        segmentsAutoSkip = appPreferences.getValue(appPreferences.playerMediaSegmentsAutoSkip)
        segmentsAutoSkipTypes =
            appPreferences
                .getValue(appPreferences.playerMediaSegmentsAutoSkipType)
                .toFindroidSegmentTypes()
        segmentsAutoSkipMode =
            when (appPreferences.getValue(appPreferences.playerMediaSegmentsAutoSkipMode)) {
                Constants.PlayerMediaSegmentsAutoSkip.ALWAYS -> MediaSegmentAutoSkipMode.ALWAYS
                Constants.PlayerMediaSegmentsAutoSkip.PIP ->
                    MediaSegmentAutoSkipMode.PICTURE_IN_PICTURE
                else -> null
            }

        val audioAttributes =
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build()

        trackSelector.setParameters(
            trackSelector
                .buildUponParameters()
                .setTunnelingEnabled(true)
                .setPreferredAudioLanguage(
                    appPreferences.getValue(appPreferences.preferredAudioLanguage)
                )
                .setPreferredTextLanguage(
                    appPreferences.getValue(appPreferences.preferredSubtitleLanguage)
                )
        )


        val playerBackend = appPreferences.getValue(appPreferences.playerBackend)
        player = when (playerBackend) {
            "exoplayer" -> {
                val renderersFactory =
                    DefaultRenderersFactory(application)
                        .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                ExoPlayer.Builder(application, renderersFactory)
                    .setAudioAttributes(audioAttributes, true)
                    .setTrackSelector(trackSelector)
                    .setSeekBackIncrementMs(
                        appPreferences.getValue(appPreferences.playerSeekBackInc)
                    )
                    .setSeekForwardIncrementMs(
                        appPreferences.getValue(appPreferences.playerSeekForwardInc)
                    )
                    .setPauseAtEndOfMediaItems(true)
                    .build()
            }
            "mpv" -> {
                MPVPlayer.Builder(application)
                    .setAudioAttributes(audioAttributes, true)
                    .setTrackSelectionParameters(trackSelector.parameters)
                    .setSeekBackIncrementMs(
                        appPreferences.getValue(appPreferences.playerSeekBackInc)
                    )
                    .setSeekForwardIncrementMs(
                        appPreferences.getValue(appPreferences.playerSeekForwardInc)
                    )
                    .setPauseAtEndOfMediaItems(true)
                    .setVideoOutput(appPreferences.getValue(appPreferences.playerMpvVo))
                    .setAudioOutput(appPreferences.getValue(appPreferences.playerMpvAo))
                    .setHwDec(appPreferences.getValue(appPreferences.playerMpvHwdec))
                    .build()
            }

            else -> throw RuntimeException("$playerBackend is not a valid player backend")
        }
    }

    private val playlistNavigationTarget =
        object : PlaylistNavigationTarget {
            override fun hasPreviousMediaItem(): Boolean = player.hasPreviousMediaItem()

            override fun hasNextMediaItem(): Boolean = player.hasNextMediaItem()

            override fun isPreviousMediaItemCommandAvailable(): Boolean =
                player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)

            override fun isNextMediaItemCommandAvailable(): Boolean =
                player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)

            override fun seekToPreviousMediaItem() = player.seekToPreviousMediaItem()

            override fun seekToNextMediaItem() = player.seekToNextMediaItem()

            override fun playWhenReady(): Boolean = player.playWhenReady

            override fun play() = player.play()

            override fun pause() = player.pause()
        }

    fun initializePlayer(itemId: UUID, itemKind: String, startFromBeginning: Boolean) {
        player.addListener(this)
        updateTrackOptions(player.currentTracks)

        viewModelScope.launch {
            val startItem =
                try {
                    playlistManager.getInitialItem(
                        itemId = itemId,
                        itemKind = BaseItemKind.fromName(itemKind),
                        mediaSourceIndex = null,
                        startFromBeginning = startFromBeginning,
                    )
                } catch (e: Exception) {
                    Timber.e(e)
                    Toast.makeText(application, e.localizedMessage, Toast.LENGTH_LONG).show()
                    null
                }

            if (startItem == null) {
                Timber.e("No start item, stopping player initialization")
                return@launch
            }

            items = listOfNotNull(startItem).toMutableList()
            currentMediaItemIndex = items.indexOf(startItem)

            val mediaItems = mutableListOf<MediaItem>()
            try {
                for (item in items) {
                    mediaItems.add(item.toMediaItem())
                }
            } catch (e: Exception) {
                Timber.e(e)
            }

            val startPosition =
                if (playbackPosition == 0L) {
                    items.getOrNull(currentMediaItemIndex)?.playbackPosition ?: C.TIME_UNSET
                } else {
                    playbackPosition
                }

            beginPlaybackPass(itemId = startItem.itemId)
            player.setMediaItems(mediaItems, 0, startPosition)
            player.prepare()
            player.play()
        }
    }

    private fun PlayerItem.toMediaItem(): MediaItem {
        val streamUrl = mediaSourceUri
        val mediaSubtitles =
            externalSubtitles.map { externalSubtitle ->
                MediaItem.SubtitleConfiguration.Builder(externalSubtitle.uri)
                    .setLabel(
                        externalSubtitle.title.ifBlank { application.getString(R.string.external) }
                    )
                    .setMimeType(externalSubtitle.mimeType)
                    .setLanguage(externalSubtitle.language)
                    .build()
            }

        Timber.d("Stream url: $streamUrl")
        val mediaItem =
            MediaItem.Builder()
                .setMediaId(itemId.toString())
                .setUri(streamUrl)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(name).build())
                .setSubtitleConfigurations(mediaSubtitles)
                .build()

        return mediaItem
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun releasePlayer() {
        val stopReport =
            playbackCompletionCoordinator.releaseStopReport(
                itemId = player.currentMediaItem?.mediaId?.toUuidOrNull(),
                positionMs = player.currentPosition,
                durationMs = player.duration,
            )
        GlobalScope.launch {
            delay(200L)
            try {
                if (stopReport != null) {
                    Timber.d("Sending playback stop")
                    postPlaybackStop(stopReport)
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }

        _uiState.update { it.copy(currentTrickplay = null) }
        playWhenReady = false
        playbackPosition = 0L
        currentMediaItemIndex = 0
        player.removeListener(this)
        player.release()
    }

    fun updatePlaybackProgress() {
        Timber.d("Updating playback progress")
        viewModelScope.launch(Dispatchers.Main) {
            savedStateHandle["position"] = player.currentPosition
            if (player.currentMediaItem != null && player.currentMediaItem!!.mediaId.isNotEmpty()) {
                val itemId = UUID.fromString(player.currentMediaItem!!.mediaId)
                try {
                    repository.postPlaybackProgress(
                        itemId,
                        player.currentPosition.times(10000),
                        !player.isPlaying,
                    )
                } catch (e: Exception) {
                    Timber.e(e)
                }
            }
        }
    }

    fun updateCurrentSegment() {
        Timber.d("Updating current segment")
        viewModelScope.launch(Dispatchers.Main) {
            when (
                val decision =
                    mediaSegmentPlayback.decisionAt(
                        positionMs = player.currentPosition,
                        isInPictureInPictureMode = isInPictureInPictureMode,
                        preferences = mediaSegmentPlaybackPreferences(),
                    )
            ) {
                is MediaSegmentPlaybackDecision.AutoSkip -> {
                    Timber.tag("SegmentInfo").d("autoSkipSegment: %s", decision.segment)
                    skipSegment(decision.segment)
                }
                is MediaSegmentPlaybackDecision.ManualPrompt -> {
                    Timber.tag("SegmentInfo").d("promptSegment: %s", decision.segment)
                    _uiState.update {
                        it.copy(
                            currentSegment = decision.segment,
                            currentSkipButtonStringRes =
                                getSkipButtonTextStringId(decision.segment),
                        )
                    }
                }
                MediaSegmentPlaybackDecision.None -> {
                    if (_uiState.value.currentSegment != null) {
                        _uiState.update { it.copy(currentSegment = null) }
                    }
                }
            }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        Timber.d("Playing MediaItem: ${mediaItem?.mediaId}")
        savedStateHandle["mediaItemIndex"] = player.currentMediaItemIndex
        chapterNavigationController.reset()
        val transitionedItemId = mediaItem?.mediaId?.toUuidOrNull()
        _uiState.update {
            it.copy(
                currentSegment = null,
                currentChapters = emptyList(),
                currentDetailsTarget =
                    transitionedItemId?.let(playlistManager::getDetailsTarget),
                playlistNavigation = playlistNavigationController.state(playlistNavigationTarget),
            )
        }
        val transitionedMediaId = mediaItem?.mediaId ?: return
        val itemId = transitionedItemId ?: return
        beginPlaybackPass(itemId = itemId)
        viewModelScope.launch {
            try {
                items
                    .first { it.itemId.toString() == transitionedMediaId }
                    .let { item ->
                        val itemTitle =
                            if (item.parentIndexNumber != null && item.indexNumber != null) {
                                if (item.indexNumberEnd == null) {
                                    "S${item.parentIndexNumber}:E${item.indexNumber} - ${item.name}"
                                } else {
                                    "S${item.parentIndexNumber}:E${item.indexNumber}-${item.indexNumberEnd} - ${item.name}"
                                }
                            } else {
                                item.name
                            }
                        if (player.currentMediaItem?.mediaId != transitionedMediaId) return@launch
                        _uiState.update {
                            it.copy(
                                currentItemTitle = itemTitle,
                                currentSegment = null,
                                currentChapters = item.chapters,
                                fileLoaded = false,
                            )
                        }

                        repository.postPlaybackStart(item.itemId)

                        if (segmentsSkipButton || segmentsAutoSkip) {
                            getSegments(item.itemId)
                        }

                        if (appPreferences.getValue(appPreferences.playerTrickplay)) {
                            getTrickplay(item)
                        }

                        playlistManager.setCurrentMediaItemIndex(item.itemId)

                        val previousItem = playlistManager.getPreviousPlayerItem()
                        if (previousItem != null) {
                            items.add(player.currentMediaItemIndex, previousItem)
                            player.addMediaItem(
                                player.currentMediaItemIndex,
                                previousItem.toMediaItem(),
                            )
                        }

                        val nextItem = playlistManager.getNextPlayerItem()
                        if (nextItem != null) {
                            items.add(player.currentMediaItemIndex + 1, nextItem)
                            player.addMediaItem(
                                player.currentMediaItemIndex + 1,
                                nextItem.toMediaItem(),
                            )
                        }

                        updatePlaylistNavigationState()

                        Timber.tag("PlayerItems").d(items.map { it.indexNumber }.toString())
                    }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        updatePlaylistNavigationState()
    }

    override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
        updatePlaylistNavigationState()
    }

    private fun updatePlaylistNavigationState() {
        val navigation = playlistNavigationController.state(playlistNavigationTarget)
        _uiState.update { state -> state.copy(playlistNavigation = navigation) }
    }

    private fun navigatePlaylist(direction: PlaylistNavigationDirection): Boolean =
        playlistNavigationController.navigate(direction, playlistNavigationTarget)

    fun goToPreviousEpisode(): Boolean =
        navigatePlaylist(PlaylistNavigationDirection.Previous)

    fun goToNextEpisode(): Boolean = navigatePlaylist(PlaylistNavigationDirection.Next)

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        // Report playback stopped for current item and transition to the next one
        if (
            !playWhenReady &&
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                player.playbackState == ExoPlayer.STATE_READY
        ) {
            viewModelScope.launch {
                val mediaId = player.currentMediaItem?.mediaId
                val position = player.currentPosition
                val duration = player.duration
                try {
                    repository.postPlaybackStop(
                        UUID.fromString(mediaId),
                        position.times(10000),
                        position.div(duration.toFloat()).times(100).toInt(),
                    )
                } catch (e: Exception) {
                    Timber.e(e)
                }
                player.seekToNextMediaItem()
                player.play()
            }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onPositionDiscontinuity(reason: Int) {
        chapterNavigationController.onPositionDiscontinuity(
            isSeek = reason == Player.DISCONTINUITY_REASON_SEEK
        )
    }

    override fun onPlaybackStateChanged(state: Int) {
        var stateString = "UNKNOWN_STATE             -"
        when (state) {
            ExoPlayer.STATE_IDLE -> {
                stateString = "ExoPlayer.STATE_IDLE      -"
            }
            ExoPlayer.STATE_BUFFERING -> {
                stateString = "ExoPlayer.STATE_BUFFERING -"
            }
            ExoPlayer.STATE_READY -> {
                stateString = "ExoPlayer.STATE_READY     -"
                _uiState.update { it.copy(fileLoaded = true) }
            }
            ExoPlayer.STATE_ENDED -> {
                stateString = "ExoPlayer.STATE_ENDED     -"
                val completion =
                    playbackCompletionCoordinator.claimTerminalCompletion(
                        itemId = player.currentMediaItem?.mediaId?.toUuidOrNull(),
                        positionMs = player.currentPosition,
                        durationMs = player.duration,
                    )
                if (completion != null) {
                    viewModelScope.launch {
                        completion.stopReport?.let { stopReport ->
                            try {
                                postPlaybackStop(stopReport)
                                playbackCompletionCoordinator.confirmReported(stopReport)
                            } catch (e: Exception) {
                                Timber.e(e)
                            }
                        }
                        eventsChannel.send(PlayerEvents.NavigateBack)
                    }
                }
            }
        }
        Timber.d("Changed player state to $stateString")
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("Clearing Player ViewModel")
        releasePlayer()
    }

    fun currentTrackOptions(trackType: @C.TrackType Int): List<Track> =
        player.currentTracks.toTrackOptions(trackType)

    /**
     * Select an exact Media3 group/format pair. Passing two null indices disables the type.
     * Mismatched, unsupported, or stale identities are rejected without changing the player.
     */
    fun switchToTrack(
        trackType: @C.TrackType Int,
        groupIndex: Int?,
        trackIndex: Int?,
    ): Boolean {
        if (groupIndex == null && trackIndex == null) {
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(trackType)
                    .setTrackTypeDisabled(trackType, true)
                    .build()
            updateSelectedTrack(trackType, groupIndex = null, trackIndex = null)
            return true
        }

        if (groupIndex == null || trackIndex == null) return false
        val requestedTrack =
            currentTrackOptions(trackType).findTrack(trackType, groupIndex, trackIndex)
                ?: return false
        if (!requestedTrack.supported) return false
        val group = player.currentTracks.groups.getOrNull(groupIndex) ?: return false
        if (
            group.type != trackType ||
                trackIndex !in 0 until group.mediaTrackGroup.length ||
                !group.isTrackSupported(trackIndex)
        ) {
            return false
        }

        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .setOverrideForType(
                    TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
                )
                .setTrackTypeDisabled(trackType, false)
                .build()
        updateSelectedTrack(trackType, groupIndex, trackIndex)
        return true
    }

    override fun onTracksChanged(tracks: Tracks) {
        updateTrackOptions(tracks)
    }

    private fun updateTrackOptions(tracks: Tracks) {
        _uiState.update {
            it.copy(
                audioTracks = tracks.toTrackOptions(C.TRACK_TYPE_AUDIO),
                subtitleTracks = tracks.toTrackOptions(C.TRACK_TYPE_TEXT),
            )
        }
    }

    private fun updateSelectedTrack(
        trackType: @C.TrackType Int,
        groupIndex: Int?,
        trackIndex: Int?,
    ) {
        _uiState.update { state ->
            when (trackType) {
                C.TRACK_TYPE_AUDIO ->
                    state.copy(
                        audioTracks = state.audioTracks.withSelectedTrack(groupIndex, trackIndex)
                    )
                C.TRACK_TYPE_TEXT ->
                    state.copy(
                        subtitleTracks =
                            state.subtitleTracks.withSelectedTrack(groupIndex, trackIndex)
                    )
                else -> state
            }
        }
    }

    fun selectSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        playbackSpeed = speed
    }

    private suspend fun getSegments(itemId: UUID) {
        try {
            val segments = repository.getSegments(itemId)
            mediaSegmentPlayback.updateSegments(itemId = itemId, segments = segments)
        } catch (e: Exception) {
            mediaSegmentPlayback.updateSegments(itemId = itemId, segments = emptyList())
            Timber.e(e)
        }
    }

    fun beginPlaybackPass() {
        mediaSegmentPlayback.beginPlaybackPass()
        playbackCompletionCoordinator.beginPlaybackPass()
        _uiState.update { it.copy(currentSegment = null) }
    }

    fun isRestartCurrentItemAvailable(currentPositionMs: Long): Boolean =
        playbackRestartController.isAvailable(
            currentPositionMs = currentPositionMs,
            seekBackIncrementMs = player.seekBackIncrement,
        )

    fun restartCurrentItem() {
        playbackRestartController.restart(
            object : PlaybackRestartTarget {
                override fun beginPlaybackPass() = this@PlayerViewModel.beginPlaybackPass()

                override fun seekTo(positionMs: Long) = player.seekTo(positionMs)

                override fun play() = player.play()
            }
        )
    }

    private fun beginPlaybackPass(itemId: UUID) {
        mediaSegmentPlayback.beginPlaybackPass(itemId = itemId)
        playbackCompletionCoordinator.beginPlaybackPass()
        _uiState.update { it.copy(currentSegment = null) }
    }

    private suspend fun postPlaybackStop(stopReport: PlaybackStopReport) {
        repository.postPlaybackStop(
            stopReport.itemId,
            stopReport.positionTicks,
            stopReport.playedPercentage,
        )
    }

    private fun mediaSegmentPlaybackPreferences(): MediaSegmentPlaybackPreferences =
        MediaSegmentPlaybackPreferences(
            autoSkipEnabled = segmentsAutoSkip && segmentsAutoSkipMode != null,
            autoSkipTypes = segmentsAutoSkipTypes,
            autoSkipMode = segmentsAutoSkipMode ?: MediaSegmentAutoSkipMode.ALWAYS,
            manualSkipEnabled = segmentsSkipButton,
            manualSkipTypes = segmentsSkipButtonTypes,
        )

    private suspend fun getTrickplay(item: PlayerItem) {
        val trickplayInfo = item.trickplayInfo ?: return
        Timber.d("Trickplay Resolution: ${trickplayInfo.width}")

        withContext(Dispatchers.Default) {
            val maxIndex =
                ceil(
                        trickplayInfo.thumbnailCount
                            .toDouble()
                            .div(trickplayInfo.tileWidth * trickplayInfo.tileHeight)
                    )
                    .toInt()
            val bitmaps = mutableListOf<Bitmap>()

            for (i in 0..maxIndex) {
                repository.getTrickplayData(item.itemId, trickplayInfo.width, i)?.let { byteArray ->
                    val fullBitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
                    for (offsetY in
                        0..<trickplayInfo.height * trickplayInfo.tileHeight step
                            trickplayInfo.height) {
                        for (offsetX in
                            0..<trickplayInfo.width * trickplayInfo.tileWidth step
                                trickplayInfo.width) {
                            val bitmap =
                                Bitmap.createBitmap(
                                    fullBitmap,
                                    offsetX,
                                    offsetY,
                                    trickplayInfo.width,
                                    trickplayInfo.height,
                                )
                            bitmaps.add(bitmap)
                        }
                    }
                }
            }
            _uiState.update {
                it.copy(currentTrickplay = Trickplay(trickplayInfo.interval, bitmaps))
            }
        }
    }

    fun skipSegment(segment: FindroidSegment) {
        if (shouldSkipToNextEpisode(segment)) {
            player.seekToNextMediaItem()
        } else {
            player.seekTo(segment.endTicks)
        }
        _uiState.update { it.copy(currentSegment = null) }
    }

    // Check if the outro segment's end time is within n milliseconds of the player's total duration
    private fun shouldSkipToNextEpisode(segment: FindroidSegment): Boolean {
        return if (segment.type == FindroidSegmentType.OUTRO && player.hasNextMediaItem()) {
            val segmentEndTimeMillis = segment.endTicks
            val playerDurationMillis = player.duration
            val thresholdMillis =
                playerDurationMillis -
                    appPreferences.getValue(appPreferences.playerMediaSegmentsNextEpisodeThreshold)

            segmentEndTimeMillis > thresholdMillis
        } else {
            false
        }
    }

    private fun getSkipButtonTextStringId(segment: FindroidSegment): Int {
        return when (shouldSkipToNextEpisode(segment)) {
            true -> R.string.player_controls_next_episode
            false ->
                when (segment.type) {
                    FindroidSegmentType.INTRO -> R.string.player_controls_skip_intro
                    FindroidSegmentType.OUTRO -> R.string.player_controls_skip_outro
                    FindroidSegmentType.RECAP -> R.string.player_controls_skip_recap
                    FindroidSegmentType.COMMERCIAL -> R.string.player_controls_skip_commercial
                    FindroidSegmentType.PREVIEW -> R.string.player_controls_skip_preview
                    else -> R.string.player_controls_skip_unknown
                }
        }
    }

    fun chapterNavigationState(
        currentPositionMs: Long = player.currentPosition
    ): ChapterNavigationState =
        chapterNavigationController.state(
            chapters = uiState.value.currentChapters,
            currentPositionMs = currentPositionMs,
            durationMs = player.duration.takeIf { it > 0L },
        )

    private fun seekToChapter(direction: ChapterNavigationDirection): PlayerChapter? =
        chapterNavigationController.seek(
            direction = direction,
            navigation = chapterNavigationState(),
            target = ChapterSeekTarget { positionMs -> player.seekTo(positionMs) },
        )

    /**
     * Seek to the next chapter
     *
     * @return the [PlayerChapter] which has been sought to
     */
    fun seekToNextChapter(): PlayerChapter? {
        return seekToChapter(ChapterNavigationDirection.Next)
    }

    /**
     * Seek to the previous chapter Will seek to start of current chapter if player position is more
     * than 5 seconds past start of chapter
     *
     * @return the [PlayerChapter] which has been sought to
     */
    fun seekToPreviousChapter(): PlayerChapter? {
        return seekToChapter(ChapterNavigationDirection.Previous)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        super.onIsPlayingChanged(isPlaying)
        eventsChannel.trySend(PlayerEvents.IsPlayingChanged(isPlaying))
    }
}

sealed interface PlayerEvents {
    data object NavigateBack : PlayerEvents

    data class IsPlayingChanged(val isPlaying: Boolean) : PlayerEvents
}

private fun Set<String>.toFindroidSegmentTypes(): Set<FindroidSegmentType> =
    mapNotNullTo(mutableSetOf()) { value ->
        FindroidSegmentType.entries.firstOrNull { type -> type.name == value }
    }

private fun String.toUuidOrNull(): UUID? = runCatching(UUID::fromString).getOrNull()
