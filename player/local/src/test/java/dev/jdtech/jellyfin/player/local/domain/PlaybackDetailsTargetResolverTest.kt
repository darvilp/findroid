package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidImages
import dev.jdtech.jellyfin.models.FindroidMovie
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackDetailsTargetResolverTest {
    @Test
    fun `episode details target is its containing season`() {
        val seasonId = UUID.fromString("20000000-0000-0000-0000-000000000000")
        val episode = episode(seasonId = seasonId)

        assertEquals(
            PlaybackDetailsTarget.Season(seasonId),
            PlaybackDetailsTargetResolver.resolve(episode),
        )
    }

    @Test
    fun `movie details target is the movie itself`() {
        val movieId = UUID.fromString("30000000-0000-0000-0000-000000000000")
        val movie = movie(id = movieId)

        assertEquals(
            PlaybackDetailsTarget.Movie(movieId),
            PlaybackDetailsTargetResolver.resolve(movie),
        )
    }

    @Test
    fun `playlist lookup resolves the item that is currently playing`() {
        val firstItemId = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val currentItemId = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val currentSeasonId = UUID.fromString("22000000-0000-0000-0000-000000000000")
        val items =
            listOf(
                episode(
                    id = firstItemId,
                    seasonId = UUID.fromString("21000000-0000-0000-0000-000000000000"),
                ),
                episode(id = currentItemId, seasonId = currentSeasonId),
            )

        assertEquals(
            PlaybackDetailsTarget.Season(currentSeasonId),
            PlaybackDetailsTargetResolver.resolve(currentItemId, items),
        )
    }

    private fun episode(
        seasonId: UUID,
        id: UUID = UUID.fromString("00000000-0000-0000-0000-000000000001"),
    ): FindroidEpisode =
        FindroidEpisode(
            id = id,
            name = "Episode",
            originalTitle = null,
            overview = "",
            indexNumber = 1,
            indexNumberEnd = null,
            parentIndexNumber = 1,
            sources = emptyList(),
            played = false,
            favorite = false,
            canPlay = true,
            canDownload = false,
            runtimeTicks = 0L,
            playbackPositionTicks = 0L,
            premiereDate = null,
            seriesId = UUID.fromString("10000000-0000-0000-0000-000000000000"),
            seriesName = "Series",
            seasonId = seasonId,
            seasonName = "Season 1",
            communityRating = null,
            people = emptyList(),
            images = FindroidImages(),
            chapters = emptyList(),
            trickplayInfo = null,
        )

    private fun movie(id: UUID): FindroidMovie =
        FindroidMovie(
            id = id,
            name = "Movie",
            originalTitle = null,
            overview = "",
            sources = emptyList(),
            played = false,
            favorite = false,
            canPlay = true,
            canDownload = false,
            runtimeTicks = 0L,
            playbackPositionTicks = 0L,
            premiereDate = null,
            people = emptyList(),
            genres = emptyList(),
            communityRating = null,
            officialRating = null,
            status = "Ended",
            productionYear = null,
            endDate = null,
            trailer = null,
            images = FindroidImages(),
            chapters = emptyList(),
            trickplayInfo = null,
        )
}
