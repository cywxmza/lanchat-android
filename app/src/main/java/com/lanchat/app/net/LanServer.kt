package com.lanchat.app.net

import android.util.Log
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * 局域网 TCP 服务器：监听 port（默认 8080），每 accept 一个 Socket 就建一条
 * PeerConnection。支持多对端同时在线；同一对端重复连接时旧连接被替换。
 */
class LanServer(
    private val port: Int,
    private val myId: String,
    private val myName: () -> String,
    private val myAvatarColor: () -> Int,
    private val listener: PeerConnection.Listener
) {
    private var serverSocket: ServerSocket? = null
    private val acceptExecutor = Executors.newSingleThreadExecutor()
    @Volatile var isRunning = false
        private set

    val connections = ConcurrentHashMap<String, PeerConnection>()

    fun start() {
        if (isRunning) return
        isRunning = true
        acceptExecutor.execute {
            try {
                serverSocket = ServerSocket(port)
                Log.i("LanServer", "listening on $port")
                while (isRunning) {
                    val socket: Socket = serverSocket?.accept() ?: break
                    socket.tcpNoDelay = true
                    // conn 在自己的初始化器里被 listener 引用会报“未解析”，
                    // 所以先用 var 占位，建好后再赋值，listener 里用 conn!!。
                    var conn: PeerConnection? = null
                    conn = PeerConnection(socket, myId, myName(), myAvatarColor(), object : PeerConnection.Listener {
                        override fun onHandshake(peerId: String, peerName: String, avatarColor: Int, remoteIp: String) {
                            connections[peerId]?.takeIf { it !== conn }?.close()
                            connections[peerId] = conn!!
                            listener.onHandshake(peerId, peerName, avatarColor, remoteIp)
                        }
                        override fun onChat(peerId: String, peerName: String, text: String, time: Long) = listener.onChat(peerId, peerName, text, time)
                        override fun onFileReceived(peerId: String, peerName: String, file: java.io.File, fileName: String, mime: String, size: Long) =
                            listener.onFileReceived(peerId, peerName, file, fileName, mime, size)
                        override fun onFileStarted(fileId: String, peerId: String, peerName: String, fileName: String, mime: String, size: Long) =
                            listener.onFileStarted(fileId, peerId, peerName, fileName, mime, size)
                        override fun onFileProgress(fileId: String, done: Long, total: Long, incoming: Boolean) = listener.onFileProgress(fileId, done, total, incoming)
                        override fun onFileDoneAck(fileId: String, ok: Boolean) = listener.onFileDoneAck(fileId, ok)
                        override fun onDisconnected(peerId: String) {
                            if (connections[peerId] === conn) connections.remove(peerId)
                            listener.onDisconnected(peerId)
                        }
                    })
                    conn!!.start()
                }
            } catch (e: Exception) {
                if (isRunning) Log.e("LanServer", "accept failed", e)
            }
        }
    }

    fun connectionTo(peerId: String): PeerConnection? = connections[peerId]

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) { }
        connections.values.forEach { it.close() }
        connections.clear()
        acceptExecutor.shutdownNow()
    }
}
