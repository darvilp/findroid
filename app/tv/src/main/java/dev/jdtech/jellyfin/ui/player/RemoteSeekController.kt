package dev.jdtech.jellyfin.ui.player

enum class RemoteSeekDirection {
    Backward,
    Forward,
}

internal data class RemoteSeekPlayback(
    val positionMs: Long,
    val durationMs: Long?,
    val seekBackIncrementMs: Long,
    val seekForwardIncrementMs: Long,
)

internal class RemoteSeekController {
    private var heldDirection: RemoteSeekDirection? = null
    private var holdStartedAtMs = 0L

    fun onKeyDown(
        direction: RemoteSeekDirection,
        eventTimeMs: Long,
        playback: RemoteSeekPlayback,
    ): Long {
        val normalizedEventTimeMs = eventTimeMs.coerceAtLeast(0L)
        if (heldDirection != direction || normalizedEventTimeMs < holdStartedAtMs) {
            heldDirection = direction
            holdStartedAtMs = normalizedEventTimeMs
        }

        val baseIncrementMs =
            when (direction) {
                RemoteSeekDirection.Backward -> playback.seekBackIncrementMs
                RemoteSeekDirection.Forward -> playback.seekForwardIncrementMs
            }
                .coerceAtLeast(0L)
        val multiplier = accelerationMultiplier(normalizedEventTimeMs - holdStartedAtMs)
        val acceleratedStepMs = baseIncrementMs.saturatedMultiply(multiplier)
        val maximumStepMs = maxOf(baseIncrementMs, MAXIMUM_ACCELERATED_STEP_MS)
        val stepMs = minOf(acceleratedStepMs, maximumStepMs)
        val durationMs = playback.durationMs?.takeIf { it >= 0L }
        val positionMs =
            playback.positionMs.coerceAtLeast(0L).let { position ->
                durationMs?.let(position::coerceAtMost) ?: position
            }

        return when (direction) {
            RemoteSeekDirection.Backward ->
                if (stepMs >= positionMs) 0L else positionMs - stepMs
            RemoteSeekDirection.Forward ->
                positionMs.saturatedAdd(stepMs).let { target ->
                    durationMs?.let(target::coerceAtMost) ?: target
                }
        }
    }

    fun onKeyUp(direction: RemoteSeekDirection) {
        if (heldDirection == direction) {
            heldDirection = null
            holdStartedAtMs = 0L
        }
    }

    private fun accelerationMultiplier(elapsedHoldMs: Long): Long =
        when {
            elapsedHoldMs < DOUBLE_STEP_AFTER_MS -> 1L
            elapsedHoldMs < QUADRUPLE_STEP_AFTER_MS -> 2L
            elapsedHoldMs < OCTUPLE_STEP_AFTER_MS -> 4L
            else -> 8L
        }

    private fun Long.saturatedMultiply(multiplier: Long): Long =
        if (this == 0L || this <= Long.MAX_VALUE / multiplier) this * multiplier
        else Long.MAX_VALUE

    private fun Long.saturatedAdd(value: Long): Long =
        if (this <= Long.MAX_VALUE - value) this + value else Long.MAX_VALUE

    private companion object {
        const val DOUBLE_STEP_AFTER_MS = 1_000L
        const val QUADRUPLE_STEP_AFTER_MS = 2_000L
        const val OCTUPLE_STEP_AFTER_MS = 3_500L
        const val MAXIMUM_ACCELERATED_STEP_MS = 60_000L
    }
}
