package dev.jdtech.jellyfin.player.local.mpv

import android.os.SystemClock
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.test.platform.app.InstrumentationRegistry
import dev.jdtech.mpv.MPVLib
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

/** Runs the real Media3 adapter and bundled native MPV, without a server or physical audio output. */
class MPVPlayerReplacementTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var player: MPVPlayer
    private lateinit var nativePlayer: MPVLib
    private lateinit var fixtureDirectory: File
    private lateinit var media: List<MediaItem>

    @Before
    fun createPlayer() {
        fixtureDirectory = File(instrumentation.targetContext.cacheDir, "mpv-replacement-test")
        fixtureDirectory.mkdirs()
        media = List(4) { index ->
            val file = File(fixtureDirectory, "tone-$index.wav")
            val dataSize = 16000 * 2 * 30
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".toByteArray()).putInt(36 + dataSize).put("WAVEfmt ".toByteArray())
                .putInt(16).putShort(1).putShort(1).putInt(16000).putInt(32000)
                .putShort(2).putShort(16).put("data".toByteArray()).putInt(dataSize).array()
            file.outputStream().use { output ->
                output.write(header)
                output.write(ByteArray(dataSize))
            }
            MediaItem.Builder().setMediaId("item-$index").setUri(Uri.fromFile(file)).build()
        }
        onMain {
            player = MPVPlayer.Builder(instrumentation.targetContext)
                .setAudioAttributes(AudioAttributes.DEFAULT, false)
                .setAudioOutput("null")
                .setVideoOutput("null")
                .build()
            // Inspect the actual decoder as well as the adapter's intended timeline. A selected
            // native playlist index alone does not prove that MPV loaded that entry.
            nativePlayer = MPVPlayer::class.java.getDeclaredField("mpvLib")
                .apply { isAccessible = true }.get(player) as MPVLib
        }
    }

    @After
    fun releasePlayer() {
        if (::player.isInitialized) onMain { player.release() }
        if (::fixtureDirectory.isInitialized) fixtureDirectory.deleteRecursively()
    }

    @Test
    @Ignore("Deferred nonzero native startup: https://github.com/darvilp/findroid/issues/13")
    fun metadataRefreshBeforePreparePreservesRequestedEpisodeAndPosition() {
        onMain {
            player.setMediaItems(media.take(3).toMutableList(), 2, 2000L)
            player.replaceMediaItem(1, media[1])
            player.prepare()
        }
        awaitReady("item-2")
        onMain { assertTrue(player.currentPosition >= 1000L) }
    }

    @Test
    @Ignore("Deferred nonzero native startup: https://github.com/darvilp/findroid/issues/13")
    fun largerReplacementBeforePreparePreservesRequestedEpisode() {
        onMain {
            player.setMediaItems(media.take(3).toMutableList(), 2, 0L)
            player.replaceMediaItems(0, 1, mutableListOf(media[0], media[3]))
            assertEquals(4, player.mediaItemCount)
            assertEquals("item-2", player.getMediaItemAt(3).mediaId)
            player.prepare()
        }
        awaitReady("item-2")
        onMain { assertEquals(3, player.currentMediaItemIndex) }
    }

    @Test
    fun activeReplacementPreservesPlayingIntentAndCanStillPause() {
        startAt(0)
        onMain {
            player.play()
            player.replaceMediaItem(0, media[3])
            assertTrue(player.playWhenReady)
        }
        awaitReady("item-3")
        onMain {
            player.pause()
            assertEquals(false, player.playWhenReady)
            assertEquals(true, nativePlayer.getPropertyBoolean("pause"))
        }
    }

    @Test
    fun activeReplacementPreservesPausedIntent() {
        startAt(0)
        onMain {
            player.pause()
            player.replaceMediaItem(0, media[3])
            assertEquals(false, player.playWhenReady)
        }
        awaitReady("item-3")
        onMain {
            assertEquals(false, player.isPlaying)
            assertEquals(true, nativePlayer.getPropertyBoolean("pause"))
        }
    }

    @Test
    fun adjacentReplacementPreservesPlaybackAndUpdatedItemsAreNavigable() {
        startAt(1)
        onMain {
            player.play()
            // Same source metadata refresh, followed by genuinely changed queued sources.
            player.replaceMediaItem(2, media[2])
            player.replaceMediaItem(0, media[3])
            player.replaceMediaItem(2, media[3].buildUpon().setMediaId("new-next").build())
        }
        instrumentation.waitForIdleSync()
        onMain {
            assertEquals("item-1", player.currentMediaItem?.mediaId)
            assertEquals(1, player.currentMediaItemIndex)
            assertTrue(player.playWhenReady)
            player.seekToNextMediaItem()
        }
        awaitReady("new-next")
        onMain { player.seekToPreviousMediaItem() }
        awaitReady("item-1")
        onMain { player.seekToPreviousMediaItem() }
        awaitReady("item-3")
    }

    private fun startAt(index: Int) {
        onMain {
            // Production initializes only the requested item, then adds adjacent episodes.
            player.setMediaItems(mutableListOf(media[index]), 0, 0L)
            player.prepare()
            player.play()
        }
        awaitReady("item-$index")
        onMain {
            if (index > 0) player.addMediaItems(0, media.take(index).toMutableList())
            if (index < 2) player.addMediaItems(player.mediaItemCount, media.subList(index + 1, 3).toMutableList())
        }
        awaitReady("item-$index")
    }

    private fun awaitReady(mediaId: String) {
        // Shorter than the 30-second fixtures: natural EOF must never satisfy a wrong start.
        val deadline = SystemClock.elapsedRealtime() + 10000L
        while (SystemClock.elapsedRealtime() < deadline) {
            var ready = false
            onMain {
                ready = player.playbackState == Player.STATE_READY &&
                    player.currentMediaItem?.mediaId == mediaId &&
                    nativePlayer.getPropertyInt("playlist-playing-pos") == player.currentMediaItemIndex &&
                    nativePlayer.getPropertyString("path")?.let { Uri.parse(it).path } ==
                    player.currentMediaItem?.localConfiguration?.uri?.path
            }
            if (ready) return
            SystemClock.sleep(50L)
        }
        onMain {
            throw AssertionError("Expected ready $mediaId; adapter=${player.currentMediaItem?.mediaId}:${player.playbackState}, nativeIndex=${nativePlayer.getPropertyInt("playlist-playing-pos")}, nativePath=${nativePlayer.getPropertyString("path")}")
        }
    }

    private fun onMain(action: () -> Unit) {
        var failure: Throwable? = null
        instrumentation.runOnMainSync {
            try {
                action()
            } catch (error: Throwable) {
                failure = error
            }
        }
        failure?.let { throw it }
    }
}
