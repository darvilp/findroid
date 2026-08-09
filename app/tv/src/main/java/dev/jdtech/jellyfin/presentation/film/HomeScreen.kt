package dev.jdtech.jellyfin.presentation.film

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeSection
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeSuggestions
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeView
import dev.jdtech.jellyfin.film.presentation.home.HomeAction
import dev.jdtech.jellyfin.film.presentation.home.HomeState
import dev.jdtech.jellyfin.film.presentation.home.HomeViewModel
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.presentation.film.components.HomeCarousel
import dev.jdtech.jellyfin.presentation.film.components.HomeRow
import dev.jdtech.jellyfin.presentation.film.components.HomeSection
import dev.jdtech.jellyfin.presentation.film.components.HomeView
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import java.util.UUID

@Composable
fun HomeScreen(
    navigateToMovie: (itemId: UUID) -> Unit,
    navigateToShow: (itemId: UUID) -> Unit,
    navigateToPlayer: (itemId: UUID) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
    isLoading: (Boolean) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(true) { viewModel.loadData() }

    LaunchedEffect(state.isLoading) { isLoading(state.isLoading) }

    HomeScreenLayout(
        state = state,
        onAction = { action ->
            when (action) {
                is HomeAction.OnItemClick -> {
                    when (action.item) {
                        is FindroidMovie -> navigateToMovie(action.item.id)
                        is FindroidShow -> navigateToShow(action.item.id)
                        is FindroidEpisode -> {
                            navigateToPlayer(action.item.id)
                        }
                    }
                }
                else -> Unit
            }
            viewModel.onAction(action)
        },
    )
}

@Composable
private fun HomeScreenLayout(state: HomeState, onAction: (HomeAction) -> Unit) {
    val itemsPadding = PaddingValues(horizontal = MaterialTheme.spacings.large)
    val fallbackFocusRequester = remember { FocusRequester() }
    var pendingPlayedItemId by remember { mutableStateOf<UUID?>(null) }

    LaunchedEffect(state, pendingPlayedItemId) {
        val itemId = pendingPlayedItemId ?: return@LaunchedEffect
        when {
            state.error != null -> pendingPlayedItemId = null
            shouldRestoreHomeFocusAfterPlayedAction(itemId, state) -> {
                fallbackFocusRequester.requestFocus()
                pendingPlayedItemId = null
            }
        }
    }

    val fallbackTarget = homeFallbackTarget(state)
    val dispatchAction: (HomeAction) -> Unit = { action ->
        if (action is HomeAction.MarkAsPlayed) pendingPlayedItemId = action.itemId
        onAction(action)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().focusRestorer(),
        contentPadding =
            PaddingValues(
                top = MaterialTheme.spacings.extraSmall,
                bottom = MaterialTheme.spacings.large,
            ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.large),
    ) {
        state.suggestionsSection?.let { section ->
            item(key = section.id) {
                HomeCarousel(
                    items = section.items,
                    onAction = dispatchAction,
                    modifier =
                        Modifier.animateItem()
                            .padding(itemsPadding)
                            .then(
                                if (fallbackTarget?.row == HomeRow.Suggestions) {
                                    Modifier.focusRequester(fallbackFocusRequester)
                                } else {
                                    Modifier
                                }
                            ),
                )
            }
        }
        state.resumeSection?.let { section ->
            item(key = section.id) {
                HomeSection(
                    section = section.homeSection,
                    itemsPadding = itemsPadding,
                    onAction = dispatchAction,
                    modifier = Modifier.animateItem(),
                    row = HomeRow.Resume,
                    fallbackFocusRequester =
                        fallbackFocusRequester.takeIf {
                            fallbackTarget?.row == HomeRow.Resume
                        },
                )
            }
        }
        state.nextUpSection?.let { section ->
            item(key = section.id) {
                HomeSection(
                    section = section.homeSection,
                    itemsPadding = itemsPadding,
                    onAction = dispatchAction,
                    modifier = Modifier.animateItem(),
                    row = HomeRow.NextUp,
                    fallbackFocusRequester =
                        fallbackFocusRequester.takeIf {
                            fallbackTarget?.row == HomeRow.NextUp
                        },
                )
            }
        }
        items(state.views, key = { view -> view.id }) { view ->
            HomeView(
                view = view,
                itemsPadding = itemsPadding,
                onAction = dispatchAction,
                modifier = Modifier.animateItem(),
                fallbackFocusRequester =
                    fallbackFocusRequester.takeIf {
                        fallbackTarget == HomeFallbackTarget(HomeRow.Latest, view.id)
                    },
            )
        }
    }
}

internal data class HomeFallbackTarget(val row: HomeRow, val viewId: UUID? = null)

internal fun homeFallbackTarget(state: HomeState): HomeFallbackTarget? =
    when {
        state.suggestionsSection?.items?.isNotEmpty() == true ->
            HomeFallbackTarget(HomeRow.Suggestions)
        state.resumeSection?.homeSection?.items?.isNotEmpty() == true ->
            HomeFallbackTarget(HomeRow.Resume)
        state.nextUpSection?.homeSection?.items?.isNotEmpty() == true ->
            HomeFallbackTarget(HomeRow.NextUp)
        else ->
            state.views
                .firstOrNull { it.view.items.isNotEmpty() }
                ?.let { HomeFallbackTarget(HomeRow.Latest, it.id) }
    }

internal fun shouldRestoreHomeFocusAfterPlayedAction(itemId: UUID, state: HomeState): Boolean {
    val playbackItemIds =
        buildSet {
            state.resumeSection?.homeSection?.items?.mapTo(this) { it.id }
            state.nextUpSection?.homeSection?.items?.mapTo(this) { it.id }
        }
    val hasFocusableContent =
        state.suggestionsSection?.items?.isNotEmpty() == true ||
            state.resumeSection?.homeSection?.items?.isNotEmpty() == true ||
            state.nextUpSection?.homeSection?.items?.isNotEmpty() == true ||
            state.views.any { it.view.items.isNotEmpty() }

    return itemId !in playbackItemIds && hasFocusableContent
}

@Preview(device = "id:tv_1080p")
@Composable
private fun HomeScreenLayoutPreview() {
    FindroidTheme {
        HomeScreenLayout(
            state =
                HomeState(
                    suggestionsSection = dummyHomeSuggestions,
                    resumeSection = dummyHomeSection,
                    views = listOf(dummyHomeView),
                ),
            onAction = {},
        )
    }
}
