/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sayertv.mobile.core.common.AppError
import com.sayertv.mobile.core.common.SResult
import com.sayertv.mobile.core.database.dao.PrefsDao
import com.sayertv.mobile.core.database.entity.PrefsEntity
import com.sayertv.mobile.core.jellyfin.LibraryRepository
import com.sayertv.mobile.core.jellyfin.SessionManager
import com.sayertv.mobile.core.jellyfin.model.HomeSections
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val sections: HomeSections? = null,
    val error: AppError? = null,
    val userName: String = "",
    val serverName: String = "",
    val homeLayout: String = "Grid",
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val sessionManager: SessionManager,
    private val prefsDao: PrefsDao,
) : ViewModel() {

    private val _ui = MutableStateFlow(HomeUiState())
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    init {
        sessionManager.current()?.let { s ->
            _ui.update { it.copy(userName = s.userName, serverName = s.serverName) }
        }
        viewModelScope.launch {
            prefsDao.observePrefs().collect { prefs ->
                _ui.update { it.copy(homeLayout = prefs?.homeLayout ?: "Grid") }
            }
        }
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        _ui.update { it.copy(loading = initial, refreshing = !initial, error = null) }
        viewModelScope.launch {
            when (val result = libraryRepository.homeSections()) {
                is SResult.Success -> _ui.update {
                    it.copy(loading = false, refreshing = false, sections = result.data)
                }
                is SResult.Error -> _ui.update {
                    it.copy(loading = false, refreshing = false, error = result.error)
                }
                SResult.Loading -> Unit
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { sessionManager.invalidate(signOut = true) }
    }
}
