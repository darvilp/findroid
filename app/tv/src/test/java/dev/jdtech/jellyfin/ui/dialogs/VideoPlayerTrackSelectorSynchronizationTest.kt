package dev.jdtech.jellyfin.ui.dialogs

import androidx.media3.common.C
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoPlayerTrackSelectorSynchronizationTest {
    @Test
    fun `synchronization entry is available for audio and subtitle tracks only with callback`() {
        assertTrue(trackDialogShowsSynchronization(C.TRACK_TYPE_AUDIO, synchronizationAvailable = true))
        assertTrue(trackDialogShowsSynchronization(C.TRACK_TYPE_TEXT, synchronizationAvailable = true))
        assertFalse(trackDialogShowsSynchronization(C.TRACK_TYPE_VIDEO, synchronizationAvailable = true))
        assertFalse(trackDialogShowsSynchronization(C.TRACK_TYPE_AUDIO, synchronizationAvailable = false))
    }
}
