package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidImages
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistEpisodePolicyTest {
    @Test
    fun `season queue preserves order while excluding unavailable episodes`() {
        val first =
            episode(
                id = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            )
        val missing =
            episode(
                id = UUID.fromString("00000000-0000-0000-0000-000000000002"),
                missing = true,
            )
        val denied =
            episode(
                id = UUID.fromString("00000000-0000-0000-0000-000000000003"),
                canPlay = false,
            )
        val last =
            episode(
                id = UUID.fromString("00000000-0000-0000-0000-000000000004"),
            )

        val available = availablePlaylistEpisodes(listOf(first, missing, denied, last))

        assertEquals(listOf(first.id, last.id), available.map { episode -> episode.id })
    }

    private fun episode(
        id: UUID,
        missing: Boolean = false,
        canPlay: Boolean = true,
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
            canPlay = canPlay,
            canDownload = false,
            runtimeTicks = 0L,
            playbackPositionTicks = 0L,
            premiereDate = null,
            seriesId = UUID.fromString("10000000-0000-0000-0000-000000000000"),
            seriesName = "Series",
            seasonId = UUID.fromString("20000000-0000-0000-0000-000000000000"),
            seasonName = "Season 1",
            communityRating = null,
            people = emptyList(),
            missing = missing,
            images = FindroidImages(),
            chapters = emptyList(),
            trickplayInfo = null,
        )
}
