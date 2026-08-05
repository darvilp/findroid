package dev.jdtech.jellyfin.player.local.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackRestartControllerTest {
    @Test
    fun `restart is available only beyond one configured back seek`() {
        val controller = PlaybackRestartController()

        assertFalse(controller.isAvailable(currentPositionMs = 5_000L, seekBackIncrementMs = 5_000L))
        assertTrue(controller.isAvailable(currentPositionMs = 5_001L, seekBackIncrementMs = 5_000L))
        assertFalse(controller.isAvailable(currentPositionMs = 0L, seekBackIncrementMs = -1L))
    }

    @Test
    fun `restart begins a new pass then seeks current item to zero and plays`() {
        val target = RecordingRestartTarget(isPlaying = false)

        PlaybackRestartController().restart(target)

        assertEquals(listOf("beginPlaybackPass", "seekTo:0", "play"), target.commands)
        assertEquals(1, target.playbackPass)
        assertEquals(0L, target.positionMs)
        assertTrue(target.isPlaying)
    }

    @Test
    fun `restart capability boundary leaves unrelated playback context untouched`() {
        val target = RecordingRestartTarget(isPlaying = true)
        val playlistBefore = target.playlist.toList()
        val audioTrackBefore = target.selectedAudioTrack
        val subtitleTrackBefore = target.selectedSubtitleTrack
        val watchedBefore = target.watched

        PlaybackRestartController().restart(target)

        assertEquals(playlistBefore, target.playlist)
        assertEquals(audioTrackBefore, target.selectedAudioTrack)
        assertEquals(subtitleTrackBefore, target.selectedSubtitleTrack)
        assertEquals(watchedBefore, target.watched)
    }

    private class RecordingRestartTarget(isPlaying: Boolean) : PlaybackRestartTarget {
        val commands = mutableListOf<String>()
        var playbackPass = 0
        var positionMs = 90_000L
        var isPlaying = isPlaying
        val playlist = mutableListOf("episode-1", "episode-2")
        val selectedAudioTrack = "jpn"
        val selectedSubtitleTrack = "eng-ass"
        val watched = true

        override fun beginPlaybackPass() {
            commands += "beginPlaybackPass"
            playbackPass += 1
        }

        override fun seekTo(positionMs: Long) {
            commands += "seekTo:$positionMs"
            this.positionMs = positionMs
        }

        override fun play() {
            commands += "play"
            isPlaying = true
        }
    }
}
