package dev.jdtech.jellyfin.film.presentation.show

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.film.presentation.PlaybackStartResolver
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.FindroidItemPerson
import dev.jdtech.jellyfin.models.FindroidShow
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.PersonKind
import timber.log.Timber

@HiltViewModel
class ShowViewModel
@Inject
constructor(
    private val repository: JellyfinRepository,
    private val playbackStartResolver: PlaybackStartResolver,
) : ViewModel() {
    private val _state = MutableStateFlow(ShowState())
    val state = _state.asStateFlow()

    lateinit var showId: UUID
    private var loadJob: Job? = null
    private val loadGeneration = ShowLoadGeneration()

    fun loadShow(showId: UUID) {
        this.showId = showId
        val generation = loadGeneration.begin()
        loadJob?.cancel()
        _state.value = _state.value.copy(playbackStart = null, error = null)
        loadJob = viewModelScope.launch {
            try {
                val show = repository.getShow(showId)
                val nextUp = getNextUp(showId)
                val seasons = repository.getSeasons(showId)
                val playbackStart = playbackStartResolver.resolveSeries(showId, seasons)
                val actors = getActors(show)
                val director = getDirector(show)
                val writers = getWriters(show)
                val committed =
                    loadGeneration.commit(generation) {
                        _state.value =
                            _state.value.copy(
                                show = show,
                                nextUp = nextUp,
                                playbackStart = playbackStart,
                                seasons = seasons,
                                actors = actors,
                                director = director,
                                writers = writers,
                            )
                    }
                if (!committed) return@launch
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadGeneration.commit(generation) {
                    _state.value = _state.value.copy(error = e)
                }
            }
        }
    }

    private suspend fun getNextUp(showId: UUID): FindroidEpisode? {
        return try {
            repository.getNextUp(showId).firstOrNull()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Timber.e(exception, "Failed to retrieve series next up display item")
            null
        }
    }

    private suspend fun getActors(item: FindroidShow): List<FindroidItemPerson> {
        return withContext(Dispatchers.Default) {
            item.people.filter { it.type == PersonKind.ACTOR }
        }
    }

    private suspend fun getDirector(item: FindroidShow): FindroidItemPerson? {
        return withContext(Dispatchers.Default) {
            item.people.firstOrNull { it.type == PersonKind.DIRECTOR }
        }
    }

    private suspend fun getWriters(item: FindroidShow): List<FindroidItemPerson> {
        return withContext(Dispatchers.Default) {
            item.people.filter { it.type == PersonKind.WRITER }
        }
    }

    fun onAction(action: ShowAction) {
        when (action) {
            is ShowAction.MarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsPlayed(showId)
                    loadShow(showId)
                }
            }
            is ShowAction.UnmarkAsPlayed -> {
                viewModelScope.launch {
                    repository.markAsUnplayed(showId)
                    loadShow(showId)
                }
            }
            is ShowAction.MarkAsFavorite -> {
                viewModelScope.launch {
                    repository.markAsFavorite(showId)
                    loadShow(showId)
                }
            }
            is ShowAction.UnmarkAsFavorite -> {
                viewModelScope.launch {
                    repository.unmarkAsFavorite(showId)
                    loadShow(showId)
                }
            }
            else -> Unit
        }
    }
}

internal class ShowLoadGeneration {
    private var current = 0L

    fun begin(): Long = ++current

    fun commit(generation: Long, update: () -> Unit): Boolean {
        if (generation != current) return false
        update()
        return true
    }
}
