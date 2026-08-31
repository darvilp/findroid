package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.settings.domain.MpvSynchronizationKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

internal sealed interface PlaybackSynchronizationSaveOutcome {
    val kind: MpvSynchronizationKind
    val valueMs: Long
    val mediaId: String
    val sessionToken: Long

    data class Success(
        override val kind: MpvSynchronizationKind,
        override val valueMs: Long,
        override val mediaId: String,
        override val sessionToken: Long,
    ) : PlaybackSynchronizationSaveOutcome

    data class Failure(
        override val kind: MpvSynchronizationKind,
        override val valueMs: Long,
        override val mediaId: String,
        override val sessionToken: Long,
        val shouldNotify: Boolean,
        val error: Throwable,
    ) : PlaybackSynchronizationSaveOutcome
}

internal class PlaybackSynchronizationSaveCoordinator(
    scope: CoroutineScope,
    private val write: suspend (MpvSynchronizationKind, Long) -> Result<Unit>,
    private val onOutcome: (PlaybackSynchronizationSaveOutcome) -> Unit,
) {
    private data class Request(
        val sequence: Long,
        val kind: MpvSynchronizationKind,
        val valueMs: Long,
        val mediaId: String,
        val sessionToken: Long,
    )

    private val requests = Channel<Request>(Channel.UNLIMITED)
    private val latestSequenceByKind = mutableMapOf<MpvSynchronizationKind, Long>()

    init {
        scope.launch {
            for (request in requests) {
                val result = write(request.kind, request.valueMs)
                onOutcome(
                    result.fold(
                        onSuccess = {
                            PlaybackSynchronizationSaveOutcome.Success(
                                request.kind,
                                request.valueMs,
                                request.mediaId,
                                request.sessionToken,
                            )
                        },
                        onFailure = {
                            PlaybackSynchronizationSaveOutcome.Failure(
                                request.kind,
                                request.valueMs,
                                request.mediaId,
                                request.sessionToken,
                                request.sequence == latestSequenceByKind[request.kind],
                                it,
                            )
                        },
                    )
                )
            }
        }
    }

    fun enqueue(
        kind: MpvSynchronizationKind,
        valueMs: Long,
        mediaId: String,
        sessionToken: Long,
    ) {
        val sequence = Math.incrementExact(latestSequenceByKind[kind] ?: 0L)
        latestSequenceByKind[kind] = sequence
        check(requests.trySend(Request(sequence, kind, valueMs, mediaId, sessionToken)).isSuccess)
    }
}
