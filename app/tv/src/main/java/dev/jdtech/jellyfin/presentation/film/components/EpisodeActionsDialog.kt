package dev.jdtech.jellyfin.presentation.film.components

import android.view.KeyEvent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.presentation.theme.spacings

@Composable
fun EpisodeActionsDialog(
    episode: FindroidEpisode,
    onTogglePlayed: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val actionFocusRequester = remember { FocusRequester() }
    val confirmationKeyGuard = remember { DialogConfirmationKeyGuard() }

    LaunchedEffect(Unit) { actionFocusRequester.requestFocus() }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.widthIn(min = 420.dp, max = 640.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(MaterialTheme.spacings.medium)) {
                Text(
                    text =
                        stringResource(
                            CoreR.string.episode_name,
                            episode.indexNumber,
                            episode.name,
                        ),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(modifier = Modifier.height(MaterialTheme.spacings.medium))
                Button(
                    onClick = onTogglePlayed,
                    modifier =
                        Modifier.focusRequester(actionFocusRequester).onPreviewKeyEvent { event ->
                            val nativeEvent = event.nativeKeyEvent
                            confirmationKeyGuard.shouldConsume(
                                confirmKey =
                                    nativeEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                                        nativeEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
                                        nativeEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER,
                                keyDown = nativeEvent.action == KeyEvent.ACTION_DOWN,
                            )
                        },
                ) {
                    Icon(
                        painter = painterResource(CoreR.drawable.ic_check),
                        contentDescription = null,
                    )
                    Spacer(modifier = Modifier.width(MaterialTheme.spacings.small))
                    Text(
                        text =
                            stringResource(
                                when (episode.played) {
                                    true -> CoreR.string.unmark_as_played
                                    false -> CoreR.string.mark_as_played
                                }
                            )
                    )
                }
            }
        }
    }
}

internal class DialogConfirmationKeyGuard {
    private var awaitingOpeningKeyUp = true

    fun shouldConsume(confirmKey: Boolean, keyDown: Boolean): Boolean {
        if (!confirmKey || !awaitingOpeningKeyUp) return false

        if (!keyDown) awaitingOpeningKeyUp = false
        return true
    }
}
