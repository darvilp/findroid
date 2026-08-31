package dev.jdtech.jellyfin.settings.domain

import java.io.FileNotFoundException
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvSynchronizationConfigStoreTest {
    @Test
    fun `atomic read propagates file not found when config exists`() {
        val directory = Files.createTempDirectory("mpv-config-store-test").toFile()
        try {
            val configFile = directory.resolve("mpv.conf").apply { writeText("vo=gpu\n") }
            val readFailure = FileNotFoundException("Permission denied")

            val thrown =
                assertThrows(FileNotFoundException::class.java) {
                    readAtomicConfigText(configFile) { throw readFailure }
                }

            assertEquals(readFailure, thrown)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `atomic read propagates file not found when backup exists`() {
        val directory = Files.createTempDirectory("mpv-config-store-test").toFile()
        try {
            val configFile = directory.resolve("mpv.conf")
            directory.resolve("mpv.conf.bak").writeText("vo=gpu\n")
            val readFailure = FileNotFoundException("Permission denied")

            val thrown =
                assertThrows(FileNotFoundException::class.java) {
                    readAtomicConfigText(configFile) { throw readFailure }
                }

            assertEquals(readFailure, thrown)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `atomic read returns empty when config and backup are absent`() {
        val directory = Files.createTempDirectory("mpv-config-store-test").toFile()
        try {
            val configFile = directory.resolve("mpv.conf")

            assertEquals(
                "",
                readAtomicConfigText(configFile) { throw FileNotFoundException("Missing") },
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun `one kind save returns read failed without committing after read failure`() {
        val readFailure = FileNotFoundException("Permission denied")
        val storage = FailingReadConfigStorage(readFailure)
        val store = MpvSynchronizationConfigStore(storage)

        val thrown =
            assertThrows(MpvSynchronizationConfigException.ReadFailed::class.java) {
                store.write(MpvSynchronizationKind.AUDIO, 300L).getOrThrow()
            }

        assertEquals(readFailure, thrown.cause)
        assertEquals(0, storage.writeCount)
    }

    @Test
    fun `read defaults uses storage recovered content`() {
        val storage = RecoveringConfigStorage(recoveredText = "audio-delay=0.125\nsub-delay=-0.050\n")
        val store = MpvSynchronizationConfigStore(storage)

        assertEquals(
            MpvSynchronizationDefaults(audioMs = 125L, subtitleMs = -50L),
            store.readDefaults().getOrThrow(),
        )
    }

    @Test
    fun `one kind save preserves recovered other kind and unrelated content`() {
        val recovered = "# user option\nvo=gpu\naudio-delay=0.125\nsub-delay=-0.050\n"
        val storage = RecoveringConfigStorage(recoveredText = recovered)
        val store = MpvSynchronizationConfigStore(storage)

        assertEquals(
            MpvSynchronizationDefaults(audioMs = 300L, subtitleMs = -50L),
            store.write(MpvSynchronizationKind.AUDIO, 300L).getOrThrow(),
        )

        val committed = requireNotNull(storage.committedText)
        assertTrue(committed.startsWith(recovered))
        assertTrue(committed.contains("audio-delay=0.300"))
        assertTrue(committed.contains("sub-delay=-0.050"))
    }

    private class RecoveringConfigStorage(private val recoveredText: String) :
        MpvSynchronizationConfigStorage {
        var committedText: String? = null
            private set

        override fun readText(): String = recoveredText

        override fun writeText(text: String) {
            committedText = text
        }
    }

    private class FailingReadConfigStorage(private val readFailure: Throwable) :
        MpvSynchronizationConfigStorage {
        var writeCount = 0
            private set

        override fun readText(): String = throw readFailure

        override fun writeText(text: String) {
            writeCount += 1
        }
    }
}
