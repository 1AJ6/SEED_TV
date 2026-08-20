/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.feature.anilist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sayertv.mobile.core.anilist.AniListAuthStore
import com.sayertv.mobile.core.anilist.AniListRepository
import com.sayertv.mobile.core.database.dao.MediaMappingDao
import com.sayertv.mobile.core.database.dao.ScrobbleHistoryDao
import com.sayertv.mobile.core.database.entity.MediaMappingEntity
import com.sayertv.mobile.core.database.entity.ScrobbleHistoryEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AniListUiState(
    val linked: Boolean = false,
    val viewerName: String? = null,
    val clientId: String = "",
    val authorizeUrl: String? = null,
)

@HiltViewModel
class AniListViewModel @Inject constructor(
    private val authStore: AniListAuthStore,
    private val aniListRepository: AniListRepository,
    private val mappingDao: MediaMappingDao,
    historyDao: ScrobbleHistoryDao,
) : ViewModel() {

    private val _ui = MutableStateFlow(AniListUiState())
    val ui: StateFlow<AniListUiState> = _ui.asStateFlow()

    val history: StateFlow<List<ScrobbleHistoryEntity>> = historyDao.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val mappings: StateFlow<List<MediaMappingEntity>> = mappingDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { refresh() }

    fun refresh() {
        _ui.update {
            it.copy(
                linked = authStore.isLinked(),
                viewerName = authStore.viewerName,
                clientId = authStore.clientId.orEmpty(),
                authorizeUrl = authStore.authorizeUrl(),
            )
        }
        if (authStore.isLinked()) aniListRepository.drainNow()
    }

    fun onClientIdChange(value: String) {
        authStore.clientId = value.trim()
        _ui.update { it.copy(clientId = value, authorizeUrl = authStore.authorizeUrl()) }
    }

    fun unlink() {
        authStore.unlink()
        refresh()
    }

    fun forgetMapping(id: Long) {
        viewModelScope.launch { mappingDao.delete(id) }
    }
}
