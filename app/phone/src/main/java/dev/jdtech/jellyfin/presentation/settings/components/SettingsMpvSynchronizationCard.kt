package dev.jdtech.jellyfin.presentation.settings.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.jdtech.jellyfin.settings.R
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceMpvSynchronization

@Composable
fun SettingsMpvSynchronizationCard(
    preference: PreferenceMpvSynchronization,
    onUpdate: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    val suffix = stringResource(R.string.ms)
    SettingsNumberInputCard(
        preference = preference,
        text = "${preference.valueMs} $suffix",
        onClick = { showDialog = true },
        modifier = modifier,
    )
    if (showDialog) {
        SettingsNumberInputDialog(
            preference = preference,
            initialValue = preference.valueMs.toString(),
            onUpdate = { value ->
                value.toLongOrNull()?.let {
                    showDialog = false
                    onUpdate(it)
                }
            },
            onDismissRequest = { showDialog = false },
            suffix = suffix,
            allowNegative = true,
        )
    }
}
