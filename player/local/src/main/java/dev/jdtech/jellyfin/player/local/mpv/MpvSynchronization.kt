package dev.jdtech.jellyfin.player.local.mpv

import dev.jdtech.jellyfin.settings.domain.MpvSynchronizationKind
import dev.jdtech.jellyfin.settings.domain.MpvSynchronizationValue
import java.math.BigDecimal

internal interface MpvSynchronization {
    fun getSynchronization(kind: MpvSynchronizationKind): Long?

    fun setSynchronization(kind: MpvSynchronizationKind, valueMs: Long)

    fun addFileLoadedListener(listener: () -> Unit)

    fun removeFileLoadedListener(listener: () -> Unit)
}

internal fun mpvSynchronizationProperty(kind: MpvSynchronizationKind): String =
    when (kind) {
        MpvSynchronizationKind.AUDIO -> "audio-delay"
        MpvSynchronizationKind.SUBTITLE -> "sub-delay"
    }

internal fun mpvSynchronizationSeconds(valueMs: Long): String =
    MpvSynchronizationValue.formatMpvSeconds(valueMs)

internal fun mpvSynchronizationMilliseconds(value: String?): Long? =
    value?.trim()?.takeIf(MPV_SECONDS_PATTERN::matches)?.let { seconds ->
        runCatching { BigDecimal(seconds).movePointRight(3).longValueExact() }.getOrNull()
    }

private val MPV_SECONDS_PATTERN = Regex("^[+-]?(?:0|[1-9]\\d*)(?:\\.\\d+)?$")
