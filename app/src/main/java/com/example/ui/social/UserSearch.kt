package com.example.ui.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.BrainApi
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

data class FoundUser(val uid: String, val name: String, val avatar: String, val bio: String)

/** Search bar at the top of the friend-discovery page (real users via brain /search/users; quota-limited). */
@Composable
fun UserSearchBar(query: String, onQuery: (String) -> Unit, results: List<FoundUser>?, onResults: (List<FoundUser>?) -> Unit) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var msg by remember { mutableStateOf("") }
    var open by remember { mutableStateOf<String?>(null) }
    fun search() {
        val q = query.trim(); if (q.isEmpty()) return
        focus.clearFocus(); msg = "กำลังค้นหา…"
        scope.launch {
            val u = FirebaseAuth.getInstance().currentUser ?: run { msg = "กรุณาเข้าสู่ระบบ"; return@launch }
            val r = BrainApi.call(u, "GET", "/search/users?q=" + android.net.Uri.encode(q))
            val arr = r.json?.optJSONArray("users")
            if (r.code == 200 && arr != null) {
                onResults((0 until arr.length()).map { i -> arr.getJSONObject(i).let { FoundUser(it.optString("uid"), it.optString("displayName"), it.optString("avatar"), it.optString("bio")) } })
                msg = ""
            } else msg = if (r.code == 429) "โควตาค้นหาวันนี้หมดแล้ว" else "ค้นหาไม่สำเร็จ (${r.code})"
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        OutlinedTextField(value = query, onValueChange = { onQuery(it.take(50)); if (it.isBlank()) onResults(null) },
            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(24.dp),
            placeholder = { Text("🔍 ค้นหาเพื่อนจากชื่อ", color = Slate400) },
            trailingIcon = { if (query.isNotBlank()) TextButton(onClick = { onQuery(""); onResults(null); msg = "" }) { Text("ล้าง", color = Slate300) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { search() }),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = White, unfocusedTextColor = White))
        if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        results?.let { list ->
            if (list.isEmpty()) Text("ไม่พบผู้ใช้ชื่อนี้", color = Slate400, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            list.forEach { f ->
                Row(Modifier.fillMaxWidth().clickable { open = f.uid }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(f.avatar, f.name, 40)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name, color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (f.bio.isNotBlank()) Text(f.bio, color = Slate400, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    open?.let { UserProfileDialog(it) { open = null } }
}
