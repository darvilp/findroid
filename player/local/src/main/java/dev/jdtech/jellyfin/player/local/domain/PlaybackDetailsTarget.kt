package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import java.util.UUID

sealed interface PlaybackDetailsTarget {
    data class Movie(val movieId: UUID) : PlaybackDetailsTarget

    data class Season(val seasonId: UUID) : PlaybackDetailsTarget
}

internal object PlaybackDetailsTargetResolver {
    fun resolve(itemId: UUID, items: List<FindroidItem>): PlaybackDetailsTarget? =
        items.firstOrNull { item -> item.id == itemId }?.let(::resolve)

    fun resolve(item: FindroidItem): PlaybackDetailsTarget? =
        when (item) {
            is FindroidEpisode -> PlaybackDetailsTarget.Season(item.seasonId)
            is FindroidMovie -> PlaybackDetailsTarget.Movie(item.id)
            else -> null
        }
}
