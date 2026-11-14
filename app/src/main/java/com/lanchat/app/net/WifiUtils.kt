package com.lanchat.app.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface

object WifiUtils {
    /** 本机局域网 IPv4（如 192.168.1.5）。优先 WifiManager，失败则遍历网卡。 */
    @Suppress("DEPRECATION")
    fun localIp(context: Context): String? {
        try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wm?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                return "%d.%d.%d.%d".format(ipInt and 0xff, ipInt shr 8 and 0xff, ipInt shr 16 and 0xff, ipInt shr 24 and 0xff)
            }
        } catch (_: Exception) { }
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.hostAddress?.startsWith("192.168.") == true }
                ?.hostAddress
                ?: NetworkInterface.getNetworkInterfaces().toList()
                    .flatMap { it.inetAddresses.toList() }
                    .firstOrNull { it is Inet4Address && !it.isLoopbackAddress && it.hostAddress?.startsWith("10.") == true }
                    ?.hostAddress
        } catch (_: Exception) { null }
    }

    fun isWifiConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    /** 扫网段发现：对 192.168.x.1~254 逐个尝试连 8080（短超时），返回在线的 ip 列表。 */
    fun scanSubnet(localIp: String, port: Int, onFound: (String) -> Unit) {
        val prefix = localIp.substringBeforeLast('.')
        val myLast = localIp.substringAfterLast('.').toIntOrNull() ?: return
        for (i in 1..254) {
            if (i == myLast) continue
            val ip = "$prefix.$i"
            try {
                java.net.Socket().use { s ->
                    s.connect(java.net.InetSocketAddress(ip, port), 120)
                    onFound(ip)
                }
            } catch (_: Exception) { /* 该地址无服务，跳过 */ }
        }
    }
}
