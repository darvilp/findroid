package dev.jdtech.jellyfin.presentation.film

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.jdtech.jellyfin.PlayerRoute
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisodes
import dev.jdtech.jellyfin.core.presentation.dummy.dummySeason
import dev.jdtech.jellyfin.film.presentation.season.SeasonAction
import dev.jdtech.jellyfin.film.presentation.season.SeasonState
import dev.jdtech.jellyfin.film.presentation.season.SeasonViewModel
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.InitialTrackSelection
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.presentation.film.components.EpisodeActionsDialog
import dev.jdtech.jellyfin.presentation.film.components.PreplayTrackControls
import dev.jdtech.jellyfin.ui.components.EpisodeCard
import java.util.UUID

@Composable
fun SeasonScreen(
    seasonId: UUID,
    navigateToPlayer: (route: PlayerRoute) -> Unit,
    viewModel: SeasonViewModel = hiltViewModel(),
    trackSelectionViewModel: PreplayTrackSelectionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val trackSelectionState by trackSelectionViewModel.state.collectAsState()

    LaunchedEffect(seasonId) { viewModel.loadSeason(seasonId = seasonId) }
    val referenceEpisode = state.playbackStart?.episode
    LaunchedEffect(referenceEpisode?.id) {
        referenceEpisode?.id?.let(trackSelectionViewModel::load)
    }

    SeasonScreenLayout(
        state = state,
        trackSelectionState = trackSelectionState,
        onSelectAudio = trackSelectionViewModel::selectAudio,
        onSelectSubtitle = trackSelectionViewModel::selectSubtitle,
        onRetryTracks = { referenceEpisode?.id?.let(trackSelectionViewModel::load) },
        onAction = { action ->
            seasonPlaybackRoute(
                    state = state,
                    action = action,
                    initialTrackSelection =
                        trackSelectionState.initialTrackSelection(referenceEpisode?.id),
                )
                ?.let(navigateToPlayer)
            viewModel.onAction(action)
        },
    )
}

internal fun seasonPlaybackRoute(
    state: SeasonState,
    action: SeasonAction,
    initialTrackSelection: InitialTrackSelection? = null,
): PlayerRoute? =
    when (action) {
        is SeasonAction.Play ->
            containerPlaybackRoute(
                playbackStart = state.playbackStart,
                startFromBeginning = action.startFromBeginning,
                initialTrackSelection = initialTrackSelection,
            )
        is SeasonAction.NavigateToItem -> PlayerRoute.episode(itemId = action.item.id)
        else -> null
    }

internal fun episodePlayedAction(episode: FindroidEpisode): SeasonAction.SetEpisodePlayed =
    SeasonAction.SetEpisodePlayed(episodeId = episode.id, played = !episode.played)

@Composable
private fun SeasonScreenLayout(
    state: SeasonState,
    trackSelectionState: PreplayTrackSelectionState,
    onSelectAudio: (Int?) -> Unit,
    onSelectSubtitle: (Int?) -> Unit,
    onRetryTracks: () -> Unit,
    onAction: (SeasonAction) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        state.season?.let { season ->
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier =
                        Modifier.weight(1f)
                            .padding(
                                start = MaterialTheme.spacings.extraLarge,
                                top = MaterialTheme.spacings.large,
                                end = MaterialTheme.spacings.large,
                            )
                ) {
                    Text(text = season.name, style = MaterialTheme.typography.displayMedium)
                    Text(text = season.seriesName, style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(MaterialTheme.spacings.default))
                    Button(
                        onClick = { onAction(SeasonAction.Play()) },
                        enabled = state.playbackStart != null,
                    ) {
                        Icon(
                            painter = painterResource(id = CoreR.drawable.ic_play),
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(id = CoreR.string.play))
                    }
                    if (hasMeaningfulPlaybackStart(state)) {
                        Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
                        Button(
                            onClick = {
                                onAction(SeasonAction.Play(startFromBeginning = true))
                            },
                            enabled = state.playbackStart != null,
                        ) {
                            Icon(
                                painter = painterResource(id = CoreR.drawable.ic_rotate_ccw),
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(id = CoreR.string.play_from_beginning))
                        }
                    }
                    Spacer(modifier = Modifier.height(MaterialTheme.spacings.small))
                    PreplayTrackControls(
                        state = trackSelectionState,
                        referenceLabel = state.playbackStart?.episode?.trackReferenceLabel(),
                        onSelectAudio = onSelectAudio,
                        onSelectSubtitle = onSelectSubtitle,
                        onRetry = onRetryTracks,
                    )
                }
                LazyColumn(
                    contentPadding =
                        PaddingValues(
                            top = MaterialTheme.spacings.large,
                            bottom = MaterialTheme.spacings.large,
                        ),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.medium),
                    modifier = Modifier.weight(2f).padding(end = MaterialTheme.spacings.extraLarge),
                ) {
                    items(items = state.episodes, key = { episode -> episode.id }) { episode ->
                        EpisodeRow(episode = episode, onAction = onAction)
                    }
                }
            }
        } ?: run { CircularProgressIndicator(modifier = Modifier.align(Alignment.Center)) }
    }
}

@Composable
private fun EpisodeRow(episode: FindroidEpisode, onAction: (SeasonAction) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    var showActions by remember { mutableStateOf(false) }
    var restoreFocusOnDismiss by remember { mutableStateOf(false) }

    LaunchedEffect(showActions) {
        if (!showActions && restoreFocusOnDismiss) {
            focusRequester.requestFocus()
            restoreFocusOnDismiss = false
        }
    }

    EpisodeCard(
        episode = episode,
        onClick = { onAction(SeasonAction.NavigateToItem(episode)) },
        onLongClick = {
            restoreFocusOnDismiss = true
            showActions = true
        },
        modifier = Modifier.focusRequester(focusRequester),
    )

    if (showActions) {
        EpisodeActionsDialog(
            episode = episode,
            onTogglePlayed = {
                showActions = false
                onAction(episodePlayedAction(episode))
            },
            onDismissRequest = { showActions = false },
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun SeasonScreenLayoutPreview() {
    FindroidTheme {
        SeasonScreenLayout(
            state = SeasonState(season = dummySeason, episodes = dummyEpisodes),
            trackSelectionState = PreplayTrackSelectionState(),
            onSelectAudio = {},
            onSelectSubtitle = {},
            onRetryTracks = {},
            onAction = {},
        )
    }
}
