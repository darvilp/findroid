package dev.jdtech.jellyfin.settings.presentation.settings

import dev.jdtech.jellyfin.settings.presentation.models.Preference
import dev.jdtech.jellyfin.settings.domain.MpvSynchronizationKind

sealed interface SettingsAction {
    data object OnBackClick : SettingsAction

    data class OnUpdate(val preference: Preference) : SettingsAction

    data class OnUpdateMpvSynchronization(val kind: MpvSynchronizationKind, val valueMs: Long) :
        SettingsAction
}
