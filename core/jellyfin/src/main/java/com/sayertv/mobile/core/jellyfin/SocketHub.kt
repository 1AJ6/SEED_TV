/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.jellyfin

import com.sayertv.mobile.core.common.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.shareIn
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.api.sockets.subscribeGeneralCommands
import org.jellyfin.sdk.api.sockets.subscribePlayStateCommands
import org.jellyfin.sdk.api.sockets.subscribeSyncPlayCommands
import org.jellyfin.sdk.model.api.GeneralCommandMessage
import org.jellyfin.sdk.model.api.PlaystateMessage
import org.jellyfin.sdk.model.api.SyncPlayCommandMessage
import org.jellyfin.sdk.model.api.SyncPlayGroupUpdateMessage

/**
 * One Jellyfin WebSocket per session, fanned out as hot shared flows (design doc §3.1, §4.4).
 * The SDK handles KeepAlive and reconnection internally; flows re-bind when the session changes.
 *
 * NOTE: verified against jellyfin-sdk-kotlin 1.8.5 — `webSocket` is a property
 * on ApiClient (SocketApi) and message types live in org.jellyfin.sdk.model.api.
 *
 * Consumers:
 *  - SyncPlayCoordinator  → syncPlayCommands + groupUpdates   (M5)
 *  - PlaybackSessionController → playState (remote control)   (M2)
 *  - In-app toasts → generalCommands (DisplayMessage)          (M1)
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class SocketHub @Inject constructor(
    private val sessionManager: SessionManager,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private fun <T> bound(block: (Session) -> Flow<T>): Flow<T> =
        sessionManager.session
            .flatMapLatest { session -> if (session != null) block(session) else emptyFlow() }
            .shareIn(scope, SharingStarted.WhileSubscribed(5_000), replay = 0)

    val syncPlayCommands: Flow<SyncPlayCommandMessage> =
        bound { it.api.webSocket.subscribeSyncPlayCommands() }

    val syncPlayGroupUpdates: Flow<SyncPlayGroupUpdateMessage> =
        bound { it.api.webSocket.subscribe<SyncPlayGroupUpdateMessage>() }

    val playStateCommands: Flow<PlaystateMessage> =
        bound { it.api.webSocket.subscribePlayStateCommands() }

    val generalCommands: Flow<GeneralCommandMessage> =
        bound { it.api.webSocket.subscribeGeneralCommands() }
}
