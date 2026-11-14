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
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
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
import com.lanchat.app.ui.theme.AvatarColors
import com.lanchat.app.viewmodel.AppViewModel

@Composable
fun ProfileScreen(vm: AppViewModel, onShowQr: () -> Unit, onScan: () -> Unit, onAbout: () -> Unit) {
    val myName by vm.myName.collectAsState()
    val myColor by vm.myColor.collectAsState()
    val myIp by vm.lanManager.myIp.collectAsState()
    val port by vm.serverPort.collectAsState()
    var nameDraft by remember(myName) { mutableStateOf(myName) }
    var portDraft by remember(port) { mutableStateOf(port.toString()) }
    var showAbout by remember { mutableStateOf(false) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                AvatarCircle(myName, myColor, 64)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(myName, fontSize = 20.sp)
                    Text("ID：${vm.lanManager.myId}", fontSize = 13.sp, color = Color.Gray)
                    Text("本机 IP：${myIp ?: "未连 Wi-Fi"} : $port", fontSize = 13.sp, color = Color.Gray)
                }
            }
            Divider()
            Column(modifier = Modifier.padding(16.dp)) {
                Text("昵称", fontSize = 14.sp, color = Color.Gray)
                OutlinedTextField(value = nameDraft, onValueChange = { nameDraft = it }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Button(onClick = { if (nameDraft.isNotBlank()) vm.setMyName(nameDraft.trim()) }) { Text("保存昵称") }
                Spacer(Modifier.height(16.dp))
                Text("头像颜色", fontSize = 14.sp, color = Color.Gray)
                Spacer(Modifier.height(8.dp))
                Row {
                    AvatarColors.forEachIndexed { idx, c ->
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.size(36.dp).padding(4.dp)
                                .clickable { vm.setMyColor(idx) }
                                .background(c, shape = androidx.compose.foundation.shape.CircleShape)
                        ) { if (idx == myColor) Text("✓", color = Color.White, modifier = Modifier.align(Alignment.Center)) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("服务器端口（改动后重启 App 生效，默认 8080）", fontSize = 14.sp, color = Color.Gray)
                OutlinedTextField(value = portDraft, onValueChange = { portDraft = it.filter { ch -> ch.isDigit() } }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { portDraft.toIntOrNull()?.let { vm.setPort(it) } }, modifier = Modifier.padding(top = 8.dp)) { Text("保存端口") }
            }
            Divider()
            Row(modifier = Modifier.fillMaxWidth().clickable { onShowQr() }.padding(16.dp)) { Text("我的二维码", fontSize = 16.sp) }
            Divider()
            Row(modifier = Modifier.fillMaxWidth().clickable { onScan() }.padding(16.dp)) { Text("扫一扫", fontSize = 16.sp) }
            Divider()
            Column(modifier = Modifier.padding(16.dp)) {
                Text("接收文件保存位置", fontSize = 14.sp, color = Color.Gray)
                Text("手机存储 / Download / LanChat /", fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp))
                Text("收到文件后可在聊天界面的文件卡片点“打开”，或直接去文件管理器查看。", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
            }
            Divider()
            Row(modifier = Modifier.fillMaxWidth().clickable { showAbout = true; onAbout() }.padding(16.dp)) { Text("关于局域传信", fontSize = 16.sp) }
            if (showAbout) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("局域传信 v1.0.0", fontSize = 15.sp)
                    Text("微信风格的局域网聊天 + 文件互传。同一 Wi-Fi 下扫码直连，消息和文件走本地 Socket，不经过任何服务器。\n\n本 App 与微信无关，未使用微信商标、Logo 或官方素材。", fontSize = 13.sp, color = Color.Gray)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
