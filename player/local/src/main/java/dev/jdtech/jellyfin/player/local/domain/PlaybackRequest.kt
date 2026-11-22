package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.models.InitialTrackSelection
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemKind

internal data class PlaybackRequest(
    val itemId: UUID,
    val kind: BaseItemKind,
    val startFromBeginning: Boolean,
    val trackSelection: InitialTrackSelection?,
    val positionMs: Long? = null,
) {
    fun forRetry(
        activeId: UUID,
        activeKind: BaseItemKind,
        positionMs: Long?,
        selection: InitialTrackSelection?,
    ): PlaybackRequest =
        PlaybackRequest(
            itemId = activeId,
            kind = activeKind,
            startFromBeginning = false,
            trackSelection = selection ?: trackSelection.takeIf { activeId == itemId },
            positionMs = positionMs?.takeIf { it >= 0L },
        )
}
