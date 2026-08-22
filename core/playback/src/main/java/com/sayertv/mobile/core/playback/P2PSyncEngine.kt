/*
 * S.E.E.D TV Mobile — GPLv3
 */
package com.sayertv.mobile.core.playback

import com.sayertv.mobile.core.common.ApplicationScope
import com.sayertv.mobile.core.common.IoDispatcher
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * GROUND-UP PEER-TO-PEER SYNC ENGINE (Notes 4 & 6).
 * Handles direct UDP communication between room members for ultra-low latency.
 *
 * 1. Discovery: Clients fetch public IPs via ipify.
 * 2. Signaling: IPs are exchanged via the existing Jellyfin fallback relay.
 * 3. Hole Punching: Clients send UDP pings to each other to open NAT gates.
 * 4. Sync: Commands and chat are sent directly via UDP once a link is verified.
 */
@Singleton
class P2PSyncEngine @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private var socket: DatagramSocket? = null
    private var listenJob: Job? = null
    private var pingJob: Job? = null
    private val peers = ConcurrentHashMap<String, PeerInfo>()
    
    var onMessage: ((author: String, payload: String) -> Unit)? = null
    var onControl: ((payload: String) -> Unit)? = null
    
    private var myPublicIp: String? = null
    private var myLocalIp: String? = null
    private var myPort: Int = 0

    data class PeerInfo(
        val name: String,
        val publicIp: String,
        val localIp: String,
        val port: Int,
        var lastSeen: Long = 0,
        var verified: Boolean = false
    )

    suspend fun start(userName: String) {
        stop()
        withContext(io) {
            runCatching {
                val s = DatagramSocket(0) // OS picks a port
                socket = s
                myPort = s.localPort
                myLocalIp = InetAddress.getLocalHost().hostAddress
                
                // Fetch public IP (Signaling helper)
                myPublicIp = runCatching { URL("https://api.ipify.org").readText() }.getOrNull()
                
                startListening(s)
                startPinging()
            }
        }
    }

    fun stop() {
        listenJob?.cancel()
        pingJob?.cancel()
        socket?.close()
        socket = null
        peers.clear()
    }

    fun getMyAddressInfo(): String {
        return "${myPublicIp ?: "0.0.0.0"}|$myLocalIp|$myPort"
    }

    fun addPeer(name: String, infoStr: String) {
        val parts = infoStr.split('|')
        if (parts.size < 3) return
        val publicIp = parts[0]
        val localIp = parts[1]
        val port = parts[2].toIntOrNull() ?: return
        
        if (!peers.containsKey(name)) {
            peers[name] = PeerInfo(name, publicIp, localIp, port)
        }
    }

    fun isPeerVerified(name: String): Boolean = peers[name]?.verified == true

    fun send(payload: String) {
        val s = socket ?: return
        val bytes = payload.toByteArray()
        scope.launch(io) {
            peers.values.forEach { peer ->
                // Send to both public and local IPs (Hole punching + LAN speed)
                sendTo(s, peer.publicIp, peer.port, bytes)
                sendTo(s, peer.localIp, peer.port, bytes)
            }
        }
    }

    private fun sendTo(socket: DatagramSocket, host: String, port: Int, data: ByteArray) {
        runCatching {
            val addr = InetAddress.getByName(host)
            socket.send(DatagramPacket(data, data.size, addr, port))
        }
    }

    private fun startListening(s: DatagramSocket) {
        listenJob = scope.launch(io) {
            val buffer = ByteArray(4096)
            while (isActive) {
                runCatching {
                    val packet = DatagramPacket(buffer, buffer.size)
                    s.receive(packet)
                    val data = String(packet.data, 0, packet.length)
                    handleIncoming(data, packet.address.hostAddress)
                }
            }
        }
    }

    private fun startPinging() {
        pingJob = scope.launch(io) {
            while (isActive) {
                // Keep holes open and verify new peers
                send("PING|${System.currentTimeMillis()}")
                delay(5_000)
            }
        }
    }

    private fun handleIncoming(data: String, fromIp: String) {
        if (data.startsWith("PING|")) {
            val peer = peers.values.find { it.publicIp == fromIp || it.localIp == fromIp }
            peer?.let {
                it.lastSeen = System.currentTimeMillis()
                it.verified = true
            }
            return
        }
        
        if (data.startsWith("#CTL#")) {
            onControl?.invoke(data.substring(5))
        } else {
            val sep = data.indexOf("|~|")
            if (sep > 0) {
                onMessage?.invoke(data.substring(0, sep), data.substring(sep + 3))
            }
        }
    }
}
