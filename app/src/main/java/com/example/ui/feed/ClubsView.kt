package com.example.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.Club
import com.example.ui.theme.*

@Composable
fun ClubsSnippetRow(
    clubs: List<Club>,
    onClubClick: (Club) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "คลับและคอมมูนิตี้ยอดนิยม 🏆",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = White
            )
            Text(
                text = "ดูทั้งหมด",
                fontSize = 12.sp,
                color = Pink400,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(clubs) { club ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Slate800,
                    modifier = Modifier
                        .width(170.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onClubClick(club) }
                ) {
                    Column {
                        Box(modifier = Modifier.height(80.dp).fillMaxWidth()) {
                            AsyncImage(
                                model = club.coverUrl,
                                contentDescription = club.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Black.copy(alpha = 0.2f), Black.copy(alpha = 0.7f))
                                        )
                                    )
                            )
                            Text(
                                text = club.icon,
                                fontSize = 24.sp,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(8.dp)
                            )
                        }
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = club.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "สมาชิก ${club.membersCount} คน",
                                fontSize = 11.sp,
                                color = Slate400
                            )
                        }
                    }
                }
            }
        }
    }
}
