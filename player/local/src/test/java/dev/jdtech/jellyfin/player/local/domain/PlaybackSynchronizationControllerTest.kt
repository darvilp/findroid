package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.settings.domain.MpvSynchronizationDefaults
import dev.jdtech.jellyfin.settings.domain.MpvSynchronizationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSynchronizationControllerTest {
    @Test
    fun `different media clears temporary values while the same media preserves them`() {
        val controller = PlaybackSynchronizationController()
        controller.load("episode-1", MpvSynchronizationDefaults(100L, 200L))
        controller.setEffective(MpvSynchronizationKind.AUDIO, 175L)

        controller.onMediaChanged("episode-1")
        assertEquals(175L, controller.effective(MpvSynchronizationKind.AUDIO))

        controller.onMediaChanged("episode-2")
        assertFalse(controller.isInitializedFor("episode-2"))
        assertNull(controller.effectiveOrNull(MpvSynchronizationKind.AUDIO))
    }

    @Test
    fun `subtitle synchronization requires a selected primary subtitle`() {
        val controller = PlaybackSynchronizationController()

        assertFalse(controller.canEdit(MpvSynchronizationKind.SUBTITLE, hasPrimarySubtitle = false))
        assertTrue(controller.canEdit(MpvSynchronizationKind.SUBTITLE, hasPrimarySubtitle = true))
        assertTrue(controller.canEdit(MpvSynchronizationKind.AUDIO, hasPrimarySubtitle = false))
    }

    @Test
    fun `same media reload reapplies temporary effective values`() {
        val controller = PlaybackSynchronizationController()
        controller.load("episode-1", MpvSynchronizationDefaults(100L, 200L))
        controller.setEffective(MpvSynchronizationKind.AUDIO, 175L)
        controller.setEffective(MpvSynchronizationKind.SUBTITLE, -50L)

        assertEquals(
            MpvSynchronizationDefaults(175L, -50L),
            controller.onFileLoaded(
                mediaId = "episode-1",
                defaults = MpvSynchronizationDefaults(100L, 200L),
                observed = MpvSynchronizationDefaults(100L, 200L),
            ),
        )
    }

    @Test
    fun `different media reload uses observed effective properties and resets temporary values`() {
        val controller = PlaybackSynchronizationController()
        controller.load("episode-1", MpvSynchronizationDefaults(100L, 200L))
        controller.setEffective(MpvSynchronizationKind.AUDIO, 175L)

        assertEquals(
            MpvSynchronizationDefaults(-25L, 300L),
            controller.onFileLoaded(
                mediaId = "episode-2",
                defaults = MpvSynchronizationDefaults(-25L, 300L),
                observed = MpvSynchronizationDefaults(-25L, 300L),
            ),
        )
        assertEquals(-25L, controller.baseline(MpvSynchronizationKind.AUDIO))
    }

    @Test
    fun `promoting one effective value preserves the other baseline`() {
        val controller = PlaybackSynchronizationController()
        controller.load("episode-1", MpvSynchronizationDefaults(100L, 200L))
        controller.setEffective(MpvSynchronizationKind.AUDIO, 175L)

        controller.promote(MpvSynchronizationKind.AUDIO)

        assertEquals(175L, controller.baseline(MpvSynchronizationKind.AUDIO))
        assertEquals(200L, controller.baseline(MpvSynchronizationKind.SUBTITLE))
    }

    @Test
    fun `new controller and changed media stay unavailable until file loaded`() {
        val controller = PlaybackSynchronizationController()

        assertFalse(controller.isInitializedFor("episode-1"))
        controller.onMediaChanged("episode-1")
        assertFalse(controller.isInitializedFor("episode-1"))

        controller.onFileLoaded(
            mediaId = "episode-1",
            defaults = MpvSynchronizationDefaults(100L, 200L),
            observed = MpvSynchronizationDefaults(100L, 200L),
        )

        assertTrue(controller.isInitializedFor("episode-1"))
    }

    @Test
    fun `save completion promotes captured value while preserving later live adjustment`() {
        val controller = PlaybackSynchronizationController()
        controller.load("episode-1", MpvSynchronizationDefaults(100L, 200L))
        controller.setEffective(MpvSynchronizationKind.AUDIO, 175L)
        val capturedForSave = controller.effective(MpvSynchronizationKind.AUDIO)
        val session = controller.sessionToken("episode-1")!!
        controller.setEffective(MpvSynchronizationKind.AUDIO, 225L)

        assertTrue(
            controller.promoteIfCurrent(
                mediaId = "episode-1",
                sessionToken = session,
                kind = MpvSynchronizationKind.AUDIO,
                savedValueMs = capturedForSave,
            )
        )

        assertEquals(175L, controller.baseline(MpvSynchronizationKind.AUDIO))
        assertEquals(225L, controller.effective(MpvSynchronizationKind.AUDIO))
        assertEquals(200L, controller.baseline(MpvSynchronizationKind.SUBTITLE))
    }

    @Test
    fun `save completion cannot promote a later playback session`() {
        val controller = PlaybackSynchronizationController()
        controller.load("episode-1", MpvSynchronizationDefaults(100L, 200L))
        val oldSession = controller.sessionToken("episode-1")!!
        controller.onMediaChanged("episode-2")
        controller.onFileLoaded(
            mediaId = "episode-2",
            defaults = MpvSynchronizationDefaults(300L, 400L),
            observed = MpvSynchronizationDefaults(300L, 400L),
        )

        assertFalse(
            controller.promoteIfCurrent(
                mediaId = "episode-1",
                sessionToken = oldSession,
                kind = MpvSynchronizationKind.AUDIO,
                savedValueMs = 175L,
            )
        )
        assertEquals(300L, controller.baseline(MpvSynchronizationKind.AUDIO))
    }
}
