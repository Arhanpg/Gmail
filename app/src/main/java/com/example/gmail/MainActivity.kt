package com.example.gmail

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.gmail.ui.theme.GmailTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        val authManager = AuthManager(this)
        setContent {
            GmailTheme {
                var user by remember { mutableStateOf(authManager.getCurrentUser()) }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        if (user == null) {
                            LoginScreen(authManager) { user = authManager.getCurrentUser() }
                        } else {
                            MainAppContent(user?.email ?: "Unknown", user?.uid ?: "", authManager)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(authManager: AuthManager, onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        scope.launch {
            if (result.data != null && authManager.signInWithIntent(result.data!!)) onLoginSuccess()
            else Toast.makeText(context, "Sign In Failed", Toast.LENGTH_SHORT).show()
        }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = { launcher.launch(authManager.getSignInIntent()) }) { Text("Sign In with Google") }
    }
}

@Composable
fun MainAppContent(userEmail: String, userId: String, authManager: AuthManager) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Dashboard", style = MaterialTheme.typography.titleLarge)
            Button(onClick = {
                context.stopService(Intent(context, VideoService::class.java))
                authManager.signOut()
            }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Sign Out") }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        BackgroundCameraControl(userEmail, userId)
    }
}

@Composable
fun BackgroundCameraControl(userEmail: String, userId: String) {
    val context = LocalContext.current
    var isRunning by remember { mutableStateOf(false) }

    // PERMISSIONS FOR CAMERA & MIC
    val camLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val hasCam = it[Manifest.permission.CAMERA] == true
        val hasMic = it[Manifest.permission.RECORD_AUDIO] == true
        if (hasCam && hasMic) {
            // Check File Permission separately
            if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
                Toast.makeText(context, "Please allow 'All Files Access' then try again", Toast.LENGTH_LONG).show()
            } else {
                startVideoService(context, userEmail, userId)
                isRunning = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(12.dp))
        .background(if (isRunning) Color.Red else Color.DarkGray), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isRunning) {
                Text("🔴 LIVE & FILES SERVER ON", color = Color.White)
                Text("Check Notification for IP URL", color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { context.stopService(Intent(context, VideoService::class.java)); isRunning = false }) { Text("Stop All") }
            } else {
                Text("System Offline", color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { camLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }) { Text("Start System") }
            }
        }
    }
}

fun startVideoService(context: Context, email: String, uid: String) {
    val intent = Intent(context, VideoService::class.java).apply { putExtra("userEmail", email); putExtra("userId", uid) }
    if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
}