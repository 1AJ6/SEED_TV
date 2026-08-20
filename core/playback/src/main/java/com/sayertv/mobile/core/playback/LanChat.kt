/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.playback

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.sayertv.mobile.core.common.ApplicationScope
import com.sayertv.mobile.core.common.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * PEER-TO-PEER room chat over the local network: zero load on Jellyfin and no
 * server permissions needed.
 *
 *  - The room HOST opens a plain TCP socket and announces it via mDNS/NSD
 *    ("_sayertv._tcp", service name carries the SyncPlay group id).
 *  - Members discover the service on the same Wi-Fi and connect directly.
 *  - The host relays each line to all other members (star topology).
 *
 * Wire format: one message per line.
 *   chat frame:    "author<SEP>text"
 *   control frame: "<CTL>payload"   (host → members: track sync etc.)
 *
 * Cross-network peers are reached via the Jellyfin session-message relay
 * fallback (see SyncPlayCoordinator); [receiveRemote] merges those with
 * LAN traffic and dedupes doubles.
 */
@Singleton
class LanChat @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val _messages = MutableStateFlow<List<SyncPlayCoordinator.ChatMessage>>(emptyList())
    val messages: StateFlow<List<SyncPlayCoordinator.ChatMessage>> = _messages.asStateFlow()

    /** Control frames (track sync etc.) — handler installed by the coordinator. */
    @Volatile var onControl: ((String) -> Unit)? = null

    private var serverSocket: ServerSocket? = null
    private val clientWriters = CopyOnWriteArrayList<PrintWriter>()
    private var memberSocket: Socket? = null
    private var memberWriter: PrintWriter? = null
    private val jobs = mutableListOf<Job>()
    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val recentKeys = ArrayDeque<String>()
    @Volatile private var myName: String = ""
    @Volatile private var roomTag: String = ""
    @Volatile private var running = false

    fun start(groupId: String, isHost: Boolean, userName: String) {
        stop()
        running = true
        myName = userName
        roomTag = "sayertv-" + groupId.replace("-", "").take(10)
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
        if (isHost) startHost() else startMember()
    }

    /** True when at least one peer is reachable over the LAN. */
    fun isDelivering(): Boolean =
        if (serverSocket != null) clientWriters.isNotEmpty() else memberWriter != null

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || !running) return
        markSeen("$myName|$trimmed")
        append(SyncPlayCoordinator.ChatMessage(myName, trimmed, System.currentTimeMillis(), mine = true))
        val line = myName + SEP + trimmed
        scope.launch(io) {
            runCatching {
                if (serverSocket != null) broadcast(line, except = null)
                else memberWriter?.apply { println(line); flush() }
            }
        }
    }

    /** Message arriving via the Jellyfin relay fallback — dedupe against LAN copies. */
    fun receiveRemote(author: String, text: String) {
        if (author == myName || !running) return
        if (!markSeen("$author|$text")) return
        append(SyncPlayCoordinator.ChatMessage(author, text, System.currentTimeMillis(), mine = false))
    }

    /** Host → members control frame over the LAN sockets. */
    fun sendControl(payload: String) {
        if (!running) return
        val line = CTL + payload
        scope.launch(io) {
            runCatching {
                if (serverSocket != null) broadcast(line, except = null)
                else memberWriter?.apply { println(line); flush() }
            }
        }
    }

    fun stop() {
        running = false
        jobs.forEach { it.cancel() }
        jobs.clear()
        runCatching { registrationListener?.let { nsdManager?.unregisterService(it) } }
        runCatching { discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) } }
        registrationListener = null
        discoveryListener = null
        runCatching { serverSocket?.close() }
        serverSocket = null
        clientWriters.clear()
        runCatching { memberSocket?.close() }
        memberSocket = null
        memberWriter = null
        synchronized(this) { recentKeys.clear() }
        _messages.value = emptyList()
    }

    // ---- Host side ----

    private fun startHost() {
        jobs += scope.launch(io) {
            runCatching {
                val server = ServerSocket(0)
                serverSocket = server
                registerService(server.localPort)
                while (running) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    val writer = PrintWriter(socket.getOutputStream(), true)
                    clientWriters += writer
                    jobs += scope.launch(io) {
                        runCatching {
                            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                            while (running) {
                                val line = reader.readLine() ?: break
                                deliver(line)
                                broadcast(line, except = writer)   // relay to the other members
                            }
                        }
                        clientWriters.remove(writer)
                    }
                }
            }
        }
    }

    private fun registerService(port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = roomTag
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {}
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {}
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        runCatching { nsdManager?.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    private fun broadcast(line: String, except: PrintWriter?) {
        clientWriters.forEach { writer ->
            if (writer !== except) runCatching { writer.println(line); writer.flush() }
        }
    }

    // ---- Member side ----

    private fun startMember() {
        val manager = nsdManager ?: return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceName.startsWith(roomTag)) return
                @Suppress("DEPRECATION")
                runCatching {
                    manager.resolveService(
                        serviceInfo,
                        object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {}
                            override fun onServiceResolved(info: NsdServiceInfo) {
                                connect(info.host?.hostAddress ?: return, info.port)
                            }
                        },
                    )
                }
            }
        }
        discoveryListener = listener
        runCatching { manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    private fun connect(host: String, port: Int) {
        if (memberSocket != null) return
        jobs += scope.launch(io) {
            runCatching {
                val socket = Socket(host, port)
                memberSocket = socket
                memberWriter = PrintWriter(socket.getOutputStream(), true)
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                while (running) {
                    val line = reader.readLine() ?: break
                    deliver(line)
                }
            }
            // Connection dropped → clear refs so isDelivering() reports false and
            // senders fall back to the Jellyfin relay (fixes silently lost chat).
            runCatching { memberSocket?.close() }
            memberSocket = null
            memberWriter = null
        }
    }

    // ---- Shared ----

    private fun deliver(line: String) {
        if (line.startsWith(CTL)) {
            onControl?.invoke(line.substring(CTL.length))
            return
        }
        val separator = line.indexOf(SEP)
        if (separator <= 0) return
        val author = line.substring(0, separator)
        val text = line.substring(separator + SEP.length)
        if (author == myName) return
        if (!markSeen("$author|$text")) return
        append(SyncPlayCoordinator.ChatMessage(author, text, System.currentTimeMillis(), mine = false))
    }

    /** Returns false when this exact message was already delivered recently. */
    @Synchronized
    private fun markSeen(key: String): Boolean {
        if (key in recentKeys) return false
        recentKeys.addLast(key)
        while (recentKeys.size > 30) recentKeys.removeFirst()
        return true
    }

    private fun append(message: SyncPlayCoordinator.ChatMessage) {
        _messages.value = (_messages.value + message).takeLast(200)
    }

    companion object {
        const val SERVICE_TYPE = "_sayertv._tcp."
        /** Field separator / control prefix — plain printable tokens, escape-proof. */
        const val SEP = "|~|"
        const val CTL = "#CTL#"
    }
}
