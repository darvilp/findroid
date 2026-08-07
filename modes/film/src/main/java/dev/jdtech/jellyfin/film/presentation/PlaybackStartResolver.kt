package dev.jdtech.jellyfin.film.presentation

import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidSeason
import dev.jdtech.jellyfin.models.isAvailableForPlayback
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import timber.log.Timber

internal interface PlaybackStartSource {
    suspend fun getResumeEpisodes(parentId: UUID): List<FindroidEpisode>

    suspend fun getNextUpEpisode(seriesId: UUID): FindroidEpisode?

    suspend fun getEpisodes(seriesId: UUID, seasonId: UUID): List<FindroidEpisode>
}

class PlaybackStartResolver internal constructor(private val source: PlaybackStartSource) {
    @Inject
    constructor(repository: JellyfinRepository) : this(RepositoryPlaybackStartSource(repository))

    suspend fun resolveSeries(
        seriesId: UUID,
        seasons: List<FindroidSeason>,
    ): PlaybackStart? {
        val scopedResumeStart =
            selectResumePlaybackStart(
                getResumeEpisodesOrEmpty(seriesId).filter { episode ->
                    episode.seriesId == seriesId
                }
            )
        if (scopedResumeStart != null) return scopedResumeStart

        val regularSeasons = seasons.filter { season -> season.indexNumber != 0 }.sortedBy {
            season -> season.indexNumber
        }
        val regularEpisodes = loadEpisodesOrNull(seriesId, regularSeasons) ?: return null
        val regularResumeStart = selectResumePlaybackStart(regularEpisodes)
        if (regularResumeStart != null) return regularResumeStart

        val nextUp =
            getNextUpEpisodeOrNull(seriesId)?.takeIf { episode -> episode.seriesId == seriesId }
        if (regularEpisodes.any(FindroidEpisode::isAvailableForPlayback)) {
            return selectPlaybackStartEpisode(
                scopedResumeEpisodes = emptyList(),
                nextUp = nextUp,
                canonicalEpisodes = regularEpisodes,
            )
        }

        val specialEpisodes =
            loadEpisodesOrNull(
                seriesId = seriesId,
                seasons =
                    seasons
                        .filter { season -> season.indexNumber == 0 }
                        .sortedBy { season -> season.indexNumber },
            ) ?: return null
        return selectPlaybackStartEpisode(
            scopedResumeEpisodes = emptyList(),
            nextUp = nextUp,
            canonicalEpisodes = specialEpisodes,
        )
    }

    suspend fun resolveSeason(
        seasonId: UUID,
        episodes: List<FindroidEpisode>,
    ): PlaybackStart? =
        selectPlaybackStartEpisode(
            scopedResumeEpisodes =
                getResumeEpisodesOrEmpty(seasonId).filter { episode ->
                    episode.seasonId == seasonId
                },
            nextUp = null,
            canonicalEpisodes = episodes.filter { episode -> episode.seasonId == seasonId },
        )

    private suspend fun getNextUpEpisodeOrNull(seriesId: UUID): FindroidEpisode? =
        try {
            source.getNextUpEpisode(seriesId)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to retrieve series next up episode")
            null
        }

    private suspend fun loadEpisodesOrNull(
        seriesId: UUID,
        seasons: List<FindroidSeason>,
    ): List<FindroidEpisode>? =
        try {
            seasons.flatMap { season ->
                source
                    .getEpisodes(seriesId, season.id)
                    .filter { episode ->
                        episode.seriesId == seriesId && episode.seasonId == season.id
                    }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to retrieve episodes for playback selection")
            null
        }

    private suspend fun getResumeEpisodesOrEmpty(parentId: UUID): List<FindroidEpisode> =
        try {
            source.getResumeEpisodes(parentId)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to retrieve scoped resume episodes")
            emptyList()
        }
}

private class RepositoryPlaybackStartSource(private val repository: JellyfinRepository) :
    PlaybackStartSource {
    override suspend fun getResumeEpisodes(parentId: UUID): List<FindroidEpisode> =
        repository.getResumeItems(parentId).filterIsInstance<FindroidEpisode>()

    override suspend fun getNextUpEpisode(seriesId: UUID): FindroidEpisode? =
        repository.getNextUp(seriesId).firstOrNull()

    override suspend fun getEpisodes(seriesId: UUID, seasonId: UUID): List<FindroidEpisode> =
        repository.getEpisodes(seriesId = seriesId, seasonId = seasonId)
}
