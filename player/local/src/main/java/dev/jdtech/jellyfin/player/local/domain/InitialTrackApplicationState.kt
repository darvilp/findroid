package dev.jdtech.jellyfin.player.local.domain

import java.util.UUID

class InitialTrackApplicationState {
    private val appliedTypes = mutableMapOf<UUID, MutableSet<Int>>()
    private val loggedFailures = mutableMapOf<UUID, MutableSet<Int>>()

    fun reset(itemId: UUID) {
        appliedTypes.remove(itemId)
        loggedFailures.remove(itemId)
    }

    fun isApplied(itemId: UUID, trackType: Int): Boolean =
        trackType in appliedTypes[itemId].orEmpty()

    fun markApplied(itemId: UUID, trackType: Int) {
        appliedTypes.getOrPut(itemId, ::mutableSetOf).add(trackType)
    }

    fun markFailureLogged(itemId: UUID, trackType: Int): Boolean =
        loggedFailures.getOrPut(itemId, ::mutableSetOf).add(trackType)
}
