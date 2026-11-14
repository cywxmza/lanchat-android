package com.lanchat.app.viewmodel

import android.content.Context
import android.net.Uri
import android.util.Log
import com.lanchat.app.data.AppDatabase
import com.lanchat.app.data.ContactEntity
import com.lanchat.app.data.MessageEntity
import com.lanchat.app.data.MessageType
import com.lanchat.app.data.UserPrefs
import com.lanchat.app.net.LanClient
import com.lanchat.app.net.LanServer
import com.lanchat.app.net.PeerConnection
import com.lanchat.app.net.Protocol
import com.lanchat.app.net.WifiUtils
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 全局唯一的管理器：持有 LanServer + 所有 PeerConnection，负责
 * 连接 / 发送 / 接收落库 / 在线状态，并把事件以 StateFlow 推给 UI。
 * MainActivity 创建一次，之后各 Screen 共用同一实例。
 */
class LanManager(private val context: Context, private val scope: CoroutineScope) {
    private val db = AppDatabase.get(context)
    private val prefs = UserPrefs(context)

    private val _myIp = MutableStateFlow<String?>(null)
    val myIp: StateFlow<String?> = _myIp.asStateFlow()
    private val _myName = MutableStateFlow("我")
    val myName: StateFlow<String> = _myName.asStateFlow()
    private val _myColor = MutableStateFlow(0)
    val myColor: StateFlow<Int> = _myColor.asStateFlow()
    private val _onlinePeers = MutableStateFlow<Set<String>>(emptySet())
    val onlinePeers: StateFlow<Set<String>> = _onlinePeers.asStateFlow()
    private val _connectError = MutableStateFlow<String?>(null)
    val connectError: StateFlow<String?> = _connectError.asStateFlow()
    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()
    private val _discoveredIps = MutableStateFlow<List<String>>(emptyList())
    val discoveredIps: StateFlow<List<String>> = _discoveredIps.asStateFlow()

    lateinit var myId: String
        private set
    var serverPort: Int = Protocol.DEFAULT_PORT
        private set

    private var server: LanServer? = null
    private var client: LanClient? = null
    private val outgoing = mutableMapOf<String, PeerConnection>()

    private val listener = object : PeerConnection.Listener {
        override fun onHandshake(peerId: String, peerName: String, avatarColor: Int, remoteIp: String) {
            scope.launch(Dispatchers.IO) {
                db.contactDao().upsert(ContactEntity(peerId, peerName, remoteIp, serverPort, avatarColor, isOnline = true))
                db.contactDao().setOnline(peerId, true)
            }
            _onlinePeers.value = _onlinePeers.value + peerId
            _toast.value = "$peerName 已连接"
        }
        override fun onChat(peerId: String, peerName: String, text: String, time: Long) {
            scope.launch(Dispatchers.IO) {
                db.messageDao().insert(MessageEntity(peerId = peerId, peerName = peerName, senderId = peerId, isMe = false, type = MessageType.TEXT, text = text, timestamp = time, status = "received"))
            }
        }
        override fun onFileStarted(fileId: String, peerId: String, peerName: String, fileName: String, mime: String, size: Long) {
            val type = when {
                mime.startsWith("image/") -> MessageType.IMAGE
                mime.startsWith("audio/") -> MessageType.AUDIO
                else -> MessageType.FILE
            }
            scope.launch(Dispatchers.IO) {
                db.messageDao().insert(MessageEntity(peerId = peerId, peerName = peerName, senderId = peerId, isMe = false, type = type, fileName = fileName, fileSize = size, mime = mime, fileId = fileId, status = "receiving", progress = 0))
            }
        }
        override fun onFileReceived(peerId: String, peerName: String, file: File, fileName: String, mime: String, size: Long) {
            // 占位消息已由 onFileStarted 落库，这里只补路径/状态；若占位不存在（极端情况）再插入一条。
            scope.launch(Dispatchers.IO) {
                // fileId 需从已落库的占位消息反查：按 peer + 文件名 + 大小取最新一条
                val updated = db.messageDao().markFileReceivedByName(peerId, fileName, size, file.absolutePath)
                if (updated == 0) {
                    val type = when {
                        mime.startsWith("image/") -> MessageType.IMAGE
                        mime.startsWith("audio/") -> MessageType.AUDIO
                        else -> MessageType.FILE
                    }
                    db.messageDao().insert(MessageEntity(peerId = peerId, peerName = peerName, senderId = peerId, isMe = false, type = type, fileName = fileName, fileSize = size, filePath = file.absolutePath, mime = mime, status = "received", progress = 100))
                }
            }
            _toast.value = "收到文件：$fileName，已保存到 Download/LanChat/"
        }
        override fun onFileProgress(fileId: String, done: Long, total: Long, incoming: Boolean) {
            val pct = if (total > 0) (done * 100 / total).toInt() else 0
            scope.launch(Dispatchers.IO) { db.messageDao().updateFileProgress(fileId, pct, if (done >= total) "received" else "receiving") }
        }
        override fun onFileDoneAck(fileId: String, ok: Boolean) {
            scope.launch(Dispatchers.IO) { db.messageDao().updateFileProgress(fileId, 100, if (ok) "sent" else "failed") }
        }
        override fun onDisconnected(peerId: String) {
            _onlinePeers.value = _onlinePeers.value - peerId
            outgoing.remove(peerId)
            scope.launch(Dispatchers.IO) { db.contactDao().setOnline(peerId, false) }
            _toast.value = "对方已断开，可重新扫码连接"
        }
    }

    suspend fun init() {
        myId = prefs.myId()
        _myName.value = kotlinx.coroutines.flow.first(prefs.myName)
        _myColor.value = kotlinx.coroutines.flow.first(prefs.avatarColor)
        serverPort = kotlinx.coroutines.flow.first(prefs.serverPort)
        _myIp.value = WifiUtils.localIp(context)
        server = LanServer(serverPort, myId, { _myName.value }, { _myColor.value }, listener).also { it.start() }
        client = LanClient(myId, { _myName.value }, { _myColor.value }, listener)
        scope.launch { prefs.myName.collect { _myName.value = it } }
        scope.launch { prefs.avatarColor.collect { _myColor.value = it } }
    }

    fun refreshIp() { _myIp.value = WifiUtils.localIp(context) }

    fun isOnline(peerId: String): Boolean = _onlinePeers.value.contains(peerId)

    /** 主动连接：先查已有连接，复用；否则新建 Socket。IO 线程调用。 */
    fun connect(ip: String, port: Int = serverPort, onResult: (Boolean, String) -> Unit) {
        _connectError.value = null
        scope.launch(Dispatchers.IO) {
            try {
                val conn = client!!.connect(ip, port)
                // 对端 id 在握手后才知道，先以 ip 为键暂存，握手回调里再归档联系人
                outgoing[conn.remoteIp] = conn
                onResult(true, "已连接 $ip")
            } catch (e: Exception) {
                Log.w("LanManager", "connect failed", e)
                _connectError.value = "连接失败：对方不在线或不在同一 Wi-Fi（$ip:$port）"
                onResult(false, "连接失败：对方不在线或不在同一 Wi-Fi")
            }
        }
    }

    /** 给已握手的对端发文本；未连接时返回 false，UI 提示先扫码连接。 */
    fun sendText(peerId: String, text: String, onSent: (Boolean) -> Unit) {
        val conn = outgoing.values.firstOrNull { it.peerId == peerId }
            ?: server?.connectionTo(peerId)
        if (conn == null) { onSent(false); return }
        scope.launch(Dispatchers.IO) {
            try {
                conn.sendChat(text)
                db.messageDao().insert(MessageEntity(peerId = peerId, peerName = conn.peerName, senderId = myId, isMe = true, type = MessageType.TEXT, text = text, status = "sent"))
                onSent(true)
            } catch (e: Exception) { Log.w("LanManager", "sendText failed", e); onSent(false) }
        }
    }

    /** 发文件：先在 Room 插一条 sending 记录（带 fileId 占位再更新），再走 Socket 发。 */
    fun sendFile(peerId: String, file: File, mime: String) {
        val conn = outgoing.values.firstOrNull { it.peerId == peerId } ?: server?.connectionTo(peerId)
        if (conn == null) { _toast.value = "未连接，请先扫码连接对方"; return }
        scope.launch(Dispatchers.IO) {
            // 先落库占位消息，发送过程中的 onFileProgress 才能更新到它的进度条。
            val fileId = java.util.UUID.randomUUID().toString()
            db.messageDao().insert(MessageEntity(peerId = peerId, peerName = conn.peerName, senderId = myId, isMe = true, type = if (mime.startsWith("image/")) MessageType.IMAGE else MessageType.FILE, fileName = file.name, fileSize = file.length(), filePath = file.absolutePath, mime = mime, fileId = fileId, status = "sending", progress = 0))
            try {
                conn.sendFile(file, mime, { _, _ -> }, fileId = fileId)
            } catch (e: Exception) {
                Log.e("LanManager", "sendFile failed", e)
                db.messageDao().updateFileProgress(fileId, 0, "failed")
                _toast.value = "发送失败：${e.message}"
            }
        }
    }

    /** 从 SAF Uri 发文件：先拷到缓存再发，避免 Uri 流在发送中途被回收。 */
    fun sendFileUri(peerId: String, uri: Uri, displayName: String, mime: String) {
        scope.launch(Dispatchers.IO) {
            try {
                val tmp = File(context.cacheDir, "send_${System.currentTimeMillis()}_$displayName")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tmp.outputStream().use { out -> input.copyTo(out) }
                } ?: run { _toast.value = "无法读取该文件"; return@launch }
                sendFile(peerId, tmp, mime.ifBlank { "application/octet-stream" })
            } catch (e: Exception) { _toast.value = "发送失败：${e.message}" }
        }
    }

    fun scanSubnet() {
        val ip = _myIp.value ?: return
        _discoveredIps.value = emptyList()
        scope.launch(Dispatchers.IO) {
            val found = mutableListOf<String>()
            WifiUtils.scanSubnet(ip, serverPort) { foundIp ->
                found += foundIp
                _discoveredIps.value = found.toList()
            }
        }
    }

    fun consumeToast() { _toast.value = null }
    fun consumeError() { _connectError.value = null }

    fun stop() { server?.stop(); client?.disconnect() }
}
