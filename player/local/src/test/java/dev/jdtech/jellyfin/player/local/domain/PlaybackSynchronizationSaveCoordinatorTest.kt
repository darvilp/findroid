package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.settings.domain.MpvSynchronizationKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackSynchronizationSaveCoordinatorTest {
    @Test
    fun `requests write fifo and every persisted success remains applicable`() = runTest {
        val firstWriteCanFinish = CompletableDeferred<Unit>()
        val writes = mutableListOf<Long>()
        val outcomes = mutableListOf<PlaybackSynchronizationSaveOutcome>()
        val coordinator =
            PlaybackSynchronizationSaveCoordinator(
                scope = backgroundScope,
                write = { _, valueMs ->
                    writes += valueMs
                    if (valueMs == 100L) firstWriteCanFinish.await()
                    Result.success(Unit)
                },
                onOutcome = outcomes::add,
            )

        coordinator.enqueue(MpvSynchronizationKind.AUDIO, 100L, "episode-1", 1L)
        coordinator.enqueue(MpvSynchronizationKind.AUDIO, 200L, "episode-1", 1L)
        runCurrent()
        assertEquals(listOf(100L), writes)

        firstWriteCanFinish.complete(Unit)
        runCurrent()

        assertEquals(listOf(100L, 200L), writes)
        assertEquals(
            listOf(
                PlaybackSynchronizationSaveOutcome.Success(
                    kind = MpvSynchronizationKind.AUDIO,
                    valueMs = 100L,
                    mediaId = "episode-1",
                    sessionToken = 1L,
                ),
                PlaybackSynchronizationSaveOutcome.Success(
                    kind = MpvSynchronizationKind.AUDIO,
                    valueMs = 200L,
                    mediaId = "episode-1",
                    sessionToken = 1L,
                ),
            ),
            outcomes,
        )
    }

    @Test
    fun `older success remains applicable when newer save fails`() = runTest {
        val firstWriteCanFinish = CompletableDeferred<Unit>()
        val outcomes = mutableListOf<PlaybackSynchronizationSaveOutcome>()
        val coordinator =
            PlaybackSynchronizationSaveCoordinator(
                scope = backgroundScope,
                write = { kind, _ ->
                    if (kind == MpvSynchronizationKind.AUDIO) {
                        firstWriteCanFinish.await()
                        Result.success(Unit)
                    } else {
                        Result.failure(IllegalStateException("subtitle failure"))
                    }
                },
                onOutcome = outcomes::add,
            )

        coordinator.enqueue(MpvSynchronizationKind.AUDIO, 100L, "episode-1", 1L)
        coordinator.enqueue(MpvSynchronizationKind.SUBTITLE, 200L, "episode-1", 1L)
        runCurrent()
        firstWriteCanFinish.complete(Unit)
        runCurrent()

        assertEquals(
            PlaybackSynchronizationSaveOutcome.Success(
                kind = MpvSynchronizationKind.AUDIO,
                valueMs = 100L,
                mediaId = "episode-1",
                sessionToken = 1L,
            ),
            outcomes[0],
        )
        assertEquals(true, (outcomes[1] as PlaybackSynchronizationSaveOutcome.Failure).shouldNotify)
    }

    @Test
    fun `newer same-kind failure suppresses the older failure`() = runTest {
        val firstWriteCanFinish = CompletableDeferred<Unit>()
        val outcomes = mutableListOf<PlaybackSynchronizationSaveOutcome>()
        val coordinator =
            PlaybackSynchronizationSaveCoordinator(
                scope = backgroundScope,
                write = { _, valueMs ->
                    if (valueMs == 100L) firstWriteCanFinish.await()
                    Result.failure(IllegalStateException(valueMs.toString()))
                },
                onOutcome = outcomes::add,
            )

        coordinator.enqueue(MpvSynchronizationKind.AUDIO, 100L, "episode-1", 1L)
        coordinator.enqueue(MpvSynchronizationKind.AUDIO, 200L, "episode-1", 1L)
        runCurrent()
        firstWriteCanFinish.complete(Unit)
        runCurrent()

        assertEquals(false, (outcomes[0] as PlaybackSynchronizationSaveOutcome.Failure).shouldNotify)
        assertEquals(true, (outcomes[1] as PlaybackSynchronizationSaveOutcome.Failure).shouldNotify)
    }

    @Test
    fun `cross-kind failures remain independently notifyable`() = runTest {
        val firstWriteCanFinish = CompletableDeferred<Unit>()
        val outcomes = mutableListOf<PlaybackSynchronizationSaveOutcome>()
        val coordinator =
            PlaybackSynchronizationSaveCoordinator(
                scope = backgroundScope,
                write = { kind, _ ->
                    if (kind == MpvSynchronizationKind.AUDIO) firstWriteCanFinish.await()
                    Result.failure(IllegalStateException(kind.name))
                },
                onOutcome = outcomes::add,
            )

        coordinator.enqueue(MpvSynchronizationKind.AUDIO, 100L, "episode-1", 1L)
        coordinator.enqueue(MpvSynchronizationKind.SUBTITLE, 200L, "episode-1", 1L)
        runCurrent()
        firstWriteCanFinish.complete(Unit)
        runCurrent()

        assertEquals(true, (outcomes[0] as PlaybackSynchronizationSaveOutcome.Failure).shouldNotify)
        assertEquals(true, (outcomes[1] as PlaybackSynchronizationSaveOutcome.Failure).shouldNotify)
    }
}
