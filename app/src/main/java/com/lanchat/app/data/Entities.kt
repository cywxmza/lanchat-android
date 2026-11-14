package com.lanchat.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 消息类型：文本 / 图片 / 文件 / 语音 */
object MessageType {
    const val TEXT = "text"
    const val IMAGE = "image"
    const val FILE = "file"
    const val AUDIO = "audio"
    const val SYSTEM = "system"
}

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 对端设备 id（会话键）；自己发的也按对端归档 */
    val peerId: String,
    val peerName: String,
    val senderId: String,
    val isMe: Boolean,
    val type: String,
    val text: String = "",
    val fileName: String = "",
    val fileSize: Long = 0L,
    /** 本地文件路径（图片/文件/语音）；图片也可用 content:// URI 字符串 */
    val filePath: String = "",
    val mime: String = "",
    val fileId: String = "",
    /** 语音时长（秒），仅音频消息用 */
    val durationSec: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    /** sending / sent / failed / received */
    val status: String = "sent",
    val progress: Int = 0
)

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String,
    val name: String,
    val ip: String = "",
    val port: Int = 8080,
    /** 头像色序号，取 AvatarColors 下标 */
    val avatarColor: Int = 0,
    val lastSeen: Long = System.currentTimeMillis(),
    val isOnline: Boolean = false
)

@Entity(tableName = "moments")
data class MomentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val authorId: String,
    val authorName: String,
    val authorColor: Int = 0,
    val text: String,
    /** 图片本地路径，多个用 | 分隔 */
    val imagePaths: String = "",
    val likeCount: Int = 0,
    val likedByMe: Boolean = false,
    /** 评论以 JSON 数组存：[{"author":"..","text":"..","time":..}] */
    val commentsJson: String = "[]",
    val timestamp: Long = System.currentTimeMillis()
)
