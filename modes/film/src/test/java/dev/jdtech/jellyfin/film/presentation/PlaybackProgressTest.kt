package dev.jdtech.jellyfin.film.presentation

import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProgressTest {
    @Test
    fun `logical playback resumes the scoped in progress episode before next up`() {
        val earlierUnplayed =
            dummyEpisode.copy(
                id = java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"),
                played = false,
                playbackPositionTicks = 0L,
            )
        val nextUp =
            dummyEpisode.copy(
                id = java.util.UUID.fromString("22222222-2222-2222-2222-222222222222"),
                played = false,
                playbackPositionTicks = 0L,
            )
        val resumable =
            dummyEpisode.copy(
                id = java.util.UUID.fromString("33333333-3333-3333-3333-333333333333"),
                played = false,
                playbackPositionTicks = 900_000_000L,
            )

        val start =
            selectPlaybackStartEpisode(
                scopedResumeEpisodes = listOf(resumable),
                nextUp = nextUp,
                canonicalEpisodes = listOf(earlierUnplayed, nextUp, resumable),
            )

        assertEquals(resumable.id, start?.episode?.id)
        assertFalse(requireNotNull(start).startFromBeginning)
    }

    @Test
    fun `logical playback skips an unavailable resume candidate and uses next up`() {
        val unavailableResume =
            dummyEpisode.copy(
                id = java.util.UUID.fromString("44444444-4444-4444-4444-444444444444"),
                missing = true,
                played = false,
                playbackPositionTicks = 900_000_000L,
            )
        val nextUp =
            dummyEpisode.copy(
                id = java.util.UUID.fromString("55555555-5555-5555-5555-555555555555"),
                missing = false,
                canPlay = true,
                played = false,
                playbackPositionTicks = 0L,
            )

        val start =
            selectPlaybackStartEpisode(
                scopedResumeEpisodes = listOf(unavailableResume),
                nextUp = nextUp,
                canonicalEpisodes = emptyList(),
            )

        assertEquals(nextUp.id, start?.episode?.id)
        assertFalse(requireNotNull(start).startFromBeginning)
    }

    @Test
    fun `canonical in progress fallback wins when scoped resume is unavailable`() {
        val canonicalResume =
            dummyEpisode.copy(
                id = java.util.UUID.fromString("66666666-6666-6666-6666-666666666666"),
                played = false,
                playbackPositionTicks = 700_000_000L,
            )
        val nextUp =
            dummyEpisode.copy(
                id = java.util.UUID.fromString("77777777-7777-7777-7777-777777777777"),
                played = false,
                playbackPositionTicks = 0L,
            )

        val start =
            selectPlaybackStartEpisode(
                scopedResumeEpisodes = emptyList(),
                nextUp = nextUp,
                canonicalEpisodes = listOf(canonicalResume, nextUp),
            )

        assertEquals(canonicalResume.id, start?.episode?.id)
    }

    @Test
    fun `logical playback skips unavailable gaps and chooses first unplayed episode`() {
        val watched = dummyEpisode.copy(played = true, playbackPositionTicks = 0L)
        val missing =
            dummyEpisode.copy(
                id = java.util.UUID.fromString("88888888-8888-8888-8888-888888888888"),
                missing = true,
                played = false,
            )
        val denied =
            dummyEpisode.copy(
                id = java.util.UUID.fromString("99999999-9999-9999-9999-999999999999"),
                missing = false,
                canPlay = false,
                played = false,
            )
        val unplayed =
            dummyEpisode.copy(
                id = java.util.UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
                missing = false,
                canPlay = true,
                played = false,
                playbackPositionTicks = 0L,
            )

        val start =
            selectPlaybackStartEpisode(
                scopedResumeEpisodes = emptyList(),
                nextUp = missing,
                canonicalEpisodes = listOf(watched, missing, denied, unplayed),
            )

        assertEquals(unplayed.id, start?.episode?.id)
        assertFalse(requireNotNull(start).startFromBeginning)
    }

    @Test
    fun `fully watched scope restarts its first available episode`() {
        val unavailable = dummyEpisode.copy(missing = true, played = true)
        val firstAvailable =
            dummyEpisode.copy(
                id = java.util.UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
                missing = false,
                canPlay = true,
                played = true,
                playbackPositionTicks = 200_000_000L,
            )
        val laterAvailable =
            dummyEpisode.copy(
                id = java.util.UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"),
                missing = false,
                canPlay = true,
                played = true,
            )

        val start =
            selectPlaybackStartEpisode(
                scopedResumeEpisodes = emptyList(),
                nextUp = null,
                canonicalEpisodes = listOf(unavailable, firstAvailable, laterAvailable),
            )

        assertEquals(firstAvailable.id, start?.episode?.id)
        assertTrue(requireNotNull(start).startFromBeginning)
    }

    @Test
    fun `scope without an available episode has no playback target`() {
        val missing = dummyEpisode.copy(missing = true, canPlay = true, played = false)
        val denied = dummyEpisode.copy(missing = false, canPlay = false, played = false)

        assertNull(
            selectPlaybackStartEpisode(
                scopedResumeEpisodes = listOf(missing, denied),
                nextUp = denied,
                canonicalEpisodes = listOf(missing, denied),
            )
        )
    }

    @Test
    fun `saved progress becomes meaningful at one minute`() {
        assertFalse(hasMeaningfulSavedProgress(playbackPositionTicks = -1L))
        assertFalse(hasMeaningfulSavedProgress(playbackPositionTicks = 0L))
        assertFalse(hasMeaningfulSavedProgress(playbackPositionTicks = 599_999_999L))
        assertTrue(hasMeaningfulSavedProgress(playbackPositionTicks = 600_000_000L))
    }

}
