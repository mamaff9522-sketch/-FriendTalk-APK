package com.example.ui.auth

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.AuthService
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseUser

@Composable
fun AuthScreen(
    onAuthSuccess: (FirebaseUser) -> Unit,
    onGuestDemoLogin: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val authService = remember { AuthService.getInstance() }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Branded Logo
            Box(
                modifier = Modifier
                    .size(88.dp)
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
                    fontSize = 36.sp,
                    color = White
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // App Title
            Text(
                text = "FriendTalk",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 32.sp,
                color = White,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "พื้นที่พบปะเพื่อนใหม่ ไลฟ์สด และคอมมูนิตี้ที่คุณรัก",
                fontSize = 14.sp,
                color = Slate300,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Error Message Banner
            AnimatedVisibility(visible = errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Rose500.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Rose500.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = Rose500,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Google Sign-In Primary Button
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = White,
                tonalElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
            ) {
                Button(
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        val activity = context as? Activity
                        if (activity != null) {
                            authService.signInWithGoogleNative(
                                activity = activity,
                                scope = coroutineScope,
                                onSuccess = { firebaseUser: FirebaseUser ->
                                    isLoading = false
                                    onAuthSuccess(firebaseUser)
                                },
                                onError = { err: String ->
                                    isLoading = false
                                    errorMessage = err
                                },
                                onCancelled = {
                                    isLoading = false
                                }
                            )
                        } else {
                            isLoading = false
                            errorMessage = "Activity Context ไม่ถูกต้อง"
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
                            // Google 'G' Symbol
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
        }
    }
}
