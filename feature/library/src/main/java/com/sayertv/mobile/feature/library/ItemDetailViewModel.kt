/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sayertv.mobile.core.common.AppError
import com.sayertv.mobile.core.common.SResult
import com.sayertv.mobile.core.jellyfin.LibraryRepository
import com.sayertv.mobile.core.jellyfin.SessionManager
import com.sayertv.mobile.core.jellyfin.TrackPreferenceStore
import com.sayertv.mobile.core.jellyfin.model.MediaItem
import com.sayertv.mobile.core.jellyfin.model.MediaKind
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ItemDetailUiState(
    val loading: Boolean = true,
    val item: MediaItem? = null,
    val seasons: List<MediaItem> = emptyList(),
    val selectedSeasonId: String? = null,
    val episodes: List<MediaItem> = emptyList(),
    val episodesLoading: Boolean = false,
    val togglingPlayed: Boolean = false,
    val error: AppError? = null,
    // Pre-playback per-show track preferences (null = server/file default)
    val audioPref: String? = null,
    val subtitlePref: String? = null,
)

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val trackPreferenceStore: TrackPreferenceStore,
    private val sessionManager: SessionManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val itemId: String = checkNotNull(savedStateHandle["itemId"])

    private val _ui = MutableStateFlow(ItemDetailUiState())
    val ui: StateFlow<ItemDetailUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            when (val result = libraryRepository.item(itemId)) {
                is SResult.Success -> {
                    val item = result.data
                    _ui.update { it.copy(loading = false, item = item) }
                    loadTrackPrefs(item)
                    if (item.kind == MediaKind.SERIES) loadSeasons(item.id)
                }
                is SResult.Error -> _ui.update { it.copy(loading = false, error = result.error) }
                SResult.Loading -> Unit
            }
        }
    }

    private fun loadSeasons(seriesId: String) {
        viewModelScope.launch {
            when (val result = libraryRepository.seriesDetail(seriesId)) {
                is SResult.Success -> {
                    val seasons = result.data.seasons
                    _ui.update { it.copy(seasons = seasons) }
                    // Auto-select first unwatched season, else first.
                    seasons.firstOrNull { (it.unplayedCount ?: 1) > 0 }?.let { selectSeason(it.id) }
                        ?: seasons.firstOrNull()?.let { selectSeason(it.id) }
                }
                is SResult.Error -> _ui.update { it.copy(error = result.error) }
                SResult.Loading -> Unit
            }
        }
    }

    fun selectSeason(seasonId: String) {
        val seriesId = _ui.value.item?.id ?: return
        _ui.update { it.copy(selectedSeasonId = seasonId, episodesLoading = true) }
        viewModelScope.launch {
            when (val result = libraryRepository.episodes(seriesId, seasonId)) {
                is SResult.Success -> _ui.update {
                    it.copy(episodes = result.data, episodesLoading = false)
                }
                is SResult.Error -> _ui.update {
                    it.copy(episodesLoading = false, error = result.error)
                }
                SResult.Loading -> Unit
            }
        }
    }

    /**
     * Manual watched toggle. Ethan Sayer's decision #3: this also scrobbles to
     * AniList once the M4 ScrobbleEngine subscribes to repository events.
     */
    fun togglePlayed(target: MediaItem) {
        _ui.update { it.copy(togglingPlayed = true) }
        viewModelScope.launch {
            when (val result = libraryRepository.setPlayed(target.id, !target.played)) {
                is SResult.Success -> {
                    val updated = result.data
                    _ui.update { state ->
                        state.copy(
                            togglingPlayed = false,
                            item = if (state.item?.id == updated.id) updated else state.item,
                            episodes = state.episodes.map { if (it.id == updated.id) updated else it },
                        )
                    }
                }
                is SResult.Error -> _ui.update {
                    it.copy(togglingPlayed = false, error = result.error)
                }
                SResult.Loading -> Unit
            }
        }
    }

    private fun loadTrackPrefs(item: MediaItem) {
        val serverId = sessionManager.current()?.serverId ?: return
        viewModelScope.launch {
            val pref = trackPreferenceStore.get(trackPreferenceStore.scopeKey(serverId, item))
            _ui.update { it.copy(audioPref = pref?.audio, subtitlePref = pref?.subtitle) }
        }
    }

    /** Applies to the whole show (all episodes/seasons) for episodes; per-item for movies. */
    fun setAudioPref(choice: String?) {
        val item = _ui.value.item ?: return
        val serverId = sessionManager.current()?.serverId ?: return
        _ui.update { it.copy(audioPref = choice) }
        viewModelScope.launch {
            trackPreferenceStore.saveAudio(trackPreferenceStore.scopeKey(serverId, item), choice)
        }
    }

    fun setSubtitlePref(choice: String?) {
        val item = _ui.value.item ?: return
        val serverId = sessionManager.current()?.serverId ?: return
        _ui.update { it.copy(subtitlePref = choice) }
        viewModelScope.launch {
            trackPreferenceStore.saveSubtitle(trackPreferenceStore.scopeKey(serverId, item), choice)
        }
    }
}
