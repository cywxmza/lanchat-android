package com.lanchat.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanchat.app.qr.QrCodeUtils
import com.lanchat.app.viewmodel.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 我的二维码：对方扫码即拿到本机 IP/端口/昵称/ID，直接 Socket 直连。 */
@Composable
fun QrScreen(vm: AppViewModel) {
    val myIp by vm.lanManager.myIp.collectAsState()
    val myName by vm.myName.collectAsState()
    val port by vm.serverPort.collectAsState()
    val myId = vm.lanManager.myId
    val payload = if (myIp != null) QrCodeUtils.encodePayload(myIp!!, port, myName, myId) else ""
    val bitmap by produceState<Bitmap?>(null, payload) {
        value = if (payload.isNotBlank()) withContext(Dispatchers.Default) { QrCodeUtils.generateBitmap(payload, 640) } else null
    }
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(32.dp))
        AvatarCircle(myName, 0, 64)
        Spacer(Modifier.height(12.dp))
        Text(myName, fontSize = 20.sp)
        Text("ID：$myId", fontSize = 13.sp, color = androidx.compose.ui.graphics.Color.Gray)
        Spacer(Modifier.height(24.dp))
        if (bitmap != null) {
            Image(bitmap = bitmap!!.asImageBitmap(), contentDescription = "我的二维码", modifier = Modifier.size(280.dp))
            Spacer(Modifier.height(16.dp))
            Text(payload, fontSize = 12.sp, color = androidx.compose.ui.graphics.Color.Gray)
            Spacer(Modifier.height(8.dp))
            Text("让对方打开 App 点「扫一扫」扫这个码，\n同一 Wi-Fi 下即可直连。", fontSize = 14.sp, lineHeight = 22.sp)
        } else {
            Text("未检测到 Wi-Fi IP，请先连接同一 Wi-Fi，\n再回到本页。", fontSize = 15.sp, lineHeight = 24.sp)
            Text("点右上角刷新可重新获取 IP。", fontSize = 13.sp, color = androidx.compose.ui.graphics.Color.Gray)
        }
    }
}
