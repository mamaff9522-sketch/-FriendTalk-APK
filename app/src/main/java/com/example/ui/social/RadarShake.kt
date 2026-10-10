package com.example.ui.social

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.social.NearbyRepo
import com.example.social.NearbyUser
import com.example.social.RealProfile
import com.example.social.ShakeDetector
import com.example.social.SocialRepo
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.cos
import kotlin.math.sin

internal fun hasLocPerm(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

@SuppressLint("MissingPermission")
internal suspend fun currentLocation(ctx: Context): Location? {
    if (!hasLocPerm(ctx)) return null
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
    val fresh = suspendCancellableCoroutine<Location?> { c ->
        runCatching {
            LocationManagerCompat.getCurrentLocation(lm, provider, null as androidx.core.os.CancellationSignal?, ContextCompat.getMainExecutor(ctx)) { c.resume(it) }
        }.onFailure { c.resume(null) }
    }
    return fresh ?: runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
}

@Composable
private fun RadiusPicker(radius: Double, onPick: (Double) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        NearbyRepo.RADII.forEach { r ->
            FilterChip(selected = r == radius, onClick = { onPick(r) }, label = { Text(NearbyRepo.radiusLabel(r), fontSize = 12.sp) })
        }
    }
}

@Composable
private fun rememberRadius(): MutableState<Double> {
    val ctx = LocalContext.current
    return remember { mutableStateOf(NearbyRepo.loadRadius(ctx)) }
}

@Composable
private fun LocationPermissionCard(onGranted: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) onGranted()
    }
    Surface(shape = RoundedCornerShape(16.dp), color = Slate800, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("ขออนุญาตใช้ตำแหน่ง 📍", color = White, fontWeight = FontWeight.Bold)
            Text("FriendTalk ใช้ตำแหน่งเพื่อหาเพื่อนที่อยู่ใกล้คุณเท่านั้น เซิร์ฟเวอร์เก็บแค่ตำแหน่งโดยประมาณ (~100 ม.) " +
                "และไม่แสดงพิกัดจริงให้ใครเห็น คนอื่นจะเห็นแค่ระยะห่างคร่าว ๆ คุณปิดการมองเห็นได้ทุกเมื่อ " +
                "หากไม่อนุญาต จะไม่มีการส่งตำแหน่งใด ๆ", color = Slate300, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
            Button(onClick = { launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) },
                colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text("อนุญาตตำแหน่ง") }
        }
    }
}

@Composable
private fun ProfileDialog(p: RealProfile, distance: String?, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = Slate900) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(p.avatar, p.displayName, 84)
                Text(p.displayName + (p.age?.let { ", $it" } ?: ""), color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
                distance?.let { Text("ห่างประมาณ $it", color = Pink400, fontSize = 12.sp) }
                if (p.bio.isNotBlank()) Text(p.bio, color = Slate300, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                if (p.interests.isNotEmpty()) Text(p.interests.joinToString(" · "), color = Slate400, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scope.launch {
                        msg = when (NearbyRepo.requestFriend(p.uid)) { "requested" -> "ส่งคำขอเป็นเพื่อนแล้ว"; "friends" -> "เป็นเพื่อนกันแล้ว!"; "already_friends" -> "เป็นเพื่อนกันอยู่แล้ว"; else -> "ส่งคำขอไม่สำเร็จ" }
                    } }, colors = ButtonDefaults.buttonColors(containerColor = Purple600)) { Text("เพิ่มเพื่อน") }
                    Button(onClick = { scope.launch {
                        val cid = SocialRepo.openChatWith(p.uid)
                        if (cid != null) { onDismiss(); SocialRepo.openChat.value = cid } else msg = SocialRepo.lastOpenError.ifBlank { "เปิดแชทไม่สำเร็จ" }
                    } }, colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text("เริ่มแชท") }
                }
                if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                TextButton(onClick = onDismiss) { Text("ปิด", color = Slate400) }
            }
        }
    }
}

/** Incoming friend requests (accept / decline). */
@Composable
fun FriendRequestsCard() {
    val scope = rememberCoroutineScope()
    var reqs by remember { mutableStateOf<List<com.example.social.FriendRequest>>(emptyList()) }
    LaunchedEffect(Unit) { reqs = NearbyRepo.requests() }
    if (reqs.isEmpty()) return
    Surface(shape = RoundedCornerShape(14.dp), color = Slate800, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(10.dp)) {
            Text("คำขอเป็นเพื่อน (${reqs.size})", color = White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            reqs.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Avatar(r.profile.avatar, r.profile.displayName, 32); Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.profile.displayName, color = White, fontSize = 13.sp)
                        r.similarity?.let { SharedInterests(it) }
                    }
                    TextButton(onClick = { scope.launch { val c = NearbyRepo.respond(r.profile.uid, true); reqs = NearbyRepo.requests(); if (!c.isNullOrBlank()) SocialRepo.openChat.value = c } }) { Text("ยอมรับ", color = Emerald400) }
                    TextButton(onClick = { scope.launch { NearbyRepo.respond(r.profile.uid, false); reqs = NearbyRepo.requests() } }) { Text("ปฏิเสธ", color = Slate400) }
                }
            }
        }
    }
}

/** Real friend radar: coarse location -> brain; shows nearby real users with rounded distance. */
@Composable
fun RealRadarView() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var granted by remember { mutableStateOf(hasLocPerm(ctx)) }
    var visible by remember { mutableStateOf(false) }
    var radius by rememberRadius()
    var users by remember { mutableStateOf<List<NearbyUser>?>(null) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<NearbyUser?>(null) }

    fun scan() {
        busy = true; msg = ""
        scope.launch {
            val loc = currentLocation(ctx)
            if (loc == null) { msg = "หาตำแหน่งไม่ได้ (เปิด GPS/ตำแหน่งในเครื่องก่อน)"; busy = false; return@launch }
            if (!NearbyRepo.shareLocation(loc.latitude, loc.longitude)) { msg = "ส่งตำแหน่งไม่สำเร็จ (ยืนยันอีเมลแล้วหรือยัง?)"; busy = false; return@launch }
            visible = true
            users = NearbyRepo.nearby(radius) ?: run { msg = "โหลดไม่สำเร็จ"; null }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        FriendRequestsCard()
        if (!granted) { LocationPermissionCard { granted = true }; return@Column }
        Text("ระยะค้นหา", color = Slate300, fontSize = 12.sp)
        RadiusPicker(radius) { radius = it; NearbyRepo.saveRadius(ctx, it); if (visible) scan() }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (visible) "คนอื่นมองเห็นคุณบนเรดาร์" else "คุณซ่อนอยู่ (ยังไม่ส่งตำแหน่ง)", color = White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(checked = visible, onCheckedChange = { on ->
                if (on) scan() else scope.launch { NearbyRepo.hide(); visible = false; users = null }
            })
        }
        RadarPulse(users.orEmpty(), busy) { selected = it }
        Button(onClick = { scan() }, enabled = !busy, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Pink500)) {
            Text(if (busy) "กำลังสแกน…" else "สแกนหาเพื่อนใกล้ ๆ")
        }
        if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp)
        users?.let { list ->
            if (list.isEmpty()) Text("ยังไม่พบใครในระยะ ${NearbyRepo.radiusLabel(radius)} (แสดงเฉพาะคนที่เปิดเรดาร์ใน 30 นาทีล่าสุด)", color = Slate400, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            list.forEach { u ->
                Surface(shape = RoundedCornerShape(12.dp), color = Slate800, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clickable { selected = u }) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(u.profile.avatar, u.profile.displayName, 38); Spacer(Modifier.width(8.dp))
                        Text(u.profile.displayName, color = White, modifier = Modifier.weight(1f))
                        Text(u.distance, color = Pink400, fontSize = 12.sp)
                    }
                }
            }
        }
    }
    selected?.let { UserProfileDialog(it.profile.uid, it.distance) { selected = null } }
}

@Composable
private fun RadarPulse(users: List<NearbyUser>, scanning: Boolean, onTap: (NearbyUser) -> Unit) {
    val t = rememberInfiniteTransition(label = "radar")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2000, easing = LinearEasing)), label = "p")
    Box(Modifier.fillMaxWidth().height(240.dp).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(220.dp)) {
            val c = Offset(size.width / 2, size.height / 2); val R = size.minDimension / 2
            for (i in 1..3) drawCircle(Slate700, R * i / 3, c, style = Stroke(1.dp.toPx()))
            drawCircle(Pink500.copy(alpha = (1 - pulse) * if (scanning) 0.8f else 0.4f), R * pulse, c, style = Stroke(3.dp.toPx()))
            drawCircle(Pink500, 6.dp.toPx(), c)
        }
        // Positions are only illustrative (ring by rank); real direction is never known by the client.
        users.take(8).forEachIndexed { i, u ->
            val ang = i * 2 * Math.PI / maxOf(1, minOf(8, users.size)); val rr = 50 + (i % 3) * 18
            Box(Modifier.offset((rr * cos(ang)).dp, (rr * sin(ang)).dp).clickable { onTap(u) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(u.profile.avatar, u.profile.displayName, 30)
                    Text(u.distance, color = White, fontSize = 8.sp)
                }
            }
        }
    }
}

/** Real shake-to-match. Search starts ONLY from a detected physical shake (no tap path). */
@Composable
fun RealShakeView() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var radius by rememberRadius()
    var granted by remember { mutableStateOf(hasLocPerm(ctx)) }
    var listening by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf("idle") } // idle, searching, matched, timeout, error
    var found by remember { mutableStateOf<RealProfile?>(null) }
    var showProfile by remember { mutableStateOf(false) }
    val detector = remember { ShakeDetector() }

    fun onShake() {
        if (state == "searching") return
        state = "searching"; found = null
        scope.launch {
            val loc = if (granted) currentLocation(ctx) else null
            val r = NearbyRepo.shake(loc?.latitude, loc?.longitude, radius)
            if (r == null) { state = "error"; return@launch }
            if (r.status == "matched") { found = r.user; state = "matched"; return@launch }
            val end = System.currentTimeMillis() + 16_000
            while (System.currentTimeMillis() < end) {
                delay(1500)
                val s = NearbyRepo.shakeResult() ?: continue
                if (s.status == "matched") { found = s.user; state = "matched"; return@launch }
                if (s.status == "timeout") break
            }
            state = "timeout"
        }
    }

    // Accelerometer only while the screen is resumed and listening is on.
    DisposableEffect(lifecycle, listening) {
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val l = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (detector.onSample(e.values[0], e.values[1], e.values[2], e.timestamp / 1_000_000)) onShake()
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        var registered = false
        fun reg() { if (listening && sensor != null && !registered) { detector.reset(); sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_GAME); registered = true } }
        fun unreg() { if (registered) { sm.unregisterListener(l); registered = false } }
        val obs = LifecycleEventObserver { _, ev -> when (ev) { Lifecycle.Event.ON_RESUME -> reg(); Lifecycle.Event.ON_PAUSE -> unreg(); else -> {} } }
        lifecycle.addObserver(obs)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) reg()
        onDispose { lifecycle.removeObserver(obs); unreg() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("📱 เขย่าเจอเพื่อน", color = White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text("เปิดโหมดรับการเขย่า แล้วเขย่าโทรศัพท์แรง ๆ 2–3 ครั้ง ระบบจะจับคู่กับคนที่เขย่าพร้อมกันภายใน 15 วินาที",
            color = Slate300, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp))
        Text("ระยะจับคู่", color = Slate300, fontSize = 12.sp)
        RadiusPicker(radius) { radius = it; NearbyRepo.saveRadius(ctx, it) }
        if (!granted) {
            Text("ยังไม่อนุญาตตำแหน่ง: ตัวกรองระยะใช้ไม่ได้ ระบบจะจับคู่กับคนที่เขย่าโดยไม่เปิดตำแหน่งเหมือนกัน (ที่ไหนก็ได้)",
                color = Amber400, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
            LocationPermissionCard { granted = true }
        }
        Spacer(Modifier.height(12.dp))
        // This button only toggles the sensor listener; it never starts a search by itself.
        Button(onClick = { listening = !listening; if (!listening && state != "searching") state = "idle" },
            colors = ButtonDefaults.buttonColors(containerColor = if (listening) Slate700 else Pink500)) {
            Text(if (listening) "หยุดรับการเขย่า" else "เริ่มรับการเขย่า")
        }
        Spacer(Modifier.height(12.dp))
        Text(when (state) {
            "searching" -> "กำลังหาคนที่เขย่าพร้อมกัน… (สูงสุด ~15 วินาที)"
            "timeout" -> "ไม่พบใครเขย่าพร้อมกัน ลองเขย่าใหม่"
            "error" -> "เชื่อมต่อไม่สำเร็จ (ยืนยันอีเมลแล้วหรือยัง?)"
            "matched" -> "เจอแล้ว! 🎉"
            else -> if (listening) "พร้อมแล้ว — เขย่าได้เลย" else "กด 'เริ่มรับการเขย่า' ก่อน"
        }, color = if (state == "matched") Emerald400 else Slate300, fontSize = 13.sp)
        if (state == "searching") CircularProgressIndicator(color = Pink500, modifier = Modifier.padding(12.dp))
        found?.let { p ->
            Surface(shape = RoundedCornerShape(16.dp), color = Slate800, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(p.avatar, p.displayName, 72)
                    Text(p.displayName, color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 6.dp))
                    TextButton(onClick = { showProfile = true }) { Text("ดูโปรไฟล์ / เพิ่มเพื่อน / เริ่มแชท", color = Pink400) }
                }
            }
            if (showProfile) UserProfileDialog(p.uid, null) { showProfile = false }
        }
    }
}
