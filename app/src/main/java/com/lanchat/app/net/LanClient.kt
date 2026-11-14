package com.lanchat.app.net

import android.util.Log
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 局域网客户端：向指定 IP:端口发起 Socket 直连，成功后同样得到一条
 * PeerConnection（与服务端共用同一读/写逻辑）。超时 5 秒，失败抛异常，
 * 调用方据此显示“对方不在线 / 不在同一 Wi-Fi”。
 */
class LanClient(
    private val myId: String,
    private val myName: () -> String,
    private val myAvatarColor: () -> Int,
    private val listener: PeerConnection.Listener
) {
    @Volatile private var connection: PeerConnection? = null

    /** 阻塞式连接（调用方应在 IO 线程调用）。成功返回 PeerConnection。 */
    fun connect(ip: String, port: Int = Protocol.DEFAULT_PORT, timeoutMs: Int = 5_000): PeerConnection {
        connection?.close()
        val socket = Socket()
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress(ip, port), timeoutMs)
        val conn = PeerConnection(socket, myId, myName(), myAvatarColor(), listener)
        connection = conn
        conn.start()
        Log.i("LanClient", "connected to $ip:$port")
        return conn
    }

    fun current(): PeerConnection? = connection

    fun disconnect() {
        connection?.close()
        connection = null
    }
}
