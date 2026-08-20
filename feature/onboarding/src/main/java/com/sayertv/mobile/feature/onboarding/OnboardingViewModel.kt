/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sayertv.mobile.core.common.AppError
import com.sayertv.mobile.core.common.SResult
import com.sayertv.mobile.core.database.dao.ServerDao
import com.sayertv.mobile.core.database.entity.ServerEntity
import com.sayertv.mobile.core.jellyfin.AuthRepository
import com.sayertv.mobile.core.jellyfin.QuickConnectStep
import com.sayertv.mobile.core.jellyfin.ServerCandidate
import com.sayertv.mobile.core.jellyfin.UserCandidate
import com.sayertv.mobile.core.jellyfin.SessionManager
import com.sayertv.mobile.core.jellyfin.AuthenticationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val step: Step = Step.ServerUrl,
    val serverUrl: String = "",
    val probing: Boolean = false,
    val server: ServerCandidate? = null,
    val savedServers: List<ServerEntity> = emptyList(),
    val publicUsers: List<UserCandidate> = emptyList(),
    val fetchingUsers: Boolean = false,
    val cleartextAcknowledged: Boolean = false,   // required for http:// servers (§9)
    val username: String = "",
    val password: String = "",
    val loggingIn: Boolean = false,
    val quickConnectCode: String? = null,
    val error: AppError? = null,
    val showSwitchConfirmation: Boolean = false, // Note 3: Confirmation before switching
    val pendingAuthentication: AuthenticationResult? = null,
    val done: Boolean = false,
) {
    enum class Step { ServerUrl, CleartextWarning, Credentials, QuickConnect }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val serverDao: ServerDao,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _ui = MutableStateFlow(OnboardingUiState())
    val ui: StateFlow<OnboardingUiState> = _ui.asStateFlow()

    private var quickConnectJob: Job? = null

    init {
        viewModelScope.launch {
            serverDao.observeServers().collect { servers ->
                _ui.update { it.copy(savedServers = servers) }
            }
        }
    }

    fun onUrlChange(url: String) = _ui.update { it.copy(serverUrl = url, error = null) }
    fun onUsernameChange(v: String) = _ui.update { it.copy(username = v, error = null) }
    fun onPasswordChange(v: String) = _ui.update { it.copy(password = v, error = null) }

    fun probeServer() {
        val url = _ui.value.serverUrl
        if (url.isBlank()) return
        _ui.update { it.copy(probing = true, error = null) }
        viewModelScope.launch {
            when (val result = authRepository.probeServer(url)) {
                is SResult.Success -> {
                    _ui.update {
                        it.copy(
                            probing = false,
                            server = result.data,
                            step = if (result.data.isHttp && !it.cleartextAcknowledged) {
                                OnboardingUiState.Step.CleartextWarning
                            } else {
                                OnboardingUiState.Step.Credentials
                            },
                        )
                    }
                    fetchPublicUsers(result.data.baseUrl)
                }
                is SResult.Error -> _ui.update { it.copy(probing = false, error = result.error) }
                SResult.Loading -> Unit
            }
        }
    }

    private suspend fun fetchPublicUsers(baseUrl: String) {
        _ui.update { it.copy(fetchingUsers = true) }
        when (val result = authRepository.fetchPublicUsers(baseUrl)) {
            is SResult.Success -> _ui.update {
                it.copy(fetchingUsers = false, publicUsers = result.data)
            }
            is SResult.Error -> _ui.update { it.copy(fetchingUsers = false) }
            SResult.Loading -> Unit
        }
    }

    fun removeServer(id: String) {
        viewModelScope.launch {
            serverDao.delete(id)
        }
    }

    fun selectSavedServer(server: ServerEntity) {
        _ui.update { it.copy(serverUrl = server.baseUrl) }
        probeServer()
    }

    fun selectUser(user: UserCandidate) {
        _ui.update { it.copy(username = user.userName) }
    }

    fun acknowledgeCleartext() = _ui.update {
        it.copy(cleartextAcknowledged = true, step = OnboardingUiState.Step.Credentials)
    }

    fun loginWithPassword() {
        val s = _ui.value
        val server = s.server ?: return
        _ui.update { it.copy(loggingIn = true, error = null) }
        viewModelScope.launch {
            when (val result = authRepository.authenticateWithPassword(server, s.username, s.password)) {
                is SResult.Success -> {
                    val auth = result.data
                    val currentSession = sessionManager.current()
                    if (currentSession != null && currentSession.serverId != server.serverId) {
                        _ui.update { it.copy(loggingIn = false, showSwitchConfirmation = true, pendingAuthentication = auth) }
                    } else {
                        sessionManager.activate(
                            serverId = server.serverId,
                            serverName = server.serverName,
                            baseUrl = server.baseUrl,
                            userId = auth.userId,
                            userName = auth.userName,
                            accessToken = auth.accessToken,
                        )
                        _ui.update { it.copy(loggingIn = false, done = true) }
                    }
                }
                is SResult.Error -> _ui.update { it.copy(loggingIn = false, error = result.error) }
                SResult.Loading -> Unit
            }
        }
    }

    fun confirmSwitch() {
        val s = _ui.value
        val auth = s.pendingAuthentication ?: return
        val server = s.server ?: return
        viewModelScope.launch {
            sessionManager.activate(
                serverId = server.serverId,
                serverName = server.serverName,
                baseUrl = server.baseUrl,
                userId = auth.userId,
                userName = auth.userName,
                accessToken = auth.accessToken,
            )
            _ui.update { it.copy(showSwitchConfirmation = false, pendingAuthentication = null, done = true) }
        }
    }

    fun cancelSwitch() {
        _ui.update { it.copy(showSwitchConfirmation = false, pendingAuthentication = null) }
    }

    fun startQuickConnect() {
        val server = _ui.value.server ?: return
        _ui.update { it.copy(step = OnboardingUiState.Step.QuickConnect, error = null) }
        quickConnectJob?.cancel()
        quickConnectJob = viewModelScope.launch {
            authRepository.quickConnect(server).collect { step ->
                when (step) {
                    is QuickConnectStep.CodeReady ->
                        _ui.update { it.copy(quickConnectCode = step.code) }
                    QuickConnectStep.Waiting -> Unit
                    is QuickConnectStep.Authenticated -> {
                        val auth = step.result
                        val currentSession = sessionManager.current()
                        if (currentSession != null && currentSession.serverId != server.serverId) {
                             _ui.update { it.copy(showSwitchConfirmation = true, pendingAuthentication = auth) }
                        } else {
                            sessionManager.activate(
                                serverId = server.serverId,
                                serverName = server.serverName,
                                baseUrl = server.baseUrl,
                                userId = auth.userId,
                                userName = auth.userName,
                                accessToken = auth.accessToken,
                            )
                            _ui.update { it.copy(done = true) }
                        }
                    }
                    is QuickConnectStep.Failed ->
                        _ui.update {
                            it.copy(error = step.error, step = OnboardingUiState.Step.Credentials)
                        }
                }
            }
        }
    }

    fun cancelQuickConnect() {
        quickConnectJob?.cancel()
        _ui.update { it.copy(quickConnectCode = null, step = OnboardingUiState.Step.Credentials) }
    }
}
