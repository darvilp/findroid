package dev.jdtech.jellyfin.settings.presentation.settings

import dev.jdtech.jellyfin.settings.domain.MpvSynchronizationDefaults
import dev.jdtech.jellyfin.settings.domain.MpvSynchronizationKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MpvSynchronizationConfigCoordinatorTest {
    @Test
    fun `same-kind saves persist and display the newest queued value`() = runTest {
        val writes = Channel<PendingWrite>(Channel.UNLIMITED)
        var persisted = MpvSynchronizationDefaults(audioMs = 0L, subtitleMs = 30L)
        var displayed = persisted
        val coordinator =
            MpvSynchronizationConfigCoordinator(
                scope = backgroundScope,
                ioDispatcher = StandardTestDispatcher(testScheduler),
                readDefaults = { Result.success(persisted) },
                writeConfig = { kind, valueMs ->
                    val completion = CompletableDeferred<Result<Unit>>()
                    writes.send(PendingWrite(kind, valueMs, completion))
                    completion.await().map {
                        persisted = persisted.withValue(kind, valueMs)
                        persisted
                    }
                },
            )

        val older = coordinator.write(MpvSynchronizationKind.AUDIO, 100L)
        val newer = coordinator.write(MpvSynchronizationKind.AUDIO, 200L)
        runCurrent()

        val first = writes.receive()
        assertEquals(100L, first.valueMs)
        first.completion.complete(Result.success(Unit))
        runCurrent()
        val second = writes.receive()
        assertEquals(200L, second.valueMs)
        second.completion.complete(Result.success(Unit))
        runCurrent()

        applyCurrent(older, onSuccess = { displayed = it })
        applyCurrent(newer, onSuccess = { displayed = it })
        assertEquals(listOf(200L, 30L), listOf(persisted.audioMs, persisted.subtitleMs))
        assertEquals(listOf(200L, 30L), listOf(displayed.audioMs, displayed.subtitleMs))
    }

    @Test
    fun `failure from an older save is stale after a newer save is queued`() = runTest {
        val writes = Channel<PendingWrite>(Channel.UNLIMITED)
        var persisted = MpvSynchronizationDefaults(audioMs = 0L, subtitleMs = 30L)
        var displayed = persisted
        var errors = 0
        val coordinator =
            MpvSynchronizationConfigCoordinator(
                scope = backgroundScope,
                ioDispatcher = StandardTestDispatcher(testScheduler),
                readDefaults = { Result.success(persisted) },
                writeConfig = { kind, valueMs ->
                    val completion = CompletableDeferred<Result<Unit>>()
                    writes.send(PendingWrite(kind, valueMs, completion))
                    completion.await().map {
                        persisted = persisted.withValue(kind, valueMs)
                        persisted
                    }
                },
            )

        val older = coordinator.write(MpvSynchronizationKind.AUDIO, 100L)
        val newer = coordinator.write(MpvSynchronizationKind.AUDIO, 200L)
        runCurrent()

        writes.receive().completion.complete(Result.failure(IllegalStateException("old failure")))
        runCurrent()
        writes.receive().completion.complete(Result.success(Unit))
        runCurrent()

        applyCurrent(older, onSuccess = { displayed = it }, onFailure = { errors += 1 })
        applyCurrent(newer, onSuccess = { displayed = it }, onFailure = { errors += 1 })
        assertEquals(0, errors)
        assertEquals(200L, persisted.audioMs)
        assertEquals(200L, displayed.audioMs)
    }

    @Test
    fun `older success remains visible when newer save fails`() = runTest {
        val writes = Channel<PendingWrite>(Channel.UNLIMITED)
        var persisted = MpvSynchronizationDefaults(audioMs = 0L, subtitleMs = 30L)
        var displayed = persisted
        var errors = 0
        val coordinator =
            MpvSynchronizationConfigCoordinator(
                scope = backgroundScope,
                ioDispatcher = StandardTestDispatcher(testScheduler),
                readDefaults = { Result.success(persisted) },
                writeConfig = { kind, valueMs ->
                    val completion = CompletableDeferred<Result<Unit>>()
                    writes.send(PendingWrite(kind, valueMs, completion))
                    completion.await().map {
                        persisted = persisted.withValue(kind, valueMs)
                        persisted
                    }
                },
            )

        val older = coordinator.write(MpvSynchronizationKind.AUDIO, 100L)
        val newer = coordinator.write(MpvSynchronizationKind.SUBTITLE, 200L)
        runCurrent()

        writes.receive().completion.complete(Result.success(Unit))
        runCurrent()
        writes.receive().completion.complete(Result.failure(IllegalStateException("new failure")))
        runCurrent()

        applyCurrent(older, onSuccess = { displayed = it }, onFailure = { errors += 1 })
        applyCurrent(newer, onSuccess = { displayed = it }, onFailure = { errors += 1 })
        assertEquals(MpvSynchronizationDefaults(100L, 30L), persisted)
        assertEquals(MpvSynchronizationDefaults(100L, 30L), displayed)
        assertEquals(1, errors)
    }

    @Test
    fun `cross-kind write does not suppress an older write failure`() = runTest {
        val writes = Channel<PendingWrite>(Channel.UNLIMITED)
        var persisted = MpvSynchronizationDefaults(audioMs = 0L, subtitleMs = 30L)
        var displayed = persisted
        var errors = 0
        val coordinator =
            MpvSynchronizationConfigCoordinator(
                scope = backgroundScope,
                ioDispatcher = StandardTestDispatcher(testScheduler),
                readDefaults = { Result.success(persisted) },
                writeConfig = { kind, valueMs ->
                    val completion = CompletableDeferred<Result<Unit>>()
                    writes.send(PendingWrite(kind, valueMs, completion))
                    completion.await().map {
                        persisted = persisted.withValue(kind, valueMs)
                        persisted
                    }
                },
            )

        val audio = coordinator.write(MpvSynchronizationKind.AUDIO, 100L)
        val subtitle = coordinator.write(MpvSynchronizationKind.SUBTITLE, 200L)
        runCurrent()

        writes.receive().completion.complete(Result.failure(IllegalStateException("audio failure")))
        runCurrent()
        writes.receive().completion.complete(Result.success(Unit))
        runCurrent()

        applyCurrent(audio, onSuccess = { displayed = it }, onFailure = { errors += 1 })
        applyCurrent(subtitle, onSuccess = { displayed = it }, onFailure = { errors += 1 })
        assertEquals(1, errors)
        assertEquals(MpvSynchronizationDefaults(0L, 200L), persisted)
        assertEquals(MpvSynchronizationDefaults(0L, 200L), displayed)
    }

    @Test
    fun `read does not suppress an older write failure`() = runTest {
        val writeCanFinish = CompletableDeferred<Result<MpvSynchronizationDefaults>>()
        var errors = 0
        val coordinator =
            MpvSynchronizationConfigCoordinator(
                scope = backgroundScope,
                ioDispatcher = StandardTestDispatcher(testScheduler),
                readDefaults = { Result.success(MpvSynchronizationDefaults(0L, 30L)) },
                writeConfig = { _, _ -> writeCanFinish.await() },
            )

        val write = coordinator.write(MpvSynchronizationKind.AUDIO, 100L)
        val read = coordinator.read()
        runCurrent()
        writeCanFinish.complete(Result.failure(IllegalStateException("write failure")))
        runCurrent()

        applyCurrent(write, onSuccess = {}, onFailure = { errors += 1 })
        applyCurrent(read, onSuccess = {})
        assertEquals(1, errors)
    }

    private suspend fun applyCurrent(
        operation: Deferred<MpvSynchronizationConfigOutcome>,
        onSuccess: (MpvSynchronizationDefaults) -> Unit,
        onFailure: () -> Unit = {},
    ) {
        when (val outcome = operation.await()) {
            is MpvSynchronizationConfigOutcome.Success -> onSuccess(outcome.defaults)
            is MpvSynchronizationConfigOutcome.Failure -> onFailure()
            MpvSynchronizationConfigOutcome.SupersededFailure -> Unit
        }
    }

    private data class PendingWrite(
        val kind: MpvSynchronizationKind,
        val valueMs: Long,
        val completion: CompletableDeferred<Result<Unit>>,
    )
}
