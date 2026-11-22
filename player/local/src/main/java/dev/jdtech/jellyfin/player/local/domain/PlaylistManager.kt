package dev.jdtech.jellyfin.player.local.domain

import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import dev.jdtech.jellyfin.models.FindroidChapter
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMediaStream
import dev.jdtech.jellyfin.models.FindroidSourceType
import dev.jdtech.jellyfin.models.FindroidSources
import dev.jdtech.jellyfin.models.InitialTrackSelection
import dev.jdtech.jellyfin.models.PlaybackTrackReport
import dev.jdtech.jellyfin.models.isAvailableForPlayback
import dev.jdtech.jellyfin.player.core.domain.models.ExternalSubtitle
import dev.jdtech.jellyfin.player.core.domain.models.PlayerChapter
import dev.jdtech.jellyfin.player.core.domain.models.PlayerItem
import dev.jdtech.jellyfin.player.core.domain.models.Track
import dev.jdtech.jellyfin.player.core.domain.models.TrickplayInfo
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber

internal data class PreparedPlayerItem(
    val item: PlayerItem,
    val streams: List<FindroidMediaStream>,
)

class PlaylistManager @Inject internal constructor(private val repository: JellyfinRepository) {
    private var items: List<FindroidItem> = emptyList()
    private val playerItems: MutableList<PlayerItem> = mutableListOf()
    private val mediaStreamsByItem: MutableMap<UUID, List<FindroidMediaStream>> = mutableMapOf()
    private var queueAudioFingerprint: QueueTrackFingerprint? = null
    private var queueSubtitleMemory: QueueSubtitleMemory? = null
    private var queueTrackSelectionVersion: Long = 0

    suspend fun getInitialItem(
        itemId: UUID,
        itemKind: BaseItemKind,
        mediaSourceIndex: Int? = null,
        startFromBeginning: Boolean = false,
        initialTrackSelection: InitialTrackSelection? = null,
    ): PlayerItem? {
        Timber.d("Retrieving initial player item")
        playerItems.clear()
        mediaStreamsByItem.clear()
        queueAudioFingerprint = null
        queueSubtitleMemory = null
        queueTrackSelectionVersion = 0

        val initialItem =
            when (itemKind) {
                BaseItemKind.MOVIE -> {
                    val movie = repository.getMovie(itemId)

                    items = listOf(movie)
                    movie
                }
                BaseItemKind.SERIES -> {
                    val nextUpEpisode = repository.getNextUp(itemId).firstOrNull()

                    val season =
                        if (nextUpEpisode != null) {
                            repository.getSeason(nextUpEpisode.seasonId)
                        } else {
                            val seasons = repository.getSeasons(itemId)
                            if (seasons.isEmpty()) {
                                return null
                            }
                            seasons.first()
                        }

                    val episodes =
                        repository
                            .getEpisodes(
                                seriesId = itemId,
                                seasonId = season.id,
                                fields = listOf(ItemFields.CHAPTERS, ItemFields.TRICKPLAY),
                            )
                            .let(::availablePlaylistEpisodes)

                    if (episodes.isEmpty()) {
                        return null
                    }

                    val episode = nextUpEpisode ?: episodes.first()

                    items = episodes
                    episode
                }
                BaseItemKind.SEASON -> {
                    val season = repository.getSeason(itemId)
                    val episodes =
                        repository
                            .getEpisodes(
                                seriesId = season.seriesId,
                                seasonId = season.id,
                                fields = listOf(ItemFields.CHAPTERS, ItemFields.TRICKPLAY),
                            )
                            .let(::availablePlaylistEpisodes)

                    if (episodes.isEmpty()) {
                        return null
                    }

                    val episode = episodes.first()

                    items = episodes
                    episode
                }
                BaseItemKind.EPISODE -> {
                    val episode = repository.getEpisode(itemId)

                    val episodes =
                        repository
                            .getEpisodes(
                                seriesId = episode.seriesId,
                                seasonId = episode.seasonId,
                                fields = listOf(ItemFields.CHAPTERS, ItemFields.TRICKPLAY),
                            )
                            .let(::availablePlaylistEpisodes)

                    items = episodes
                    episode
                }
                else -> null
            }

        if (initialItem == null) {
            return null
        }

        val playbackPosition =
            if (!startFromBeginning) initialItem.playbackPositionTicks.div(10000) else 0
        val prepared =
            initialItem.toPlayerItem(
                mediaSourceIndex = mediaSourceIndex,
                playbackPosition = playbackPosition,
                requestedTrackSelection = initialTrackSelection,
            )
        registerPlayerItem(prepared)
        return prepared.item
    }

    internal suspend fun loadAdjacentItem(
        anchorId: UUID,
        direction: PlaylistNavigationDirection,
    ): PreparedPlayerItem? {
        val anchorIndex = items.indexOfFirst { it.id == anchorId }
        if (items.getOrNull(anchorIndex) !is FindroidEpisode) return null
        val offset = if (direction == PlaylistNavigationDirection.Previous) -1 else 1
        val neighbor = items.getOrNull(anchorIndex + offset) ?: return null
        if (playerItems.any { it.itemId == neighbor.id }) return null
        return prepareItem(neighbor)
    }

    internal fun registerPlayerItem(prepared: PreparedPlayerItem) {
        val itemId = prepared.item.itemId
        mediaStreamsByItem[itemId] = prepared.streams
        playerItems.removeAll { it.itemId == itemId }
        playerItems.add(prepared.item)
    }

    fun getDetailsTarget(itemId: UUID): PlaybackDetailsTarget? =
        PlaybackDetailsTargetResolver.resolve(itemId = itemId, items = items)

    fun getMediaStreams(itemId: UUID): List<FindroidMediaStream> =
        mediaStreamsByItem[itemId].orEmpty()

    fun getPlaybackTrackReport(
        itemId: UUID,
        audioTracks: List<Track> = emptyList(),
        subtitleTracks: List<Track> = emptyList(),
    ): PlaybackTrackReport? {
        val item = playerItems.firstOrNull { it.itemId == itemId } ?: return null
        val streams = getMediaStreams(itemId)
        val audioIndex =
            audioTracks.firstOrNull(Track::selected)?.let {
                RuntimeTrackResolver.resolveSourceStream(it, streams, audioTracks)?.index
            } ?: item.defaultAudioStreamIndex
        val selectedSubtitleIndex =
            subtitleTracks.firstOrNull(Track::selected)?.let {
                RuntimeTrackResolver.resolveSourceStream(it, streams, subtitleTracks)?.index
            }
        val subtitleIndex =
            when {
                selectedSubtitleIndex != null -> selectedSubtitleIndex
                subtitleTracks.isNotEmpty() -> InitialTrackSelection.SUBTITLE_OFF
                else -> item.defaultSubtitleStreamIndex
            }
        return PlaybackTrackReport(item.mediaSourceId, audioIndex, subtitleIndex)
    }

    internal suspend fun rebuildPlayerItem(itemId: UUID): PreparedPlayerItem? =
        items.firstOrNull { it.id == itemId }?.let { prepareItem(it) }

    private suspend fun prepareItem(item: FindroidItem): PreparedPlayerItem? =
        try {
            item.toPlayerItem(
                mediaSourceIndex = null,
                playbackPosition = 0L,
                requestedTrackSelection = null,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            Timber.e(error, "Failed to load queued player item %s", item.id)
            null
        }

    fun rememberRuntimeTrackSelection(
        itemId: UUID,
        trackType: Int,
        selectedTrack: Track?,
        runtimeTracks: List<Track>,
    ) {
        if (trackType == C.TRACK_TYPE_TEXT && selectedTrack == null) {
            queueSubtitleMemory = QueueSubtitleMemory.Off
            queueTrackSelectionVersion++
            return
        }
        if (trackType == C.TRACK_TYPE_AUDIO && selectedTrack == null) {
            queueAudioFingerprint = null
            queueTrackSelectionVersion++
            return
        }
        selectedTrack ?: return
        val streams = getMediaStreams(itemId)
        val stream = RuntimeTrackResolver.resolveSourceStream(selectedTrack, streams, runtimeTracks)
        if (stream == null) {
            when (trackType) {
                C.TRACK_TYPE_AUDIO -> queueAudioFingerprint = null
                C.TRACK_TYPE_TEXT -> queueSubtitleMemory = null
            }
            queueTrackSelectionVersion++
            Timber.d(
                "Unable to remember runtime track item=%s source=%s type=%s group=%s track=%s",
                itemId,
                playerItems.firstOrNull { it.itemId == itemId }?.mediaSourceId,
                trackType,
                selectedTrack.groupIndex,
                selectedTrack.trackIndex,
            )
            return
        }
        val fingerprint = QueueTrackFingerprint.fromStream(stream, streams) ?: return
        when (trackType) {
            C.TRACK_TYPE_AUDIO -> {
                queueAudioFingerprint = fingerprint
                queueTrackSelectionVersion++
            }
            C.TRACK_TYPE_TEXT -> {
                queueSubtitleMemory = QueueSubtitleMemory.Selected(fingerprint)
                queueTrackSelectionVersion++
            }
        }
    }

    private suspend fun FindroidItem.toPlayerItem(
        mediaSourceIndex: Int?,
        playbackPosition: Long,
        requestedTrackSelection: InitialTrackSelection?,
    ): PreparedPlayerItem {
        Timber.d("Converting FindroidItem ${this.id} to PlayerItem")
        val selectionVersion = queueTrackSelectionVersion

        val queueSelection =
            if (requestedTrackSelection == null && hasQueueTrackMemory()) {
                val discoverySources = repository.getMediaSources(id, includePath = false)
                resolveQueueSelection(id, discoverySources)
            } else {
                null
            }
        val candidateSelection = requestedTrackSelection ?: queueSelection
        val mediaSources =
            repository.getMediaSources(
                itemId = id,
                includePath = true,
                initialTrackSelection = candidateSelection,
            )
        val resolved =
            if (mediaSourceIndex != null && candidateSelection == null) {
                ResolvedPlaybackSource(mediaSources[mediaSourceIndex], null)
            } else {
                PlaybackSourceResolver.resolve(mediaSources, candidateSelection)
            }
        val mediaSource = resolved.source
        if (requestedTrackSelection == null && selectionVersion != queueTrackSelectionVersion) {
            return toPlayerItem(mediaSourceIndex, playbackPosition, requestedTrackSelection = null)
        }
        if (candidateSelection != null && resolved.initialTrackSelection == null) {
            Timber.d(
                "Discarding invalid track selection item=%s source=%s requestedSource=%s",
                id,
                mediaSource.id,
                candidateSelection.mediaSourceId,
            )
        }
        mediaSource.mediaStreams
            .filter {
                it.isExternal && it.type == MediaStreamType.SUBTITLE && it.path.isNullOrBlank()
            }
            .forEach {
                Timber.d(
                    "Ignoring external subtitle without path item=%s source=%s stream=%s",
                    id,
                    mediaSource.id,
                    it.index,
                )
            }
        val externalSubtitles =
            mediaSource.mediaStreams
                .filter { mediaStream ->
                    mediaStream.isExternal &&
                        mediaStream.type == MediaStreamType.SUBTITLE &&
                        !mediaStream.path.isNullOrBlank()
                }
                .map { mediaStream ->
                    ExternalSubtitle(
                        mediaStream.title,
                        mediaStream.language,
                        mediaStream.path!!.toUri(),
                        when (mediaStream.codec) {
                            "subrip" -> MimeTypes.APPLICATION_SUBRIP
                            "webvtt" -> MimeTypes.APPLICATION_SUBRIP
                            "ass" -> MimeTypes.TEXT_SSA
                            else -> MimeTypes.TEXT_UNKNOWN
                        },
                        mediaStream.index,
                    )
                }
        val trickplayInfo =
            when (this) {
                is FindroidSources -> {
                    this.trickplayInfo?.get(mediaSource.id)?.let {
                        TrickplayInfo(
                            width = it.width,
                            height = it.height,
                            tileWidth = it.tileWidth,
                            tileHeight = it.tileHeight,
                            thumbnailCount = it.thumbnailCount,
                            interval = it.interval,
                            bandwidth = it.bandwidth,
                        )
                    }
                }
                else -> null
            }
        val playerItem =
            PlayerItem(
                name = name,
                itemId = id,
                mediaSourceId = mediaSource.id,
                mediaSourceUri = mediaSource.path,
                playbackPosition = playbackPosition,
                parentIndexNumber = if (this is FindroidEpisode) parentIndexNumber else null,
                indexNumber = if (this is FindroidEpisode) indexNumber else null,
                indexNumberEnd = if (this is FindroidEpisode) indexNumberEnd else null,
                externalSubtitles = externalSubtitles,
                chapters = chapters.toPlayerChapters(),
                trickplayInfo = trickplayInfo,
                initialAudioStreamIndex = resolved.initialTrackSelection?.audioStreamIndex,
                initialSubtitleStreamIndex = resolved.initialTrackSelection?.subtitleStreamIndex,
                defaultAudioStreamIndex = mediaSource.defaultAudioStreamIndex,
                defaultSubtitleStreamIndex = mediaSource.defaultSubtitleStreamIndex,
            )
        return PreparedPlayerItem(playerItem, mediaSource.mediaStreams)
    }

    private fun hasQueueTrackMemory(): Boolean =
        queueAudioFingerprint != null || queueSubtitleMemory != null

    private fun resolveQueueSelection(
        itemId: UUID,
        sources: List<dev.jdtech.jellyfin.models.FindroidSource>,
    ): InitialTrackSelection? {
        val source = sources.firstOrNull { it.type == FindroidSourceType.REMOTE } ?: return null
        val audioIndex = queueAudioFingerprint?.let {
            QueueTrackSelectionResolver.resolve(it, source.mediaStreams).also { resolvedIndex ->
                if (resolvedIndex == null) {
                    Timber.d(
                        "No queue audio match item=%s source=%s",
                        itemId,
                        source.id,
                    )
                }
            }
        }
        val subtitleIndex =
            when (val memory = queueSubtitleMemory) {
                QueueSubtitleMemory.Off -> InitialTrackSelection.SUBTITLE_OFF
                is QueueSubtitleMemory.Selected ->
                    QueueTrackSelectionResolver.resolve(memory.fingerprint, source.mediaStreams)
                        .also { resolvedIndex ->
                            if (resolvedIndex == null) {
                                Timber.d(
                                    "No queue subtitle match item=%s source=%s",
                                    itemId,
                                    source.id,
                                )
                            }
                        }
                null -> null
            }
        if (audioIndex == null && subtitleIndex == null) return null
        return InitialTrackSelection(
            mediaSourceId = source.id,
            audioStreamIndex = audioIndex,
            subtitleStreamIndex = subtitleIndex,
        )
    }

    private fun List<FindroidChapter>.toPlayerChapters(): List<PlayerChapter> {
        return this.map { chapter ->
            PlayerChapter(startPosition = chapter.startPosition, name = chapter.name)
        }
    }
}

private sealed interface QueueSubtitleMemory {
    data object Off : QueueSubtitleMemory

    data class Selected(val fingerprint: QueueTrackFingerprint) : QueueSubtitleMemory
}

internal fun availablePlaylistEpisodes(
    episodes: List<FindroidEpisode>,
): List<FindroidEpisode> = episodes.filter(FindroidEpisode::isAvailableForPlayback)
