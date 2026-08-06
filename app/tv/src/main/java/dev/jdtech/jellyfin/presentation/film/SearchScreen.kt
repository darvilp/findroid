package dev.jdtech.jellyfin.presentation.film

import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import dev.jdtech.jellyfin.core.R as CoreR
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.core.presentation.dummy.dummyShow
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.film.presentation.search.SearchAction
import dev.jdtech.jellyfin.film.presentation.search.SearchState
import dev.jdtech.jellyfin.film.presentation.search.SearchViewModel
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.presentation.theme.FindroidTheme
import dev.jdtech.jellyfin.presentation.theme.spacings
import dev.jdtech.jellyfin.ui.components.Direction
import dev.jdtech.jellyfin.ui.components.ItemCard
import java.util.UUID

private const val SEARCH_COLUMN_COUNT = 5

@Composable
fun SearchScreen(
    navigateToMovie: (itemId: UUID) -> Unit,
    navigateToShow: (itemId: UUID) -> Unit,
    rememberedItemId: UUID?,
    onRememberedItemChange: (UUID?) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val results = remember(state.items) { supportedSearchResults(state.items) }

    SearchScreenLayout(
        state = state,
        results = results,
        rememberedItemId = rememberedItemId,
        onRememberedItemChange = onRememberedItemChange,
        onAction = { action ->
            if (action is SearchAction.Search && action.query != state.query) {
                onRememberedItemChange(null)
            }
            if (action is SearchAction.OnItemClick) {
                when (val destination = searchDestination(action.item)) {
                    is SearchDestination.Movie -> navigateToMovie(destination.itemId)
                    is SearchDestination.Show -> navigateToShow(destination.itemId)
                    null -> Unit
                }
            }
            viewModel.onAction(action)
        },
    )
}

@Composable
private fun SearchScreenLayout(
    state: SearchState,
    results: List<FindroidItem>,
    rememberedItemId: UUID?,
    onRememberedItemChange: (UUID?) -> Unit,
    onAction: (SearchAction) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val inputFocusRequester = remember { FocusRequester() }
    val resultFocusRequesters =
        remember(results.map { it.id }) { results.associate { it.id to FocusRequester() } }
    var resultFocusPending by remember { mutableStateOf(false) }

    fun requestResultFocus() {
        if (state.query.isNotBlank()) {
            resultFocusPending = true
            keyboardController?.hide()
        }
    }

    LaunchedEffect(Unit) {
        when (
            searchFocusTarget(
                event = SearchFocusEvent.Entered,
                query = state.query,
                results = results,
                rememberedItemId = rememberedItemId,
            )
        ) {
            SearchFocusTarget.Input -> {
                inputFocusRequester.requestFocus()
                keyboardController?.show()
            }
            is SearchFocusTarget.Result -> resultFocusPending = true
            null -> Unit
        }
    }

    LaunchedEffect(resultFocusPending, state.query, results, rememberedItemId) {
        val target =
            searchFocusTarget(
                event =
                    if (resultFocusPending) {
                        SearchFocusEvent.ResultsRequested
                    } else {
                        SearchFocusEvent.ResultsChanged
                    },
                query = state.query,
                results = results,
                rememberedItemId = rememberedItemId,
            )
        if (target is SearchFocusTarget.Result) {
            resultFocusRequesters[target.itemId]?.requestFocus()
            resultFocusPending = false
        }
    }

    Column(
        modifier =
            Modifier.fillMaxSize()
                .padding(
                    start = MaterialTheme.spacings.default * 2,
                    end = MaterialTheme.spacings.default * 2,
                    bottom = MaterialTheme.spacings.large,
                )
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = { query ->
                resultFocusPending = false
                onAction(SearchAction.Search(query))
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(CoreR.drawable.ic_search),
                    contentDescription = null,
                )
            },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            resultFocusPending = false
                            onRememberedItemChange(null)
                            onAction(SearchAction.Search(""))
                            inputFocusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    ) {
                        Icon(
                            painter = painterResource(CoreR.drawable.ic_x),
                            contentDescription = null,
                        )
                    }
                }
            },
            placeholder = { Text(stringResource(FilmR.string.search_placeholder)) },
            singleLine = true,
            keyboardOptions =
                androidx.compose.foundation.text.KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Search,
                ),
            keyboardActions =
                androidx.compose.foundation.text.KeyboardActions(onSearch = { requestResultFocus() }),
            modifier =
                Modifier.fillMaxWidth()
                    .focusRequester(inputFocusRequester)
                    .onPreviewKeyEvent { keyEvent ->
                        if (
                            keyEvent.type == KeyEventType.KeyDown &&
                                keyEvent.nativeKeyEvent.keyCode == KEYCODE_DPAD_DOWN &&
                                results.isNotEmpty()
                        ) {
                            requestResultFocus()
                            true
                        } else {
                            false
                        }
                    },
        )

        Spacer(modifier = Modifier.height(MaterialTheme.spacings.default))

        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                state.loading -> CircularProgressIndicator(modifier = Modifier.size(40.dp))
                state.error != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
                    ) {
                        androidx.tv.material3.Text(
                            text = stringResource(CoreR.string.error_loading_data),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Button(onClick = { onAction(SearchAction.Retry) }) {
                            androidx.tv.material3.Text(stringResource(CoreR.string.retry))
                        }
                    }
                }
                state.query.isNotBlank() && results.isEmpty() -> {
                    androidx.tv.material3.Text(
                        text = stringResource(CoreR.string.no_search_results),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                results.isNotEmpty() -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(SEARCH_COLUMN_COUNT),
                        horizontalArrangement =
                            Arrangement.spacedBy(MaterialTheme.spacings.default),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacings.default),
                        contentPadding = PaddingValues(bottom = MaterialTheme.spacings.large),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(results, key = { _, item -> item.id }) { index, item ->
                            val requester = resultFocusRequesters.getValue(item.id)
                            ItemCard(
                                item = item,
                                direction = Direction.VERTICAL,
                                onClick = { onAction(SearchAction.OnItemClick(item)) },
                                modifier = Modifier.animateItem(),
                                surfaceModifier =
                                    Modifier.focusRequester(requester)
                                        .onFocusChanged { focusState ->
                                            if (focusState.isFocused) {
                                                onRememberedItemChange(item.id)
                                            }
                                        }
                                        .focusProperties {
                                            if (index < SEARCH_COLUMN_COUNT) {
                                                up = inputFocusRequester
                                            }
                                        },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun SearchScreenLayoutPreview() {
    FindroidTheme {
        SearchScreenLayout(
            state = SearchState(query = "movie", items = listOf(dummyMovie, dummyShow)),
            results = listOf(dummyMovie, dummyShow),
            rememberedItemId = null,
            onRememberedItemChange = {},
            onAction = {},
        )
    }
}
