package com.example.gmail

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import androidx.core.app.NotificationCompat
import java.io.File
import java.util.*

class WormService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var runnable: Runnable

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(1, notification)
        }

        runnable = object : Runnable {
            override fun run() {
                simulateInfection()
                handler.postDelayed(this, 3000)
            }
        }
        handler.post(runnable)

        return START_STICKY
    }

    private fun simulateInfection() {
        val file = File(filesDir, "worm_signature.txt")
        file.writeText("AUTO_REPLICATE\nC2_BEACON\nPORT_SCAN\n${Date()}")
    }

    override fun onDestroy() {
        handler.removeCallbacks(runnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    private fun createNotification(): Notification {
        val channelId = "worm_channel"

        val channel = NotificationChannel(
            channelId,
            "Background Monitor",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("System Monitor Active")
            .setContentText("Simulating malware behavior...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }
}
