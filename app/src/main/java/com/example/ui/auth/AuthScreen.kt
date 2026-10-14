package com.example.ui.auth

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.example.ui.components.ft.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.AuthService
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseUser

@Composable
fun AuthScreen(
    onAuthSuccess: (FirebaseUser) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authService = remember { AuthService.getInstance() }

    val serviceAuthError by authService.authError.collectAsStateWithLifecycle()
    var isLoading by rememberSaveable { mutableStateOf(false) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    var infoMessage by rememberSaveable { mutableStateOf<String?>(null) }

    // 0 = Google Sign-In, 1 = Email / Password, 2 = Phone / OTP
    var selectedAuthChannel by rememberSaveable { mutableIntStateOf(0) }

    // Email / Password Form States
    var isSignUp by rememberSaveable { mutableStateOf(false) }
    var emailInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var confirmPasswordInput by rememberSaveable { mutableStateOf("") }
    var displayNameInput by rememberSaveable { mutableStateOf("") }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }

    // Phone / OTP Form States
    var phoneInput by rememberSaveable { mutableStateOf("+66") }
    var otpInput by rememberSaveable { mutableStateOf("") }
    var verificationId by rememberSaveable { mutableStateOf<String?>(null) }
    var isOtpSent by rememberSaveable { mutableStateOf(false) }

    val displayError = localError ?: serviceAuthError
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color(0xFF0B0824), androidx.compose.ui.graphics.Color(0xFF221050), androidx.compose.ui.graphics.Color(0xFF4A1660)))),
        contentAlignment = Alignment.Center
    ) {
        // soft neon glows behind the card
        Box(Modifier.align(Alignment.TopStart).offset((-80).dp, 40.dp).size(300.dp).background(Brush.radialGradient(listOf(androidx.compose.ui.graphics.Color(0x5533C8FF), androidx.compose.ui.graphics.Color.Transparent))))
        Box(Modifier.align(Alignment.BottomEnd).offset(90.dp, (-60).dp).size(340.dp).background(Brush.radialGradient(listOf(androidx.compose.ui.graphics.Color(0x66FF2FA8), androidx.compose.ui.graphics.Color.Transparent))))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 22.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(44.dp))

            // Brand logo (same artwork as the launcher icon) with a soft halo
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(170.dp).background(Brush.radialGradient(listOf(androidx.compose.ui.graphics.Color(0x66FF4FB8), androidx.compose.ui.graphics.Color(0x2233C8FF), androidx.compose.ui.graphics.Color.Transparent))))
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(com.example.R.drawable.brand_logo),
                    contentDescription = "FriendTalk",
                    modifier = Modifier.size(96.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Friend", fontWeight = FontWeight.Black, fontSize = 32.sp, color = PureWhite, letterSpacing = (-0.5).sp)
                Text("Talk", fontWeight = FontWeight.Black, fontSize = 32.sp, color = androidx.compose.ui.graphics.Color(0xFFFF4FB8), letterSpacing = (-0.5).sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "เจอเพื่อนใหม่ ใกล้ตัวคุณ",
                fontSize = 15.sp,
                color = PureWhite.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Glass card holding the sign-in options
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(if (com.example.ui.theme.AppThemeMode.dark) androidx.compose.ui.graphics.Color(0xD9140F35) else androidx.compose.ui.graphics.Color(0xF2FFFFFF))
                    .border(1.dp, Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0x8833C8FF), androidx.compose.ui.graphics.Color(0x88FF2FA8))), RoundedCornerShape(28.dp))
                    .padding(horizontal = 20.dp, vertical = 22.dp)
            ) {
            Text("ยินดีต้อนรับ", color = White, fontWeight = FontWeight.Bold, fontSize = 22.sp, modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp), textAlign = TextAlign.Center)
            Text("เข้าสู่ระบบหรือสมัครสมาชิกฟรี", color = Slate400, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp), textAlign = TextAlign.Center)

            // Error Banner
            AnimatedVisibility(visible = displayError != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Rose500.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Rose500.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ข้อผิดพลาด ⚠️",
                                color = Rose500,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = {
                                    localError = null
                                    authService.clearError()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "ปิด",
                                    tint = Rose500,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = displayError ?: "",
                            color = Rose500,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            // Info Banner
            AnimatedVisibility(visible = infoMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Emerald400.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Emerald400.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = infoMessage ?: "",
                            color = Emerald400,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { infoMessage = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "ปิด", tint = Emerald400, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            if (selectedAuthChannel != 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                        selectedAuthChannel = 0; localError = null; infoMessage = null; authService.clearError()
                    }.padding(vertical = 6.dp, horizontal = 2.dp)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Slate400, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("เลือกวิธีอื่น", color = Slate400, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(10.dp))
            } else {
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Channel Content
            when (selectedAuthChannel) {
                0 -> {
                    // Google Sign-In Channel
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = PureWhite,
                            border = androidx.compose.foundation.BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFDADCE0)),
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                        ) {
                            Button(
                                onClick = {
                                    isLoading = true
                                    localError = null
                                    infoMessage = null
                                    authService.clearError()
                                    val activity = context as? Activity
                                    if (activity != null) {
                                        authService.signInWithGoogleNative(
                                            activity = activity,
                                            scope = coroutineScope,
                                            onSuccess = { firebaseUser ->
                                                isLoading = false
                                                onAuthSuccess(firebaseUser)
                                            },
                                            onError = { err ->
                                                isLoading = false
                                                localError = err
                                            },
                                            onCancelled = { reason ->
                                                isLoading = false
                                                localError = reason
                                            }
                                        )
                                    } else {
                                        isLoading = false
                                        localError = "Activity Context ไม่ถูกต้อง"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PureWhite,
                                    contentColor = androidx.compose.ui.graphics.Color(0xFF1F1F1F),
                                    disabledContainerColor = PureWhite
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxSize(),
                                enabled = !isLoading
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = Pink500,
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        GoogleMark()
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = "ดำเนินการต่อด้วย Google",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                            color = androidx.compose.ui.graphics.Color(0xFF1F1F1F)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Box(Modifier.weight(1f).height(1.dp).background(Slate400.copy(alpha = 0.3f)))
                            Text("หรือ", color = Slate400, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
                            Box(Modifier.weight(1f).height(1.dp).background(Slate400.copy(alpha = 0.3f)))
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            AltMethodButton(Icons.Default.Email, "อีเมล", Modifier.weight(1f)) {
                                selectedAuthChannel = 1; localError = null; infoMessage = null; authService.clearError()
                            }
                            AltMethodButton(Icons.Default.Phone, "เบอร์โทร", Modifier.weight(1f)) {
                                selectedAuthChannel = 2; localError = null; infoMessage = null; authService.clearError()
                            }
                        }
                    }
                }

                1 -> {
                    // Email / Password Channel
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Sub-toggle: Sign In vs Sign Up
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            TextButton(
                                onClick = {
                                    isSignUp = false
                                    localError = null
                                    infoMessage = null
                                }
                            ) {
                                Text(
                                    text = "เข้าสู่ระบบ",
                                    color = if (!isSignUp) Pink400 else Slate400,
                                    fontWeight = if (!isSignUp) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            TextButton(
                                onClick = {
                                    isSignUp = true
                                    localError = null
                                    infoMessage = null
                                }
                            ) {
                                Text(
                                    text = "สมัครสมาชิกใหม่",
                                    color = if (isSignUp) Pink400 else Slate400,
                                    fontWeight = if (isSignUp) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (isSignUp) {
                            OutlinedTextField(
                                value = displayNameInput,
                                onValueChange = { displayNameInput = it },
                                label = { Text("ชื่อที่แสดง (Display Name)", color = Slate400, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = White,
                                    unfocusedTextColor = White,
                                    focusedBorderColor = Pink500,
                                    unfocusedBorderColor = Slate800,
                                    focusedContainerColor = Slate900,
                                    unfocusedContainerColor = Slate900
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        OutlinedTextField(
                            value = emailInput,
                            onValueChange = { emailInput = it },
                            label = { Text("อีเมล (Email)", color = Slate400, fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = White,
                                unfocusedTextColor = White,
                                focusedBorderColor = Pink500,
                                unfocusedBorderColor = Slate800,
                                focusedContainerColor = Slate900,
                                unfocusedContainerColor = Slate900
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("รหัสผ่าน (Password)", color = Slate400, fontSize = 12.sp) },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = null,
                                        tint = Slate400,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = White,
                                unfocusedTextColor = White,
                                focusedBorderColor = Pink500,
                                unfocusedBorderColor = Slate800,
                                focusedContainerColor = Slate900,
                                unfocusedContainerColor = Slate900
                            )
                        )

                        if (isSignUp) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = confirmPasswordInput,
                                onValueChange = { confirmPasswordInput = it },
                                label = { Text("ยืนยันรหัสผ่าน", color = Slate400, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp)) },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = White,
                                    unfocusedTextColor = White,
                                    focusedBorderColor = Pink500,
                                    unfocusedBorderColor = Slate800,
                                    focusedContainerColor = Slate900,
                                    unfocusedContainerColor = Slate900
                                )
                            )
                        }

                        if (!isSignUp) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        if (emailInput.isBlank()) {
                                            localError = "กรุณากรอกอีเมลก่อนเพื่อขอรีเซ็ตรหัสผ่าน"
                                        } else {
                                            isLoading = true
                                            authService.sendPasswordReset(
                                                email = emailInput,
                                                onSuccess = {
                                                    isLoading = false
                                                    infoMessage = "ส่งลิงก์รีเซ็ตรหัสผ่านไปยัง $emailInput เรียบร้อยแล้ว"
                                                },
                                                onError = { err ->
                                                    isLoading = false
                                                    localError = err
                                                }
                                            )
                                        }
                                    }
                                ) {
                                    Text("ลืมรหัสผ่าน?", fontSize = 11.sp, color = Pink400)
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(14.dp))
                        }

                        Button(
                            onClick = {
                                if (isSignUp) {
                                    if (passwordInput != confirmPasswordInput) {
                                        localError = "รหัสผ่านทั้งสองช่องไม่ตรงกัน"
                                        return@Button
                                    }
                                    isLoading = true
                                    localError = null
                                    infoMessage = null
                                    authService.signUpWithEmail(
                                        email = emailInput,
                                        password = passwordInput,
                                        displayName = displayNameInput,
                                        onSuccess = { user ->
                                            isLoading = false
                                            onAuthSuccess(user)
                                        },
                                        onError = { err ->
                                            isLoading = false
                                            localError = err
                                        }
                                    )
                                } else {
                                    isLoading = true
                                    localError = null
                                    infoMessage = null
                                    authService.signInWithEmail(
                                        email = emailInput,
                                        password = passwordInput,
                                        onSuccess = { user ->
                                            isLoading = false
                                            onAuthSuccess(user)
                                        },
                                        onError = { err ->
                                            isLoading = false
                                            localError = err
                                        }
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFE6399B)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = White, strokeWidth = 2.dp)
                            } else {
                                Text(
                                    text = if (isSignUp) "สมัครสมาชิก" else "เข้าสู่ระบบ",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isSignUp) "เราจะส่งลิงก์ยืนยันไปที่อีเมลของคุณ" else "ลืมรหัสผ่าน? เข้าด้วย Google หรือเบอร์โทรแทนได้",
                            fontSize = 11.sp,
                            color = Slate400,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                2 -> {
                    // Phone Number / OTP Channel
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (!isOtpSent) {
                            // Step 1: Input Phone
                            OutlinedTextField(
                                value = phoneInput,
                                onValueChange = { phoneInput = it },
                                label = { Text("เบอร์โทรศัพท์ (มีรหัสประเทศ)", color = Slate400, fontSize = 12.sp) },
                                placeholder = { Text("+66812345678", color = Slate500) },
                                leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = Slate400, modifier = Modifier.size(18.dp)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = White,
                                    unfocusedTextColor = White,
                                    focusedBorderColor = Pink500,
                                    unfocusedBorderColor = Slate800,
                                    focusedContainerColor = Slate900,
                                    unfocusedContainerColor = Slate900
                                )
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    val activity = context as? Activity
                                    if (activity != null) {
                                        isLoading = true
                                        localError = null
                                        infoMessage = null
                                        authService.sendPhoneOtp(
                                            activity = activity,
                                            phoneNumber = phoneInput,
                                            onCodeSent = { vId ->
                                                isLoading = false
                                                verificationId = vId
                                                isOtpSent = true
                                                infoMessage = "ส่งรหัส OTP ไปยัง $phoneInput แล้ว"
                                            },
                                            onAutoVerified = { user ->
                                                isLoading = false
                                                onAuthSuccess(user)
                                            },
                                            onError = { err ->
                                                isLoading = false
                                                localError = err
                                            }
                                        )
                                    } else {
                                        localError = "Activity Context ไม่ถูกต้อง"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Purple600),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                enabled = !isLoading
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = White, strokeWidth = 2.dp)
                                } else {
                                    Text("ส่งรหัส OTP ทาง SMS", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        } else {
                            // Step 2: Input OTP
                            Text(
                                text = "กรอกรหัส OTP 6 หลักที่ส่งไปยัง $phoneInput",
                                fontSize = 12.sp,
                                color = Slate300
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = otpInput,
                                onValueChange = { otpInput = it },
                                label = { Text("รหัส OTP 6 หลัก", color = Slate400, fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = White,
                                    unfocusedTextColor = White,
                                    focusedBorderColor = Purple500,
                                    unfocusedBorderColor = Slate800,
                                    focusedContainerColor = Slate900,
                                    unfocusedContainerColor = Slate900
                                )
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    val vId = verificationId
                                    if (vId != null) {
                                        isLoading = true
                                        localError = null
                                        infoMessage = null
                                        authService.verifyPhoneOtpAndSignIn(
                                            verificationId = vId,
                                            otpCode = otpInput,
                                            onSuccess = { user ->
                                                isLoading = false
                                                onAuthSuccess(user)
                                            },
                                            onError = { err ->
                                                isLoading = false
                                                localError = err
                                            }
                                        )
                                    } else {
                                        localError = "ไม่พบ Verification ID กรุณากดส่งรหัสใหม่"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                enabled = !isLoading
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = White, strokeWidth = 2.dp)
                                } else {
                                    Text("ยืนยันรหัส OTP และเข้าสู่ระบบ", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            TextButton(
                                onClick = {
                                    isOtpSent = false
                                    otpInput = ""
                                    localError = null
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("เปลี่ยนเบอร์โทร หรือส่งรหัสใหม่", color = Slate400, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "การดำเนินการต่อถือว่าคุณยอมรับข้อกำหนดการใช้งาน\nและนโยบายความเป็นส่วนตัวของ FriendTalk",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = PureWhite.copy(alpha = 0.55f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AltMethodButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, Slate400.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Four-colour Google "G" drawn as arcs so no extra asset is needed. */
@Composable
private fun GoogleMark() {
    androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
        val stroke = size.minDimension * 0.2f
        val inset = stroke / 2
        val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
        val tl = androidx.compose.ui.geometry.Offset(inset, inset)
        val st = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        drawArc(androidx.compose.ui.graphics.Color(0xFFEA4335), -150f, 105f, false, tl, arcSize, style = st)
        drawArc(androidx.compose.ui.graphics.Color(0xFFFBBC05), 150f, 60f, false, tl, arcSize, style = st)
        drawArc(androidx.compose.ui.graphics.Color(0xFF34A853), 45f, 105f, false, tl, arcSize, style = st)
        drawArc(androidx.compose.ui.graphics.Color(0xFF4285F4), -5f, 50f, false, tl, arcSize, style = st)
        drawRect(
            androidx.compose.ui.graphics.Color(0xFF4285F4),
            topLeft = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2 - stroke / 2),
            size = androidx.compose.ui.geometry.Size(size.width / 2 - inset + stroke / 2, stroke)
        )
    }
}
