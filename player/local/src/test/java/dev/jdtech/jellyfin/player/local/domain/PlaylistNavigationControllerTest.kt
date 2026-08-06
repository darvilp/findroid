package dev.jdtech.jellyfin.player.local.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistNavigationControllerTest {
    @Test
    fun `availability reflects all adjacent playlist item combinations`() {
        val navigation =
            listOf(
                    false to false,
                    false to true,
                    true to false,
                    true to true,
                )
                .map { (hasPrevious, hasNext) ->
                    PlaylistNavigationController()
                        .state(
                            RecordingPlaylistNavigationTarget(
                                hasPreviousMediaItem = hasPrevious,
                                hasNextMediaItem = hasNext,
                            )
                        )
                }

        assertEquals(
            listOf(
                PlaylistNavigationState(canGoPrevious = false, canGoNext = false),
                PlaylistNavigationState(canGoPrevious = false, canGoNext = true),
                PlaylistNavigationState(canGoPrevious = true, canGoNext = false),
                PlaylistNavigationState(canGoPrevious = true, canGoNext = true),
            ),
            navigation,
        )
    }

    @Test
    fun `an adjacent item is unavailable when its player command is unavailable`() {
        val navigation =
            PlaylistNavigationController()
                .state(
                    RecordingPlaylistNavigationTarget(
                        hasPreviousMediaItem = true,
                        hasNextMediaItem = true,
                        previousCommandAvailable = true,
                        nextCommandAvailable = false,
                    )
                )

        assertEquals(
            PlaylistNavigationState(canGoPrevious = true, canGoNext = false),
            navigation,
        )
    }

    @Test
    fun `previous always moves to the previous media item`() {
        val target =
            RecordingPlaylistNavigationTarget(
                hasPreviousMediaItem = true,
                hasNextMediaItem = true,
            )

        val navigated =
            PlaylistNavigationController().navigate(PlaylistNavigationDirection.Previous, target)

        assertTrue(navigated)
        assertEquals(listOf("previousMediaItem"), target.commands)
    }

    @Test
    fun `next moves to the next media item`() {
        val target =
            RecordingPlaylistNavigationTarget(
                hasPreviousMediaItem = true,
                hasNextMediaItem = true,
            )

        val navigated =
            PlaylistNavigationController().navigate(PlaylistNavigationDirection.Next, target)

        assertTrue(navigated)
        assertEquals(listOf("nextMediaItem"), target.commands)
    }

    @Test
    fun `an unavailable direction does nothing`() {
        val target =
            RecordingPlaylistNavigationTarget(
                hasPreviousMediaItem = false,
                hasNextMediaItem = false,
            )

        assertFalse(
            PlaylistNavigationController()
                .navigate(PlaylistNavigationDirection.Previous, target)
        )
        assertFalse(
            PlaylistNavigationController().navigate(PlaylistNavigationDirection.Next, target)
        )
        assertEquals(emptyList<String>(), target.commands)
    }

    @Test
    fun `navigation rechecks availability after state was observed`() {
        val target =
            RecordingPlaylistNavigationTarget(
                hasPreviousMediaItem = false,
                hasNextMediaItem = true,
            )
        val controller = PlaylistNavigationController()

        assertTrue(controller.state(target).canGoNext)
        target.hasNextMediaItem = false

        assertFalse(controller.navigate(PlaylistNavigationDirection.Next, target))
        assertEquals(emptyList<String>(), target.commands)
    }

    @Test
    fun `navigation preserves a paused session when the backend starts the new item`() {
        val target =
            RecordingPlaylistNavigationTarget(
                hasPreviousMediaItem = false,
                hasNextMediaItem = true,
                playWhenReady = false,
                forcePlayOnNavigation = true,
            )

        PlaylistNavigationController().navigate(PlaylistNavigationDirection.Next, target)

        assertFalse(target.playWhenReady)
    }

    private class RecordingPlaylistNavigationTarget(
        var hasPreviousMediaItem: Boolean,
        var hasNextMediaItem: Boolean,
        var previousCommandAvailable: Boolean = true,
        var nextCommandAvailable: Boolean = true,
        var playWhenReady: Boolean = true,
        private val forcePlayOnNavigation: Boolean = false,
    ) : PlaylistNavigationTarget {
        val commands = mutableListOf<String>()

        override fun hasPreviousMediaItem(): Boolean = hasPreviousMediaItem

        override fun hasNextMediaItem(): Boolean = hasNextMediaItem

        override fun isPreviousMediaItemCommandAvailable(): Boolean = previousCommandAvailable

        override fun isNextMediaItemCommandAvailable(): Boolean = nextCommandAvailable

        override fun seekToPreviousMediaItem() {
            commands += "previousMediaItem"
            if (forcePlayOnNavigation) playWhenReady = true
        }

        override fun seekToNextMediaItem() {
            commands += "nextMediaItem"
            if (forcePlayOnNavigation) playWhenReady = true
        }

        override fun playWhenReady(): Boolean = playWhenReady

        override fun play() {
            playWhenReady = true
        }

        override fun pause() {
            playWhenReady = false
        }
    }
}
