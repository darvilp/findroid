package dev.jdtech.jellyfin.presentation.settings

import dev.jdtech.jellyfin.settings.domain.models.Preference as BackendPreference
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceGroup
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceMultiSelect
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceSelect
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsPreferenceFocusTest {
    @Test
    fun `focused select resolves the refreshed preference value`() {
        val original = selectPreference(value = "mpv")
        val refreshed = selectPreference(value = "exoplayer")
        val key = original.focusKey()

        val resolved = listOf(PreferenceGroup(preferences = listOf(refreshed))).findPreference(key)

        assertEquals("exoplayer", (resolved as PreferenceSelect).value)
    }

    @Test
    fun `focused multi select resolves the refreshed preference value`() {
        val original = multiSelectPreference(value = setOf("INTRO"))
        val refreshed = multiSelectPreference(value = setOf("INTRO", "OUTRO"))
        val key = original.focusKey()

        val resolved = listOf(PreferenceGroup(preferences = listOf(refreshed))).findPreference(key)

        assertEquals(setOf("INTRO", "OUTRO"), (resolved as PreferenceMultiSelect).value)
    }

    private fun selectPreference(value: String) =
        PreferenceSelect(
            nameStringResource = 1,
            backendPreference = BackendPreference("player_backend", "exoplayer"),
            options = 2,
            optionValues = 3,
            value = value,
        )

    private fun multiSelectPreference(value: Set<String>) =
        PreferenceMultiSelect(
            nameStringResource = 4,
            backendPreference = BackendPreference("segment_types", emptySet()),
            options = 5,
            optionValues = 6,
            value = value,
        )
}
