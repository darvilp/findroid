package dev.jdtech.jellyfin.presentation.film

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.jdtech.jellyfin.models.InitialTrackSelection
import dev.jdtech.jellyfin.models.FindroidEpisode
import dev.jdtech.jellyfin.models.PlaybackTrackChoices
import dev.jdtech.jellyfin.models.playbackTrackChoices
import dev.jdtech.jellyfin.repository.JellyfinRepository
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PreplayTrackSelectionState(
    val referenceItemId: UUID? = null,
    val choices: PlaybackTrackChoices? = null,
    val selectedAudioStreamIndex: Int? = null,
    val selectedSubtitleStreamIndex: Int? = null,
    val loading: Boolean = false,
    val error: Throwable? = null,
) {
    fun initialTrackSelection(expectedItemId: UUID? = null): InitialTrackSelection? {
        val currentChoices = choices ?: return null
        if (expectedItemId != null && currentChoices.itemId != expectedItemId) return null
        if (selectedAudioStreamIndex == null && selectedSubtitleStreamIndex == null) return null
        return InitialTrackSelection(
            mediaSourceId = currentChoices.mediaSourceId,
            audioStreamIndex = selectedAudioStreamIndex,
            subtitleStreamIndex = selectedSubtitleStreamIndex,
        )
    }

    fun withChoices(newChoices: PlaybackTrackChoices): PreplayTrackSelectionState =
        copy(
            referenceItemId = newChoices.itemId,
            choices = newChoices,
            selectedAudioStreamIndex =
                rematchSelection(selectedAudioStreamIndex, choices?.audio.orEmpty(), newChoices.audio),
            selectedSubtitleStreamIndex =
                if (selectedSubtitleStreamIndex == InitialTrackSelection.SUBTITLE_OFF) {
                    InitialTrackSelection.SUBTITLE_OFF
                } else {
                    rematchSelection(
                        selectedSubtitleStreamIndex,
                        choices?.subtitles.orEmpty(),
                        newChoices.subtitles,
                    )
                },
            loading = false,
            error = null,
        )
}

private fun rematchSelection(
    selectedIndex: Int?,
    previousChoices: List<dev.jdtech.jellyfin.models.PlaybackTrackChoice>,
    newChoices: List<dev.jdtech.jellyfin.models.PlaybackTrackChoice>,
): Int? {
    val previous = previousChoices.singleOrNull { it.streamIndex == selectedIndex } ?: return null
    return newChoices
        .filter { candidate ->
            candidate.language.equals(previous.language, ignoreCase = true) &&
                candidate.displayTitle.equals(previous.displayTitle, ignoreCase = true) &&
                candidate.codec.equals(previous.codec, ignoreCase = true) &&
                candidate.channelLayout.equals(previous.channelLayout, ignoreCase = true) &&
                candidate.isExternal == previous.isExternal &&
                candidate.isForced == previous.isForced &&
                candidate.isHearingImpaired == previous.isHearingImpaired
        }
        .singleOrNull()
        ?.streamIndex
}

internal fun FindroidEpisode.trackReferenceLabel(): String =
    "S$parentIndexNumber:E$indexNumber — $name"

@HiltViewModel
class PreplayTrackSelectionViewModel
@Inject
constructor(private val repository: JellyfinRepository) : ViewModel() {
    private val _state = MutableStateFlow(PreplayTrackSelectionState())
    val state = _state.asStateFlow()
    private var loadJob: Job? = null

    fun load(referenceItemId: UUID) {
        if (_state.value.referenceItemId == referenceItemId && _state.value.choices != null) return
        val previousState = _state.value
        _state.value =
            previousState.copy(
                referenceItemId = referenceItemId,
                choices = null,
                loading = true,
                error = null,
            )
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val choices = repository.getMediaSources(referenceItemId).playbackTrackChoices(referenceItemId)
                if (_state.value.referenceItemId != referenceItemId) return@launch
                _state.update {
                    if (choices == null) {
                        it.copy(choices = null, loading = false, error = null)
                    } else {
                        previousState.withChoices(choices)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (_state.value.referenceItemId == referenceItemId) {
                    _state.update { it.copy(choices = null, loading = false, error = error) }
                }
            }
        }
    }

    fun selectAudio(streamIndex: Int?) {
        val choices = _state.value.choices ?: return
        if (streamIndex != null && choices.audio.none { it.streamIndex == streamIndex }) return
        _state.update { it.copy(selectedAudioStreamIndex = streamIndex) }
    }

    fun selectSubtitle(streamIndex: Int?) {
        val choices = _state.value.choices ?: return
        if (
            streamIndex != null &&
                streamIndex != InitialTrackSelection.SUBTITLE_OFF &&
                choices.subtitles.none { it.streamIndex == streamIndex }
        ) {
            return
        }
        _state.update { it.copy(selectedSubtitleStreamIndex = streamIndex) }
    }
}
