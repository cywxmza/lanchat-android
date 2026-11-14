package com.lanchat.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.lanchat.app.MainActivity
import com.lanchat.app.R

/**
 * 前台 Service：App 切后台后局域网服务器仍在监听，对端仍能发来消息/文件。
 * 实际的 LanServer 由 LanManager 持有，本 Service 只负责前台通知保活；
 * 进程被杀后由用户重新打开 App 恢复（Android 无法保证 100% 不被杀）。
 */
class LanServerService : Service() {
    companion object {
        const val CHANNEL_ID = "lanchat_server"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.lanchat.app.STOP_SERVER"

        fun start(context: Context) {
            val intent = Intent(context, LanServerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(context: Context) { context.stopService(Intent(context, LanServerService::class.java)) }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "局域传信服务", NotificationManager.IMPORTANCE_LOW)
            channel.description = "保持局域网接收在线，后台也能收到消息和文件"
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, LanServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("局域传信正在运行")
            .setContentText("局域网接收已开启，同一 Wi-Fi 的好友可发来消息和文件")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openIntent)
            .addAction(0, "停止", stopIntent)
            .setOngoing(true)
            .build()
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
