/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.jellyfin

import com.sayertv.mobile.core.common.AppError
import com.sayertv.mobile.core.common.IoDispatcher
import com.sayertv.mobile.core.common.SResult
import com.sayertv.mobile.core.database.dao.ServerDao
import com.sayertv.mobile.core.database.entity.ServerEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.quickConnectApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.jellyfin.sdk.model.api.QuickConnectDto

data class ServerCandidate(
    val baseUrl: String,
    val serverId: String,
    val serverName: String,
    val version: String,
    val isHttp: Boolean,          // UI shows the cleartext warning gate when true (§9)
)

data class UserCandidate(
    val userId: String,
    val userName: String,
)

data class AuthenticationResult(
    val userId: String,
    val userName: String,
    val accessToken: String,
)

sealed interface QuickConnectStep {
    data class CodeReady(val code: String) : QuickConnectStep
    data object Waiting : QuickConnectStep
    data class Authenticated(val result: AuthenticationResult) : QuickConnectStep
    data class Failed(val error: AppError) : QuickConnectStep
}

@Singleton
class AuthRepository @Inject constructor(
    private val jellyfin: Jellyfin,
    private val sessionManager: SessionManager,
    private val serverDao: ServerDao,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /**
     * Validate a user-entered URL: reachable, is Jellyfin, and meets the 10.11 floor.
     * Ethan Sayer's decision: no back-compat below 10.11 (design doc §12).
     * CRASH-PROOF: any unexpected exception surfaces as a UI error, never a crash.
     */
    suspend fun probeServer(rawUrl: String): SResult<ServerCandidate> = withContext(io) {
        val baseUrl = normalizeUrl(rawUrl)
        try {
            val api = jellyfin.createApi(baseUrl = baseUrl)
            val info by api.systemApi.getPublicSystemInfo()
            val version = info.version.orEmpty()
            if (!meetsMinimumVersion(version)) {
                return@withContext SResult.Error(AppError.SERVER_UNSUPPORTED)
            }
            val candidate = ServerCandidate(
                baseUrl = baseUrl,
                serverId = info.id.orEmpty(),
                serverName = info.serverName ?: baseUrl,
                version = version,
                isHttp = baseUrl.startsWith("http://"),
            )
            // Save to database as a "known" server
            serverDao.upsert(
                ServerEntity(
                    id = candidate.serverId,
                    name = candidate.serverName,
                    baseUrl = candidate.baseUrl,
                    lastUsedAt = System.currentTimeMillis()
                )
            )
            SResult.Success(candidate)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiClientException) {
            SResult.Error(AppError.SERVER_UNREACHABLE, e)
        } catch (e: Throwable) {
            SResult.Error(AppError.UNKNOWN, e)
        }
    }

    suspend fun authenticateWithPassword(
        server: ServerCandidate,
        username: String,
        password: String,
    ): SResult<AuthenticationResult> = withContext(io) {
        try {
            val api = jellyfin.createApi(baseUrl = server.baseUrl)
            val result by api.userApi.authenticateUserByName(
                AuthenticateUserByName(username = username, pw = password),
            )
            val user = result.user ?: return@withContext SResult.Error(AppError.UNKNOWN)
            val token = result.accessToken ?: return@withContext SResult.Error(AppError.UNKNOWN)
            
            SResult.Success(AuthenticationResult(user.id.toString(), user.name.orEmpty(), token))
        } catch (e: CancellationException) {
            throw e
        } catch (e: InvalidStatusException) {
            if (e.status == 401) SResult.Error(AppError.UNAUTHORIZED, e)
            else SResult.Error(AppError.NETWORK, e)
        } catch (e: ApiClientException) {
            SResult.Error(AppError.NETWORK, e)
        } catch (e: Throwable) {
            SResult.Error(AppError.UNKNOWN, e)
        }
    }

    suspend fun loginWithPassword(
        server: ServerCandidate,
        username: String,
        password: String,
    ): SResult<Unit> = withContext(io) {
        when (val auth = authenticateWithPassword(server, username, password)) {
            is SResult.Success -> {
                sessionManager.activate(
                    serverId = server.serverId,
                    serverName = server.serverName,
                    baseUrl = server.baseUrl,
                    userId = auth.data.userId,
                    userName = auth.data.userName,
                    accessToken = auth.data.accessToken,
                )
                // Update lastUsedAt
                serverDao.upsert(
                    ServerEntity(
                        id = server.serverId,
                        name = server.serverName,
                        baseUrl = server.baseUrl,
                        lastUsedAt = System.currentTimeMillis()
                    )
                )
                SResult.Success(Unit)
            }
            is SResult.Error -> SResult.Error(auth.error, auth.cause)
            SResult.Loading -> SResult.Loading
        }
    }

    suspend fun fetchPublicUsers(baseUrl: String): SResult<List<UserCandidate>> = withContext(io) {
        try {
            val api = jellyfin.createApi(baseUrl = baseUrl)
            val users by api.userApi.getPublicUsers()
            SResult.Success(users.map { UserCandidate(it.id.toString(), it.name.orEmpty()) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiClientException) {
            SResult.Error(AppError.NETWORK, e)
        } catch (e: Throwable) {
            SResult.Error(AppError.UNKNOWN, e)
        }
    }

    /**
     * Quick Connect flow (§4.1): initiate → surface code → poll state every 2s
     * until approved on another device → authenticate with the secret.
     */
    fun quickConnect(server: ServerCandidate): Flow<QuickConnectStep> = flow {
        val api = jellyfin.createApi(baseUrl = server.baseUrl)
        try {
            val state by api.quickConnectApi.initiateQuickConnect()
            emit(QuickConnectStep.CodeReady(state.code))
            var secret = state.secret
            while (true) {
                delay(POLL_INTERVAL_MS)
                emit(QuickConnectStep.Waiting)
                val current by api.quickConnectApi.getQuickConnectState(secret = secret)
                secret = current.secret
                if (current.authenticated) break
            }
            val auth by api.userApi.authenticateWithQuickConnect(QuickConnectDto(secret = secret))
            val user = auth.user
            val token = auth.accessToken
            if (user == null || token == null) {
                emit(QuickConnectStep.Failed(AppError.UNKNOWN))
                return@flow
            }
            emit(QuickConnectStep.Authenticated(AuthenticationResult(user.id.toString(), user.name.orEmpty(), token)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiClientException) {
            emit(QuickConnectStep.Failed(AppError.NETWORK))
        } catch (e: Throwable) {
            emit(QuickConnectStep.Failed(AppError.UNKNOWN))
        }
    }.flowOn(io)

    internal fun normalizeUrl(raw: String): String = AuthValidation.normalizeUrl(raw)

    internal fun meetsMinimumVersion(version: String): Boolean =
        AuthValidation.meetsMinimumVersion(version)

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L
    }
}

/** Pure, dependency-free validation logic — unit-tested directly. */
object AuthValidation {
    fun normalizeUrl(raw: String): String {
        var url = raw.trim().removeSuffix("/")
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
        return url
    }

    fun meetsMinimumVersion(version: String): Boolean {
        val parts = version.split('.').mapNotNull { it.toIntOrNull() }
        if (parts.size < 2) return false
        val (major, minor) = parts
        return major > BuildInfo.MIN_SERVER_MAJOR ||
            (major == BuildInfo.MIN_SERVER_MAJOR && minor >= BuildInfo.MIN_SERVER_MINOR)
    }
}
