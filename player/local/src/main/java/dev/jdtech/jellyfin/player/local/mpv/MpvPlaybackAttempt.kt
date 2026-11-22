package dev.jdtech.jellyfin.player.local.mpv

import dev.jdtech.mpv.MPVLib.MpvEndFileReason

/** Associates native file callbacks with the load that was active when START_FILE was delivered. */
internal class MpvPlaybackAttempt {
    @Volatile private var requested = 0L
    @Volatile
    var nativeGeneration = -1L
        private set

    private var failed = false

    fun requestFile() {
        requested++
        failed = false
    }

    fun onNativeStart(): Long {
        val generation = requested
        nativeGeneration = generation
        return generation
    }

    fun accepts(generation: Long): Boolean = generation == requested && !failed

    fun claimFailure(generation: Long, reason: Int): Boolean {
        if (reason != MpvEndFileReason.MPV_END_FILE_REASON_ERROR || !accepts(generation))
            return false
        failed = true
        return true
    }
}
