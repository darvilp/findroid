package dev.jdtech.jellyfin.film.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.repository.JellyfinRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class SearchViewModel
internal constructor(
    private val getSearchItems: suspend (String) -> List<FindroidItem>,
    private val searchScope: CoroutineScope?,
) : ViewModel() {
    @Inject
    constructor(repository: JellyfinRepository) : this(repository::getSearchItems, null)

    private val _state = MutableStateFlow(SearchState())
    val state = _state.asStateFlow()

    private var currentJob: Job? = null
    private var searchGeneration = 0

    private fun search(query: String, debounceSearch: Boolean = true) {
        currentJob?.cancel()
        val generation = ++searchGeneration
        _state.value = SearchState(query = query, loading = query.isNotBlank())
        if (query.isBlank()) {
            return
        }
        currentJob =
            (searchScope ?: viewModelScope).launch {
                try {
                    if (debounceSearch) delay(searchDebounceMillis(query))
                    if (generation != searchGeneration) return@launch
                    val items = getSearchItems(query.trim())

                    if (generation == searchGeneration) {
                        _state.emit(SearchState(query = query, items = items))
                    }
                } catch (_: CancellationException) {} catch (e: Exception) {
                    Timber.e(e)
                    if (generation == searchGeneration) {
                        _state.emit(_state.value.copy(loading = false, error = e))
                    }
                }
            }
    }

    fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.Search -> {
                search(query = action.query)
            }
            SearchAction.Retry -> {
                search(query = _state.value.query, debounceSearch = false)
            }
            else -> Unit
        }
    }
}

internal fun searchDebounceMillis(query: String): Long = minOf(50L + query.length * 50L, 300L)
