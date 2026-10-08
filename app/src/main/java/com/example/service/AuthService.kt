package com.example.service

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCustomException
import androidx.credentials.exceptions.GetCredentialException
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
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthService private constructor() {

    private val TAG = "AUTH_FLOW_DEBUG"
    private val authScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    fun clearError() {
        _authError.value = null
    }

    private var firebaseAuth: FirebaseAuth? = null
    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    fun initialize(context: Context) {
        Log.i(TAG, "[AUTH_FLOW_DEBUG_INIT] initialize() called")
        val auth = ensureAuth(context)
        val current = auth?.currentUser
        Log.i(TAG, "[AUTH_FLOW_DEBUG_INIT] currentUser at startup: ${current?.uid ?: "null"}")
        if (current != null) {
            _currentUser.value = current
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
                Log.i(TAG, "[AUTH_FLOW_DEBUG_INIT] FirebaseApp.initializeApp called successfully")
            }
            val auth = Firebase.auth
            firebaseAuth = auth

            // Register AuthStateListener to monitor Firebase auth state changes
            if (authStateListener == null) {
                val listener = FirebaseAuth.AuthStateListener { fa ->
                    val user = fa.currentUser
                    Log.i(TAG, "[AUTH_FLOW_DEBUG_STEP_7_STATE] onAuthStateChanged callback received: user=${user?.uid ?: "null"}, email=${user?.email ?: "null"}")
                    if (user != null) {
                        _currentUser.value = user
                        _isInitialized.value = true
                    } else {
                        if (_currentUser.value != null) {
                            Log.w(TAG, "[AUTH_FLOW_DEBUG_STEP_7_LOGOUT] onAuthStateChanged reported user changed from ${_currentUser.value?.uid} to null!")
                        }
                        _currentUser.value = null
                        _isInitialized.value = true
                    }
                }
                authStateListener = listener
                auth.addAuthStateListener(listener)
                Log.i(TAG, "[AUTH_FLOW_DEBUG_STEP_7] AuthStateListener registered with FirebaseAuth")
            }

            _currentUser.value = auth.currentUser
            _isInitialized.value = true
        } catch (e: Throwable) {
            Log.e(TAG, "[AUTH_FLOW_DEBUG_INIT] Firebase Auth initialization warning: ${e.message}", e)
            try {
                val auth = FirebaseAuth.getInstance()
                firebaseAuth = auth
                _currentUser.value = auth.currentUser
            } catch (ex: Throwable) {
                Log.e(TAG, "[AUTH_FLOW_DEBUG_INIT] Fallback FirebaseAuth failed: ${ex.message}", ex)
            }
            _isInitialized.value = true
        }

        return firebaseAuth
    }

    fun getWebClientId(context: Context): String {
        return try {
            val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (resId != 0) {
                val found = context.getString(resId)
                Log.i(TAG, "[AUTH_FLOW_DEBUG_10] Found default_web_client_id from resources: $found")
                found
            } else {
                val fallback = "123224091480-04es6lppmsd7iouk4akokd22o8sduh73.apps.googleusercontent.com"
                Log.w(TAG, "[AUTH_FLOW_DEBUG_10] default_web_client_id resource not found, using: $fallback")
                fallback
            }
        } catch (e: Exception) {
            val fallback = "123224091480-04es6lppmsd7iouk4akokd22o8sduh73.apps.googleusercontent.com"
            Log.w(TAG, "[AUTH_FLOW_DEBUG_10] Exception resolving default_web_client_id: ${e.message}, using: $fallback")
            fallback
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
            Log.i(TAG, "[AUTH_FLOW_DEBUG_AUTO] Existing currentUser found: ${user.uid}")
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

        authScope.launch {
            try {
                Log.i(TAG, "[AUTH_FLOW_DEBUG_AUTO] Attempting silent CredentialManager.getCredential...")
                val result = credentialManager.getCredential(context, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    Log.i(TAG, "[AUTH_FLOW_DEBUG_AUTO] Auto sign-in obtained ID Token length=${googleIdToken.length}")
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    val authResult = auth?.signInWithCredential(authCredential)?.await()
                    val signedInUser = authResult?.user ?: auth?.currentUser
                    if (signedInUser != null) {
                        Log.i(TAG, "[AUTH_FLOW_DEBUG_AUTO] Auto sign-in Firebase success: uid=${signedInUser.uid}")
                        _currentUser.value = signedInUser
                        _isInitialized.value = true
                        onSuccess(signedInUser)
                        return@launch
                    }
                }
                _isInitialized.value = true
                onUnauthenticated()
            } catch (e: Exception) {
                Log.d(TAG, "[AUTH_FLOW_DEBUG_AUTO] Silent auto sign-in skipped: ${e.javaClass.simpleName} - ${e.message}")
                _isInitialized.value = true
                onUnauthenticated()
            }
        }
    }

    /**
     * Native Google Sign-In using Android Credential Manager with step-by-step diagnostic logging
     */
    fun signInWithGoogleNative(
        activity: Activity,
        scope: CoroutineScope? = null,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit,
        onCancelled: (String) -> Unit = {}
    ) {
        clearError()
        val auth = ensureAuth(activity)
        val clientId = getWebClientId(activity)
        val credentialManager = CredentialManager.create(activity)

        // Step 1: Log Google Sign-In start with non-sensitive identifiers
        val safeClientIdPrefix = if (clientId.length > 12) clientId.take(12) + "..." else clientId
        Log.i(TAG, "[AUTH_FLOW_DEBUG_STEP_1_INIT] START Google Sign-In requested. Activity=${activity.localClassName}, Package=${activity.packageName}, ServerClientIdPrefix=$safeClientIdPrefix (len=${clientId.length})")

        if (activity.isFinishing || activity.isDestroyed) {
            val err = "Activity ไม่พร้อมใช้งาน (isFinishing/isDestroyed) ไม่สามารถเปิดหน้าต่างเลือกบัญชีได้"
            Log.e(TAG, "[AUTH_FLOW_DEBUG_STEP_1_FAIL] $err")
            _authError.value = err
            onError(err)
            return
        }

        // Primary: Use GetSignInWithGoogleOption (the official standard for interactive button sign-in)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        authScope.launch {
            try {
                Log.i(TAG, "[AUTH_FLOW_DEBUG_STEP_2_REQ] Invoking credentialManager.getCredential with GetSignInWithGoogleOption...")
                val result = credentialManager.getCredential(activity, request)
                Log.i(TAG, "[AUTH_FLOW_DEBUG_STEP_3_CALLBACK] CredentialManager returned successfully. Type=${result.credential.type}")
                handleCredentialResult(activity, result.credential, auth, onSuccess, onError)
            } catch (e: GetCredentialCancellationException) {
                // Step 3: Cancellation exception
                val cancelMsg = e.message ?: "Google Play Services ปิดหน้าต่างเลือกบัญชี"
                Log.w(TAG, "[AUTH_FLOW_DEBUG_STEP_3_CANCEL] GetCredentialCancellationException: $cancelMsg")
                val fullMsg = "การเลือกบัญชีถูกปิดหรือยกเลิก ($cancelMsg). หากเลือกอีเมลแล้วเด้งกลับ กรุณาตรวจสอบว่าได้ลงทะเบียน SHA-1 ใน Firebase Console แล้วหรือยัง"
                _authError.value = fullMsg
                onCancelled(fullMsg)
            } catch (e: NoCredentialException) {
                // Step 3: NoCredentialException -> Try GetGoogleIdOption fallback
                Log.w(TAG, "[AUTH_FLOW_DEBUG_STEP_3_FALLBACK] NoCredentialException: ${e.message}. Trying GetGoogleIdOption fallback...")
                tryGoogleIdOptionFallback(activity, clientId, auth, onSuccess, onError, onCancelled)
            } catch (e: GetCredentialCustomException) {
                Log.e(TAG, "[AUTH_FLOW_DEBUG_STEP_3_ERROR] GetCredentialCustomException: type=${e.type}, message=${e.message}", e)
                tryGoogleIdOptionFallback(activity, clientId, auth, onSuccess, onError, onCancelled)
            } catch (e: GetCredentialException) {
                Log.e(TAG, "[AUTH_FLOW_DEBUG_STEP_3_ERROR] GetCredentialException: type=${e.type}, message=${e.message}", e)
                tryGoogleIdOptionFallback(activity, clientId, auth, onSuccess, onError, onCancelled)
            } catch (e: Exception) {
                Log.e(TAG, "[AUTH_FLOW_DEBUG_STEP_3_ERROR] Native Google Sign-In unexpected exception: ${e.javaClass.simpleName} - ${e.message}", e)
                tryGoogleIdOptionFallback(activity, clientId, auth, onSuccess, onError, onCancelled)
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
        // Step 3: Verify Credential Type
        if (credential !is CustomCredential || credential.type != TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val msg = "ไม่ได้รับ Google ID Token: Credential Type ไม่ถูกต้อง (${credential.type})"
            Log.e(TAG, "[AUTH_FLOW_DEBUG_STEP_3_FAIL] $msg")
            _authError.value = msg
            onError(msg)
            return
        }

        // Step 4: Parse GoogleIdTokenCredential and verify ID Token existence
        val googleIdTokenCred = try {
            GoogleIdTokenCredential.createFrom(credential.data)
        } catch (e: Exception) {
            Log.e(TAG, "[AUTH_FLOW_DEBUG_STEP_4_FAIL] Failed parsing GoogleIdTokenCredential: ${e.message}", e)
            val err = "ล้มเหลวในการอ่าน ID Token: [${e.javaClass.simpleName}] ${e.message}"
            _authError.value = err
            onError(err)
            return
        }

        val idToken = googleIdTokenCred.idToken
        if (idToken.isBlank()) {
            val err = "ข้อผิดพลาด: ได้รับข้อมูลจาก Google แต่ไม่มี ID Token (idToken ว่างเปล่า)"
            Log.e(TAG, "[AUTH_FLOW_DEBUG_STEP_4_FAIL] $err")
            _authError.value = err
            onError(err)
            return
        }

        val safeIdPrefix = if (googleIdTokenCred.id.length > 6) googleIdTokenCred.id.take(6) + "..." else googleIdTokenCred.id
        Log.i(TAG, "[AUTH_FLOW_DEBUG_STEP_4_SUCCESS] Google ID Token received. Length=${idToken.length}, DisplayName=${googleIdTokenCred.displayName ?: "null"}, IdPrefix=$safeIdPrefix")

        // Step 5: Check FirebaseAuth and invoke signInWithCredential
        val currentAuth = auth ?: ensureAuth(context)
        if (currentAuth == null) {
            val err = "ไม่สามารถเชื่อมต่อ Firebase Auth ได้ (FirebaseAuth instance เป็น null)"
            Log.e(TAG, "[AUTH_FLOW_DEBUG_STEP_5_FAIL] $err")
            _authError.value = err
            onError(err)
            return
        }

        Log.i(TAG, "[AUTH_FLOW_DEBUG_STEP_5_SIGNIN] Creating GoogleAuthProvider credential and calling FirebaseAuth.signInWithCredential...")
        val authCredential = GoogleAuthProvider.getCredential(idToken, null)

        try {
            val authResult = currentAuth.signInWithCredential(authCredential).await()
            val user = authResult.user ?: currentAuth.currentUser

            // Step 6: Verify sign-in result
            if (user != null) {
                Log.i(TAG, "[AUTH_FLOW_DEBUG_STEP_6_SUCCESS] FirebaseAuth.signInWithCredential SUCCESS: userUid=${user.uid}, email=${user.email ?: "null"}")
                _currentUser.value = user
                _authError.value = null
                Log.i(TAG, "[AUTH_FLOW_DEBUG_STEP_8_NAV] Triggering onSuccess callback with user=${user.uid}")
                onSuccess(user)
            } else {
                val msg = "[AUTH_FLOW_DEBUG_STEP_6_FAIL] Firebase signInWithCredential สำเร็จแต่ user เป็น null"
                Log.e(TAG, msg)
                _authError.value = msg
                onError(msg)
            }
        } catch (authEx: FirebaseAuthException) {
            val errCode = authEx.errorCode
            val errMsg = authEx.message ?: "Unknown Firebase error"
            Log.e(TAG, "[AUTH_FLOW_DEBUG_STEP_5_FAIL] FirebaseAuthException: ErrorCode=[$errCode], Message=[$errMsg]", authEx)
            val fullError = when (errCode) {
                "ERROR_INVALID_CREDENTIAL" -> "ข้อมูลรับรอง Google ไม่ถูกต้องหรือหมดอายุ [$errCode]. กรุณาตรวจสอบ SHA-1 ใน Firebase Console"
                "ERROR_USER_DISABLED" -> "บัญชีผู้ใช้นี้ถูกระงับการใช้งานใน Firebase [$errCode]"
                else -> "Firebase Auth ล้มเหลว [$errCode]: $errMsg"
            }
            _authError.value = fullError
            onError(fullError)
        } catch (authEx: Exception) {
            val exClass = authEx.javaClass.simpleName
            val exMsg = authEx.message ?: "Unknown error"
            Log.e(TAG, "[AUTH_FLOW_DEBUG_STEP_5_FAIL] Exception during signInWithCredential: Class=[$exClass], Message=[$exMsg]", authEx)
            val fullError = "Firebase Sign-In Error: [$exClass] $exMsg"
            _authError.value = fullError
            onError(fullError)
        }
    }

    private fun tryGoogleIdOptionFallback(
        activity: Activity,
        clientId: String,
        auth: FirebaseAuth?,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit,
        onCancelled: (String) -> Unit
    ) {
        val credentialManager = CredentialManager.create(activity)
        Log.i(TAG, "[AUTH_FLOW_DEBUG_STEP_3_FALLBACK] Calling GetGoogleIdOption fallback with serverClientIdPrefix=${clientId.take(12)}...")

        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        authScope.launch {
            try {
                val result = credentialManager.getCredential(activity, request)
                Log.i(TAG, "[AUTH_FLOW_DEBUG_STEP_3_FALLBACK] Fallback returned credential type=${result.credential.type}")
                handleCredentialResult(activity, result.credential, auth, onSuccess, onError)
            } catch (e: GetCredentialCancellationException) {
                val cancelMsg = e.message ?: "ผู้ใช้หรือระบบยกเลิก"
                Log.w(TAG, "[AUTH_FLOW_DEBUG_STEP_3_CANCEL] Fallback cancelled: $cancelMsg")
                val fullMsg = "การเข้าสู่ระบบถูกยกเลิก ($cancelMsg). หากเลือกอีเมลแล้วเด้งกลับ ตรวจสอบ SHA-1 ใน Firebase Console"
                _authError.value = fullMsg
                onCancelled(fullMsg)
            } catch (e: Exception) {
                val exName = e.javaClass.simpleName
                val exMsg = e.message ?: "Unknown error"
                Log.e(TAG, "[AUTH_FLOW_DEBUG_STEP_3_FAIL] Fallback failed: [$exName] $exMsg", e)
                val errorText = "Google Sign-In ล้มเหลว: [$exName] $exMsg"
                _authError.value = errorText
                onError(errorText)
            }
        }
    }

    // Step 9: Diagnostic tracking for signOut
    fun signOut(
        context: Context,
        scope: CoroutineScope,
        onComplete: () -> Unit
    ) {
        Log.i(TAG, "[AUTH_FLOW_DEBUG_9] signOut() called. Setting currentUser = null")
        try {
            firebaseAuth?.signOut()
        } catch (e: Exception) {
            Log.w(TAG, "[AUTH_FLOW_DEBUG_9] Error on signOut: ${e.message}")
        }
        _currentUser.value = null

        val credentialManager = CredentialManager.create(context)
        scope.launch(Dispatchers.Main) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
                Log.i(TAG, "[AUTH_FLOW_DEBUG_9] CredentialManager.clearCredentialState completed")
            } catch (e: Exception) {
                Log.e(TAG, "[AUTH_FLOW_DEBUG_9] Failed to clear CredentialManager state: ${e.message}", e)
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
