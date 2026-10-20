package com.androidmaster.app

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

class DeviceConnectionService : Service() {
    private lateinit var bridge: DeviceBridge

    override fun onCreate() {
        super.onCreate()
        bridge = DeviceBridgeHolder.get(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Keeping your device connection available"))
        if (intent?.action == ACTION_DISCONNECT) {
            bridge.disconnect()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val url = intent?.getStringExtra(EXTRA_URL)
        val code = intent?.getStringExtra(EXTRA_CODE)
        if (!url.isNullOrBlank() && !code.isNullOrBlank()) {
            bridge.connect(url, code)
        } else {
            bridge.autoConnectSaved()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "AndroidMaster connection", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows when AndroidMaster maintains its remote-control connection"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("AndroidMaster is connected")
            .setContentText(message)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "androidmaster_connection"
        private const val NOTIFICATION_ID = 7301
        private const val ACTION_DISCONNECT = "com.androidmaster.app.DISCONNECT"
        private const val EXTRA_URL = "relay_url"
        private const val EXTRA_CODE = "device_code"

        fun start(context: Context, url: String, code: String) {
            val intent = Intent(context, DeviceConnectionService::class.java)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_CODE, code)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun startSaved(context: Context) {
            val intent = Intent(context, DeviceConnectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun stop(context: Context) {
            DeviceBridgeHolder.get(context).disconnect()
            context.stopService(Intent(context, DeviceConnectionService::class.java))
        }
    }
}
