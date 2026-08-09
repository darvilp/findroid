package dev.jdtech.jellyfin.presentation.film

import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeSection
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeSuggestions
import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeView
import dev.jdtech.jellyfin.core.presentation.dummy.dummyMovie
import dev.jdtech.jellyfin.film.presentation.home.HomeState
import dev.jdtech.jellyfin.presentation.film.components.HomeRow
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFocusRestorationTest {
    @Test
    fun `fallback targets the first non-empty home row`() {
        val nonEmptyView =
            dummyHomeView.copy(
                view =
                    dummyHomeView.view.copy(
                        id = UUID.fromString("196d3ef1-e0bb-4c9e-8eb6-22360b87129c")
                    )
            )
        val state =
            HomeState(
                suggestionsSection = dummyHomeSuggestions.copy(items = emptyList()),
                resumeSection =
                    dummyHomeSection.copy(
                        homeSection = dummyHomeSection.homeSection.copy(items = emptyList())
                    ),
                nextUpSection =
                    dummyHomeSection.copy(
                        homeSection = dummyHomeSection.homeSection.copy(items = emptyList())
                    ),
                views =
                    listOf(
                        dummyHomeView.copy(
                            view =
                                dummyHomeView.view.copy(
                                    id =
                                        UUID.fromString(
                                            "70d24415-87f8-46c0-87a2-b51b8921c1f5"
                                        ),
                                    items = emptyList(),
                                ),
                        ),
                        nonEmptyView,
                    ),
            )

        assertEquals(
            HomeFallbackTarget(HomeRow.Latest, nonEmptyView.id),
            homeFallbackTarget(state),
        )
    }

    @Test
    fun `focus remains on the playback row while the acted item is present`() {
        val itemId = UUID.fromString("28de55b4-b0b8-4ae4-925a-78d329301b87")
        val state =
            HomeState(
                resumeSection =
                    dummyHomeSection.copy(
                        homeSection =
                            dummyHomeSection.homeSection.copy(
                                items = listOf(dummyMovie.copy(id = itemId))
                            )
                    )
            )

        assertFalse(shouldRestoreHomeFocusAfterPlayedAction(itemId, state))
    }

    @Test
    fun `focus moves to surviving content when the playback card disappears`() {
        val itemId = UUID.fromString("cd8e20b2-aadf-484a-95c1-adc6c3b72bf8")
        val state =
            HomeState(
                views =
                    listOf(
                        dummyHomeView.copy(
                            view =
                                dummyHomeView.view.copy(
                                    items = listOf(dummyMovie.copy(id = itemId))
                                )
                        )
                    )
            )

        assertTrue(shouldRestoreHomeFocusAfterPlayedAction(itemId, state))
    }

    @Test
    fun `focus restoration is skipped when home has no surviving content`() {
        assertFalse(shouldRestoreHomeFocusAfterPlayedAction(UUID.randomUUID(), HomeState()))
    }
}
