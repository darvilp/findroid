package dev.jdtech.jellyfin.film.presentation.home

import dev.jdtech.jellyfin.core.presentation.dummy.dummyHomeSection
import dev.jdtech.jellyfin.models.HomeItem
import dev.jdtech.jellyfin.models.Server
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Test
    fun `marking a home item played refreshes home after the mutation`() = runTest {
        val itemId = UUID.fromString("7d6f28be-2ffc-42a1-80d7-f354dbbbdb5c")
        val events = mutableListOf<String>()
        val viewModel =
            HomeViewModel(
                dataSource =
                    object : TestHomeDataSource() {
                        override suspend fun loadServer() =
                            null.also {
                                events += "load"
                            }

                        override suspend fun markAsPlayed(itemId: UUID) {
                            events += "mark:$itemId"
                        }
                    },
                homeScope = this,
            )

        viewModel.loadData()
        runCurrent()
        viewModel.onAction(HomeAction.MarkAsPlayed(itemId))
        runCurrent()

        assertEquals(listOf("load", "mark:$itemId", "load"), events)
    }

    @Test
    fun `marking a home item played publishes refreshed home content`() = runTest {
        val initialState = HomeState(resumeSection = dummyHomeSection)
        val refreshedState = HomeState(nextUpSection = dummyHomeSection)
        var loadCount = 0
        val viewModel =
            HomeViewModel(
                dataSource =
                    object : TestHomeDataSource() {
                        override suspend fun loadResumeItems() =
                            if (++loadCount == 1) initialState.resumeSection else null

                        override suspend fun loadNextUpItems() =
                            if (loadCount == 1) null else refreshedState.nextUpSection

                        override suspend fun markAsPlayed(itemId: UUID) = Unit
                    },
                homeScope = this,
            )

        viewModel.loadData()
        runCurrent()
        viewModel.onAction(HomeAction.MarkAsPlayed(UUID.randomUUID()))
        runCurrent()

        assertEquals(refreshedState, viewModel.state.value)
    }

    @Test
    fun `mark played failure retains home content and exposes the error`() = runTest {
        val initialState = HomeState(resumeSection = dummyHomeSection)
        val failure = IllegalStateException("refresh failed")
        var failRefresh = false
        val viewModel =
            HomeViewModel(
                dataSource =
                    object : TestHomeDataSource() {
                        override suspend fun loadResumeItems() = initialState.resumeSection

                        override suspend fun loadNextUpItems() =
                            if (failRefresh) throw failure else null

                        override suspend fun markAsPlayed(itemId: UUID) {
                            failRefresh = true
                        }
                    },
                homeScope = this,
            )

        viewModel.loadData()
        runCurrent()
        viewModel.onAction(HomeAction.MarkAsPlayed(UUID.randomUUID()))
        runCurrent()

        assertEquals(initialState.copy(error = failure), viewModel.state.value)
    }
}

private abstract class TestHomeDataSource : HomeDataSource {
    override suspend fun loadServer(): Server? = null

    override suspend fun loadSuggestions(): HomeItem.Suggestions? = null

    override suspend fun loadResumeItems(): HomeItem.Section? = null

    override suspend fun loadNextUpItems(): HomeItem.Section? = null

    override suspend fun loadViews() = emptyList<HomeItem.ViewItem>()
}
