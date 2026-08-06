package dev.jdtech.jellyfin.presentation.film

import dev.jdtech.jellyfin.models.FindroidItem
import dev.jdtech.jellyfin.models.FindroidMovie
import dev.jdtech.jellyfin.models.FindroidShow
import java.util.UUID

internal sealed interface SearchDestination {
    data class Movie(val itemId: UUID) : SearchDestination

    data class Show(val itemId: UUID) : SearchDestination
}

internal sealed interface SearchFocusTarget {
    data object Input : SearchFocusTarget

    data class Result(val itemId: UUID) : SearchFocusTarget
}

internal enum class SearchFocusEvent {
    Entered,
    ResultsRequested,
    ResultsChanged,
}

internal fun supportedSearchResults(items: List<FindroidItem>): List<FindroidItem> =
    items.filter { it is FindroidMovie || it is FindroidShow }

internal fun searchDestination(item: FindroidItem): SearchDestination? =
    when (item) {
        is FindroidMovie -> SearchDestination.Movie(item.id)
        is FindroidShow -> SearchDestination.Show(item.id)
        else -> null
    }

internal fun searchFocusTarget(
    event: SearchFocusEvent,
    query: String,
    results: List<FindroidItem>,
    rememberedItemId: UUID?,
): SearchFocusTarget? {
    if (event == SearchFocusEvent.ResultsChanged) return null
    if (query.isBlank() || results.isEmpty()) {
        return if (event == SearchFocusEvent.Entered) SearchFocusTarget.Input else null
    }

    val resultId =
        rememberedItemId?.takeIf { remembered -> results.any { it.id == remembered } }
            ?: results.first().id
    return SearchFocusTarget.Result(resultId)
}
