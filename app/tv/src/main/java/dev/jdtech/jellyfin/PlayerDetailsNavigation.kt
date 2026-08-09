package dev.jdtech.jellyfin

import dev.jdtech.jellyfin.player.local.domain.PlaybackDetailsTarget

internal fun playerDetailsRoute(target: PlaybackDetailsTarget): Any =
    when (target) {
        is PlaybackDetailsTarget.Movie -> MovieRoute(target.movieId.toString())
        is PlaybackDetailsTarget.Season -> SeasonRoute(target.seasonId.toString())
    }
