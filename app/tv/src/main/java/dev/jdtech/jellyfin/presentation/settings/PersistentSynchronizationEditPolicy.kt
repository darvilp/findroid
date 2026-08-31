package dev.jdtech.jellyfin.presentation.settings

internal class PersistentSynchronizationEditPolicy(initialValueMs: Long) {
    private var draftValueMs = initialValueMs

    fun edit(valueMs: Long) {
        draftValueMs = valueMs
    }

    fun cancel(): Long? = null

    fun save(): Long = draftValueMs
}
