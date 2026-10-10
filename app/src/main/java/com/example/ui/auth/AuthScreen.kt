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
            .background(Slate950)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Branded Logo
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(Pink500, Purple600))
                    )
                    .border(2.dp, Pink400.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "FT",
                    fontWeight = FontWeight.Black,
                    fontSize = 32.sp,
                    color = White
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // App Title
            Text(
                text = "FriendTalk",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 28.sp,
                color = White,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "พื้นที่พบปะเพื่อนใหม่ ไลฟ์สด และคอมมูนิตี้ที่คุณรัก",
                fontSize = 13.sp,
                color = Slate300,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

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

            // Segmented Channel Selector (3 Channels)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Slate900,
                border = androidx.compose.foundation.BorderStroke(1.dp, Slate800),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(4.dp)) {
                    val channels = listOf("🌐 Google", "✉️ อีเมล", "📱 เบอร์โทร")
                    channels.forEachIndexed { index, title ->
                        val isSelected = selectedAuthChannel == index
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Pink600 else Slate900,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedAuthChannel = index
                                    localError = null
                                    infoMessage = null
                                    authService.clearError()
                                }
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) White else Slate400,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

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
                            color = White,
                            tonalElevation = 4.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
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
                                    containerColor = White,
                                    contentColor = Slate900
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
                                        Text(text = "🌐", fontSize = 18.sp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "เข้าสู่ระบบด้วย Google",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Slate900
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "💡 สำหรับอุปกรณ์จริง (APK) ที่มี Google Account",
                            fontSize = 11.sp,
                            color = Slate400,
                            textAlign = TextAlign.Center
                        )
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
                            colors = ButtonDefaults.buttonColors(containerColor = Pink600),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = White, strokeWidth = 2.dp)
                            } else {
                                Text(
                                    text = if (isSignUp) "สมัครสมาชิกด้วย Firebase" else "เข้าสู่ระบบด้วย Firebase Email",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "💡 ใช้งานได้บน AI Studio Preview และทุกอุปกรณ์",
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
                                    Text("ส่งรหัส OTP ผ่าน SMS (Firebase)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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

            Spacer(modifier = Modifier.height(24.dp))

            // Security note
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = Slate500,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "รับรองความปลอดภัยด้วย Firebase Authentication",
                    fontSize = 11.sp,
                    color = Slate500
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
