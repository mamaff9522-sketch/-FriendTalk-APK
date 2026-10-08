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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthService private constructor() {

    private val TAG = "AUTH_FLOW_DEBUG"

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

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
                    Log.i(TAG, "[AUTH_FLOW_DEBUG_7] onAuthStateChanged callback received: user=${user?.uid ?: "null"}")
                    _currentUser.value = user
                    _isInitialized.value = true
                }
                authStateListener = listener
                auth.addAuthStateListener(listener)
                Log.i(TAG, "[AUTH_FLOW_DEBUG_7] AuthStateListener registered with FirebaseAuth")
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

        scope.launch(Dispatchers.Main) {
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
        scope: CoroutineScope,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        val auth = ensureAuth(activity)
        val clientId = getWebClientId(activity)
        val credentialManager = CredentialManager.create(activity)

        // Step 1: Log Google Sign-In start
        Log.i(TAG, "[AUTH_FLOW_DEBUG_1] START Google Sign-In requested. Activity=${activity.localClassName}, Package=${activity.packageName}, ServerClientId=$clientId")

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
                Log.i(TAG, "[AUTH_FLOW_DEBUG_1] Invoking credentialManager.getCredential with GetGoogleIdOption...")
                val result = credentialManager.getCredential(activity, request)
                Log.i(TAG, "[AUTH_FLOW_DEBUG_2] CredentialManager returned credential type=${result.credential.type}")
                handleCredentialResult(activity, result.credential, auth, onSuccess, onError)
            } catch (e: GetCredentialCancellationException) {
                // Step 3: Cancellation exception
                Log.w(TAG, "[AUTH_FLOW_DEBUG_3] Google Sign-In CANCELLED by user: ${e.message}")
                onCancelled()
            } catch (e: NoCredentialException) {
                // Step 3: NoCredentialException
                Log.w(TAG, "[AUTH_FLOW_DEBUG_3] NoCredentialException: ${e.message}. Trying GetSignInWithGoogleOption fallback...")
                tryInteractiveFallback(activity, clientId, auth, scope, onSuccess, onError, onCancelled)
            } catch (e: GetCredentialCustomException) {
                // Step 3: Custom exception with type
                Log.e(TAG, "[AUTH_FLOW_DEBUG_3] GetCredentialCustomException: type=${e.type}, message=${e.message}", e)
                tryInteractiveFallback(activity, clientId, auth, scope, onSuccess, onError, onCancelled)
            } catch (e: GetCredentialException) {
                // Step 3: General GetCredentialException
                Log.e(TAG, "[AUTH_FLOW_DEBUG_3] GetCredentialException: type=${e.type}, message=${e.message}", e)
                tryInteractiveFallback(activity, clientId, auth, scope, onSuccess, onError, onCancelled)
            } catch (e: Exception) {
                // Step 3: Any other exception
                Log.e(TAG, "[AUTH_FLOW_DEBUG_3] Native Google Sign-In unexpected exception: ${e.javaClass.simpleName} - ${e.message}", e)
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
        // Step 2 & 3: Check Google Credential / ID Token
        if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val googleIdTokenCred = try {
                GoogleIdTokenCredential.createFrom(credential.data)
            } catch (e: Exception) {
                Log.e(TAG, "[AUTH_FLOW_DEBUG_3] Failed parsing GoogleIdTokenCredential: ${e.message}", e)
                val err = "ล้มเหลวในการอ่าน ID Token: [${e.javaClass.simpleName}] ${e.message}"
                _authError.value = err
                onError(err)
                return
            }

            val idToken = googleIdTokenCred.idToken
            Log.i(TAG, "[AUTH_FLOW_DEBUG_2] Google ID Token received successfully. Length=${idToken.length}, DisplayName=${googleIdTokenCred.displayName ?: "null"}, Id=${googleIdTokenCred.id}")

            // Step 4: Check FirebaseAuth.signInWithCredential()
            Log.i(TAG, "[AUTH_FLOW_DEBUG_4] Creating GoogleAuthProvider credential and calling FirebaseAuth.signInWithCredential...")
            val authCredential = GoogleAuthProvider.getCredential(idToken, null)

            try {
                val authResult = auth?.signInWithCredential(authCredential)?.await()
                val user = authResult?.user ?: auth?.currentUser

                // Step 5: signInWithCredential result check
                Log.i(TAG, "[AUTH_FLOW_DEBUG_5] FirebaseAuth.signInWithCredential SUCCESS: userUid=${user?.uid ?: "null"}, email=${user?.email ?: "null"}")

                // Step 6: Immediately check currentUser
                val immediateCurrentUser = auth?.currentUser
                Log.i(TAG, "[AUTH_FLOW_DEBUG_6] Immediate check FirebaseAuth.currentUser: uid=${immediateCurrentUser?.uid ?: "null"}")

                if (user != null) {
                    _currentUser.value = user
                    _authError.value = null
                    Log.i(TAG, "[AUTH_FLOW_DEBUG_8] Triggering onSuccess callback with user=${user.uid}")
                    onSuccess(user)
                    return
                } else {
                    val msg = "[AUTH_FLOW_DEBUG_6_FAIL] Firebase signInWithCredential succeeded but returned null user"
                    Log.e(TAG, msg)
                    _authError.value = msg
                    onError(msg)
                    return
                }
            } catch (authEx: FirebaseAuthException) {
                // Step 5: FirebaseAuthException details
                val errCode = authEx.errorCode
                val errMsg = authEx.message ?: "Unknown Firebase error"
                Log.e(TAG, "[AUTH_FLOW_DEBUG_5_FAIL] FirebaseAuthException: ErrorCode=[$errCode], Message=[$errMsg]", authEx)
                val fullError = "Firebase Auth ล้มเหลว: [$errCode] $errMsg"
                _authError.value = fullError
                onError(fullError)
                return
            } catch (authEx: Exception) {
                // Step 5: Generic Exception details
                val exClass = authEx.javaClass.name
                val exMsg = authEx.message ?: "Unknown error"
                Log.e(TAG, "[AUTH_FLOW_DEBUG_5_FAIL] Exception during signInWithCredential: Class=[$exClass], Message=[$exMsg]", authEx)
                val fullError = "Firebase Sign-In Error: [$exClass] $exMsg"
                _authError.value = fullError
                onError(fullError)
                return
            }
        } else {
            // Step 3: Not a Google ID Token credential
            val msg = "ไม่ได้รับ ID Token: Credential Type ไม่ถูกต้อง (${credential.type})"
            Log.e(TAG, "[AUTH_FLOW_DEBUG_3_FAIL] $msg")
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
        Log.i(TAG, "[AUTH_FLOW_DEBUG_FALLBACK] Calling GetSignInWithGoogleOption with serverClientId=$clientId")

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        scope.launch(Dispatchers.Main) {
            try {
                val result = credentialManager.getCredential(activity, request)
                Log.i(TAG, "[AUTH_FLOW_DEBUG_FALLBACK] Fallback returned credential type=${result.credential.type}")
                handleCredentialResult(activity, result.credential, auth, onSuccess, onError)
            } catch (e: GetCredentialCancellationException) {
                Log.w(TAG, "[AUTH_FLOW_DEBUG_3] Fallback cancelled by user: ${e.message}")
                onCancelled()
            } catch (e: Exception) {
                val exName = e.javaClass.simpleName
                val exMsg = e.message ?: "Unknown error"
                Log.e(TAG, "[AUTH_FLOW_DEBUG_3_FAIL] Fallback failed: [$exName] $exMsg", e)
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
