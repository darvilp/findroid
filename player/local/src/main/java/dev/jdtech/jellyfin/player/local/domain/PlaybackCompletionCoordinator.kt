package dev.jdtech.jellyfin.player.local.domain

import java.util.UUID

internal data class PlaybackStopReport(
    val itemId: UUID,
    val positionTicks: Long,
    val playedPercentage: Int,
)

internal data class TerminalPlaybackCompletion(val stopReport: PlaybackStopReport?)

/** Keeps natural playback completion idempotent and preserves its stop report until release. */
internal class PlaybackCompletionCoordinator {
    private var terminalCompletionClaimed = false
    private var terminalStopReport: PlaybackStopReport? = null
    private var terminalStopReported = false

    fun beginPlaybackPass() {
        terminalCompletionClaimed = false
        terminalStopReport = null
        terminalStopReported = false
    }

    fun claimTerminalCompletion(
        itemId: UUID?,
        positionMs: Long,
        durationMs: Long,
    ): TerminalPlaybackCompletion? {
        if (terminalCompletionClaimed) return null

        terminalCompletionClaimed = true
        terminalStopReport =
            itemId?.let {
                val completedPositionMs =
                    when {
                        durationMs > 0L -> durationMs
                        positionMs >= 0L -> positionMs
                        else -> return@let null
                    }
                PlaybackStopReport(
                    itemId = it,
                    positionTicks = completedPositionMs * TICKS_PER_MILLISECOND,
                    playedPercentage = 100,
                )
            }

        return TerminalPlaybackCompletion(stopReport = terminalStopReport)
    }

    fun confirmReported(report: PlaybackStopReport) {
        if (report == terminalStopReport) terminalStopReported = true
    }

    fun releaseStopReport(
        itemId: UUID?,
        positionMs: Long,
        durationMs: Long,
    ): PlaybackStopReport? {
        terminalStopReport?.let { return it.takeUnless { terminalStopReported } }
        if (terminalCompletionClaimed || itemId == null || positionMs < 0L || durationMs <= 0L) {
            return null
        }

        return PlaybackStopReport(
            itemId = itemId,
            positionTicks = positionMs * TICKS_PER_MILLISECOND,
            playedPercentage =
                ((positionMs * 100L) / durationMs).coerceIn(MIN_PERCENTAGE, MAX_PERCENTAGE).toInt(),
        )
    }

    companion object {
        private const val TICKS_PER_MILLISECOND = 10_000L
        private const val MIN_PERCENTAGE = 0L
        private const val MAX_PERCENTAGE = 100L
    }
}
