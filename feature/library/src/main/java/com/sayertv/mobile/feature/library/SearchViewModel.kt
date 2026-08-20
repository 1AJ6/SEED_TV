/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sayertv.mobile.core.common.SResult
import com.sayertv.mobile.core.jellyfin.LibraryRepository
import com.sayertv.mobile.core.jellyfin.model.MediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<MediaItem> = emptyList(),
    val searched: Boolean = false,
    val recommended: List<MediaItem> = emptyList(), // Added recommended (Note 4)
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(SearchUiState())
    val ui: StateFlow<SearchUiState> = _ui.asStateFlow()

    private val queryFlow = MutableStateFlow("")

    init {
        loadRecommendations() // Fetch recommendations on init (Note 4)
        viewModelScope.launch {
            queryFlow
                .debounce(350)
                .distinctUntilChanged()
                .collect { query -> performSearch(query) }
        }
    }

    private fun loadRecommendations() {
        viewModelScope.launch {
            when (val result = libraryRepository.homeSections()) {
                is SResult.Success -> {
                    val items = (result.data.continueWatching + result.data.nextUp + 
                                 result.data.latest.flatMap { it.items })
                        .distinctBy { it.id }
                        .take(20)
                    _ui.update { it.copy(recommended = items) }
                }
                else -> Unit
            }
        }
    }

    fun onQueryChange(query: String) {
        _ui.update { it.copy(query = query) }
        queryFlow.value = query
    }

    private suspend fun performSearch(query: String) {
        if (query.isBlank()) {
            _ui.update { it.copy(results = emptyList(), searched = false, searching = false) }
            return
        }
        _ui.update { it.copy(searching = true) }
        when (val result = libraryRepository.search(query)) {
            is SResult.Success -> _ui.update {
                it.copy(searching = false, searched = true, results = result.data.distinctBy { item -> item.id })
            }
            is SResult.Error -> _ui.update { it.copy(searching = false, searched = true) }
            SResult.Loading -> Unit
        }
    }
}
