package dev.jdtech.jellyfin.film.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.database.ServerDatabaseDao
import dev.jdtech.jellyfin.film.R as FilmR
import dev.jdtech.jellyfin.models.CollectionType
import dev.jdtech.jellyfin.models.HomeItem
import dev.jdtech.jellyfin.models.HomeSection
import dev.jdtech.jellyfin.models.Server
import dev.jdtech.jellyfin.models.UiText
import dev.jdtech.jellyfin.repository.JellyfinRepository
import dev.jdtech.jellyfin.settings.domain.AppPreferences
import dev.jdtech.jellyfin.utils.toView
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltViewModel
class HomeViewModel
internal constructor(
    private val dataSource: HomeDataSource,
    private val homeScope: CoroutineScope?,
) : ViewModel() {
    @Inject
    constructor(
        repository: JellyfinRepository,
        appPreferences: AppPreferences,
        database: ServerDatabaseDao,
    ) : this(
        dataSource = RepositoryHomeDataSource(repository, appPreferences, database),
        homeScope = null,
    )

    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    fun loadData() {
        refreshHomeAtomically()
    }

    fun onAction(action: HomeAction) {
        when (action) {
            is HomeAction.MarkAsPlayed -> {
                refreshHomeAtomically { dataSource.markAsPlayed(action.itemId) }
            }
            is HomeAction.OnRetryClick -> {
                loadData()
            }
            else -> Unit
        }
    }

    private fun refreshHomeAtomically(beforeLoad: (suspend () -> Unit)? = null) {
        (homeScope ?: viewModelScope).launch {
            val retainedState = _state.value.copy(isLoading = false, error = null)
            _state.emit(_state.value.copy(isLoading = true, error = null))
            try {
                beforeLoad?.invoke()
                val refreshedState =
                    HomeState(
                        server = dataSource.loadServer() ?: retainedState.server,
                        suggestionsSection = dataSource.loadSuggestions(),
                        resumeSection = dataSource.loadResumeItems(),
                        nextUpSection = dataSource.loadNextUpItems(),
                        views = dataSource.loadViews(),
                        isLoading = true,
                    )
                _state.emit(refreshedState)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e)
                _state.emit(retainedState.copy(isLoading = true, error = e))
            } finally {
                _state.emit(_state.value.copy(isLoading = false))
            }
        }
    }
}

internal interface HomeDataSource {
    suspend fun loadServer(): Server?

    suspend fun loadSuggestions(): HomeItem.Suggestions?

    suspend fun loadResumeItems(): HomeItem.Section?

    suspend fun loadNextUpItems(): HomeItem.Section?

    suspend fun loadViews(): List<HomeItem.ViewItem>

    suspend fun markAsPlayed(itemId: UUID)
}

internal class RepositoryHomeDataSource(
    private val repository: JellyfinRepository,
    private val appPreferences: AppPreferences,
    private val database: ServerDatabaseDao,
) : HomeDataSource {
    private val uuidSuggestions = UUID.fromString("31e47044-9b79-4bb0-99d0-0e477ed65420")
    private val uuidContinueWatching =
        UUID(4937169328197226115, -4704919157662094443) // 44845958-8326-4e83-beb4-c4f42e9eeb95
    private val uuidNextUp =
        UUID(1783371395749072194, -6164625418200444295) // 18bfced5-f237-4d42-aa72-d9d7fed19279

    private val uiTextContinueWatching = UiText.StringResource(FilmR.string.continue_watching)
    private val uiTextNextUp = UiText.StringResource(FilmR.string.next_up)

    override suspend fun markAsPlayed(itemId: UUID) {
        repository.markAsPlayed(itemId)
    }

    override suspend fun loadServer(): Server? =
        withContext(Dispatchers.Default) {
            appPreferences.getValue(appPreferences.currentServer)?.let(database::get)
        }

    override suspend fun loadSuggestions(): HomeItem.Suggestions? =
        withContext(Dispatchers.Default) {
            if (!appPreferences.getValue(appPreferences.homeSuggestions)) return@withContext null
            repository
                .getSuggestions()
                .takeIf { it.isNotEmpty() }
                ?.let { HomeItem.Suggestions(id = uuidSuggestions, items = it) }
        }

    override suspend fun loadResumeItems(): HomeItem.Section? =
        withContext(Dispatchers.Default) {
            if (!appPreferences.getValue(appPreferences.homeContinueWatching)) {
                return@withContext null
            }
            repository
                .getResumeItems()
                .takeIf { it.isNotEmpty() }
                ?.let {
                    HomeItem.Section(
                        HomeSection(uuidContinueWatching, uiTextContinueWatching, it)
                    )
                }
        }

    override suspend fun loadNextUpItems(): HomeItem.Section? =
        withContext(Dispatchers.Default) {
            if (!appPreferences.getValue(appPreferences.homeNextUp)) return@withContext null
            repository
                .getNextUp()
                .takeIf { it.isNotEmpty() }
                ?.let { HomeItem.Section(HomeSection(uuidNextUp, uiTextNextUp, it)) }
        }

    override suspend fun loadViews(): List<HomeItem.ViewItem> =
        withContext(Dispatchers.Default) {
            if (appPreferences.getValue(appPreferences.homeLatest)) {
                repository
                    .getUserViews()
                    .filter { view ->
                        CollectionType.fromString(view.collectionType?.serialName) in
                            CollectionType.supported
                    }
                    .map { view -> view to repository.getLatestMedia(view.id) }
                    .filter { (_, latest) -> latest.isNotEmpty() }
                    .map { (view, latest) -> view.toView(latest) }
                    .map { HomeItem.ViewItem(it) }
            } else {
                emptyList()
            }
        }
}
