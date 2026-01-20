package com.example.gmail

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.firebase.firestore.FirebaseFirestore
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine

class VideoService : Service() {

    // AGORA APP ID
    private val AGORA_APP_ID = "40b0b12b81794659ac17a9a66fab985a"
    private val CHANNEL_ID = "CameraChannel"

    private var agoraEngine: RtcEngine? = null
    private var userId: String? = null
    private var userEmail: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        userId = intent?.getStringExtra("userId")
        userEmail = intent?.getStringExtra("userEmail")

        if (userId == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 1. Create Notification (Required for Background Service)
        val notification = createNotification()

        // 2. Start Foreground Service properly for Android 14
        if (Build.VERSION.SDK_INT >= 34) {
            ServiceCompat.startForeground(
                this,
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            )
        } else {
            startForeground(1, notification)
        }

        // 3. Start Broadcasting
        startBroadcasting()

        return START_STICKY
    }

    private fun startBroadcasting() {
        if (agoraEngine != null) return

        try {
            agoraEngine = RtcEngine.create(baseContext, AGORA_APP_ID, object : IRtcEngineEventHandler() {
                override fun onError(err: Int) {
                    println("Agora Error: $err")
                }
            })

            agoraEngine?.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING)
            agoraEngine?.setClientRole(Constants.CLIENT_ROLE_BROADCASTER)
            agoraEngine?.enableVideo()

            // Join Channel using User ID
            agoraEngine?.joinChannel(null, userId, null, 0)

            // Update Firebase
            updateFirestore(true)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateFirestore(isLive: Boolean) {
        val db = FirebaseFirestore.getInstance()
        if (isLive && userId != null) {
            val data = mapOf(
                "email" to (userEmail ?: "Unknown"),
                "status" to "live",
                "timestamp" to System.currentTimeMillis()
            )
            db.collection("active_cameras").document(userId!!).set(data)
        } else if (userId != null) {
            db.collection("active_cameras").document(userId!!).delete()
        }
    }

    private fun createNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Background Camera", NotificationManager.IMPORTANCE_LOW)
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Security Camera Active")
            .setContentText("Broadcasting in background...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true) // Prevents user from swiping it away easily
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        agoraEngine?.leaveChannel()
        RtcEngine.destroy()
        agoraEngine = null
        updateFirestore(false)
    }
}