package dev.jdtech.jellyfin.presentation.film

import dev.jdtech.jellyfin.core.presentation.dummy.dummyEpisode
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.core.presentation.dummy.dummyShow
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchUiPolicyTest {
    @Test
    fun `TV search preserves supported results and routes them to details`() {
        val results = supportedSearchResults(listOf(dummyEpisode, dummyShow, dummyMovie))

        assertEquals(listOf(dummyShow, dummyMovie), results)
        assertEquals(SearchDestination.Show(dummyShow.id), searchDestination(dummyShow))
        assertEquals(SearchDestination.Movie(dummyMovie.id), searchDestination(dummyMovie))
        assertEquals(null, searchDestination(dummyEpisode))
    }

    @Test
    fun `search entry restores remembered focus with deterministic fallbacks`() {
        val results = listOf(dummyMovie, dummyShow)

        assertEquals(
            SearchFocusTarget.Input,
            searchFocusTarget(SearchFocusEvent.Entered, "", results, dummyShow.id),
        )
        assertEquals(
            SearchFocusTarget.Result(dummyShow.id),
            searchFocusTarget(SearchFocusEvent.Entered, "query", results, dummyShow.id),
        )
        assertEquals(
            SearchFocusTarget.Result(dummyMovie.id),
            searchFocusTarget(SearchFocusEvent.Entered, "query", results, dummyEpisode.id),
        )
        assertEquals(
            SearchFocusTarget.Input,
            searchFocusTarget(SearchFocusEvent.Entered, "query", emptyList(), null),
        )
    }

    @Test
    fun `new results do not request focus unless result focus is pending`() {
        val results = listOf(dummyMovie, dummyShow)

        assertEquals(
            null,
            searchFocusTarget(
                event = SearchFocusEvent.ResultsChanged,
                query = "query",
                results = results,
                rememberedItemId = dummyShow.id,
            ),
        )
        assertEquals(
            SearchFocusTarget.Result(dummyShow.id),
            searchFocusTarget(
                event = SearchFocusEvent.ResultsRequested,
                query = "query",
                results = results,
                rememberedItemId = dummyShow.id,
            ),
        )
    }
}
