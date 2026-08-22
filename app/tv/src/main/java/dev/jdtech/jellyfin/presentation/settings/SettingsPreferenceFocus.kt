package dev.jdtech.jellyfin.presentation.settings

import dev.jdtech.jellyfin.settings.presentation.models.Preference
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceCategory
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceGroup
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceMultiSelect
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceSelect
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceSwitch

internal data class SettingsPreferenceFocusKey(
    val type: SettingsPreferenceType,
    val nameStringResource: Int,
    val backendName: String?,
)

internal enum class SettingsPreferenceType {
    Category,
    Switch,
    Select,
    MultiSelect,
    Other,
}

internal fun Preference.focusKey(): SettingsPreferenceFocusKey =
    when (this) {
        is PreferenceCategory ->
            SettingsPreferenceFocusKey(
                type = SettingsPreferenceType.Category,
                nameStringResource = nameStringResource,
                backendName = null,
            )
        is PreferenceSwitch ->
            SettingsPreferenceFocusKey(
                type = SettingsPreferenceType.Switch,
                nameStringResource = nameStringResource,
                backendName = backendPreference.backendName,
            )
        is PreferenceSelect ->
            SettingsPreferenceFocusKey(
                type = SettingsPreferenceType.Select,
                nameStringResource = nameStringResource,
                backendName = backendPreference.backendName,
            )
        is PreferenceMultiSelect ->
            SettingsPreferenceFocusKey(
                type = SettingsPreferenceType.MultiSelect,
                nameStringResource = nameStringResource,
                backendName = backendPreference.backendName,
            )
        else ->
            SettingsPreferenceFocusKey(
                type = SettingsPreferenceType.Other,
                nameStringResource = nameStringResource,
                backendName = null,
            )
    }

internal fun List<PreferenceGroup>.findPreference(
    key: SettingsPreferenceFocusKey?
): Preference? =
    key?.let { focusKey ->
        asSequence()
            .flatMap { it.preferences.asSequence() }
            .firstOrNull { it.focusKey() == focusKey }
    }
