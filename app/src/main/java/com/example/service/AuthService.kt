package com.example.service

import android.app.Activity
import android.content.Context
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
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

    fun initialize(context: Context) {
        ensureAuth(context)
        _isInitialized.value = true
    }

    fun ensureAuth(context: Context): FirebaseAuth? {
        if (firebaseAuth != null) {
            return firebaseAuth
        }

        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId("1:913251346174:android:d41d8cd98f00b204e9800998ecf8427e")
                    .setApiKey("AIzaSyB_FriendTalkProductionDefaultApiKey")
                    .setProjectId("friendtalk-app")
                    .build()
                FirebaseApp.initializeApp(context.applicationContext, options)
            }
            val auth = Firebase.auth
            firebaseAuth = auth
            _currentUser.value = auth.currentUser
            auth.addAuthStateListener { fa ->
                _currentUser.value = fa.currentUser
                _isInitialized.value = true
            }
        } catch (e: Throwable) {
            try {
                FirebaseApp.initializeApp(context.applicationContext)
                val auth = Firebase.auth
                firebaseAuth = auth
                _currentUser.value = auth.currentUser
                auth.addAuthStateListener { fa ->
                    _currentUser.value = fa.currentUser
                    _isInitialized.value = true
                }
            } catch (ex: Throwable) {
                Log.e("AuthService", "Firebase Auth init error", ex)
            }
        }

        _isInitialized.value = true
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
                    val signedInUser = authResult?.user
                    if (signedInUser != null) {
                        _currentUser.value = signedInUser
                        _isInitialized.value = true
                        onSuccess(signedInUser)
                        return@launch
                    }
                }
                _isInitialized.value = true
                onUnauthenticated()
            } catch (e: Exception) {
                Log.d("AuthService", "Auto sign-in skipped: ${e.message}")
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
        if (auth == null) {
            val errorMsg = "กำลังเชื่อมต่อกับ Firebase Authentication กรุณาลองใหม่อีกครั้ง"
            _authError.value = errorMsg
            onError(errorMsg)
            return
        }

        val clientId = getWebClientId(activity)
        val credentialManager = CredentialManager.create(activity)

        // Native Google Account Chooser Option
        val nativeAccountOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false) // Prompts Native Account Chooser listing all accounts on device
            .setServerClientId(clientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(nativeAccountOption)
            .build()

        scope.launch(Dispatchers.Main) {
            try {
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = auth.signInWithCredential(authCredential).await()
                    val user = authResult.user
                    if (user != null) {
                        _currentUser.value = user
                        _authError.value = null
                        onSuccess(user)
                    } else {
                        val msg = "ไม่พบข้อมูลผู้ใช้หลังจาก Sign-In"
                        _authError.value = msg
                        onError(msg)
                    }
                } else {
                    val msg = "ประเภท Credential ไม่ถูกต้อง"
                    _authError.value = msg
                    onError(msg)
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("AuthService", "Google Sign-In cancelled: ${e.message}")
                onCancelled()
            } catch (e: NoCredentialException) {
                // If no pre-authorized credentials, try interactive sign in option
                tryInteractiveFallback(activity, clientId, auth, onSuccess, onError, onCancelled)
            } catch (e: Exception) {
                Log.e("AuthService", "Native Google Sign-In error", e)
                tryInteractiveFallback(activity, clientId, auth, onSuccess, onError, onCancelled)
            }
        }
    }

    private fun tryInteractiveFallback(
        activity: Activity,
        clientId: String,
        auth: FirebaseAuth,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit
    ) {
        val credentialManager = CredentialManager.create(activity)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = auth.signInWithCredential(authCredential).await()
                    val user = authResult.user
                    if (user != null) {
                        _currentUser.value = user
                        _authError.value = null
                        onSuccess(user)
                    } else {
                        val msg = "ไม่สามารถยืนยันตัวตนกับ Firebase ได้"
                        _authError.value = msg
                        onError(msg)
                    }
                } else {
                    val msg = "ไม่พบบัญชี Google สำหรับเข้าสู่ระบบ"
                    _authError.value = msg
                    onError(msg)
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("AuthService", "Interactive sign in cancelled: ${e.message}")
                onCancelled()
            } catch (e: Exception) {
                Log.e("AuthService", "Interactive sign in failed", e)
                val errorText = e.localizedMessage ?: "การเข้าสู่ระบบผ่าน Google ไม่สำเร็จ"
                _authError.value = errorText
                onError(errorText)
            }
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
        val displayName = firebaseUser.displayName ?: firebaseUser.email?.substringBefore("@") ?: "ผู้ใช้ FriendTalk"
        val username = firebaseUser.email?.substringBefore("@")?.lowercase()?.replace(".", "_") ?: "user_${firebaseUser.uid.take(6)}"
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
