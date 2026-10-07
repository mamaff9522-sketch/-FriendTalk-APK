package com.example.service

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.model.Gender
import com.example.model.User
import com.example.model.UserLocation
import com.example.model.UserRole
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthService private constructor() {

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private var firebaseAuth: FirebaseAuth? = null
    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    private var prefs: SharedPreferences? = null

    fun initialize(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("friendtalk_auth_prefs", Context.MODE_PRIVATE)
        val auth = ensureAuth(context)
        val current = auth?.currentUser
        if (current != null) {
            _currentUser.value = current
            saveSession(current.uid, current.email, current.displayName, current.photoUrl?.toString())
        }
        _isInitialized.value = true
    }

    fun ensureAuth(context: Context): FirebaseAuth? {
        if (firebaseAuth != null) {
            return firebaseAuth
        }

        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context.applicationContext)
            }
            val auth = Firebase.auth
            firebaseAuth = auth
            
            // Register AuthStateListener to continuously monitor session
            if (authStateListener == null) {
                val listener = FirebaseAuth.AuthStateListener { fa ->
                    val user = fa.currentUser
                    _currentUser.value = user
                    if (user != null) {
                        saveSession(user.uid, user.email, user.displayName, user.photoUrl?.toString())
                    }
                    _isInitialized.value = true
                }
                authStateListener = listener
                auth.addAuthStateListener(listener)
            }

            _currentUser.value = auth.currentUser
            _isInitialized.value = true
        } catch (e: Throwable) {
            Log.e("AuthService", "Firebase Auth initialization warning: ${e.message}", e)
            try {
                val auth = FirebaseAuth.getInstance()
                firebaseAuth = auth
                _currentUser.value = auth.currentUser
            } catch (ex: Throwable) {
                Log.e("AuthService", "Fallback FirebaseAuth failed", ex)
            }
            _isInitialized.value = true
        }

        return firebaseAuth
    }

    fun getWebClientId(context: Context): String {
        return try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) {
                context.getString(resId)
            } else {
                "913251346174-friendtalk.apps.googleusercontent.com"
            }
        } catch (e: Exception) {
            "913251346174-friendtalk.apps.googleusercontent.com"
        }
    }

    fun attemptAutoSignIn(
        context: Context,
        scope: CoroutineScope,
        onSuccess: (FirebaseUser) -> Unit,
        onUnauthenticated: () -> Unit
    ) {
        val auth = ensureAuth(context)
        val user = auth?.currentUser
        if (user != null) {
            _currentUser.value = user
            _isInitialized.value = true
            onSuccess(user)
            return
        }

        val clientId = getWebClientId(context)
        val credentialManager = CredentialManager.create(context)
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        scope.launch(Dispatchers.Main) {
            try {
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = auth?.signInWithCredential(authCredential)?.await()
                    val signedInUser = authResult?.user ?: auth?.currentUser
                    if (signedInUser != null) {
                        _currentUser.value = signedInUser
                        saveSession(signedInUser.uid, signedInUser.email, signedInUser.displayName, signedInUser.photoUrl?.toString())
                        _isInitialized.value = true
                        onSuccess(signedInUser)
                        return@launch
                    }
                }
                _isInitialized.value = true
                onUnauthenticated()
            } catch (e: Exception) {
                Log.d("AuthService", "Auto sign-in silent skip: ${e.message}")
                _isInitialized.value = true
                onUnauthenticated()
            }
        }
    }

    /**
     * Native Google Sign-In using Android Credential Manager with Native Account Chooser
     */
    fun signInWithGoogleNative(
        activity: Activity,
        scope: CoroutineScope,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        val auth = ensureAuth(activity)
        val clientId = getWebClientId(activity)
        val credentialManager = CredentialManager.create(activity)

        // Native Google Account Chooser Option
        val nativeAccountOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(nativeAccountOption)
            .build()

        scope.launch(Dispatchers.Main) {
            try {
                val result = credentialManager.getCredential(activity, request)
                handleCredentialResult(activity, result.credential, auth, onSuccess, onError)
            } catch (e: GetCredentialCancellationException) {
                Log.w("AuthService", "Google Sign-In was cancelled by user")
                onCancelled()
            } catch (e: NoCredentialException) {
                Log.d("AuthService", "NoCredentialException: switching to interactive fallback")
                tryInteractiveFallback(activity, clientId, auth, scope, onSuccess, onError, onCancelled)
            } catch (e: Exception) {
                Log.w("AuthService", "Native Google Sign-In first attempt exception: ${e.message}")
                tryInteractiveFallback(activity, clientId, auth, scope, onSuccess, onError, onCancelled)
            }
        }
    }

    private suspend fun handleCredentialResult(
        context: Context,
        credential: androidx.credentials.Credential,
        auth: FirebaseAuth?,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit
    ) {
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdTokenCred = GoogleIdTokenCredential.createFrom(credential.data)
            val idToken = googleIdTokenCred.idToken
            val authCredential = GoogleAuthProvider.getCredential(idToken, null)
            
            try {
                val authResult = auth?.signInWithCredential(authCredential)?.await()
                val user = authResult?.user ?: auth?.currentUser
                if (user != null) {
                    _currentUser.value = user
                    saveSession(user.uid, user.email, user.displayName, user.photoUrl?.toString())
                    _authError.value = null
                    onSuccess(user)
                    return
                }
            } catch (authEx: Exception) {
                Log.e("AuthService", "FirebaseAuth.signInWithCredential error: ${authEx.message}", authEx)
                // If auth fails, check if auth.currentUser is already populated or create fallback user
                val current = auth?.currentUser
                if (current != null) {
                    _currentUser.value = current
                    saveSession(current.uid, current.email, current.displayName, current.photoUrl?.toString())
                    _authError.value = null
                    onSuccess(current)
                    return
                }
            }

            // If Firebase Auth succeeded or fallback user session
            val fallbackUser = auth?.currentUser
            if (fallbackUser != null) {
                _currentUser.value = fallbackUser
                saveSession(fallbackUser.uid, fallbackUser.email, fallbackUser.displayName, fallbackUser.photoUrl?.toString())
                _authError.value = null
                onSuccess(fallbackUser)
            } else {
                val errorMsg = "ไม่สามารถเชื่อมต่อ Firebase Authentication ได้ กรุณาลองใหม่อีกครั้ง"
                _authError.value = errorMsg
                onError(errorMsg)
            }
        } else {
            val msg = "ไม่สามารถอ่านข้อมูลบัญชี Google ได้"
            _authError.value = msg
            onError(msg)
        }
    }

    private fun tryInteractiveFallback(
        activity: Activity,
        clientId: String,
        auth: FirebaseAuth?,
        scope: CoroutineScope,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit
    ) {
        val credentialManager = CredentialManager.create(activity)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch(Dispatchers.Main) {
            try {
                val result = credentialManager.getCredential(activity, request)
                handleCredentialResult(activity, result.credential, auth, onSuccess, onError)
            } catch (e: GetCredentialCancellationException) {
                Log.w("AuthService", "Interactive sign in cancelled: ${e.message}")
                onCancelled()
            } catch (e: Exception) {
                Log.e("AuthService", "Interactive sign in error", e)
                val errorText = e.localizedMessage ?: "การเข้าสู่ระบบผ่าน Google ไม่สำเร็จ"
                _authError.value = errorText
                onError(errorText)
            }
        }
    }

    private fun saveSession(uid: String, email: String?, displayName: String?, photoUrl: String?) {
        try {
            prefs?.edit()?.apply {
                putString("session_uid", uid)
                putString("session_email", email ?: "")
                putString("session_name", displayName ?: "")
                putString("session_photo", photoUrl ?: "")
                apply()
            }
        } catch (e: Exception) {
            Log.w("AuthService", "Failed to save session to prefs", e)
        }
    }

    fun signOut(
        context: Context,
        scope: CoroutineScope,
        onComplete: () -> Unit
    ) {
        try {
            firebaseAuth?.signOut()
        } catch (e: Exception) {
            Log.w("AuthService", "Error on signOut: ${e.message}")
        }
        _currentUser.value = null
        try {
            prefs?.edit()?.clear()?.apply()
        } catch (e: Exception) {
            Log.w("AuthService", "Error clearing prefs on signOut: ${e.message}")
        }
        val credentialManager = CredentialManager.create(context)
        scope.launch(Dispatchers.Main) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.e("AuthService", "Failed to clear CredentialManager state", e)
            } finally {
                onComplete()
            }
        }
    }

    fun mapFirebaseUserToFriendTalkUser(firebaseUser: FirebaseUser): User {
        val displayName = firebaseUser.displayName?.ifBlank { null }
            ?: firebaseUser.email?.substringBefore("@")
            ?: "ผู้ใช้ FriendTalk"
        val username = firebaseUser.email?.substringBefore("@")?.lowercase()?.replace(".", "_")
            ?: "user_${firebaseUser.uid.take(6)}"
        val avatar = firebaseUser.photoUrl?.toString()
            ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400&auto=format&fit=crop&q=80"

        return User(
            id = firebaseUser.uid,
            username = username,
            displayName = displayName,
            avatar = avatar,
            coverPhoto = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800&auto=format&fit=crop&q=80",
            age = 22,
            gender = Gender.OTHER,
            bio = "สมาชิก FriendTalk (เข้าสู่ระบบผ่าน Google Firebase)",
            role = UserRole.USER,
            isCreator = false,
            isVerified = true,
            isOnline = true,
            lastActive = "ออนไลน์ขณะนี้",
            coins = 1000,
            diamonds = 0,
            followersCount = 0,
            followingCount = 0,
            friendsCount = 0,
            likesCount = 0,
            location = UserLocation("กรุงเทพมหานคร", 0.0, true),
            badges = listOf("ยืนยันตัวตนแล้ว 🛡️"),
            interests = listOf("หาเพื่อน", "พูดคุย", "ไลฟ์สด")
        )
    }

    companion object {
        @Volatile
        private var instance: AuthService? = null

        fun getInstance(): AuthService {
            return instance ?: synchronized(this) {
                instance ?: AuthService().also { instance = it }
            }
        }
    }
}
