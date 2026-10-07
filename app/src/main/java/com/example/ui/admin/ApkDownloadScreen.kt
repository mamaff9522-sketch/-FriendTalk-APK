package com.example.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.model.ApkBuildRecord
import com.example.ui.theme.*

@Composable
fun ApkDownloadDialog(
    apkBuilds: List<ApkBuildRecord>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Slate900,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(0.95f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "ประวัติและดาวน์โหลด APK 📦", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = White)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(apkBuilds) { build ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Slate800,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "FriendTalk v${build.versionName} (Build #${build.versionCode})", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = White)
                                    Surface(color = Emerald400.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                                        Text(build.status, fontSize = 9.sp, color = Emerald400, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = build.notes, fontSize = 11.sp, color = Slate300)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "ขนาด: ${build.apkSizeMb} • วันที่: ${build.buildDate}", fontSize = 10.sp, color = Slate500)
                            }
                        }
                    }
                }
            }
        }
    }
}
