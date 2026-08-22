package dev.jdtech.jellyfin.player.local.mpv

internal fun isHardwareDecodingActive(currentHwdec: String?): Boolean? =
    currentHwdec?.let { it != SOFTWARE_DECODING }

internal fun requestedHwdec(enabled: Boolean, configuredHwdec: String): String =
    when {
        !enabled -> SOFTWARE_DECODING
        configuredHwdec != SOFTWARE_DECODING -> configuredHwdec
        else -> DEFAULT_HARDWARE_DECODING
    }

private const val SOFTWARE_DECODING = "no"
private const val DEFAULT_HARDWARE_DECODING = "mediacodec"
