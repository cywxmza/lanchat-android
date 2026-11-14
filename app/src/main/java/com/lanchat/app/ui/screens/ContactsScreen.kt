package com.lanchat.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanchat.app.viewmodel.AppViewModel

@Composable
fun ContactsScreen(
    vm: AppViewModel,
    onOpenChat: (peerId: String, peerName: String) -> Unit,
    onScan: () -> Unit,
    onManualConnect: () -> Unit
) {
    val contacts by vm.contacts.collectAsState()
    val online by vm.lanManager.onlinePeers.collectAsState()
    val discovered by vm.lanManager.discoveredIps.collectAsState()
    var manualIp by remember { mutableStateOf("") }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(modifier = Modifier.fillMaxWidth().clickable { onScan() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color(0xFF07C160))
                Spacer(Modifier.width(12.dp))
                Text("扫一扫加好友", fontSize = 16.sp)
            }
            Divider()
            Row(modifier = Modifier.fillMaxWidth().clickable { vm.lanManager.scanSubnet() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF07C160))
                Spacer(Modifier.width(12.dp))
                Text("搜索同一 Wi-Fi 下的设备", fontSize = 16.sp)
            }
            if (discovered.isNotEmpty()) {
                Text("已发现：", modifier = Modifier.padding(start = 16.dp, top = 8.dp), fontSize = 13.sp, color = Color.Gray)
                discovered.forEach { ip ->
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        vm.lanManager.connect(ip) { _, _ -> }
                    }.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(ip, fontSize = 15.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("点此连接", fontSize = 13.sp, color = Color(0xFF07C160))
                    }
                }
            }
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("手动输入 IP 连接", fontSize = 13.sp, color = Color.Gray)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = manualIp, onValueChange = { manualIp = it },
                    label = { Text("如 192.168.1.5") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Button(
                    onClick = { if (manualIp.isNotBlank()) vm.lanManager.connect(manualIp.trim()) { _, _ -> } },
                    enabled = manualIp.isNotBlank()
                ) { Text("连接") }
            }
            Divider()
            Text("我的好友（${contacts.size}）", modifier = Modifier.padding(16.dp), fontSize = 14.sp, color = Color.Gray)
        }
        items(contacts, key = { it.id }) { c ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onOpenChat(c.id, c.name) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(c.name, c.avatarColor, 44)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(c.name, fontSize = 16.sp)
                    Text(if (online.contains(c.id)) "在线 · ${c.ip}" else "离线 · 上次 ${formatTime(c.lastSeen)}", fontSize = 12.sp, color = Color.Gray)
                }
                if (online.contains(c.id)) Text("● 在线", fontSize = 12.sp, color = Color(0xFF07C160))
            }
            Divider(modifier = Modifier.padding(start = 72.dp))
        }
        if (contacts.isEmpty()) {
            item {
                Text("还没有好友，扫一扫对方的二维码即可添加。", modifier = Modifier.padding(16.dp), fontSize = 14.sp, color = Color.Gray)
            }
        }
    }
}
