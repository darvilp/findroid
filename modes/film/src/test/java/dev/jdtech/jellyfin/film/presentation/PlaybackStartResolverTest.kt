package dev.jdtech.jellyfin.film.presentation

import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.core.presentation.dummy.dummySeason
import dev.jdtech.jellyfin.models.FindroidEpisode
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackStartResolverTest {
    @Test
    fun `season resolution resumes the first scoped resume result`() = runBlocking {
        val seasonId = UUID.fromString("11111111-2222-3333-4444-555555555555")
        val canonical =
            dummyEpisode.copy(
                id = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                seasonId = seasonId,
                played = false,
                playbackPositionTicks = 0L,
            )
        val mostRecentResume =
            canonical.copy(
                id = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                playbackPositionTicks = 900_000_000L,
            )
        val olderResume =
            canonical.copy(
                id = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                playbackPositionTicks = 700_000_000L,
            )
        val source =
            FakePlaybackStartSource(
                resumeEpisodes = listOf(mostRecentResume, olderResume),
            )

        val start =
            PlaybackStartResolver(source)
                .resolveSeason(seasonId = seasonId, episodes = listOf(canonical))

        assertEquals(mostRecentResume.id, start?.episode?.id)
    }

    @Test
    fun `series resolution uses Jellyfin next up when nothing is in progress`() = runBlocking {
        val seriesId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd")
        val nextUp =
            dummyEpisode.copy(
                id = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
                seriesId = seriesId,
                played = false,
                playbackPositionTicks = 0L,
            )
        val source = FakePlaybackStartSource(nextUpEpisode = nextUp)

        val start =
            PlaybackStartResolver(source)
                .resolveSeries(
                    seriesId = seriesId,
                    seasons = listOf(dummySeason.copy(seriesId = seriesId, indexNumber = 1)),
                )

        assertEquals(nextUp.id, start?.episode?.id)
    }

    @Test
    fun `series canonical progress wins over Jellyfin next up`() = runBlocking {
        val seriesId = UUID.fromString("00000000-0000-0000-0000-000000000070")
        val seasonId = UUID.fromString("00000000-0000-0000-0000-000000000071")
        val canonicalProgress =
            dummyEpisode.copy(
                id = UUID.fromString("00000000-0000-0000-0000-000000000072"),
                seriesId = seriesId,
                seasonId = seasonId,
                played = false,
                playbackPositionTicks = 900_000_000L,
            )
        val nextUp =
            dummyEpisode.copy(
                id = UUID.fromString("00000000-0000-0000-0000-000000000073"),
                seriesId = seriesId,
                seasonId = seasonId,
                played = false,
                playbackPositionTicks = 0L,
            )
        val source =
            FakePlaybackStartSource(
                nextUpEpisode = nextUp,
                episodesBySeason = mapOf(seasonId to listOf(canonicalProgress, nextUp)),
            )

        val start =
            PlaybackStartResolver(source)
                .resolveSeries(
                    seriesId = seriesId,
                    seasons =
                        listOf(
                            dummySeason.copy(
                                id = seasonId,
                                seriesId = seriesId,
                                indexNumber = 1,
                            )
                        ),
                )

        assertEquals(canonicalProgress.id, start?.episode?.id)
    }

    @Test
    fun `series fallback skips unavailable gaps and keeps regular seasons ahead of specials`() =
        runBlocking {
            val seriesId = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")
            val specialsId = UUID.fromString("00000000-0000-0000-0000-000000000010")
            val seasonOneId = UUID.fromString("00000000-0000-0000-0000-000000000011")
            val seasonTwoId = UUID.fromString("00000000-0000-0000-0000-000000000012")
            val special =
                dummyEpisode.copy(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000020"),
                    seriesId = seriesId,
                    seasonId = specialsId,
                    played = false,
                )
            val unavailableGap =
                dummyEpisode.copy(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000021"),
                    seriesId = seriesId,
                    seasonId = seasonOneId,
                    missing = true,
                    played = false,
                )
            val nextRegular =
                dummyEpisode.copy(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000022"),
                    seriesId = seriesId,
                    seasonId = seasonTwoId,
                    missing = false,
                    canPlay = true,
                    played = false,
                )
            val source =
                FakePlaybackStartSource(
                    episodesBySeason =
                        mapOf(
                            specialsId to listOf(special),
                            seasonOneId to listOf(unavailableGap),
                            seasonTwoId to listOf(nextRegular),
                        )
                )

            val start =
                PlaybackStartResolver(source)
                    .resolveSeries(
                        seriesId = seriesId,
                        seasons =
                            listOf(
                                dummySeason.copy(
                                    id = specialsId,
                                    seriesId = seriesId,
                                    indexNumber = 0,
                                ),
                                dummySeason.copy(
                                    id = seasonOneId,
                                    seriesId = seriesId,
                                    indexNumber = 1,
                                ),
                                dummySeason.copy(
                                    id = seasonTwoId,
                                    seriesId = seriesId,
                                    indexNumber = 2,
                                ),
                            ),
                    )

            assertEquals(nextRegular.id, start?.episode?.id)
        }

    @Test
    fun `season resolution falls back to canonical progress when scoped resume fails`() =
        runBlocking {
            val seasonId = UUID.fromString("00000000-0000-0000-0000-000000000030")
            val canonicalResume =
                dummyEpisode.copy(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000031"),
                    seasonId = seasonId,
                    played = false,
                    playbackPositionTicks = 800_000_000L,
                )
            val source =
                FakePlaybackStartSource(resumeFailure = IllegalStateException("resume failed"))

            val start =
                PlaybackStartResolver(source)
                    .resolveSeason(seasonId = seasonId, episodes = listOf(canonicalResume))

            assertEquals(canonicalResume.id, start?.episode?.id)
        }

    @Test
    fun `series resolution falls back to canonical episodes when next up fails`() = runBlocking {
        val seriesId = UUID.fromString("00000000-0000-0000-0000-000000000040")
        val seasonId = UUID.fromString("00000000-0000-0000-0000-000000000041")
        val firstUnplayed =
            dummyEpisode.copy(
                id = UUID.fromString("00000000-0000-0000-0000-000000000042"),
                seriesId = seriesId,
                seasonId = seasonId,
                played = false,
                playbackPositionTicks = 0L,
            )
        val source =
            FakePlaybackStartSource(
                episodesBySeason = mapOf(seasonId to listOf(firstUnplayed)),
                nextUpFailure = IllegalStateException("next up failed"),
            )

        val start =
            PlaybackStartResolver(source)
                .resolveSeries(
                    seriesId = seriesId,
                    seasons =
                        listOf(
                            dummySeason.copy(
                                id = seasonId,
                                seriesId = seriesId,
                                indexNumber = 1,
                            )
                        ),
                )

        assertEquals(firstUnplayed.id, start?.episode?.id)
    }

    @Test
    fun `series resolution returns no target when canonical episode loading fails`() =
        runBlocking {
            val seriesId = UUID.fromString("00000000-0000-0000-0000-000000000050")
            val seasonId = UUID.fromString("00000000-0000-0000-0000-000000000051")
            val nextUp =
                dummyEpisode.copy(
                    id = UUID.fromString("00000000-0000-0000-0000-000000000052"),
                    seriesId = seriesId,
                    seasonId = seasonId,
                    played = false,
                    playbackPositionTicks = 0L,
                )
            val source =
                FakePlaybackStartSource(
                    nextUpEpisode = nextUp,
                    episodeFailure = IllegalStateException("episodes failed"),
                )

            val start =
                PlaybackStartResolver(source)
                    .resolveSeries(
                        seriesId = seriesId,
                        seasons =
                            listOf(
                                dummySeason.copy(
                                    id = seasonId,
                                    seriesId = seriesId,
                                    indexNumber = 1,
                                )
                            ),
                    )

            assertNull(start)
        }

    @Test
    fun `series resolution ignores candidates outside the requested series`() = runBlocking {
        val seriesId = UUID.fromString("00000000-0000-0000-0000-000000000060")
        val otherSeriesId = UUID.fromString("00000000-0000-0000-0000-000000000061")
        val seasonId = UUID.fromString("00000000-0000-0000-0000-000000000062")
        val outsideScope =
            dummyEpisode.copy(
                id = UUID.fromString("00000000-0000-0000-0000-000000000063"),
                seriesId = otherSeriesId,
                seasonId = seasonId,
                played = false,
                playbackPositionTicks = 900_000_000L,
            )
        val inScope =
            dummyEpisode.copy(
                id = UUID.fromString("00000000-0000-0000-0000-000000000064"),
                seriesId = seriesId,
                seasonId = seasonId,
                played = false,
                playbackPositionTicks = 0L,
            )
        val source =
            FakePlaybackStartSource(
                resumeEpisodes = listOf(outsideScope),
                nextUpEpisode = outsideScope,
                episodesBySeason = mapOf(seasonId to listOf(inScope)),
            )

        val start =
            PlaybackStartResolver(source)
                .resolveSeries(
                    seriesId = seriesId,
                    seasons =
                        listOf(
                            dummySeason.copy(
                                id = seasonId,
                                seriesId = seriesId,
                                indexNumber = 1,
                            )
                        ),
                )

        assertEquals(inScope.id, start?.episode?.id)
    }
}

private class FakePlaybackStartSource(
    private val resumeEpisodes: List<FindroidEpisode> = emptyList(),
    private val nextUpEpisode: FindroidEpisode? = null,
    private val episodesBySeason: Map<UUID, List<FindroidEpisode>> = emptyMap(),
    private val resumeFailure: Exception? = null,
    private val nextUpFailure: Exception? = null,
    private val episodeFailure: Exception? = null,
) : PlaybackStartSource {
    override suspend fun getResumeEpisodes(parentId: UUID): List<FindroidEpisode> {
        resumeFailure?.let { throw it }
        return resumeEpisodes
    }

    override suspend fun getNextUpEpisode(seriesId: UUID): FindroidEpisode? {
        nextUpFailure?.let { throw it }
        return nextUpEpisode
    }

    override suspend fun getEpisodes(seriesId: UUID, seasonId: UUID): List<FindroidEpisode> {
        episodeFailure?.let { throw it }
        return episodesBySeason[seasonId].orEmpty()
    }
}
