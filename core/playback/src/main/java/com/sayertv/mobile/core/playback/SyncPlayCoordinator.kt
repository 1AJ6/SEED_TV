/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.playback

import com.sayertv.mobile.core.common.ApplicationScope
import com.sayertv.mobile.core.common.IoDispatcher
import com.sayertv.mobile.core.jellyfin.SessionManager
import com.sayertv.mobile.core.jellyfin.TrackPreferenceStore
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive as coroutineIsActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.syncPlayApi
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.JoinGroupRequestDto
import org.jellyfin.sdk.model.api.MessageCommand
import org.jellyfin.sdk.model.api.NewGroupRequestDto

/**
 * SayerTV NATIVE watch-together (2026-08-18 redesign, tester decision):
 * Jellyfin's SyncPlay command relay proved unreliable (commands reaching only
 * one device), so transport sync is now APP-TO-APP over our own channel:
 *
 *   - Jellyfin is used ONLY for room bookkeeping (create/join/list groups →
 *     member names for the relay) — no SyncPlay commands, no server time-sync.
 *   - The HOST is the single source of truth. Every host action and a 2s
 *     heartbeat broadcast an authoritative STATE frame:
 *         STATE|~|playing|~|positionMs|~|hostEpochMs
 *     PLAY frames announce new media; TRACK frames sync audio/subtitles.
 *   - Frames travel over the LAN sockets (LanChat) and fall back to the
 *     Jellyfin session-message relay for cross-network members.
 *   - Members apply frames: clock offset from LAN time-sync (TSYNC ping-pong),
 *     rate-nudge under 2s of drift, hard seek beyond, pause/play to match.
 *
 * Trade-off (accepted): jellyfin-web clients can no longer participate.
 */
@Singleton
class SyncPlayCoordinator @Inject constructor(
    private val sessionManager: SessionManager,
    private val lanChat: dagger.Lazy<LanChat>,
    private val p2p: P2PSyncEngine, // Note 4 & 6
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    data class SyncGroup(val id: String, val name: String, val participants: List<String>)

    data class ChatMessage(val author: String, val text: String, val at: Long, val mine: Boolean)

    private val _activeGroup = MutableStateFlow<SyncGroup?>(null)
    val activeGroup: StateFlow<SyncGroup?> = _activeGroup.asStateFlow()

    private val _isOwnerFlow = MutableStateFlow(false)
    val isOwnerFlow: StateFlow<Boolean> = _isOwnerFlow.asStateFlow()
    val isOwner: Boolean get() = _isOwnerFlow.value

    /** Host started media → members auto-open this Jellyfin item id. */
    private val _playRequests = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val playRequests: SharedFlow<String> = _playRequests.asSharedFlow()

    /** The item currently playing in this room (null = nothing yet). Late
     *  joiners and members who left playback use it to (re)join the session. */
    private val _currentItem = MutableStateFlow<String?>(null)
    val currentItem: StateFlow<String?> = _currentItem.asStateFlow()

    val chat: StateFlow<List<ChatMessage>> get() = lanChat.get().messages

    val isActive: Boolean get() = _activeGroup.value != null

    private var engine: PlayerEngine? = null
    private val jobs = mutableListOf<Job>()

    // Authoritative state from the host (member side)
    @Volatile private var anchorPlaying = false
    @Volatile private var anchorPositionMs = 0L
    @Volatile private var anchorAtLocalMs = 0L
    /** hostClock - memberClock, measured via LAN TSYNC; 0 = trust NTP. */
    @Volatile private var peerClockOffsetMs = 0L
    @Volatile private var relayBeat = 0

    fun attachEngine(playerEngine: PlayerEngine) { engine = playerEngine }

    fun detachEngine() { engine = null }

    // ---- Rooms (Jellyfin used for bookkeeping only) ----

    suspend fun listGroups(): List<SyncGroup> = withContext(io) {
        val api = sessionManager.current()?.api ?: return@withContext emptyList()
        runCatching {
            api.syncPlayApi.syncPlayGetGroups().content.map {
                SyncGroup(it.groupId.toString(), it.groupName, it.participants)
            }
        }.getOrDefault(emptyList())
    }

    suspend fun createGroup(name: String): Boolean = withContext(io) {
        val api = sessionManager.current()?.api ?: return@withContext false
        val created = runCatching { api.syncPlayApi.syncPlayCreateGroup(NewGroupRequestDto(name)) }.isSuccess
        if (!created) return@withContext false
        val mine = listGroups().firstOrNull { it.name == name }
        if (mine != null) {
            _activeGroup.value = mine
            _isOwnerFlow.value = true
            val me = sessionManager.current()?.userName ?: "Host"
            p2p.start(me)
            startLoops()
            lanChat.get().start(mine.id, isHost = true, userName = me)
        }
        mine != null
    }

    suspend fun joinGroup(group: SyncGroup): Boolean = withContext(io) {
        val api = sessionManager.current()?.api ?: return@withContext false
        val joined = runCatching {
            api.syncPlayApi.syncPlayJoinGroup(JoinGroupRequestDto(UUID.fromString(group.id)))
        }.isSuccess
        if (joined) {
            _activeGroup.value = group
            _isOwnerFlow.value = false
            val me = sessionManager.current()?.userName ?: "Guest"
            p2p.start(me)
            startLoops()
            lanChat.get().start(group.id, isHost = false, userName = me)
        }
        joined
    }

    fun leaveGroupAsync() {
        if (!isActive) return
        scope.launch(io) { runCatching { leaveGroup() } }
    }

    suspend fun leaveGroup() = withContext(io) {
        val api = sessionManager.current()?.api
        runCatching { api?.syncPlayApi?.syncPlayLeaveGroup() }
        stopLoops()
        lanChat.get().stop()
        p2p.stop()
        _activeGroup.value = null
        _isOwnerFlow.value = false
        anchorPlaying = false
        anchorAtLocalMs = 0
        _currentItem.value = null
        engine?.setRate(1f)
    }

    // ---- HOST: announce media + broadcast authoritative state ----

    /** Host started media — the whole room follows. */
    fun hostAnnouncePlay(itemId: String, startPositionMs: Long) {
        if (!isActive || !isOwner) return
        _currentItem.value = itemId
        sendFrame("PLAY" + F + itemId + F + startPositionMs, forceRelay = true)
        hostBroadcastState()
    }

    /** Called after every host transport action (play/pause/seek). */
    fun hostBroadcastState(forceRelay: Boolean = true) {
        if (!isActive || !isOwner) return
        val e = engine ?: return
        val frame = "STATE" + F + (if (e.playing.value) "1" else "0") + F +
            e.position.value + F + System.currentTimeMillis() + F +
            (_currentItem.value ?: "")
        sendFrame(frame, forceRelay = forceRelay)
    }

    /** HOST: broadcast the chosen audio/subtitle track so members follow. */
    fun broadcastTrack(type: TrackType, trackName: String) {
        if (!isActive || !isOwner) return
        sendFrame("TRACK" + F + type.name + F + trackName, forceRelay = true)
    }

    private fun sendFrame(payload: String, forceRelay: Boolean) {
        val chat = lanChat.get()
        chat.sendControl(payload)
        p2p.send("#CTL#$payload") // Note 4: Send via P2P
        
        // Relay for cross-network members who haven't linked via P2P yet.
        if (forceRelay || !chat.isDelivering()) {
            relayViaJellyfin(CTL_HEADER, payload)
        }
    }

    // ---- Frames from the host (member side) ----

    private fun handleControl(payload: String) {
        val parts = payload.split(F)
        when (parts.firstOrNull()) {
            "PLAY" -> if (!isOwner && parts.size >= 2) {
                if (_currentItem.value != parts[1]) {
                    _currentItem.value = parts[1]
                    _playRequests.tryEmit(parts[1])   // fresh start → auto-open
                }
            }
            "STATE" -> if (!isOwner && parts.size >= 4) {
                val playing = parts[1] == "1"
                val positionMs = parts[2].toLongOrNull() ?: return
                val hostAtMs = parts[3].toLongOrNull() ?: return
                anchorPlaying = playing
                anchorPositionMs = positionMs
                anchorAtLocalMs = hostAtMs - peerClockOffsetMs
                // Late joiners learn the running session from the heartbeat —
                // surfaced as a "Join current playback" button, NOT auto-open.
                if (parts.size >= 5 && parts[4].isNotBlank() && _currentItem.value == null) {
                    _currentItem.value = parts[4]
                }
                applyStateNow()
            }
            "TRACK" -> if (!isOwner && parts.size >= 3) {
                val type = runCatching { TrackType.valueOf(parts[1]) }.getOrNull() ?: return
                val wanted = parts[2]
                val e = engine ?: return
                val candidates = if (type == TrackType.AUDIO) e.tracks.value.audio else e.tracks.value.subtitle
                candidates.firstOrNull { TrackPreferenceStore.matches(wanted, it.name) }
                    ?.let { e.selectTrack(type, it.id) }
            }
            // LAN clock sync: member sends TSYNC|name|t0 → host answers TSYNC2|name|t0|hostNow
            "TSYNC" -> if (isOwner && parts.size >= 3) {
                lanChat.get().sendControl("TSYNC2" + F + parts[1] + F + parts[2] + F + System.currentTimeMillis())
            }
            "TSYNC2" -> if (!isOwner && parts.size >= 4) {
                val me = sessionManager.current()?.userName ?: return
                if (parts[1] != me) return
                val t0 = parts[2].toLongOrNull() ?: return
                val hostNow = parts[3].toLongOrNull() ?: return
                val t3 = System.currentTimeMillis()
                peerClockOffsetMs = hostNow - (t0 + t3) / 2
            }
            "P2P_INFO" -> if (parts.size >= 3) {
                p2p.addPeer(parts[1], parts[2])
            }
        }
    }

    /** Immediate application of a fresh STATE frame. */
    private fun applyStateNow() {
        val e = engine ?: return
        if (!anchorPlaying) {
            if (e.playing.value) e.pause()
            val drift = abs(e.position.value - anchorPositionMs)
            if (drift > 750) e.seekTo(anchorPositionMs)
            e.setRate(1f)
        } else {
            val expected = anchorPositionMs + (System.currentTimeMillis() - anchorAtLocalMs)
            if (abs(e.position.value - expected) > 2_000) e.seekTo(expected)
            if (!e.playing.value) e.play()
        }
    }

    // ---- Chat (unchanged transport: LAN first, Jellyfin relay fallback) ----

    fun sendChat(text: String) {
        if (!isActive) return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val chat = lanChat.get()
        chat.send(trimmed)
        val me = sessionManager.current()?.userName ?: "?"
        p2p.send("$me|~|$trimmed") // Note 4: Send via P2P
        
        if (!chat.isDelivering()) {
            relayViaJellyfin(CHAT_HEADER, me + LanChat.SEP + trimmed)
        }
    }

    private fun relayViaJellyfin(header: String, text: String) {
        scope.launch(io) {
            runCatching {
                val session = sessionManager.current() ?: return@launch
                val me = session.userName
                val participants = _activeGroup.value?.participants ?: return@launch
                session.api.sessionApi.getSessions().content
                    .filter { it.userName != null && it.userName in participants && it.userName != me && it.id != null }
                    .distinctBy { it.userName }
                    .forEach { target ->
                        runCatching {
                            session.api.sessionApi.sendMessageCommand(
                                sessionId = target.id!!,
                                data = MessageCommand(header = header, text = text, timeoutMs = 1),
                            )
                        }
                    }
            }
        }
    }

    // ---- Loops ----

    private fun startLoops() {
        stopLoops()
        lanChat.get().onControl = ::handleControl
        p2p.onControl = ::handleControl
        p2p.onMessage = { author, text -> lanChat.get().receiveRemote(author, text) }

        // Signaling: Broadcast my P2P info every 10s via Jellyfin relay
        jobs += scope.launch {
            while (coroutineIsActive && this@SyncPlayCoordinator.isActive) {
                val me = sessionManager.current()?.userName
                if (me != null) {
                    relayViaJellyfin(CTL_HEADER, "P2P_INFO" + F + me + F + p2p.getMyAddressInfo())
                }
                delay(10_000)
            }
        }

        // Jellyfin-relay receive path (cross-network chat + control fallback)
        jobs += scope.launch {
            socketHubGeneralCommands()
        }
        // HOST: heartbeat every 2s (LAN always; relay every ~14s when no LAN)
        jobs += scope.launch {
            while (coroutineIsActive && this@SyncPlayCoordinator.isActive) {
                delay(2_000)
                if (isOwner && engine != null) {
                    relayBeat++
                    hostBroadcastState(forceRelay = relayBeat % 7 == 0 && !lanChat.get().isDelivering())
                }
            }
        }
        // MEMBER: LAN clock sync every 30s + continuous drift correction
        jobs += scope.launch {
            while (coroutineIsActive && this@SyncPlayCoordinator.isActive) {
                if (!isOwner && lanChat.get().isDelivering()) {
                    val me = sessionManager.current()?.userName
                    if (me != null) {
                        lanChat.get().sendControl("TSYNC" + F + me + F + System.currentTimeMillis())
                    }
                }
                delay(30_000)
            }
        }
        jobs += scope.launch {
            while (coroutineIsActive && this@SyncPlayCoordinator.isActive) {
                delay(1_000)
                if (!isOwner) correctDrift()
            }
        }
    }

    private suspend fun socketHubGeneralCommands() {
        socketHub?.generalCommands?.collect { message ->
            val data = message.data ?: return@collect
            if (data.name != GeneralCommandType.DISPLAY_MESSAGE) return@collect
            val args = data.arguments ?: return@collect
            val body = args["Text"] ?: return@collect
            when (args["Header"]) {
                CHAT_HEADER -> {
                    val separator = body.indexOf(LanChat.SEP)
                    if (separator > 0) {
                        lanChat.get().receiveRemote(
                            body.substring(0, separator),
                            body.substring(separator + LanChat.SEP.length),
                        )
                    }
                }
                CTL_HEADER -> handleControl(body)
            }
        }
    }

    private fun stopLoops() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        lanChat.get().onControl = null
    }

    private fun correctDrift() {
        if (anchorAtLocalMs == 0L) return
        val e = engine ?: return
        if (!anchorPlaying) {
            if (e.playing.value) e.pause()
            return
        }
        if (!e.playing.value) {
            if (e.state.value == EngineState.Paused) {
                e.seekTo(anchorPositionMs + (System.currentTimeMillis() - anchorAtLocalMs))
                e.play()
            }
            return
        }
        val expected = anchorPositionMs + (System.currentTimeMillis() - anchorAtLocalMs)
        val drift = e.position.value - expected
        when {
            abs(drift) < 60 -> e.setRate(1.0f)
            abs(drift) <= 2_000 -> e.setRate(if (drift > 0) 0.95f else 1.05f)
            else -> {
                e.seekTo(expected)
                e.setRate(1.0f)
            }
        }
    }

    // Injected lazily to avoid touching the WebSocket before a session exists.
    @Inject lateinit var socketHubProvider: dagger.Lazy<com.sayertv.mobile.core.jellyfin.SocketHub>
    private val socketHub: com.sayertv.mobile.core.jellyfin.SocketHub?
        get() = runCatching { socketHubProvider.get() }.getOrNull()

    private companion object {
        const val CHAT_HEADER = "SayerTV-Chat"
        const val CTL_HEADER = "SayerTV-Ctl"
        const val F = LanChat.SEP
    }
}
