package com.example.gmail

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

class AuthManager(private val context: Context) {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val WEB_CLIENT_ID = "595808403098-51096iteaaunk52a9d30nn4dt0va7q45.apps.googleusercontent.com"

    private val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(WEB_CLIENT_ID)
        .requestEmail()
        .build()

    private val googleSignInClient = GoogleSignIn.getClient(context, gso)

    fun getCurrentUser() = auth.currentUser

    fun getSignInIntent(): Intent {
        googleSignInClient.signOut() // Force account picker to show
        return googleSignInClient.signInIntent
    }

    suspend fun signInWithIntent(intent: Intent): Boolean {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(intent)
            val account = task.getResult(ApiException::class.java)

            Log.d("AuthManager", "Google Success: ${account.email}")

            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            val authResult = auth.signInWithCredential(credential).await()
            authResult.user != null
        } catch (e: ApiException) {
            // LOOK AT THIS NUMBER IN LOGCAT IF IT FAILS
            Log.e("AuthManager", "Google Sign In Failed. Code: ${e.statusCode}")
            // Code 10 = Wrong SHA-1 or google-services.json
            // Code 12500 = Wrong WEB_CLIENT_ID (You pasted the Android ID instead of Web ID)
            false
        } catch (e: Exception) {
            Log.e("AuthManager", "Firebase Auth Failed: ${e.message}")
            false
        }
    }

    fun signOut() {
        auth.signOut()
        googleSignInClient.signOut()
    }
}