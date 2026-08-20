package dev.jdtech.jellyfin.presentation.film.components

import dev.jdtech.jellyfin.models.PlaybackTrackChoice
import java.util.Locale

enum class PreplayTrackKind {
    Audio,
    Subtitle,
}

data class PreplayTrackLabel(val compact: String, val primary: String, val secondary: String?)

fun PlaybackTrackChoice.preplayLabel(
    kind: PreplayTrackKind,
    locale: Locale = Locale.getDefault(),
): PreplayTrackLabel {
    val languageLabel = language?.displayLanguage(locale)
    val fallback = "${kind.name} ${ordinalWithinType + 1}"
    val meaningfulTitle = title?.trim()?.takeIf(String::isNotBlank)
    val primary =
        when {
            meaningfulTitle == null ->
                languageLabel ?: displayTitle.takeIf(String::isNotBlank) ?: fallback
            languageLabel == null || meaningfulTitle.contains(languageLabel, ignoreCase = true) ->
                meaningfulTitle
            else -> "$languageLabel — $meaningfulTitle"
        }
    val channelLabel = channelLayout?.displayChannelLayout()
    val technical =
        buildList {
                codec?.uppercase(locale)?.takeIf(String::isNotBlank)?.let(::add)
                channelLabel?.let(::add)
                if (isExternal) add("External")
                if (isDefault) add("Default")
                if (isForced) add("Forced")
                if (isHearingImpaired) add("SDH")
            }
            .distinctBy { it.lowercase(locale) }
            .filterNot { primary.contains(it, ignoreCase = true) }

    val compactBase = languageLabel ?: meaningfulTitle ?: displayTitle.takeIf(String::isNotBlank) ?: fallback
    val compactMetadata =
        when (kind) {
            PreplayTrackKind.Audio -> listOfNotNull(channelLabel)
            PreplayTrackKind.Subtitle ->
                buildList {
                    codec?.uppercase(locale)?.takeIf(String::isNotBlank)?.let(::add)
                    if (isForced) add("Forced")
                    if (isHearingImpaired) add("SDH")
                }
        }

    return PreplayTrackLabel(
        compact = (listOf(compactBase) + compactMetadata).distinct().joinToString(" · "),
        primary = primary,
        secondary = technical.joinToString(" · ").ifBlank { null },
    )
}

private fun String.displayChannelLayout(): String? {
    val normalized = trim()
    if (normalized.isBlank()) return null
    return when {
        normalized.equals("mono", ignoreCase = true) -> "Mono"
        normalized.equals("stereo", ignoreCase = true) -> "Stereo"
        else -> normalized
    }
}

private fun String.displayLanguage(locale: Locale): String? {
    val normalized = trim().replace('_', '-')
    if (normalized.isBlank()) return null
    val display = Locale.forLanguageTag(normalized).getDisplayLanguage(locale)
    return display.takeUnless { it.isBlank() || it.equals(normalized, ignoreCase = true) }
        ?: normalized.uppercase(locale)
}
