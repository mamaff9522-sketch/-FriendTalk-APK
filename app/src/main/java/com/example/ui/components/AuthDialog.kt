package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.Gender
import com.example.model.User
import com.example.model.UserRole
import com.example.ui.theme.*

@Composable
fun AuthDialog(
    currentUser: User,
    users: List<User>,
    onDismiss: () -> Unit,
    onSwitchUser: (String) -> Unit,
    onRegisterUser: (displayName: String, username: String, age: Int, gender: Gender) -> Unit
) {
    var isRegisterMode by remember { mutableStateOf(false) }
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var ageText by remember { mutableStateOf("20") }
    var gender by remember { mutableStateOf(Gender.FEMALE) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = Slate900,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isRegisterMode) "สมัครสมาชิกใหม่" else "สลับบัญชีผู้ใช้ (Switch Account)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = White
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (!isRegisterMode) {
                    Text(
                        text = "เลือกบัญชีผู้ใช้เพื่อทดสอบการใช้งาน (Admin / User / Companion / Creator):",
                        fontSize = 13.sp,
                        color = Slate400
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(users) { user ->
                            val isCurrent = user.id == currentUser.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        onSwitchUser(user.id)
                                        onDismiss()
                                    },
                                color = if (isCurrent) Pink500.copy(alpha = 0.15f) else Slate800,
                                shape = RoundedCornerShape(14.dp),
                                border = if (isCurrent) androidx.compose.foundation.BorderStroke(1.dp, Pink500) else null
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = user.avatar,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = user.displayName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = White
                                            )
                                            if (user.role == UserRole.SUPERADMIN) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = Purple600,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "ADMIN",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = White,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "@${user.username} • อายุ ${user.age} ปี • เหรียญ ${user.coins}",
                                            fontSize = 12.sp,
                                            color = Slate400
                                        )
                                    }
                                    if (isCurrent) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = "Active",
                                            tint = Pink400,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { isRegisterMode = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Slate800)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Pink400)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("สร้างบัญชีใหม่", color = White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("ชื่อที่แสดง (Display Name)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("ชื่อผู้ใช้ (Username)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = ageText,
                        onValueChange = { ageText = it },
                        label = { Text("อายุ (ปี)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = gender == Gender.FEMALE,
                            onClick = { gender = Gender.FEMALE },
                            label = { Text("หญิง 👩") }
                        )
                        FilterChip(
                            selected = gender == Gender.MALE,
                            onClick = { gender = Gender.MALE },
                            label = { Text("ชาย 👨") }
                        )
                        FilterChip(
                            selected = gender == Gender.OTHER,
                            onClick = { gender = Gender.OTHER },
                            label = { Text("อื่นๆ 🌈") }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { isRegisterMode = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("ย้อนกลับ", color = Slate300)
                        }
                        Button(
                            onClick = {
                                val age = ageText.toIntOrNull() ?: 20
                                if (displayName.isNotBlank() && username.isNotBlank()) {
                                    onRegisterUser(displayName, username, age, gender)
                                    onDismiss()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Pink500)
                        ) {
                            Text("ยืนยัน", color = White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
