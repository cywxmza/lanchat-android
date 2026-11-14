package com.lanchat.app.net

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException

/**
 * 局域传信私有协议 v1（见 README「协议说明」）。
 *
 * 传输层：TCP 长连接，一条 Socket 对应一个对端。
 * 帧格式（控制帧）：一行 JSON + '\n'（UTF-8）。
 *   {"type":"handshake","id":"dev-xxxx","name":"小明","avatarColor":2,"port":8080}
 *   {"type":"chat","id":"...","name":"...","text":"你好","time":1730000000000}
 *   {"type":"heartbeat","time":...}
 *   {"type":"file-meta","fileId":"...","name":"报告.pdf","size":123456,"mime":"application/pdf"}
 *   {"type":"file-done","fileId":"...","ok":true}
 * 文件体帧：紧跟在 file-meta 行之后，原始二进制，按 16 KiB 一片顺序发送，
 * 接收方累计读满 size 字节即完成（不靠换行符分隔，见 FileTransfer）。
 * 这样控制与文件共用一条连接，文本可边聊边传文件。
 */
object Protocol {
    const val CHUNK_SIZE = 16 * 1024 // 16 KiB
    const val DEFAULT_PORT = 8080
    val gson: Gson = Gson()

    const val TYPE_HANDSHAKE = "handshake"
    const val TYPE_CHAT = "chat"
    const val TYPE_HEARTBEAT = "heartbeat"
    const val TYPE_FILE_META = "file-meta"
    const val TYPE_FILE_DONE = "file-done"

    data class WireMessage(
        @SerializedName("type") val type: String,
        @SerializedName("id") val id: String = "",
        @SerializedName("name") val name: String = "",
        @SerializedName("avatarColor") val avatarColor: Int = 0,
        @SerializedName("port") val port: Int = DEFAULT_PORT,
        @SerializedName("text") val text: String = "",
        @SerializedName("time") val time: Long = System.currentTimeMillis(),
        @SerializedName("fileId") val fileId: String = "",
        @SerializedName("fileName") val fileName: String = "",
        @SerializedName("fileSize") val fileSize: Long = 0L,
        @SerializedName("mime") val mime: String = "",
        @SerializedName("ok") val ok: Boolean = true
    )

    fun toJson(msg: WireMessage): String = gson.toJson(msg)
    fun fromJson(json: String): WireMessage = gson.fromJson(json, WireMessage::class.java)

    /** 从流中读一行 JSON（按字节读到 '\n'，避免 BufferedReader 预读吞掉后续文件二进制）。 */
    @Throws(IOException::class)
    fun readLine(input: BufferedInputStream): String? {
        val buf = StringBuilder()
        while (true) {
            val b = input.read()
            if (b == -1) return if (buf.isEmpty()) null else buf.toString()
            if (b == '\n'.code) return buf.toString()
            if (b != '\r'.code) buf.append(b.toChar())
            if (buf.length > 1024 * 1024) throw IOException("JSON 行过长")
        }
    }

    fun writeLine(output: BufferedOutputStream, json: String) {
        val bytes = (json + "\n").toByteArray(Charsets.UTF_8)
        synchronized(output) {
            output.write(bytes)
            output.flush()
        }
    }
}
