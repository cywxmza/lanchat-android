package com.lanchat.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lanchat.app.service.LanServerService
import com.lanchat.app.ui.screens.ChatListScreen
import com.lanchat.app.ui.screens.ChatScreen
import com.lanchat.app.ui.screens.ContactsScreen
import com.lanchat.app.ui.screens.DiscoverScreen
import com.lanchat.app.ui.screens.ProfileScreen
import com.lanchat.app.ui.screens.QrScreen
import com.lanchat.app.ui.screens.ScanScreen
import com.lanchat.app.ui.theme.LanChatTheme
import com.lanchat.app.viewmodel.AppViewModel
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val CHATS = "chats"
    const val CONTACTS = "contacts"
    const val DISCOVER = "discover"
    const val PROFILE = "profile"
    const val CHAT = "chat/{peerId}/{peerName}"
    const val QR = "qr"
    const val SCAN = "scan"
    fun chat(peerId: String, peerName: String) =
        "chat/${URLEncoder.encode(peerId, "UTF-8")}/${URLEncoder.encode(peerName, "UTF-8")}"
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val denied = results.filterValues { !it }.keys
        if (denied.isNotEmpty()) {
            Toast.makeText(this, "部分权限未授予：${denied.joinToString()}，扫码/录音/通知可能不可用", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionsIfNeeded()
        // 前台服务保活：切后台仍可接收消息/文件。
        LanServerService.start(this)
        setContent { LanChatTheme { LanChatApp(vm) } }
    }

    private fun requestPermissionsIfNeeded() {
        val perms = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms += Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms += Manifest.permission.NEARBY_WIFI_DEVICES
        val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    override fun onDestroy() {
        // 不在这里停服务：用户可能只是切后台；服务由用户在通知栏点“停止”关闭，
        // 或在“我”页退出时由系统回收。进程被杀时服务随之结束。
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanChatApp(vm: AppViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: Routes.CHATS
    val toast by vm.lanManager.toast.collectAsState()
    val error by vm.lanManager.connectError.collectAsState()
    // 用 Toast 显示管理器事件（连接成功/收到文件/断开等）
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.LaunchedEffect(toast) {
        toast?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); vm.lanManager.consumeToast() }
    }
    androidx.compose.runtime.LaunchedEffect(error) {
        error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); vm.lanManager.consumeError() }
    }

    var showAddMenu by remember { mutableStateOf(false) }
    val isTab = route in listOf(Routes.CHATS, Routes.CONTACTS, Routes.DISCOVER, Routes.PROFILE)
    val title = when {
        route == Routes.CHATS -> "局域传信"
        route == Routes.CONTACTS -> "通讯录"
        route == Routes.DISCOVER -> "发现"
        route == Routes.PROFILE -> "我"
        route == Routes.QR -> "我的二维码"
        route == Routes.SCAN -> "扫一扫"
        route.startsWith("chat/") -> URLDecoder.decode(route.substringAfterLast("/"), "UTF-8")
        else -> "局域传信"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFEDEDED)),
                actions = {
                    if (route == Routes.CHATS) {
                        IconButton(onClick = { nav.navigate(Routes.SCAN) }) { Icon(Icons.Default.QrCodeScanner, contentDescription = "扫一扫") }
                        IconButton(onClick = { showAddMenu = !showAddMenu }) { Icon(Icons.Default.Add, contentDescription = "更多") }
                    }
                    if (route == Routes.QR) {
                        IconButton(onClick = { vm.lanManager.refreshIp() }) { Icon(Icons.Default.Refresh, contentDescription = "刷新 IP") }
                    }
                }
            )
        },
        bottomBar = {
            if (isTab) {
                NavigationBar(containerColor = Color(0xFFF7F7F7)) {
                    NavigationBarItem(selected = route == Routes.CHATS, onClick = { nav.navigate(Routes.CHATS) { popUpTo(Routes.CHATS) } }, icon = { Icon(Icons.Default.ChatBubble, null) }, label = { Text("微信") })
                    NavigationBarItem(selected = route == Routes.CONTACTS, onClick = { nav.navigate(Routes.CONTACTS) { popUpTo(Routes.CHATS) } }, icon = { Icon(Icons.Default.Contacts, null) }, label = { Text("通讯录") })
                    NavigationBarItem(selected = route == Routes.DISCOVER, onClick = { nav.navigate(Routes.DISCOVER) { popUpTo(Routes.CHATS) } }, icon = { Icon(Icons.Default.Explore, null) }, label = { Text("发现") })
                    NavigationBarItem(selected = route == Routes.PROFILE, onClick = { nav.navigate(Routes.PROFILE) { popUpTo(Routes.CHATS) } }, icon = { Icon(Icons.Default.Person, null) }, label = { Text("我") })
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            NavHost(navController = nav, startDestination = Routes.CHATS) {
                composable(Routes.CHATS) {
                    ChatListScreen(vm) { peerId, peerName -> nav.navigate(Routes.chat(peerId, peerName)) }
                }
                composable(Routes.CONTACTS) {
                    ContactsScreen(vm,
                        onOpenChat = { peerId, peerName -> nav.navigate(Routes.chat(peerId, peerName)) },
                        onScan = { nav.navigate(Routes.SCAN) },
                        onManualConnect = { nav.navigate(Routes.SCAN) })
                }
                composable(Routes.DISCOVER) { DiscoverScreen(vm) }
                composable(Routes.PROFILE) {
                    ProfileScreen(vm, onShowQr = { nav.navigate(Routes.QR) }, onScan = { nav.navigate(Routes.SCAN) }, onAbout = {})
                }
                composable(
                    Routes.CHAT,
                    arguments = listOf(navArgument("peerId") { type = NavType.StringType }, navArgument("peerName") { type = NavType.StringType })
                ) { entry ->
                    val peerId = URLDecoder.decode(entry.arguments?.getString("peerId") ?: "", "UTF-8")
                    val peerName = URLDecoder.decode(entry.arguments?.getString("peerName") ?: "", "UTF-8")
                    ChatScreen(vm, peerId, peerName)
                }
                composable(Routes.QR) { QrScreen(vm) }
                composable(Routes.SCAN) {
                    ScanScreen(vm) { peerId, peerName -> nav.navigate(Routes.chat(peerId, peerName)) }
                }
            }
            if (showAddMenu) {
                // 轻量“+”菜单：不引入额外弹窗库，直接用一列按钮覆盖右上角区域
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(16.dp).align(androidx.compose.ui.Alignment.TopEnd)
                ) {
                    androidx.compose.material3.Button(onClick = { showAddMenu = false; nav.navigate(Routes.SCAN) }) { Text("扫一扫") }
                    androidx.compose.material3.Button(onClick = { showAddMenu = false; nav.navigate(Routes.QR) }) { Text("我的二维码") }
                    androidx.compose.material3.Button(onClick = { showAddMenu = false; vm.lanManager.scanSubnet(); nav.navigate(Routes.CONTACTS) }) { Text("搜索附近设备") }
                }
            }
        }
    }
}
