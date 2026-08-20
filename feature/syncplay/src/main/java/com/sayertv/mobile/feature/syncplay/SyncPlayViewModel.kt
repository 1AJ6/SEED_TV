/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.feature.syncplay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sayertv.mobile.core.common.SResult
import com.sayertv.mobile.core.jellyfin.LibraryRepository
import com.sayertv.mobile.core.jellyfin.SessionManager
import com.sayertv.mobile.core.jellyfin.model.MediaItem
import com.sayertv.mobile.core.playback.SyncPlayCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SyncPlayUiState(
    val loading: Boolean = false,
    val groups: List<SyncPlayCoordinator.SyncGroup> = emptyList(),
    val draft: String = "",
    val playingMediaItem: MediaItem? = null, // Note 1
)

@HiltViewModel
class SyncPlayViewModel @Inject constructor(
    val coordinator: SyncPlayCoordinator,
    private val sessionManager: SessionManager,
    private val libraryRepository: LibraryRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(SyncPlayUiState())
    val ui: StateFlow<SyncPlayUiState> = _ui.asStateFlow()

    val activeGroup = coordinator.activeGroup
    val chat = coordinator.chat
    val currentItem = coordinator.currentItem

    init { 
        refresh()
        viewModelScope.launch {
            currentItem.collect { id ->
                if (id != null) {
                    when (val result = libraryRepository.item(id)) {
                        is SResult.Success -> _ui.update { it.copy(playingMediaItem = result.data) }
                        else -> _ui.update { it.copy(playingMediaItem = null) }
                    }
                } else {
                    _ui.update { it.copy(playingMediaItem = null) }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            val groups = coordinator.listGroups()
            _ui.update { it.copy(loading = false, groups = groups) }
        }
    }

    fun createGroup() {
        val host = sessionManager.current()?.userName ?: "SayerTV"
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            coordinator.createGroup("$host's room")
            refresh()
        }
    }

    fun join(group: SyncPlayCoordinator.SyncGroup) {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            coordinator.joinGroup(group)
            _ui.update { it.copy(loading = false) }
        }
    }

    fun leave() {
        viewModelScope.launch {
            coordinator.leaveGroup()
            refresh()
        }
    }

    fun onDraftChange(value: String) = _ui.update { it.copy(draft = value) }

    fun sendChat() {
        val text = _ui.value.draft
        if (text.isBlank()) return
        coordinator.sendChat(text)
        _ui.update { it.copy(draft = "") }
    }
}
