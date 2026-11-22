package dev.jdtech.jellyfin.player.local.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Sends playback reports in order and drains accepted reports when the screen closes. */
internal class PlaybackReportQueue(scope: CoroutineScope, onFailure: (Exception) -> Unit) {
    private val reports = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (report in reports) {
                try {
                    report()
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    onFailure(failure)
                }
            }
        }
    }

    fun enqueue(report: suspend () -> Unit) {
        reports.trySend(report)
    }

    fun close() {
        reports.close()
    }
}
