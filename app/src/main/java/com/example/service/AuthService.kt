package com.example.service

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.Credential
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
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthService private constructor() {

    private val TAG = "AUTH_FLOW"
    private val authScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> =
        _currentUser.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> =
        _isInitialized.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> =
        _authError.asStateFlow()

    private var firebaseAuth: FirebaseAuth? = null
    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    fun clearError() {
        _authError.value = null
    }

    fun initialize(context: Context) {
        ensureAuth(context)
        _isInitialized.value = true
    }

    private fun ensureAuth(context: Context): FirebaseAuth? {

        firebaseAuth?.let {
            return it
        }

        return try {

            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context.applicationContext)
            }

            val auth = Firebase.auth
            firebaseAuth = auth

            if (authStateListener == null) {

                val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->

                    val user = firebaseAuth.currentUser

                    Log.d(
                        TAG,
                        "AuthStateChanged user=${user?.email ?: "null"}"
                    )

                    _currentUser.value = user
                    _isInitialized.value = true
                }

                authStateListener = listener
                auth.addAuthStateListener(listener)
            }

            _currentUser.value = auth.currentUser
            _isInitialized.value = true

            auth

        } catch (e: Exception) {

            Log.e(
                TAG,
                "Firebase initialization failed",
                e
            )

            _authError.value =
                "Firebase initialization failed: ${e.message}"

            _isInitialized.value = true

            null
        }
    }

    private fun getWebClientId(context: Context): String {

        val resourceId =
            context.resources.getIdentifier(
                "default_web_client_id",
                "string",
                context.packageName
            )

        if (resourceId != 0) {

            val clientId =
                context.getString(resourceId)

            Log.d(
                TAG,
                "Using default_web_client_id"
            )

            return clientId
        }

        /*
         * Web OAuth Client ID
         * client_type = 3
         */
        return "123224091480-04es6lppmsd7iouk4akokd22o8sduh73.apps.googleusercontent.com"
    }

    /*
     * Startup:
     *
     * ไม่เรียก Credential Manager อัตโนมัติ
     * ไม่เปิด account chooser
     * ไม่ทำ silent Google login
     *
     * Firebase เป็นคนจำ session เอง
     */
    fun attemptAutoSignIn(
        context: Context,
        scope: CoroutineScope,
        onSuccess: (FirebaseUser) -> Unit,
        onUnauthenticated: () -> Unit
    ) {

        val auth = ensureAuth(context)

        val user = auth?.currentUser

        if (user != null) {

            Log.d(
                TAG,
                "Existing Firebase session found"
            )

            _currentUser.value = user
            _isInitialized.value = true

            onSuccess(user)

        } else {

            Log.d(
                TAG,
                "No existing Firebase session"
            )

            _currentUser.value = null
            _isInitialized.value = true

            onUnauthenticated()
        }
    }

    /*
     * Google Login
     *
     * ใช้ flow เดียวเท่านั้น:
     *
     * GetGoogleIdOption
     * ->
     * Google ID Token
     * ->
     * FirebaseAuth.signInWithCredential
     *
     * ไม่มี fallback วนซ้ำ
     */
    fun signInWithGoogleNative(
        activity: Activity,
        scope: CoroutineScope? = null,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit,
        onCancelled: (String) -> Unit = {}
    ) {

        clearError()

        if (activity.isFinishing || activity.isDestroyed) {

            val error =
                "Activity ไม่พร้อมสำหรับ Google Sign-In"

            _authError.value = error
            onError(error)

            return
        }

        val auth =
            ensureAuth(activity)

        if (auth == null) {

            val error =
                "Firebase Authentication ยังไม่พร้อมใช้งาน"

            _authError.value = error
            onError(error)

            return
        }

        val clientId =
            getWebClientId(activity)

        Log.d(
            TAG,
            "Starting Google Sign-In"
        )

        authScope.launch {

            requestGoogleCredential(
                activity = activity,
                clientId = clientId,
                auth = auth,
                retryAfterReauthFailure = true,
                onSuccess = onSuccess,
                onError = onError,
                onCancelled = onCancelled
            )
        }
    }

    private suspend fun requestGoogleCredential(
        activity: Activity,
        clientId: String,
        auth: FirebaseAuth,
        retryAfterReauthFailure: Boolean,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit,
        onCancelled: (String) -> Unit
    ) {

        val credentialManager =
            CredentialManager.create(activity)

        /*
         * false =
         * แสดงทุก Google Account ที่อยู่ในเครื่อง
         *
         * ไม่จำกัดเฉพาะบัญชีที่เคย authorize แล้ว
         */
        val googleIdOption =
            GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .build()

        val request =
            GetCredentialRequest.Builder()
                .addCredentialOption(
                    googleIdOption
                )
                .build()

        try {

            Log.d(
                TAG,
                "Opening Google account chooser"
            )

            val result =
                credentialManager.getCredential(
                    activity,
                    request
                )

            handleCredential(
                context = activity,
                credential = result.credential,
                auth = auth,
                onSuccess = onSuccess,
                onError = onError
            )

        } catch (
            e: GetCredentialCancellationException
        ) {

            val message =
                e.message.orEmpty()

            Log.e(
                TAG,
                "Credential cancellation: $message",
                e
            )

            /*
             * Error 16 / Account reauth failed
             *
             * ล้าง Credential Manager state
             * แล้วลองใหม่เพียง 1 ครั้ง
             *
             * ห้ามวนซ้ำไม่จำกัด
             */
            if (
                retryAfterReauthFailure &&
                isAccountReauthFailure(message)
            ) {

                Log.w(
                    TAG,
                    "Account reauth failed - clearing credential state once"
                )

                clearCredentialState(
                    credentialManager
                )

                delay(300)

                requestGoogleCredential(
                    activity = activity,
                    clientId = clientId,
                    auth = auth,
                    retryAfterReauthFailure = false,
                    onSuccess = onSuccess,
                    onError = onError,
                    onCancelled = onCancelled
                )

                return
            }

            val error =
                if (isAccountReauthFailure(message)) {

                    """
                    Google Account re-authentication failed (Error 16)

                    Google Play Services ไม่สามารถยืนยันบัญชี Google ของเครื่องได้
                    กรุณาลองเลือกบัญชี Google อีกครั้ง
                    """.trimIndent()

                } else {

                    "การเข้าสู่ระบบถูกยกเลิก: $message"
                }

            _authError.value = error
            onCancelled(error)

        } catch (
            e: GetCredentialCustomException
        ) {

            val message =
                e.message.orEmpty()

            Log.e(
                TAG,
                "Credential custom error type=${e.type}: $message",
                e
            )

            if (
                retryAfterReauthFailure &&
                isAccountReauthFailure(message)
            ) {

                clearCredentialState(
                    credentialManager
                )

                delay(300)

                requestGoogleCredential(
                    activity = activity,
                    clientId = clientId,
                    auth = auth,
                    retryAfterReauthFailure = false,
                    onSuccess = onSuccess,
                    onError = onError,
                    onCancelled = onCancelled
                )

                return
            }

            val error =
                "Google Sign-In Error [${e.type}]: $message"

            _authError.value = error
            onError(error)

        } catch (
            e: NoCredentialException
        ) {

            Log.e(
                TAG,
                "No Google credential",
                e
            )

            val error =
                "ไม่พบบัญชี Google ที่สามารถใช้เข้าสู่ระบบได้"

            _authError.value = error
            onError(error)

        } catch (
            e: GetCredentialException
        ) {

            Log.e(
                TAG,
                "Credential Manager error type=${e.type}",
                e
            )

            val error =
                "Google Sign-In Error [${e.type}]: ${e.message}"

            _authError.value = error
            onError(error)

        } catch (
            e: Exception
        ) {

            Log.e(
                TAG,
                "Unexpected Google Sign-In error",
                e
            )

            val error =
                "${e.javaClass.simpleName}: ${e.message}"

            _authError.value = error
            onError(error)
        }
    }

    private suspend fun clearCredentialState(
        credentialManager: CredentialManager
    ) {

        try {

            credentialManager.clearCredentialState(
                ClearCredentialStateRequest()
            )

            Log.d(
                TAG,
                "Credential state cleared"
            )

        } catch (e: Exception) {

            Log.w(
                TAG,
                "Unable to clear credential state: ${e.message}"
            )
        }
    }

    private fun isAccountReauthFailure(
        message: String
    ): Boolean {

        return message.contains(
            "Account reauth failed",
            ignoreCase = true
        ) ||
        message.contains(
            "[16]",
            ignoreCase = true
        ) ||
        message.contains(
            "reauth",
            ignoreCase = true
        )
    }

    private suspend fun handleCredential(
        context: Context,
        credential: Credential,
        auth: FirebaseAuth,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit
    ) {

        if (
            credential !is CustomCredential ||
            credential.type !=
            TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {

            val error =
                "Credential ที่ได้รับไม่ใช่ Google ID Token"

            Log.e(
                TAG,
                "$error type=${credential.type}"
            )

            _authError.value = error
            onError(error)

            return
        }

        val googleCredential =
            try {

                GoogleIdTokenCredential.createFrom(
                    credential.data
                )

            } catch (e: Exception) {

                val error =
                    "ไม่สามารถอ่าน Google ID Token ได้: ${e.message}"

                Log.e(
                    TAG,
                    error,
                    e
                )

                _authError.value = error
                onError(error)

                return
            }

        val idToken =
            googleCredential.idToken

        if (idToken.isBlank()) {

            val error =
                "Google ID Token ว่างเปล่า"

            _authError.value = error
            onError(error)

            return
        }

        Log.d(
            TAG,
            "Google ID Token received length=${idToken.length}"
        )

        val firebaseCredential =
            GoogleAuthProvider.getCredential(
                idToken,
                null
            )

        try {

            val authResult =
                auth.signInWithCredential(
                    firebaseCredential
                ).await()

            val user =
                authResult.user
                    ?: auth.currentUser

            if (user == null) {

                val error =
                    "Firebase Login สำเร็จแต่ไม่พบ FirebaseUser"

                _authError.value = error
                onError(error)

                return
            }

            Log.d(
                TAG,
                "Firebase Google Login SUCCESS uid=${user.uid}"
            )

            _currentUser.value = user
            _isInitialized.value = true
            _authError.value = null

            onSuccess(user)

        } catch (
            e: FirebaseAuthException
        ) {

            Log.e(
                TAG,
                "Firebase Auth error=${e.errorCode}",
                e
            )

            val error =
                "Firebase Auth [${e.errorCode}]: ${e.message}"

            _authError.value = error
            onError(error)

        } catch (
            e: Exception
        ) {

            Log.e(
                TAG,
                "Firebase signInWithCredential failed",
                e
            )

            val error =
                "Firebase Sign-In Error: ${e.javaClass.simpleName}: ${e.message}"

            _authError.value = error
            onError(error)
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

            Log.w(
                TAG,
                "Firebase signOut failed",
                e
            )
        }

        _currentUser.value = null

        val credentialManager =
            CredentialManager.create(context)

        scope.launch {

            try {

                credentialManager.clearCredentialState(
                    ClearCredentialStateRequest()
                )

            } catch (e: Exception) {

                Log.w(
                    TAG,
                    "Credential clear on logout failed",
                    e
                )

            } finally {

                onComplete()
            }
        }
    }

    // ==========================================
    // 1. Email / Password Authentication (Firebase Real)
    // ==========================================
    fun signInWithEmail(
        email: String,
        password: String,
        scope: CoroutineScope? = null,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit
    ) {
        clearError()
        val auth = firebaseAuth ?: Firebase.auth
        if (email.isBlank() || password.isBlank()) {
            val err = "กรุณากรอกอีเมลและรหัสผ่านให้ครบถ้วน"
            _authError.value = err
            onError(err)
            return
        }
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    _currentUser.value = user
                    onSuccess(user)
                } else {
                    val err = "ไม่พบข้อมูลผู้ใช้หลังเข้าสู่ระบบ"
                    _authError.value = err
                    onError(err)
                }
            }
            .addOnFailureListener { e ->
                val err = when (e) {
                    is FirebaseAuthInvalidUserException -> "ไม่พบบัญชีผู้ใช้นี้ในระบบ กรุณาสมัครสมาชิกใหม่"
                    is FirebaseAuthInvalidCredentialsException -> "อีเมลหรือรหัสผ่านไม่ถูกต้อง"
                    else -> "เข้าสู่ระบบด้วยอีเมลไม่สำเร็จ: ${e.localizedMessage}"
                }
                _authError.value = err
                onError(err)
            }
    }

    fun signUpWithEmail(
        email: String,
        password: String,
        displayName: String,
        scope: CoroutineScope? = null,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit
    ) {
        clearError()
        val auth = firebaseAuth ?: Firebase.auth
        if (email.isBlank() || password.isBlank()) {
            val err = "กรุณากรอกอีเมลและรหัสผ่านให้ครบถ้วน"
            _authError.value = err
            onError(err)
            return
        }
        if (password.length < 6) {
            val err = "รหัสผ่านต้องมีความยาวอย่างน้อย 6 ตัวอักษร"
            _authError.value = err
            onError(err)
            return
        }
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { result ->
                val user = result.user
                if (user != null) {
                    // Email verification: backend + DB rules block writes until verified
                    user.sendEmailVerification()
                    if (displayName.isNotBlank()) {
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(displayName.trim())
                            .build()
                        user.updateProfile(profileUpdates).addOnCompleteListener {
                            _currentUser.value = user
                            onSuccess(user)
                        }
                    } else {
                        _currentUser.value = user
                        onSuccess(user)
                    }
                } else {
                    val err = "สมัครสมาชิกสำเร็จ แต่ไม่พบข้อมูลผู้ใช้"
                    _authError.value = err
                    onError(err)
                }
            }
            .addOnFailureListener { e ->
                val err = when (e) {
                    is FirebaseAuthUserCollisionException -> "อีเมลนี้มีบัญชีในระบบแล้ว กรุณาเข้าสู่ระบบแทน"
                    is FirebaseAuthWeakPasswordException -> "รหัสผ่านไม่ปลอดภัยพอ กรุณาตั้งรหัสผ่านที่มีตัวเลขและตัวอักษร"
                    else -> "สมัครสมาชิกไม่สำเร็จ: ${e.localizedMessage}"
                }
                _authError.value = err
                onError(err)
            }
    }

    fun sendPasswordReset(
        email: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        clearError()
        val auth = firebaseAuth ?: Firebase.auth
        if (email.isBlank()) {
            val err = "กรุณากรอกอีเมลเพื่อส่งลิงก์รีเซ็ตรหัสผ่าน"
            _authError.value = err
            onError(err)
            return
        }
        auth.sendPasswordResetEmail(email.trim())
            .addOnSuccessListener {
                onSuccess()
            }
            .addOnFailureListener { e ->
                val err = "ส่งอีเมลรีเซ็ตรหัสผ่านไม่สำเร็จ: ${e.localizedMessage}"
                _authError.value = err
                onError(err)
            }
    }

    // ==========================================
    // 2. Phone Number / OTP Authentication (Firebase Real)
    // ==========================================
    fun sendPhoneOtp(
        activity: Activity,
        phoneNumber: String,
        onCodeSent: (verificationId: String) -> Unit,
        onAutoVerified: (FirebaseUser) -> Unit,
        onError: (String) -> Unit
    ) {
        clearError()
        val auth = firebaseAuth ?: Firebase.auth
        val cleanPhone = phoneNumber.trim().replace(" ", "").replace("-", "")
        if (cleanPhone.isBlank()) {
            val err = "กรุณาระบุหมายเลขโทรศัพท์ (รวมรหัสประเทศ เช่น +66812345678)"
            _authError.value = err
            onError(err)
            return
        }

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                auth.signInWithCredential(credential)
                    .addOnSuccessListener { res ->
                        val user = res.user
                        if (user != null) {
                            _currentUser.value = user
                            onAutoVerified(user)
                        }
                    }
                    .addOnFailureListener { e ->
                        val err = e.localizedMessage ?: "ยืนยันหมายเลขโทรศัพท์อัตโนมัติไม่สำเร็จ"
                        _authError.value = err
                        onError(err)
                    }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.e(TAG, "Phone verification failed", e)
                val err = "การยืนยันเบอร์โทรศัพท์ล้มเหลว: ${e.localizedMessage} (ต้องเปิดใช้ Phone Provider ใน Firebase Console)"
                _authError.value = err
                onError(err)
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                Log.d(TAG, "OTP Code sent to $cleanPhone, verificationId=$verificationId")
                onCodeSent(verificationId)
            }
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(cleanPhone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyPhoneOtpAndSignIn(
        verificationId: String,
        otpCode: String,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit
    ) {
        clearError()
        val auth = firebaseAuth ?: Firebase.auth
        if (verificationId.isBlank() || otpCode.isBlank()) {
            val err = "กรุณากรอกรหัส OTP 6 หลัก"
            _authError.value = err
            onError(err)
            return
        }
        val credential = PhoneAuthProvider.getCredential(verificationId, otpCode.trim())
        auth.signInWithCredential(credential)
            .addOnSuccessListener { res ->
                val user = res.user
                if (user != null) {
                    _currentUser.value = user
                    onSuccess(user)
                } else {
                    val err = "ยืนยัน OTP สำเร็จ แต่ไม่พบข้อมูลผู้ใช้"
                    _authError.value = err
                    onError(err)
                }
            }
            .addOnFailureListener { e ->
                val err = "รหัส OTP ไม่ถูกต้องหรือหมดอายุ: ${e.localizedMessage}"
                _authError.value = err
                onError(err)
            }
    }

    // ==========================================
    // 3. Firebase Account Linking (Single UID Protection)
    // ==========================================
    fun linkWithEmailCredential(
        email: String,
        password: String,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit
    ) {
        clearError()
        val user = firebaseAuth?.currentUser ?: run {
            onError("กรุณาเข้าสู่ระบบก่อนเชื่อมต่อบัญชี")
            return
        }
        if (email.isBlank() || password.isBlank()) {
            onError("กรุณากรอกอีเมลและรหัสผ่านให้ครบถ้วน")
            return
        }
        val credential = EmailAuthProvider.getCredential(email.trim(), password)
        user.linkWithCredential(credential)
            .addOnSuccessListener { res ->
                val updatedUser = res.user ?: user
                _currentUser.value = updatedUser
                onSuccess(updatedUser)
            }
            .addOnFailureListener { e ->
                val err = if (e is FirebaseAuthUserCollisionException) {
                    "อีเมลนี้ถูกเชื่อมโยงกับบัญชีผู้ใช้อื่นในระบบแล้ว ไม่สามารถเชื่อมซ้ำได้ (UID แยกต่างหาก ห้ามรวมบัญชีอัตโนมัติ)"
                } else {
                    "การเชื่อมต่ออีเมลล้มเหลว: ${e.localizedMessage}"
                }
                _authError.value = err
                onError(err)
            }
    }

    fun linkWithPhoneCredential(
        verificationId: String,
        otpCode: String,
        onSuccess: (FirebaseUser) -> Unit,
        onError: (String) -> Unit
    ) {
        clearError()
        val user = firebaseAuth?.currentUser ?: run {
            onError("กรุณาเข้าสู่ระบบก่อนเชื่อมต่อบัญชี")
            return
        }
        if (verificationId.isBlank() || otpCode.isBlank()) {
            onError("กรุณากรอกรหัส OTP ให้ครบถ้วน")
            return
        }
        val credential = PhoneAuthProvider.getCredential(verificationId, otpCode.trim())
        user.linkWithCredential(credential)
            .addOnSuccessListener { res ->
                val updatedUser = res.user ?: user
                _currentUser.value = updatedUser
                onSuccess(updatedUser)
            }
            .addOnFailureListener { e ->
                val err = if (e is FirebaseAuthUserCollisionException) {
                    "เบอร์โทรศัพท์นี้ถูกเชื่อมโยงกับบัญชีผู้ใช้อื่นในระบบแล้ว ไม่สามารถเชื่อมซ้ำได้ (UID แยกต่างหาก ห้ามรวมบัญชีอัตโนมัติ)"
                } else {
                    "การเชื่อมต่อเบอร์โทรศัพท์ล้มเหลว: ${e.localizedMessage}"
                }
                _authError.value = err
                onError(err)
            }
    }

    fun getLinkedProviders(user: FirebaseUser): List<String> {
        return user.providerData.map { it.providerId }
    }

    // ==========================================
    // 4. Backend Super Admin Role Check (No Client Mock)
    // ==========================================
    fun checkBackendSuperAdminRole(
        uid: String,
        onResult: (isSuperAdmin: Boolean, backendStatus: String) -> Unit
    ) {
        try {
            val db = FirebaseFirestore.getInstance()
            db.collection("system_roles").document(uid).get()
                .addOnSuccessListener { doc ->
                    if (doc != null && doc.exists()) {
                        val role = doc.getString("role")
                        if (role.equals("SUPERADMIN", ignoreCase = true)) {
                            onResult(true, "ยืนยันสิทธิ์ SUPER ADMIN สำเร็จผ่าน Backend Firestore (system_roles/$uid)")
                        } else {
                            onResult(false, "สิทธิ์ที่ระบุใน Backend คือ: $role")
                        }
                    } else {
                        onResult(false, "ยังไม่มี Document สิทธิ์ใน Backend Firestore (system_roles/$uid) - ต้องแต่งตั้งผ่าน Backend เท่านั้น")
                    }
                }
                .addOnFailureListener { e ->
                    onResult(false, "ยังไม่สามารถตรวจสอบ Backend ได้: ${e.localizedMessage}")
                }
        } catch (e: Exception) {
            onResult(false, "Backend Firestore ยังไม่ได้เตรียมการ: ${e.localizedMessage}")
        }
    }

    fun mapFirebaseUserToFriendTalkUser(
        firebaseUser: FirebaseUser
    ): User {

        val displayName =
            firebaseUser.displayName
                ?.ifBlank { null }
                ?: firebaseUser.email
                    ?.substringBefore("@")
                ?: "ผู้ใช้ FriendTalk"

        val username =
            firebaseUser.email
                ?.substringBefore("@")
                ?.lowercase()
                ?.replace(".", "_")
                ?: "user_${firebaseUser.uid.take(6)}"

        val avatar =
            firebaseUser.photoUrl
                ?.toString()
                ?: "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=400&auto=format&fit=crop&q=80"

        val email = firebaseUser.email.orEmpty().trim()
        val phone = firebaseUser.phoneNumber.orEmpty()
        val linkedProviders = firebaseUser.providerData.map { it.providerId }

        // ตามข้อกำหนด 6: ห้ามกำหนดสิทธิ์ Super Admin จากอีเมลใน Client
        // กำหนดเป็น USER เริ่มต้น และรอการยืนยันจาก Backend ที่เชื่อถือได้
        val role = UserRole.USER
        val isMamaTarget = email.equals("mama.ff9522@gmail.com", ignoreCase = true)
        val backendStatus = if (isMamaTarget) {
            "รอการยืนยันและแต่งตั้งสิทธิ์ผ่าน Backend ที่เชื่อถือได้ (UID: ${firebaseUser.uid})"
        } else {
            "บัญชีผู้ใช้งานทั่วไป (UID: ${firebaseUser.uid})"
        }

        val badges = if (isMamaTarget) {
            listOf("รอการยืนยันสิทธิ์ Backend ⏳", "ยืนยันตัวตนแล้ว 🛡️")
        } else {
            listOf("ยืนยันตัวตนแล้ว 🛡️")
        }

        val bio = "สมาชิก FriendTalk (UID: ${firebaseUser.uid})"

        return User(
            id = firebaseUser.uid,
            username = username,
            displayName = displayName,
            avatar = avatar,
            email = email,
            phoneNumber = phone,
            linkedProviders = linkedProviders,
            backendRoleStatus = backendStatus,
            coverPhoto = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800&auto=format&fit=crop&q=80",
            age = 22,
            gender = Gender.OTHER,
            bio = bio,
            role = role,
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
            location = UserLocation(
                "กรุงเทพมหานคร",
                0.0,
                true
            ),
            badges = badges,
            interests = listOf(
                "หาเพื่อน",
                "พูดคุย",
                "ไลฟ์สด"
            )
        )
    }

    companion object {

        @Volatile
        private var instance:
            AuthService? = null

        fun getInstance(): AuthService {

            return instance
                ?: synchronized(this) {

                    instance
                        ?: AuthService()
                            .also {
                                instance = it
                            }
                }
        }
    }
}
