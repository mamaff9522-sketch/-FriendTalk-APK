package com.example.ui.live

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.model.TopGifterRecord
import com.example.ui.theme.*

@Composable
fun TopGifterDialog(
    gifters: List<TopGifterRecord>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Slate900,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "อันดับผู้ส่งของขวัญสูงสุด 👑",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Amber400
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Slate400)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(gifters) { item ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Slate800,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "#${item.rank}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = if (item.rank == 1) Amber400 else Slate300
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                AsyncImage(
                                    model = item.userAvatar,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(36.dp).clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = item.userName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = White,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "🪙 ${item.coinsContributed}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Amber400
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
