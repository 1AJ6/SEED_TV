/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.jellyfin

import com.sayertv.mobile.core.common.IoDispatcher
import com.sayertv.mobile.core.database.dao.AccountDao
import com.sayertv.mobile.core.database.dao.ServerDao
import com.sayertv.mobile.core.database.entity.AccountEntity
import com.sayertv.mobile.core.database.entity.ServerEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient

/** Active server + user + authenticated ApiClient. */
data class Session(
    val serverId: String,
    val serverName: String,
    val baseUrl: String,
    val userId: String,
    val userName: String,
    val api: ApiClient,
)

/**
 * Single source of truth for "who is logged in where".
 * Exposes StateFlow so navigation can react (session == null → onboarding graph).
 * On any 401 from the API layer, callers invoke [invalidate]. (Design doc §3.1/§4.1)
 */
@Singleton
class SessionManager @Inject constructor(
    private val jellyfin: Jellyfin,
    private val tokenStore: EncryptedTokenStore,
    private val serverDao: ServerDao,
    private val accountDao: AccountDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    fun current(): Session? = _session.value

    fun requireApi(): ApiClient =
        _session.value?.api ?: error("No active session — UI must gate on session != null")

    /** Called by auth flows after a successful login. Persists server + account, activates session. */
    suspend fun activate(
        serverId: String,
        serverName: String,
        baseUrl: String,
        userId: String,
        userName: String,
        accessToken: String,
    ) = withContext(io) {
        val tokenRef = tokenStore.put(accessToken)
        serverDao.upsert(ServerEntity(serverId, serverName, baseUrl, System.currentTimeMillis()))
        accountDao.upsert(
            AccountEntity(
                id = "$serverId:$userId",
                serverId = serverId,
                userId = userId,
                userName = userName,
                tokenRef = tokenRef,
            ),
        )
        _session.value = Session(
            serverId = serverId,
            serverName = serverName,
            baseUrl = baseUrl,
            userId = userId,
            userName = userName,
            api = jellyfin.createApi(baseUrl = baseUrl, accessToken = accessToken),
        )
    }

    /** Restore the most recently used server/account on app start. Returns true on success. NEVER throws. */
    suspend fun restoreLast(): Boolean = runCatching { restoreLastOrThrow() }.getOrDefault(false)

    private suspend fun restoreLastOrThrow(): Boolean = withContext(io) {
        val server = serverDao.mostRecentlyUsed() ?: return@withContext false
        val account = accountDao.byServer(server.id).firstOrNull() ?: return@withContext false
        val token = tokenStore.get(account.tokenRef) ?: return@withContext false
        _session.value = Session(
            serverId = server.id,
            serverName = server.name,
            baseUrl = server.baseUrl,
            userId = account.userId,
            userName = account.userName,
            api = jellyfin.createApi(baseUrl = server.baseUrl, accessToken = token),
        )
        true
    }

    /** 401 anywhere, or explicit sign-out. */
    suspend fun invalidate(signOut: Boolean = false): Unit = withContext(io) {
        val s = _session.value ?: return@withContext
        if (signOut) {
            accountDao.byId("${s.serverId}:${s.userId}")?.let {
                tokenStore.remove(it.tokenRef)
                accountDao.delete(it.id)
            }
        }
        _session.value = null
    }
}
