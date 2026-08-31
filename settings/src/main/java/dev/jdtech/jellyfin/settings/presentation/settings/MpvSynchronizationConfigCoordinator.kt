package dev.jdtech.jellyfin.settings.presentation.settings

import dev.jdtech.jellyfin.settings.domain.MpvSynchronizationDefaults
import dev.jdtech.jellyfin.settings.domain.MpvSynchronizationKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal sealed interface MpvSynchronizationConfigOutcome {
    data class Success(val defaults: MpvSynchronizationDefaults) : MpvSynchronizationConfigOutcome

    data class Failure(val error: Throwable) : MpvSynchronizationConfigOutcome

    data object SupersededFailure : MpvSynchronizationConfigOutcome
}

internal class MpvSynchronizationConfigCoordinator(
    scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val readDefaults: suspend () -> Result<MpvSynchronizationDefaults>,
    private val writeConfig:
        suspend (MpvSynchronizationKind, Long) -> Result<MpvSynchronizationDefaults>,
) {
    private val operations = Channel<Operation>(Channel.UNLIMITED)
    private val latestGenerationByKey = mutableMapOf<OperationKey, Long>()

    init {
        scope.launch {
            for (operation in operations) {
                val result = withContext(ioDispatcher) { operation.execute() }
                val outcome =
                    result.fold(
                        onSuccess = MpvSynchronizationConfigOutcome::Success,
                        onFailure = {
                            if (operation.generation == latestGenerationByKey[operation.key]) {
                                MpvSynchronizationConfigOutcome.Failure(it)
                            } else {
                                MpvSynchronizationConfigOutcome.SupersededFailure
                            }
                        },
                    )
                operation.response.complete(outcome)
            }
        }
    }

    fun read(): Deferred<MpvSynchronizationConfigOutcome> =
        enqueue(OperationKey.Read, readDefaults)

    fun write(
        kind: MpvSynchronizationKind,
        valueMs: Long,
    ): Deferred<MpvSynchronizationConfigOutcome> =
        enqueue(OperationKey.Write(kind)) { writeConfig(kind, valueMs) }

    private fun enqueue(
        key: OperationKey,
        execute: suspend () -> Result<MpvSynchronizationDefaults>
    ): CompletableDeferred<MpvSynchronizationConfigOutcome> {
        val response = CompletableDeferred<MpvSynchronizationConfigOutcome>()
        val generation = Math.incrementExact(latestGenerationByKey[key] ?: 0L)
        latestGenerationByKey[key] = generation
        operations.trySend(Operation(key, generation, execute, response)).getOrThrow()
        return response
    }

    private sealed interface OperationKey {
        data object Read : OperationKey

        data class Write(val kind: MpvSynchronizationKind) : OperationKey
    }

    private data class Operation(
        val key: OperationKey,
        val generation: Long,
        val execute: suspend () -> Result<MpvSynchronizationDefaults>,
        val response: CompletableDeferred<MpvSynchronizationConfigOutcome>,
    )
}
