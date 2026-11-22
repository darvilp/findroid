package dev.jdtech.jellyfin.player.local.mpv

import java.net.URI

internal fun externalSubtitleStreamIndex(
    filename: String,
    registered: List<Pair<String, Int?>>,
): Int? =
    registered
        .singleOrNull { (location, _) ->
            filename == location ||
                localSubtitlePath(filename)?.let { it == localSubtitlePath(location) } == true
        }
        ?.second

private fun localSubtitlePath(location: String): String? =
    when {
        location.startsWith("/") -> location
        location.startsWith("file:") -> runCatching { URI(location).path }.getOrNull()
        else -> null
    }
