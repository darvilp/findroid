package dev.jdtech.jellyfin.player.local.domain

import dev.jdtech.jellyfin.settings.domain.MpvSynchronizationDefaults
import dev.jdtech.jellyfin.settings.domain.MpvSynchronizationKind
import dev.jdtech.jellyfin.settings.domain.MpvSynchronizationState

internal class PlaybackSynchronizationController {
    private var mediaId: String? = null
    private var state: MpvSynchronizationState? = null
    private var sessionToken = 0L

    fun load(mediaId: String, defaults: MpvSynchronizationDefaults) {
        this.mediaId = mediaId
        state = MpvSynchronizationState(defaults.audioMs, defaults.subtitleMs)
        sessionToken = Math.incrementExact(sessionToken)
    }

    fun onMediaChanged(mediaId: String) {
        if (this.mediaId == mediaId) return
        this.mediaId = mediaId
        state = null
    }

    fun onFileLoaded(
        mediaId: String,
        defaults: MpvSynchronizationDefaults,
        observed: MpvSynchronizationDefaults,
    ): MpvSynchronizationDefaults {
        if (isInitializedFor(mediaId)) {
            return effectiveDefaults()
        }
        load(mediaId, defaults)
        requireState().setEffective(MpvSynchronizationKind.AUDIO, observed.audioMs)
        requireState().setEffective(MpvSynchronizationKind.SUBTITLE, observed.subtitleMs)
        return effectiveDefaults()
    }

    fun isInitializedFor(mediaId: String): Boolean = this.mediaId == mediaId && state != null

    fun sessionToken(mediaId: String): Long? =
        sessionToken.takeIf { isInitializedFor(mediaId) }

    fun baseline(kind: MpvSynchronizationKind): Long = requireState().baseline(kind)

    fun effective(kind: MpvSynchronizationKind): Long = requireState().effective(kind)

    fun effectiveOrNull(kind: MpvSynchronizationKind): Long? = state?.effective(kind)

    fun setEffective(kind: MpvSynchronizationKind, valueMs: Long) {
        requireState().setEffective(kind, valueMs)
    }

    fun reset(kind: MpvSynchronizationKind) {
        requireState().reset(kind)
    }

    fun promote(kind: MpvSynchronizationKind) {
        requireState().promote(kind)
    }

    fun promote(kind: MpvSynchronizationKind, savedValueMs: Long) {
        val currentEffective = effectiveDefaults()
        val baselines =
            MpvSynchronizationDefaults(
                    audioMs = baseline(MpvSynchronizationKind.AUDIO),
                    subtitleMs = baseline(MpvSynchronizationKind.SUBTITLE),
                )
                .withValue(kind, savedValueMs)
        state = MpvSynchronizationState(baselines.audioMs, baselines.subtitleMs)
        requireState().setEffective(MpvSynchronizationKind.AUDIO, currentEffective.audioMs)
        requireState().setEffective(MpvSynchronizationKind.SUBTITLE, currentEffective.subtitleMs)
    }

    fun promoteIfCurrent(
        mediaId: String,
        sessionToken: Long,
        kind: MpvSynchronizationKind,
        savedValueMs: Long,
    ): Boolean {
        if (this.mediaId != mediaId || this.sessionToken != sessionToken || state == null) {
            return false
        }
        promote(kind, savedValueMs)
        return true
    }

    fun canEdit(kind: MpvSynchronizationKind, hasPrimarySubtitle: Boolean): Boolean =
        kind == MpvSynchronizationKind.AUDIO || hasPrimarySubtitle

    private fun effectiveDefaults() =
        MpvSynchronizationDefaults(
            audioMs = effective(MpvSynchronizationKind.AUDIO),
            subtitleMs = effective(MpvSynchronizationKind.SUBTITLE),
        )

    private fun requireState(): MpvSynchronizationState =
        checkNotNull(state) { "Synchronization is unavailable before a file is loaded" }
}
