package com.lanchat.app.net

import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.Socket
import java.util.UUID
import java.util.concurrent.Executors

/**
 * 一条与对端的 TCP 长连接（服务端 accept 得到、客户端 connect 得到都用它）。
 * 读循环在单线程跑：读 JSON 行 → chat/handshake/heartbeat/file-meta；
 * 遇到 file-meta 立即在同一流上读文件体（见 Protocol 注释），完成后回调。
 */
class PeerConnection(
    val socket: Socket,
    val myId: String,
    val myName: String,
    val myAvatarColor: Int,
    private val listener: Listener
) {
    interface Listener {
        fun onHandshake(peerId: String, peerName: String, avatarColor: Int, remoteIp: String)
        fun onChat(peerId: String, peerName: String, text: String, time: Long)
        fun onFileReceived(peerId: String, peerName: String, file: File, fileName: String, mime: String, size: Long)
        /** 收到 file-meta 后立即回调，调用方先落库一条占位消息，之后 onFileProgress 才能更新到它。 */
        fun onFileStarted(fileId: String, peerId: String, peerName: String, fileName: String, mime: String, size: Long)
        fun onFileProgress(fileId: String, done: Long, total: Long, incoming: Boolean)
        fun onFileDoneAck(fileId: String, ok: Boolean)
        fun onDisconnected(peerId: String)
    }

    @Volatile var peerId: String = ""
    @Volatile var peerName: String = ""
    @Volatile var peerAvatarColor: Int = 0
    @Volatile private var running = true

    private val input = BufferedInputStream(socket.getInputStream())
    private val output = BufferedOutputStream(socket.getOutputStream())
    private val executor = Executors.newSingleThreadExecutor()
    private var lastHeartbeat = System.currentTimeMillis()

    val remoteIp: String get() = socket.inetAddress?.hostAddress ?: ""

    fun start() {
        sendHandshake()
        executor.execute { readLoop() }
        executor.execute { heartbeatLoop() }
    }

    fun sendHandshake() {
        val msg = Protocol.WireMessage(
            type = Protocol.TYPE_HANDSHAKE, id = myId, name = myName,
            avatarColor = myAvatarColor, port = Protocol.DEFAULT_PORT
        )
        sendJson(msg)
    }

    fun sendChat(text: String) {
        sendJson(Protocol.WireMessage(type = Protocol.TYPE_CHAT, id = myId, name = myName, text = text))
    }

    fun sendJson(msg: Protocol.WireMessage) {
        try { Protocol.writeLine(output, Protocol.toJson(msg)) }
        catch (e: Exception) { Log.w("PeerConnection", "send failed", e); close() }
    }

    /** 发送文件：先发 file-meta 行，再发文件体；完成后等对方回 file-done（由读循环处理）。 */
    fun sendFile(file: File, mime: String, onProgress: (Long, Long) -> Unit, fileId: String = UUID.randomUUID().toString()): String {
        sendJson(Protocol.WireMessage(
            type = Protocol.TYPE_FILE_META, id = myId, name = myName,
            fileId = fileId, fileName = file.name, fileSize = file.length(), mime = mime
        ))
        FileInputStream(file).use { fis ->
            FileTransfer.sendFileBody(output, fis, file.length()) { sent, total ->
                onProgress(sent, total)
                listener.onFileProgress(fileId, sent, total, false)
            }
        }
        return fileId
    }

    private fun heartbeatLoop() {
        while (running && !socket.isClosed) {
            try {
                Thread.sleep(15_000)
                if (!running) return
                sendJson(Protocol.WireMessage(type = Protocol.TYPE_HEARTBEAT, id = myId, time = System.currentTimeMillis()))
                if (System.currentTimeMillis() - lastHeartbeat > 45_000) { close(); return }
            } catch (_: InterruptedException) { return }
            catch (_: Exception) { close(); return }
        }
    }

    private fun readLoop() {
        try {
            while (running && !socket.isClosed) {
                val line = Protocol.readLine(input) ?: break
                if (line.isBlank()) continue
                val msg = try { Protocol.fromJson(line) } catch (e: Exception) {
                    Log.w("PeerConnection", "bad json: $line", e); continue
                }
                lastHeartbeat = System.currentTimeMillis()
                when (msg.type) {
                    Protocol.TYPE_HANDSHAKE -> {
                        peerId = msg.id; peerName = msg.name; peerAvatarColor = msg.avatarColor
                        listener.onHandshake(msg.id, msg.name, msg.avatarColor, remoteIp)
                    }
                    Protocol.TYPE_CHAT -> listener.onChat(msg.id, msg.name, msg.text, msg.time)
                    Protocol.TYPE_HEARTBEAT -> { /* 仅刷新 lastHeartbeat */ }
                    Protocol.TYPE_FILE_DONE -> listener.onFileDoneAck(msg.fileId, msg.ok)
                    Protocol.TYPE_FILE_META -> {
                        val target = FileTransfer.uniqueFile(FileTransfer.receiveDir(), msg.fileName)
                        listener.onFileStarted(msg.fileId, msg.id, msg.name, msg.fileName, msg.mime, msg.fileSize)
                        try {
                            FileTransfer.receiveFileBody(input, msg.fileSize, target) { r, t ->
                                listener.onFileProgress(msg.fileId, r, t, true)
                            }
                            sendJson(Protocol.WireMessage(type = Protocol.TYPE_FILE_DONE, id = myId, fileId = msg.fileId, ok = true))
                            listener.onFileReceived(msg.id, msg.name, target, msg.fileName, msg.mime, msg.fileSize)
                        } catch (e: Exception) {
                            Log.e("PeerConnection", "receive file failed", e)
                            sendJson(Protocol.WireMessage(type = Protocol.TYPE_FILE_DONE, id = myId, fileId = msg.fileId, ok = false))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            if (running) Log.w("PeerConnection", "read loop ended", e)
        } finally {
            close()
            if (peerId.isNotEmpty()) listener.onDisconnected(peerId)
        }
    }

    fun close() {
        if (!running) return
        running = false
        try { socket.close() } catch (_: Exception) { }
        executor.shutdownNow()
    }
}
