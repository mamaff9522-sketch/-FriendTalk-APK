package com.example.ui.components

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.Gender
import com.example.model.User
import com.example.model.UserRole
import com.example.service.AuthService
import com.example.ui.theme.*

@Composable
fun AuthDialog(
    currentUser: User,
    users: List<User> = emptyList(),
    onDismiss: () -> Unit,
    onSwitchUser: ((String) -> Unit)? = null,
    onRegisterUser: ((String, String, Int, Gender) -> Unit)? = null,
    onSignOut: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authService = remember { AuthService.getInstance() }

    var linkingStatusMessage by remember { mutableStateOf<String?>(null) }
    var linkingErrorMessage by remember { mutableStateOf<String?>(null) }
    var isLinkingLoading by remember { mutableStateOf(false) }

    // Dialog state for linking email
    var showLinkEmailDialog by remember { mutableStateOf(false) }
    var linkEmailInput by remember { mutableStateOf("") }
    var linkPasswordInput by remember { mutableStateOf("") }

    // Dialog state for linking phone
    var showLinkPhoneDialog by remember { mutableStateOf(false) }
    var linkPhoneInput by remember { mutableStateOf("+66") }
    var linkOtpInput by remember { mutableStateOf("") }
    var linkVerificationId by remember { mutableStateOf<String?>(null) }
    var isLinkOtpSent by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            color = Slate900,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ข้อมูลบัญชี & เชื่อมโยงช่องทาง",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = White
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "ปิด", tint = Slate400)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Avatar
                Box(contentAlignment = Alignment.BottomEnd) {
                    AsyncImage(
                        model = currentUser.avatar,
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .border(2.dp, Pink500, CircleShape)
                    )
                    if (currentUser.isVerified) {
                        Surface(
                            shape = CircleShape,
                            color = Pink500,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Verified,
                                contentDescription = "Verified",
                                tint = White,
                                modifier = Modifier.padding(2.dp).fillMaxSize()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Display Name & Username
                Text(
                    text = currentUser.displayName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = White
                )
                Text(
                    text = "@${currentUser.username}",
                    fontSize = 12.sp,
                    color = Slate400
                )

                if (currentUser.email.isNotBlank()) {
                    Text(
                        text = "✉️ ${currentUser.email}",
                        fontSize = 12.sp,
                        color = Slate300
                    )
                }
                if (currentUser.phoneNumber.isNotBlank()) {
                    Text(
                        text = "📱 ${currentUser.phoneNumber}",
                        fontSize = 12.sp,
                        color = Slate300
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Role Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = when (currentUser.role) {
                        UserRole.SUPERADMIN -> Amber400.copy(alpha = 0.2f)
                        UserRole.ADMIN -> Blue500.copy(alpha = 0.2f)
                        UserRole.MODERATOR -> Amber500.copy(alpha = 0.15f)
                        UserRole.CREATOR -> Pink500.copy(alpha = 0.15f)
                        UserRole.USER -> Slate800
                    },
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = when (currentUser.role) {
                            UserRole.SUPERADMIN -> Amber400
                            UserRole.ADMIN -> Blue400
                            UserRole.MODERATOR -> Amber400
                            UserRole.CREATOR -> Pink400
                            UserRole.USER -> Slate700
                        }
                    )
                ) {
                    Text(
                        text = when (currentUser.role) {
                            UserRole.SUPERADMIN -> "👑 SUPER ADMIN"
                            UserRole.ADMIN -> "🛡️ ADMIN (ผู้ดูแล)"
                            UserRole.MODERATOR -> "⚖️ MODERATOR"
                            UserRole.CREATOR -> "⭐ CREATOR"
                            UserRole.USER -> "👤 USER (สมาชิกทั่วไป)"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (currentUser.role) {
                            UserRole.SUPERADMIN -> Amber300
                            UserRole.ADMIN -> Blue400
                            UserRole.MODERATOR -> Amber400
                            UserRole.CREATOR -> Pink400
                            UserRole.USER -> Slate300
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }

                // Backend Verification Note (Requirement 6)
                if (currentUser.backendRoleStatus.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Slate800,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "🛡️ ${currentUser.backendRoleStatus}",
                            fontSize = 10.sp,
                            color = Slate300,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Single UID Card (Requirement 3 & 5)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Slate800.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate700),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Firebase UID (เอกสิทธิ์เดียว)", fontSize = 11.sp, color = Slate400, fontWeight = FontWeight.SemiBold)
                            Text(text = "🔒 แยกข้อมูลอิสระ", fontSize = 10.sp, color = Emerald400)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = currentUser.id,
                            fontSize = 11.sp,
                            color = Cyan400,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "การเชื่อมหลายช่องทางจะไม่เปลี่ยน UID นี้ และข้อมูลโปรไฟล์/แชต/กระเป๋าจะไม่ปะปนกับบัญชีอื่น",
                            fontSize = 9.sp,
                            color = Slate400
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Status Banners
                AnimatedVisibility(visible = linkingErrorMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Rose500.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Rose500.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(text = linkingErrorMessage ?: "", color = Rose500, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { linkingErrorMessage = null }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Rose500, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = linkingStatusMessage != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Emerald400.copy(alpha = 0.15f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Emerald400.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(text = linkingStatusMessage ?: "", color = Emerald400, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            IconButton(onClick = { linkingStatusMessage = null }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Emerald400, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                // Account Linking Section (Requirement 2, 3, 4)
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Slate800,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Link, contentDescription = null, tint = Pink400, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ช่องทางล็อกอินที่ผูกกับบัญชีนี้ (Account Linking)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = White
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val providers = currentUser.linkedProviders
                        val hasGoogle = providers.any { it.contains("google", ignoreCase = true) }
                        val hasPassword = providers.any { it.contains("password", ignoreCase = true) }
                        val hasPhone = providers.any { it.contains("phone", ignoreCase = true) }

                        // Provider Item: Google
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "🌐 Google Account", fontSize = 12.sp, color = Slate200)
                            if (hasGoogle) {
                                Surface(shape = RoundedCornerShape(6.dp), color = Emerald400.copy(alpha = 0.2f)) {
                                    Text("เชื่อมแล้ว ✓", fontSize = 10.sp, color = Emerald400, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            } else {
                                Button(
                                    onClick = {
                                        val activity = context as? Activity
                                        if (activity != null) {
                                            isLinkingLoading = true
                                            linkingErrorMessage = null
                                            linkingStatusMessage = null
                                            authService.signInWithGoogleNative(
                                                activity = activity,
                                                scope = coroutineScope,
                                                onSuccess = { user ->
                                                    isLinkingLoading = false
                                                    linkingStatusMessage = "เชื่อมต่อ Google Account สำเร็จ (UID คงเดิม)"
                                                },
                                                onError = { err ->
                                                    isLinkingLoading = false
                                                    linkingErrorMessage = err
                                                }
                                            )
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("ผูก Google", fontSize = 10.sp, color = White)
                                }
                            }
                        }

                        // Provider Item: Email / Password
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "✉️ Email / Password", fontSize = 12.sp, color = Slate200)
                            if (hasPassword) {
                                Surface(shape = RoundedCornerShape(6.dp), color = Emerald400.copy(alpha = 0.2f)) {
                                    Text("เชื่อมแล้ว ✓", fontSize = 10.sp, color = Emerald400, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            } else {
                                Button(
                                    onClick = {
                                        showLinkEmailDialog = true
                                        linkingErrorMessage = null
                                        linkingStatusMessage = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("ผูกอีเมล", fontSize = 10.sp, color = White)
                                }
                            }
                        }

                        // Provider Item: Phone / OTP
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "📱 Phone / OTP", fontSize = 12.sp, color = Slate200)
                            if (hasPhone) {
                                Surface(shape = RoundedCornerShape(6.dp), color = Emerald400.copy(alpha = 0.2f)) {
                                    Text("เชื่อมแล้ว ✓", fontSize = 10.sp, color = Emerald400, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            } else {
                                Button(
                                    onClick = {
                                        showLinkPhoneDialog = true
                                        isLinkOtpSent = false
                                        linkOtpInput = ""
                                        linkingErrorMessage = null
                                        linkingStatusMessage = null
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Slate700),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("ผูกเบอร์โทร", fontSize = 10.sp, color = White)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Stats: Coins & Diamonds
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), color = Slate800) {
                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "🪙 เหรียญ", fontSize = 11.sp, color = Amber400)
                            Text(text = "${currentUser.coins}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = White)
                        }
                    }
                    Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), color = Slate800) {
                        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "💎 เพชร", fontSize = 11.sp, color = Cyan400)
                            Text(text = "${currentUser.diamonds}", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Sign Out & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("ปิด", color = Slate300)
                    }

                    Button(
                        onClick = {
                            onDismiss()
                            if (onSignOut != null) {
                                onSignOut()
                            } else {
                                authService.signOut(context, coroutineScope) {}
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Rose500),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.ExitToApp, contentDescription = null, tint = White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("ออกจากระบบ", color = White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    // Link Email Dialog
    if (showLinkEmailDialog) {
        Dialog(onDismissRequest = { showLinkEmailDialog = false }) {
            Surface(shape = RoundedCornerShape(18.dp), color = Slate900, modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("เชื่อมโยง Email / Password ✉️", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("ผูกอีเมลเข้ากับบัญชีนี้ (UID: ${currentUser.id.take(8)}... จะไม่เปลี่ยน)", fontSize = 11.sp, color = Slate400)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = linkEmailInput,
                        onValueChange = { linkEmailInput = it },
                        label = { Text("อีเมลที่ต้องการผูก", color = Slate400, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = linkPasswordInput,
                        onValueChange = { linkPasswordInput = it },
                        label = { Text("รหัสผ่านใหม่", color = Slate400, fontSize = 12.sp) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showLinkEmailDialog = false }, modifier = Modifier.weight(1f)) {
                            Text("ยกเลิก", color = Slate300)
                        }
                        Button(
                            onClick = {
                                isLinkingLoading = true
                                authService.linkWithEmailCredential(
                                    email = linkEmailInput,
                                    password = linkPasswordInput,
                                    onSuccess = {
                                        isLinkingLoading = false
                                        showLinkEmailDialog = false
                                        linkingStatusMessage = "เชื่อมต่ออีเมลสำเร็จ! บัญชีของคุณใช้ UID เดิม"
                                    },
                                    onError = { err ->
                                        isLinkingLoading = false
                                        showLinkEmailDialog = false
                                        linkingErrorMessage = err
                                    }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Pink600),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("เชื่อมต่อ", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Link Phone Dialog
    if (showLinkPhoneDialog) {
        Dialog(onDismissRequest = { showLinkPhoneDialog = false }) {
            Surface(shape = RoundedCornerShape(18.dp), color = Slate900, modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("เชื่อมโยงเบอร์โทรศัพท์ (SMS OTP) 📱", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("ผูกเบอร์โทรเข้ากับบัญชีนี้ (UID: ${currentUser.id.take(8)}... จะไม่เปลี่ยน)", fontSize = 11.sp, color = Slate400)
                    Spacer(modifier = Modifier.height(12.dp))

                    if (!isLinkOtpSent) {
                        OutlinedTextField(
                            value = linkPhoneInput,
                            onValueChange = { linkPhoneInput = it },
                            label = { Text("เบอร์โทรศัพท์ (รวมรหัสประเทศ เช่น +66...)", color = Slate400, fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { showLinkPhoneDialog = false }, modifier = Modifier.weight(1f)) {
                                Text("ยกเลิก", color = Slate300)
                            }
                            Button(
                                onClick = {
                                    val activity = context as? Activity
                                    if (activity != null) {
                                        authService.sendPhoneOtp(
                                            activity = activity,
                                            phoneNumber = linkPhoneInput,
                                            onCodeSent = { vId ->
                                                linkVerificationId = vId
                                                isLinkOtpSent = true
                                            },
                                            onAutoVerified = {
                                                showLinkPhoneDialog = false
                                                linkingStatusMessage = "ยืนยันเบอร์โทรศัพท์อัตโนมัติสำเร็จ"
                                            },
                                            onError = { err ->
                                                showLinkPhoneDialog = false
                                                linkingErrorMessage = err
                                            }
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Purple600),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("ส่ง OTP", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = linkOtpInput,
                            onValueChange = { linkOtpInput = it },
                            label = { Text("กรอก OTP 6 หลัก", color = Slate400, fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { isLinkOtpSent = false }, modifier = Modifier.weight(1f)) {
                                Text("ย้อนกลับ", color = Slate300)
                            }
                            Button(
                                onClick = {
                                    val vId = linkVerificationId
                                    if (vId != null) {
                                        authService.linkWithPhoneCredential(
                                            verificationId = vId,
                                            otpCode = linkOtpInput,
                                            onSuccess = {
                                                showLinkPhoneDialog = false
                                                linkingStatusMessage = "เชื่อมต่อเบอร์โทรศัพท์สำเร็จ! บัญชีของคุณใช้ UID เดิม"
                                            },
                                            onError = { err ->
                                                showLinkPhoneDialog = false
                                                linkingErrorMessage = err
                                            }
                                        )
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("ยืนยัน OTP", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
