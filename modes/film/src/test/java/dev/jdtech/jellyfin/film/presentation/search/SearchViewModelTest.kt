package dev.jdtech.jellyfin.film.presentation.search

import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.core.presentation.dummy.dummyShow
import dev.jdtech.jellyfin.models.FindroidItem
import kotlin.coroutines.Continuation
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @Test
    fun `whitespace query resets immediately without searching`() = runTest {
        val queries = mutableListOf<String>()
        val viewModel =
            SearchViewModel(
                getSearchItems = { query ->
                    queries += query
                    emptyList()
                },
                searchScope = this,
            )

        viewModel.onAction(SearchAction.Search("old"))
        advanceTimeBy(200)
        runCurrent()
        viewModel.onAction(SearchAction.Search("   "))

        assertEquals(SearchState(query = "   "), viewModel.state.value)
        assertEquals(listOf("old"), queries)
    }

    @Test
    fun `search waits for the phone debounce policy and trims the repository query`() = runTest {
        val queries = mutableListOf<String>()
        val viewModel =
            SearchViewModel(
                getSearchItems = { query ->
                    queries += query
                    listOf(dummyMovie)
                },
                searchScope = this,
            )

        viewModel.onAction(SearchAction.Search("  ab  "))
        runCurrent()
        assertEquals(SearchState(query = "  ab  ", loading = true), viewModel.state.value)

        advanceTimeBy(299)
        assertEquals(emptyList<String>(), queries)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("ab"), queries)
        assertEquals(SearchState(query = "  ab  ", items = listOf(dummyMovie)), viewModel.state.value)
    }

    @Test
    fun `out of order response cannot replace the latest query`() = runTest {
        val pending = mutableMapOf<String, Continuation<List<FindroidItem>>>()
        val viewModel =
            SearchViewModel(
                getSearchItems = { query ->
                    suspendCoroutine { continuation -> pending[query] = continuation }
                },
                searchScope = this,
            )

        viewModel.onAction(SearchAction.Search("old"))
        advanceTimeBy(200)
        runCurrent()
        viewModel.onAction(SearchAction.Search("new"))
        advanceTimeBy(200)
        runCurrent()

        pending.getValue("new").resumeWith(Result.success(listOf(dummyMovie)))
        runCurrent()
        assertEquals(SearchState(query = "new", items = listOf(dummyMovie)), viewModel.state.value)

        pending.getValue("old").resumeWith(Result.success(listOf(dummyShow)))
        runCurrent()
        assertEquals(SearchState(query = "new", items = listOf(dummyMovie)), viewModel.state.value)
    }

    @Test
    fun `search failure is exposed without clearing the query`() = runTest {
        val failure = IllegalStateException("search failed")
        val viewModel =
            SearchViewModel(
                getSearchItems = { throw failure },
                searchScope = this,
            )

        viewModel.onAction(SearchAction.Search("error"))
        advanceTimeBy(300)
        runCurrent()

        assertEquals(SearchState(query = "error", error = failure), viewModel.state.value)
    }

    @Test
    fun `retry repeats the current query without another debounce`() = runTest {
        var attempts = 0
        val viewModel =
            SearchViewModel(
                getSearchItems = {
                    attempts += 1
                    if (attempts == 1) error("search failed") else listOf(dummyMovie)
                },
                searchScope = this,
            )

        viewModel.onAction(SearchAction.Search("movie"))
        advanceTimeBy(300)
        runCurrent()
        viewModel.onAction(SearchAction.Retry)
        runCurrent()

        assertEquals(2, attempts)
        assertEquals(SearchState(query = "movie", items = listOf(dummyMovie)), viewModel.state.value)
    }

    @Test
    fun `empty search exposes loading before settling on no results`() = runTest {
        lateinit var response: Continuation<List<FindroidItem>>
        val viewModel =
            SearchViewModel(
                getSearchItems = { suspendCoroutine { response = it } },
                searchScope = this,
            )

        viewModel.onAction(SearchAction.Search("none"))
        advanceTimeBy(250)
        runCurrent()
        assertEquals(SearchState(query = "none", loading = true), viewModel.state.value)

        response.resumeWith(Result.success(emptyList()))
        runCurrent()
        assertEquals(SearchState(query = "none"), viewModel.state.value)
    }

    @Test
    fun `debounce curve matches the established phone timing`() {
        assertEquals(100L, searchDebounceMillis("a"))
        assertEquals(150L, searchDebounceMillis("ab"))
        assertEquals(300L, searchDebounceMillis("abcde"))
        assertEquals(300L, searchDebounceMillis("a long query"))
    }
}
