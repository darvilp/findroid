package dev.jdtech.jellyfin.player.local.domain

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackCompletionCoordinatorTest {
    @Test
    fun `natural completion reports the full duration exactly once`() {
        val itemId = UUID.fromString("9094ff80-cd70-475a-94f8-4528e53114a1")
        val coordinator = PlaybackCompletionCoordinator()

        assertEquals(
            TerminalPlaybackCompletion(
                stopReport =
                    PlaybackStopReport(
                        itemId = itemId,
                        positionTicks = 18_000_000L,
                        playedPercentage = 100,
                    )
            ),
            coordinator.claimTerminalCompletion(
                itemId = itemId,
                positionMs = 1_750L,
                durationMs = 1_800L,
            ),
        )
        assertNull(
            coordinator.claimTerminalCompletion(
                itemId = itemId,
                positionMs = 1_800L,
                durationMs = 1_800L,
            )
        )
    }

    @Test
    fun `completion without reportable timing still claims navigation once`() {
        val coordinator = PlaybackCompletionCoordinator()

        assertEquals(
            TerminalPlaybackCompletion(stopReport = null),
            coordinator.claimTerminalCompletion(
                itemId = null,
                positionMs = -1L,
                durationMs = -1L,
            ),
        )
        assertNull(
            coordinator.claimTerminalCompletion(
                itemId = null,
                positionMs = -1L,
                durationMs = -1L,
            )
        )
    }

    @Test
    fun `new playback pass permits a later natural completion`() {
        val firstItemId = UUID.fromString("9094ff80-cd70-475a-94f8-4528e53114a1")
        val secondItemId = UUID.fromString("5a469cc1-c8c3-46fb-91aa-d157fab68dd9")
        val coordinator = PlaybackCompletionCoordinator()

        coordinator.claimTerminalCompletion(firstItemId, 900L, 1_000L)
        coordinator.beginPlaybackPass()

        assertEquals(
            secondItemId,
            coordinator
                .claimTerminalCompletion(secondItemId, 1_900L, 2_000L)
                ?.stopReport
                ?.itemId,
        )
    }

    @Test
    fun `unconfirmed terminal report is retried during release`() {
        val itemId = UUID.fromString("9094ff80-cd70-475a-94f8-4528e53114a1")
        val coordinator = PlaybackCompletionCoordinator()
        val completion =
            coordinator.claimTerminalCompletion(
                itemId = itemId,
                positionMs = 1_750L,
                durationMs = 1_800L,
            )!!

        assertEquals(
            completion.stopReport,
            coordinator.releaseStopReport(itemId, positionMs = -1L, durationMs = -1L),
        )
    }

    @Test
    fun `confirmed terminal report is not duplicated during release`() {
        val itemId = UUID.fromString("9094ff80-cd70-475a-94f8-4528e53114a1")
        val coordinator = PlaybackCompletionCoordinator()
        val completion =
            coordinator.claimTerminalCompletion(
                itemId = itemId,
                positionMs = 1_750L,
                durationMs = 1_800L,
            )!!

        coordinator.confirmReported(completion.stopReport!!)

        assertNull(coordinator.releaseStopReport(itemId, positionMs = 1_800L, durationMs = 1_800L))
    }

    @Test
    fun `manual release reports actual partial progress`() {
        val itemId = UUID.fromString("9094ff80-cd70-475a-94f8-4528e53114a1")
        val coordinator = PlaybackCompletionCoordinator()

        assertEquals(
            PlaybackStopReport(
                itemId = itemId,
                positionTicks = 300_000_000L,
                playedPercentage = 25,
            ),
            coordinator.releaseStopReport(
                itemId = itemId,
                positionMs = 30_000L,
                durationMs = 120_000L,
            ),
        )
    }
}
