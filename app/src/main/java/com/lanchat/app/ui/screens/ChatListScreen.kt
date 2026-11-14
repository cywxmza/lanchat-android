package com.lanchat.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanchat.app.data.MessageEntity
import com.lanchat.app.data.MessageType
import com.lanchat.app.viewmodel.AppViewModel

@Composable
fun ChatListScreen(vm: AppViewModel, onOpenChat: (peerId: String, peerName: String) -> Unit) {
    val list by vm.chatList.collectAsState()
    val online by vm.lanManager.onlinePeers.collectAsState()
    if (list.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(80.dp))
            Text("还没有聊天", fontSize = 17.sp, color = Color.Gray)
            Spacer(Modifier.height(12.dp))
            Text("点右上角 + 扫一扫对方的二维码，\n连上同一 Wi-Fi 的手机就能开始聊天、传文件。", fontSize = 14.sp, color = Color.Gray, lineHeight = 22.sp)
            Spacer(Modifier.height(12.dp))
            Text(if (online.isEmpty()) "当前无在线好友" else "在线：${online.size} 人", fontSize = 13.sp, color = Color.Gray)
        }
        return
    }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(list, key = { it.peerId }) { msg ->
            ChatRow(msg, vm, onOpenChat)
            Divider(modifier = Modifier.padding(start = 72.dp), color = Color(0xFFE5E5E5))
        }
    }
}

@Composable
private fun ChatRow(msg: MessageEntity, vm: AppViewModel, onOpenChat: (String, String) -> Unit) {
    val unread by vm.unreadCount(msg.peerId).collectAsState()
    val preview = when (msg.type) {
        MessageType.TEXT -> msg.text
        MessageType.IMAGE -> "[图片]"
        MessageType.FILE -> "[文件] ${msg.fileName}"
        MessageType.AUDIO -> "[语音 ${msg.durationSec}″]"
        else -> msg.text
    }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onOpenChat(msg.peerId, msg.peerName) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BadgedBox(badge = { if (unread > 0) Badge { Text("$unread") } }) {
            Avatar(msg.peerName, msg.peerId.hashCode(), 48)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(msg.peerName, fontSize = 16.sp, maxLines = 1, modifier = Modifier.weight(1f))
                Text(formatTime(msg.timestamp), fontSize = 12.sp, color = Color.Gray)
            }
            Spacer(Modifier.height(4.dp))
            Text(preview, fontSize = 14.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(6.dp))
        val online by vm.lanManager.onlinePeers.collectAsState()
        if (online.contains(msg.peerId)) {
            Text("●", fontSize = 10.sp, color = Color(0xFF07C160))
        }
    }
}
