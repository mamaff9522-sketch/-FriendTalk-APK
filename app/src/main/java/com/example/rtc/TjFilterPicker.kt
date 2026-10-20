package com.example.rtc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.threejunks.sdk.LibraryFilter
import kotlinx.coroutines.delay

/**
 * 3Jump filter picker (calls and lives), laid out like a camera app's effect panel: the chosen filter's name on the
 * video, then ⦸ (off) + category tabs, then a grid of preview images. Filters and images come from the Three Junks
 * filter library (3Jump Filter Studio → คลังฟิลเตอร์ 3Jump).
 */
@Composable
internal fun FilterPicker(modifier: Modifier = Modifier) {
    val list = TjFilters.list
    val sel = TjFilters.selectedId
    val cats = remember(list) { list.map { it.category.ifBlank { "ความงาม" } }.distinct() }
    var tab by remember { mutableStateOf(cats.firstOrNull { c -> list.any { it.id == sel && it.category.ifBlank { "ความงาม" } == c } } ?: cats.firstOrNull() ?: "") }
    if (tab !in cats && cats.isNotEmpty()) tab = cats.first()
    val shown = list.filter { it.category.ifBlank { "ความงาม" } == tab }
    Column(modifier.fillMaxWidth()) {
        FilterNamePill(list.firstOrNull { it.id == sel }?.name, Modifier.padding(start = 12.dp, bottom = 8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)).background(Color(0xE6121218))) {
            Row(Modifier.fillMaxWidth().padding(start = 10.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { TjFilters.select(null) }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Block, "ไม่ใช้ฟิลเตอร์", tint = if (sel == null) Color.White else Color(0x99FFFFFF), modifier = Modifier.size(26.dp))
                }
                LazyRow(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 6.dp)) {
                    items(cats) { c ->
                        Column(Modifier.clickable { tab = c }.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(c, color = if (c == tab) Color.White else Color(0x99FFFFFF), fontSize = 15.sp, fontWeight = if (c == tab) FontWeight.Bold else FontWeight.Normal)
                            Box(Modifier.padding(top = 6.dp).width(28.dp).height(2.dp).background(if (c == tab) Color.White else Color.Transparent))
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x22FFFFFF)))
            LazyVerticalGrid(GridCells.Fixed(5), Modifier.fillMaxWidth().heightIn(max = 250.dp),
                contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shown, key = { it.id }) { f -> FilterTile(f, f.id == sel) { TjFilters.select(f) } }
            }
        }
    }
}

@Composable
private fun FilterTile(f: LibraryFilter, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))
            .border(if (selected) 3.dp else 0.dp, if (selected) Color.White else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            if (f.thumbUrl != null) {
                AsyncImage(f.thumbUrl, f.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().padding(if (selected) 3.dp else 0.dp).clip(RoundedCornerShape(12.dp)))
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFFF7AA8), Color(0xFF7A5CFF)))), contentAlignment = Alignment.Center) {
                    Text(f.name.take(1), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(f.name, color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
    }
}

/** "✨ name" over the video while a filter is on (bright for a moment after it changes). */
@Composable
private fun FilterNamePill(name: String?, modifier: Modifier = Modifier) {
    if (name == null) return
    var fresh by remember(name) { mutableStateOf(true) }
    LaunchedEffect(name) { delay(2500); fresh = false }
    Row(modifier.clip(RoundedCornerShape(20.dp)).background(Color(if (fresh) 0xCC000000 else 0x66000000)).padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(16.dp))
        Text(name, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
    }
}
