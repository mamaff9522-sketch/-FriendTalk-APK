package com.example.companion.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
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
import com.example.companion.model.CompanionSession
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun CompanionSessionActiveDialog(
    session: CompanionSession,
    onEndSession: (durationSec: Int) -> Unit
) {
    var secondsElapsed by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            secondsElapsed++
        }
    }

    Dialog(
        onDismissRequest = { onEndSession(secondsElapsed) },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Slate950
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(modifier = Modifier.height(48.dp))
                    AsyncImage(
                        model = session.companionAvatar,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "กำลังคุยกับ ${session.companionName}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "เวลาที่คุย: ${secondsElapsed / 60} นาที ${secondsElapsed % 60} วินาที",
                        fontSize = 15.sp,
                        color = Emerald400,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "อัตราค่าบริการ: 🪙 ${session.rateCoinsPerMin} เหรียญ/นาที",
                        fontSize = 12.sp,
                        color = Amber400
                    )
                }

                Button(
                    onClick = { onEndSession(secondsElapsed) },
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .padding(bottom = 36.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Rose500)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("จบการสนทนา", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
