package com.lanchat.app.ui.screens

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.InsertEmoticon
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lanchat.app.data.MessageEntity
import com.lanchat.app.data.MessageType
import com.lanchat.app.net.FileTransfer
import com.lanchat.app.viewmodel.AppViewModel
import java.io.File

private val EMOJIS = listOf("😀","😂","😍","🥰","😎","😢","😡","👍","🙏","🎉","❤️","🔥","😊","🤔","👏","💪","🌹","☕","🚀","🐶")

@Composable
fun ChatScreen(vm: AppViewModel, peerId: String, peerName: String) {
    val context = LocalContext.current
    val messages by vm.messagesFor(peerId).collectAsState()
    val online by vm.lanManager.onlinePeers.collectAsState()
    val isOnline = online.contains(peerId)
    var input by remember { mutableStateOf("") }
    var showEmoji by remember { mutableStateOf(false) }
    var showAttach by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var playingId by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()
    val recorderRef = remember { mutableStateOf<MediaRecorder?>(null) }
    val recordFileRef = remember { mutableStateOf<File?>(null) }
    val recordStartRef = remember { mutableStateOf(0L) }

    LaunchedEffect(peerId) { vm.markRead(peerId) }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // 图片：相册选择
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val file = uriToCacheFile(context, uri, "img_${System.currentTimeMillis()}.jpg") ?: return@rememberLauncherForActivityResult
        vm.lanManager.sendFile(peerId, file, "image/jpeg")
        Toast.makeText(context, "图片发送中…", Toast.LENGTH_SHORT).show()
    }
    // 文件：SAF 任意类型
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file_${System.currentTimeMillis()}"
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        vm.lanManager.sendFileUri(peerId, uri, name, mime)
        Toast.makeText(context, "文件发送中…", Toast.LENGTH_SHORT).show()
    }

    fun sendText() {
        if (input.isBlank()) return
        if (!isOnline) Toast.makeText(context, "对方不在线，请先扫码重新连接", Toast.LENGTH_SHORT).show()
        vm.lanManager.sendText(peerId, input.trim()) { ok ->
            if (!ok) Toast.makeText(context, "发送失败：未连接", Toast.LENGTH_SHORT).show()
        }
        input = ""
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFEDEDED))) {
        if (!isOnline) {
            Text("对方离线，消息暂无法送达 — 重新扫码连接后可继续", modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF3CD)).padding(8.dp), fontSize = 13.sp, color = Color(0xFF856404))
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)) {
            items(messages, key = { it.id }) { msg ->
                MessageBubble(msg, peerName, playingId, onPlayAudio = { m ->
                    playAudio(context, m, playingId) { playingId = it }
                }, onOpenFile = { m -> openFile(context, m) })
                Spacer(Modifier.height(10.dp))
            }
        }
        if (showEmoji) {
            Column(modifier = Modifier.fillMaxWidth().background(Color.White).padding(8.dp)) {
                EMOJIS.chunked(10).forEach { row ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { e -> Text(e, fontSize = 26.sp, modifier = Modifier.clickable { input += e }.padding(4.dp)) }
                    }
                }
            }
        }
        if (showAttach) {
            Row(modifier = Modifier.fillMaxWidth().background(Color.White).padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = { pickImage.launch("image/*"); showAttach = false }) { Text("图片") }
                Button(onClick = { pickFile.launch("*/*"); showAttach = false }) { Text("文件") }
                Button(onClick = { showAttach = false; Toast.makeText(context, "长按下方麦克风录音", Toast.LENGTH_SHORT).show() }) { Text("语音") }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().background(Color(0xFFF7F7F7)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { showEmoji = !showEmoji; showAttach = false }) {
                Icon(Icons.Default.InsertEmoticon, contentDescription = "表情")
            }
            OutlinedTextField(
                value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("输入消息…") }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { sendText() })
            )
            IconButton(onClick = { showAttach = !showAttach; showEmoji = false }) {
                Icon(Icons.Default.AttachFile, contentDescription = "附件")
            }
            // 按住说话
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(22.dp))
                    .background(if (recording) Color(0xFF07C160) else Color(0xFFE5E5E5))
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                val f = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
                                val rec = startRecording(context, f)
                                if (rec != null) {
                                    recorderRef.value = rec; recordFileRef.value = f
                                    recordStartRef.value = System.currentTimeMillis(); recording = true
                                    try { awaitRelease() } finally {
                                        stopRecording(recorderRef.value)
                                        recorderRef.value = null; recording = false
                                        val dur = ((System.currentTimeMillis() - recordStartRef.value) / 1000).toInt().coerceAtLeast(1)
                                        val vf = recordFileRef.value
                                        if (vf != null && vf.exists() && vf.length() > 0) {
                                            vm.lanManager.sendFile(peerId, vf, "audio/mp4")
                                            Toast.makeText(context, "语音已发送（${dur}″）", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "录音失败：请检查麦克风权限", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Mic, contentDescription = "按住说话", tint = if (recording) Color.White else Color.DarkGray) }
            IconButton(onClick = { sendText() }) {
                Icon(Icons.Default.Send, contentDescription = "发送", tint = Color(0xFF07C160))
            }
        }
        if (recording) Text("正在录音…松开发送", modifier = Modifier.fillMaxWidth().background(Color(0xFFFFE0E0)).padding(6.dp), fontSize = 13.sp, color = Color.Red)
    }
}

@Composable
private fun MessageBubble(msg: MessageEntity, peerName: String, playingId: Long?, onPlayAudio: (MessageEntity) -> Unit, onOpenFile: (MessageEntity) -> Unit) {
    val isMe = msg.isMe
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start) {
        if (!isMe) { Avatar(peerName, msg.peerId.hashCode(), 36); Spacer(Modifier.width(8.dp)) }
        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            when (msg.type) {
                MessageType.TEXT -> Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (isMe) Color(0xFF95EC69) else Color.White).padding(10.dp)) {
                    Text(msg.text, fontSize = 16.sp)
                }
                MessageType.IMAGE -> {
                    val path = msg.filePath
                    if (path.isNotBlank() && File(path).exists()) {
                        AsyncImage(model = File(path), contentDescription = "图片", modifier = Modifier.size(180.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                    } else {
                        Box(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).padding(10.dp)) { Text("[图片] ${msg.fileName}") }
                    }
                }
                MessageType.AUDIO -> Row(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (isMe) Color(0xFF95EC69) else Color.White).clickable { onPlayAudio(msg) }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text("语音 ${msg.durationSec}″ ${if (playingId == msg.id) "播放中…" else ""}", fontSize = 15.sp)
                }
                else -> Column(modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).padding(12.dp).clickable { onOpenFile(msg) }) {
                    Text(msg.fileName.ifBlank { "文件" }, fontSize = 15.sp)
                    Text(FileTransfer.formatSize(msg.fileSize), fontSize = 12.sp, color = Color.Gray)
                    if (msg.progress in 1..99) {
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(progress = msg.progress / 100f, modifier = Modifier.width(180.dp))
                        Text("${msg.progress}% · ${msg.status}", fontSize = 12.sp, color = Color.Gray)
                    } else {
                        Text(if (msg.status == "failed") "传输失败，点此重试（重新发送）" else "点此打开 · 已保存到 Download/LanChat/", fontSize = 12.sp, color = Color(0xFF07C160))
                    }
                }
            }
            Text(formatTime(msg.timestamp), fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 2.dp))
        }
        if (isMe) { Spacer(Modifier.width(8.dp)); Avatar("我", 0, 36) }
    }
}

private fun uriToCacheFile(context: Context, uri: Uri, name: String): File? = try {
    val f = File(context.cacheDir, name)
    context.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use { out -> input.copyTo(out) } }
    f
} catch (_: Exception) { null }

@Suppress("DEPRECATION")
private fun startRecording(context: Context, file: File): MediaRecorder? = try {
    val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
    rec.setAudioSource(MediaRecorder.AudioSource.MIC)
    rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
    rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
    rec.setOutputFile(file.absolutePath)
    rec.prepare(); rec.start(); rec
} catch (_: Exception) { null }

private fun stopRecording(rec: MediaRecorder?) {
    try { rec?.stop() } catch (_: Exception) { }
    try { rec?.release() } catch (_: Exception) { }
}

private var currentPlayer: MediaPlayer? = null
private fun playAudio(context: Context, msg: MessageEntity, playingId: Long?, setPlaying: (Long?) -> Unit) {
    try {
        currentPlayer?.release()
        if (playingId == msg.id) { setPlaying(null); return }
        val path = msg.filePath
        if (path.isBlank() || !File(path).exists()) { Toast.makeText(context, "音频文件不存在", Toast.LENGTH_SHORT).show(); return }
        val p = MediaPlayer().apply { setDataSource(path); prepare(); start() }
        currentPlayer = p
        setPlaying(msg.id)
        p.setOnCompletionListener { setPlaying(null); p.release(); currentPlayer = null }
    } catch (e: Exception) {
        Toast.makeText(context, "播放失败：${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun openFile(context: Context, msg: MessageEntity) {
    val path = msg.filePath
    if (path.isBlank() || !File(path).exists()) { Toast.makeText(context, "文件不存在（可能已被清理）", Toast.LENGTH_SHORT).show(); return }
    try {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "com.lanchat.app.fileprovider", File(path))
        val mime = msg.mime.ifBlank { "*/*" }
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "打开文件"))
    } catch (e: Exception) {
        Toast.makeText(context, "无法打开：${e.message}", Toast.LENGTH_SHORT).show()
    }
}
