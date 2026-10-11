package com.example.ui.social

import com.example.ui.components.tr
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.social.NearbyRepo
import com.example.social.NearbyUser
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/** The one app-wide search: real user search (brain /search/users) + hashtag post search. Runs only on submit. */
@Composable
fun GlobalSearchDialog(onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var users by remember { mutableStateOf<List<NearbyUser>?>(null) }
    var msg by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf<String?>(null) }
    var openUid by remember { mutableStateOf<String?>(null) }
    val fr = remember { FocusRequester() }
    LaunchedEffect(Unit) { fr.requestFocus() }
    val tagQ = q.trim().removePrefix("#").replace(Regex("\\s+"), "")
    fun run() {
        val t = q.trim(); if (t.isEmpty() || t.startsWith("#")) return
        busy = true; msg = ""
        scope.launch {
            val r = NearbyRepo.search(t, false, 0.0)
            users = r?.users ?: emptyList()
            msg = when { r == null -> tr("ค้นหาไม่สำเร็จ"); r.code == 429 -> tr("โควตาค้นหาวันนี้หมดแล้ว"); r.code != 200 -> tr("ค้นหาไม่สำเร็จ"); else -> "" }
            busy = false
        }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Slate950) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose) { Text("‹", color = White, fontSize = 26.sp) }
                    OutlinedTextField(value = q, onValueChange = { if (it.length <= 60) q = it }, singleLine = true, shape = RoundedCornerShape(18.dp),
                        placeholder = { Text(tr("ค้นหาเพื่อน หรือ #แฮชแท็ก…"), color = Slate400) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { if (q.trim().startsWith("#")) { if (tagQ.isNotEmpty()) tag = tagQ } else run() }),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White, focusedBorderColor = Pink500, unfocusedBorderColor = Pink500.copy(alpha = 0.6f)),
                        modifier = Modifier.weight(1f).focusRequester(fr))
                }
                Spacer(Modifier.height(12.dp))
                if (tagQ.isNotEmpty()) Surface(onClick = { tag = tagQ }, shape = RoundedCornerShape(14.dp), color = Slate900, border = BorderStroke(1.dp, Purple500.copy(alpha = 0.4f)), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("#", color = Pink400, fontSize = 22.sp, fontWeight = FontWeight.Black); Spacer(Modifier.width(10.dp))
                        Text("ดูโพสต์ที่มี #$tagQ", color = White, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("›", color = Slate300, fontSize = 20.sp)
                    }
                }
                if (q.isNotBlank() && !q.trim().startsWith("#")) TextButton(onClick = { run() }) { Text("🔍 ค้นหาผู้คนชื่อ \"${q.trim()}\"", color = Pink400) }
                if (busy) LinearProgressIndicator(color = Pink500, modifier = Modifier.fillMaxWidth())
                if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 13.sp)
                val u = users
                if (u != null && u.isEmpty() && msg.isBlank() && !busy) Text(tr("ไม่พบผู้ใช้"), color = Slate400, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                if (!u.isNullOrEmpty()) {
                    Text(tr("ผู้คน"), color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(u, key = { it.profile.uid }) { n ->
                            Surface(shape = RoundedCornerShape(14.dp), color = Slate900, modifier = Modifier.fillMaxWidth().clickable { openUid = n.profile.uid }) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(n.profile.avatar, n.profile.displayName, 44); Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(n.profile.displayName, color = White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (n.profile.bio.isNotBlank()) Text(n.profile.bio, color = Slate400, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text("›", color = Slate300, fontSize = 20.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    tag?.let { TagFeedDialog(it) { tag = null } }
    openUid?.let { UserProfileDialog(it) { openUid = null } }
}
