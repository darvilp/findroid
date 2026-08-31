package dev.jdtech.jellyfin.presentation.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.presentation.settings.PersistentSynchronizationEditPolicy
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.settings.presentation.models.PreferenceMpvSynchronization
import dev.jdtech.jellyfin.settings.R as SettingsR
import dev.jdtech.jellyfin.ui.dialogs.SynchronizationValueEditor

@Composable
fun SettingsMpvSynchronizationCard(
    preference: PreferenceMpvSynchronization,
    onSave: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    val millisecondsSuffix = stringResource(SettingsR.string.ms)
    Surface(onClick = { showDialog = true }, modifier = modifier) {
        Column(Modifier.padding(MaterialTheme.spacings.medium)) {
            Text(stringResource(preference.nameStringResource))
            Text("${preference.valueMs} $millisecondsSuffix")
        }
    }
    if (showDialog) {
        val policy = remember(preference.valueMs) { PersistentSynchronizationEditPolicy(preference.valueMs) }
        var draftValueMs by remember(preference.valueMs) { mutableStateOf(preference.valueMs) }
        var editing by remember { mutableStateOf(false) }
        Dialog(onDismissRequest = { showDialog = false }) {
            Surface(shape = MaterialTheme.shapes.medium) {
                Column(
                    Modifier.padding(MaterialTheme.spacings.large),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
                ) {
                    Text(stringResource(preference.nameStringResource))
                    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default)) {
                        Button(onClick = { editing = true }) {
                            Text(stringResource(SettingsR.string.edit))
                        }
                        Button(onClick = {
                            onSave(policy.save())
                            showDialog = false
                        }) { Text(stringResource(SettingsR.string.save)) }
                        Button(onClick = { policy.cancel(); showDialog = false }) {
                            Text(stringResource(SettingsR.string.cancel))
                        }
                    }
                }
            }
        }
        if (editing) {
            SynchronizationValueEditor(
                valueMs = draftValueMs,
                onValue = {
                    draftValueMs = it
                    policy.edit(it)
                },
                onDone = { editing = false },
            )
        }
    }
}
