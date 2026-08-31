package dev.jdtech.jellyfin.settings.domain

import android.app.Application
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Files
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MpvSynchronizationConfigStore internal constructor(
    private val configStorage: MpvSynchronizationConfigStorage,
) {
    @Inject
    constructor(application: Application) :
        this(AtomicMpvSynchronizationConfigStorage(File(application.filesDir, "mpv/mpv.conf")))

    @Synchronized
    fun readDefaults(): Result<MpvSynchronizationDefaults> {
        val text =
            runCatching { configStorage.readText() }
                .getOrElse {
                    return Result.failure(MpvSynchronizationConfigException.ReadFailed(it))
                }
        return MpvSynchronizationConfigCodec.readDefaults(text)
    }

    @Synchronized
    fun writeDefaults(defaults: MpvSynchronizationDefaults): Result<Unit> {
        val current =
            runCatching { configStorage.readText() }
                .getOrElse {
                    return Result.failure(MpvSynchronizationConfigException.ReadFailed(it))
                }
        val updated =
            MpvSynchronizationConfigCodec.writeDefaults(current, defaults).getOrElse {
                return Result.failure(it)
            }
        return runCatching {
                configStorage.writeText(updated)
            }
            .recoverCatching { throw MpvSynchronizationConfigException.WriteFailed(it) }
    }

    @Synchronized
    fun write(kind: MpvSynchronizationKind, valueMs: Long): Result<MpvSynchronizationDefaults> {
        val updated = readDefaults().map { it.withValue(kind, valueMs) }.getOrElse {
            return Result.failure(it)
        }
        return writeDefaults(updated).map { updated }
    }
}

internal interface MpvSynchronizationConfigStorage {
    fun readText(): String

    fun writeText(text: String)
}

private class AtomicMpvSynchronizationConfigStorage(private val configFile: File) :
    MpvSynchronizationConfigStorage {
    private val atomicFile = AtomicFile(configFile)

    override fun readText(): String = readAtomicConfigText(configFile, atomicFile::openRead)

    override fun writeText(text: String) {
        configFile.parentFile?.mkdirs()
        val output = atomicFile.startWrite()
        try {
            output.write(text.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            atomicFile.failWrite(output)
            throw error
        }
    }
}

internal fun readAtomicConfigText(
    configFile: File,
    openRead: () -> InputStream,
): String =
    try {
        openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
    } catch (error: FileNotFoundException) {
        val backupFile = File(configFile.path + ".bak")
        if (Files.notExists(configFile.toPath()) && Files.notExists(backupFile.toPath())) {
            ""
        } else {
            throw error
        }
    }
