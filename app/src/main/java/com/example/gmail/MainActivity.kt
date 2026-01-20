package com.example.gmail

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
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

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }

        val authManager = AuthManager(this)

        setContent {
            GmailTheme {
                var user by remember { mutableStateOf(authManager.getCurrentUser()) }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        if (user == null) {
                            LoginScreen(
                                authManager = authManager,
                                onLoginSuccess = { user = authManager.getCurrentUser() }
                            )
                        } else {
                            MainAppContent(
                                userEmail = user?.email ?: "Unknown",
                                userId = user?.uid ?: "",
                                onSignOut = {
                                    authManager.signOut()
                                    user = null
                                }
                            )
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

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        scope.launch {
            if (result.data != null) {
                val success = authManager.signInWithIntent(result.data!!)
                if (success) {
                    onLoginSuccess()
                } else {
                    Toast.makeText(context, "Sign In Failed. Check Logcat!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Security App", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { launcher.launch(authManager.getSignInIntent()) }) {
                Text("Sign In with Google")
            }
        }
    }
}

@Composable
fun MainAppContent(userEmail: String, userId: String, onSignOut: () -> Unit) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Dashboard", style = MaterialTheme.typography.titleLarge)
            Button(
                onClick = {
                    val intent = Intent(context, VideoService::class.java)
                    context.stopService(intent)
                    onSignOut()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Sign Out") }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        Text("Background Camera", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(16.dp))

        BackgroundCameraControl(userEmail, userId)
    }
}

@Composable
fun BackgroundCameraControl(userEmail: String, userId: String) {
    val context = LocalContext.current
    var isRunning by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val camera = perms[Manifest.permission.CAMERA] ?: false
        val mic = perms[Manifest.permission.RECORD_AUDIO] ?: false
        if (camera && mic) {
            startVideoService(context, userEmail, userId)
            isRunning = true
        } else {
            Toast.makeText(context, "Permissions Required!", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isRunning) Color.Red else Color.DarkGray),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isRunning) {
                Text("🔴 BROADCASTING", color = Color.White, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    val intent = Intent(context, VideoService::class.java)
                    context.stopService(intent)
                    isRunning = false
                }) {
                    Text("Stop Broadcasting")
                }
            } else {
                Text("Camera Offline", color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    launcher.launch(arrayOf(
                        Manifest.permission.CAMERA,
                        Manifest.permission.RECORD_AUDIO
                    ))
                }) {
                    Text("Start Background Stream")
                }
            }
        }
    }
}

fun startVideoService(context: Context, email: String, uid: String) {
    val intent = Intent(context, VideoService::class.java).apply {
        putExtra("userEmail", email)
        putExtra("userId", uid)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}