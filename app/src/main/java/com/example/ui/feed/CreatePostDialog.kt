package com.example.ui.feed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.PostType
import com.example.model.User
import com.example.ui.theme.*

@Composable
fun CreatePostDialog(
    currentUser: User,
    onDismiss: () -> Unit,
    onCreatePost: (content: String, type: PostType, images: List<String>, videoUrl: String?, tags: List<String>) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(PostType.TEXT) }
    var imageUrlInput by remember { mutableStateOf("") }
    var tagsInput by remember { mutableStateOf("") }

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
                        text = "สร้างโพสต์ใหม่ ✍️",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = White
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = currentUser.avatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = currentUser.displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = White
                        )
                        Text(
                            text = "โพสต์สาธารณะ • ${currentUser.location.province}",
                            fontSize = 11.sp,
                            color = Slate400
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("คุณกำลังคิดอะไรอยู่... แชร์เรื่องราวให้เพื่อนๆ ฟังกันเลย ✨", fontSize = 14.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedType == PostType.TEXT,
                        onClick = { selectedType = PostType.TEXT },
                        label = { Text("ข้อความ 📝") }
                    )
                    FilterChip(
                        selected = selectedType == PostType.IMAGE,
                        onClick = {
                            selectedType = PostType.IMAGE
                            if (imageUrlInput.isBlank()) {
                                imageUrlInput = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800&auto=format&fit=crop&q=80"
                            }
                        },
                        label = { Text("รูปภาพ 📸") }
                    )
                    FilterChip(
                        selected = selectedType == PostType.MULTI_IMAGE,
                        onClick = { selectedType = PostType.MULTI_IMAGE },
                        label = { Text("หลายรูป 🖼️") }
                    )
                }

                if (selectedType == PostType.IMAGE || selectedType == PostType.MULTI_IMAGE) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = imageUrlInput,
                        onValueChange = { imageUrlInput = it },
                        label = { Text("Image URL") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = tagsInput,
                    onValueChange = { tagsInput = it },
                    label = { Text("แท็ก (คั่นด้วยจุลภาค เช่น หาเพื่อน, เที่ยว)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (text.isNotBlank() || imageUrlInput.isNotBlank()) {
                            val imgList = if (imageUrlInput.isNotBlank()) listOf(imageUrlInput) else emptyList()
                            val tagList = tagsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            onCreatePost(text, selectedType, imgList, null, tagList)
                            onDismiss()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Pink500),
                    enabled = text.isNotBlank() || imageUrlInput.isNotBlank()
                ) {
                    Text("โพสต์เลย", color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}
