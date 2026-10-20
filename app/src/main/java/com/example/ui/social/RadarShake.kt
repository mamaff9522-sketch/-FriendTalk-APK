package com.example.ui.social

import com.example.ui.components.tr
import com.example.ui.components.dialogNavPadding
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import com.example.ui.components.ft.Text
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
internal fun RadiusPicker(radius: Double, onPick: (Double) -> Unit) {
    // Slider over the allowed steps (0.5 … 50 km); snaps to each step, saves when released.
    val steps = NearbyRepo.RADII
    var idx by remember(radius) { mutableStateOf(steps.indexOf(radius).coerceAtLeast(0).toFloat()) }
    val cur = steps[idx.toInt().coerceIn(0, steps.lastIndex)]
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("ระยะค้นหา · ใช้ร่วมกันทั้งเรดาร์และเขย่า"), color = Slate300, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text(NearbyRepo.radiusLabel(cur), color = White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Slider(value = idx, onValueChange = { idx = Math.round(it).toFloat() }, valueRange = 0f..steps.lastIndex.toFloat(), steps = (steps.size - 2).coerceAtLeast(0),
            onValueChangeFinished = { if (cur != radius) onPick(cur) },
            colors = SliderDefaults.colors(thumbColor = Pink500, activeTrackColor = Pink500, inactiveTrackColor = AppThemeMode.divider,
                activeTickColor = androidx.compose.ui.graphics.Color.Transparent, inactiveTickColor = androidx.compose.ui.graphics.Color.Transparent))
        Row { Text(NearbyRepo.radiusLabel(steps.first()), color = Slate400, fontSize = 10.sp, modifier = Modifier.weight(1f)); Text(NearbyRepo.radiusLabel(steps.last()), color = Slate400, fontSize = 10.sp) }
    }
}

@Composable
internal fun rememberRadius(): MutableState<Double> {
    val ctx = LocalContext.current
    return remember { mutableStateOf(NearbyRepo.loadRadius(ctx)) }
}

@Composable
internal fun LocationPermissionCard(onGranted: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.any { it }) onGranted()
    }
    Surface(shape = RoundedCornerShape(16.dp), color = Slate800, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(tr("ขออนุญาตใช้ตำแหน่ง 📍"), color = White, fontWeight = FontWeight.Bold)
            Text("FriendTalk ใช้ตำแหน่งเพื่อหาเพื่อนที่อยู่ใกล้คุณเท่านั้น เซิร์ฟเวอร์เก็บแค่ตำแหน่งโดยประมาณ (~100 ม.) " +
                "และไม่แสดงพิกัดจริงให้ใครเห็น คนอื่นจะเห็นแค่ระยะห่างคร่าว ๆ คุณปิดการมองเห็นได้ทุกเมื่อ " +
                "หากไม่อนุญาต จะไม่มีการส่งตำแหน่งใด ๆ", color = Slate300, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
            Button(onClick = { launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)) },
                colors = ButtonDefaults.buttonColors(containerColor = Pink500)) { Text(tr("อนุญาตตำแหน่ง")) }
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
                    TextButton(onClick = { scope.launch { val c = NearbyRepo.respond(r.profile.uid, true); reqs = NearbyRepo.requests(); if (!c.isNullOrBlank()) SocialRepo.openChat.value = c } }) { Text(tr("ยอมรับ"), color = Emerald400) }
                    TextButton(onClick = { scope.launch { NearbyRepo.respond(r.profile.uid, false); reqs = NearbyRepo.requests() } }) { Text(tr("ปฏิเสธ"), color = Slate400) }
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
            if (loc == null) { msg = tr("หาตำแหน่งไม่ได้ (เปิด GPS/ตำแหน่งในเครื่องก่อน)"); busy = false; return@launch }
            if (!NearbyRepo.shareLocation(loc.latitude, loc.longitude)) { msg = tr("ส่งตำแหน่งไม่สำเร็จ (ยืนยันอีเมลแล้วหรือยัง?)"); busy = false; return@launch }
            visible = true
            users = NearbyRepo.nearby(radius) ?: run { msg = tr("โหลดไม่สำเร็จ"); null }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        FriendRequestsCard()
        if (!granted) { LocationPermissionCard { granted = true }; return@Column }
        RadiusPicker(radius) { radius = it; NearbyRepo.saveRadius(ctx, it); if (visible) scan() }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (visible) tr("คนอื่นมองเห็นคุณบนเรดาร์") else tr("คุณซ่อนอยู่ (ยังไม่ส่งตำแหน่ง)"), color = White, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Switch(checked = visible, onCheckedChange = { on ->
                if (on) scan() else scope.launch { NearbyRepo.hide(); visible = false; users = null }
            })
        }
        RadarPulse(users.orEmpty(), busy) { selected = it }
        Button(onClick = { scan() }, enabled = !busy, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Pink500)) {
            Text(if (busy) tr("กำลังสแกน…") else tr("สแกนหาเพื่อนใกล้ ๆ"))
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
                        Spacer(Modifier.width(6.dp)); GiftToUserButton(u.profile.uid, compact = true)
                    }
                }
            }
            // Launch period: while few real people are nearby, offer AI characters to chat with (always labelled AI, never shown with a distance).
            if (list.size < AI_FILL_BELOW) AiFillSection(seed = list.size)
        }
    }
    selected?.let { UserProfileDialog(it.profile.uid, it.distance) { selected = null } }
}

/** Show AI characters when the radar finds fewer real people than this. */
private const val AI_FILL_BELOW = 3

@Composable
private fun AiFillSection(seed: Int) {
    val bots by produceState<List<com.example.network.AiRepo.AiBot>?>(null, seed) {
        value = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.let { com.example.network.AiRepo.bots(it).shuffled().take(3) } ?: emptyList()
    }
    var chat by remember { mutableStateOf<com.example.network.AiRepo.AiBot?>(null) }
    val l = bots ?: return
    if (l.isEmpty()) return
    Text(tr("ระหว่างรอเพื่อนใหม่ — คุยกับตัวละคร AI ได้"), color = Slate300, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
    l.forEach { b ->
        Surface(shape = RoundedCornerShape(12.dp), color = Slate800, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clickable { chat = b }) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(b.avatarUrl, b.name, 38); Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(b.name, color = White, maxLines = 1, modifier = Modifier.weight(1f, fill = false)); Spacer(Modifier.width(6.dp)); com.example.ui.ai.AiBadge()
                    }
                    if (b.bio.isNotBlank()) Text(b.bio, color = Slate400, fontSize = 11.sp, maxLines = 1)
                }
                Text(tr("คุยเลย ›"), color = Pink400, fontSize = 12.sp)
            }
        }
    }
    chat?.let { b ->
        Dialog(onDismissRequest = { chat = null }, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize(), color = Slate950) {
                Box(Modifier.fillMaxSize().systemBarsPadding().dialogNavPadding().imePadding()) {
                    com.example.ui.ai.BotChatScreen(bot = b, onEnd = {
                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.let { fu -> kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { com.example.network.AiRepo.endBotChat(fu, b.id) } }
                        chat = null
                    }, onSkip = { chat = null })
                }
            }
        }
    }
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
    var anonRoom by remember { mutableStateOf<String?>(null) } // shake match → anonymous 60 s chat
    var showProfile by remember { mutableStateOf(false) }
    val detector = remember { ShakeDetector() }
    val fx = remember { ShakeFeedback(ctx) }
    DisposableEffect(Unit) { onDispose { fx.release() } }
    var motion by remember { mutableStateOf(0f) }
    val burst = remember { Animatable(0f) }
    var burstStrength by remember { mutableStateOf(0.5f) }

    fun onShake() {
        burstStrength = motion.coerceIn(0.3f, 1f)
        fx.trigger(burstStrength)
        scope.launch { burst.snapTo(1f); burst.animateTo(0f, tween(1100, easing = LinearOutSlowInEasing)) }
        if (state == "searching") return
        state = "searching"; found = null
        scope.launch {
            val loc = if (granted) currentLocation(ctx) else null
            val r = NearbyRepo.shake(loc?.latitude, loc?.longitude, radius)
            if (r == null) { state = "error"; return@launch }
            if (r.status == "matched") { if (r.room.isNotBlank()) anonRoom = r.room else found = r.user; state = "matched"; return@launch }
            val end = System.currentTimeMillis() + 16_000
            while (System.currentTimeMillis() < end) {
                delay(1500)
                val s = NearbyRepo.shakeResult() ?: continue
                if (s.status == "matched") { if (s.room.isNotBlank()) anonRoom = s.room else found = s.user; state = "matched"; return@launch }
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
                val g = kotlin.math.sqrt((e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2]).toDouble()).toFloat() / 9.80665f
                val target = ((g - 1.25f) / 1.75f).coerceIn(0f, 1f)
                motion = if (target > motion) target else motion * 0.9f + target * 0.1f
                fx.pulse(motion)
                if (detector.onSample(e.values[0], e.values[1], e.values[2], e.timestamp / 1_000_000)) onShake()
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        var registered = false
        fun reg() { if (listening && sensor != null && !registered) { detector.reset(); sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_GAME); registered = true } }
        fun unreg() { if (registered) { sm.unregisterListener(l); registered = false; motion = 0f } }
        val obs = LifecycleEventObserver { _, ev -> when (ev) { Lifecycle.Event.ON_RESUME -> reg(); Lifecycle.Event.ON_PAUSE -> unreg(); else -> {} } }
        lifecycle.addObserver(obs)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) reg()
        onDispose { lifecycle.removeObserver(obs); unreg() }
    }

    val grad = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.Color(0xFFFF2FA8), androidx.compose.ui.graphics.Color(0xFF9B3DFF)))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // hero: dark gradient stage with the animated shake orb
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFF140F35), androidx.compose.ui.graphics.Color(0xFF2B1660), androidx.compose.ui.graphics.Color(0xFF5A1A6E))))) {
            Column(Modifier.fillMaxWidth().padding(vertical = 20.dp, horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tr("เขย่าเจอเพื่อน"), color = PureWhite, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Text(tr("เขย่าพร้อมกัน แล้วคุยแบบนิรนาม 60 วินาที"), color = PureWhite.copy(alpha = 0.75f), fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                Spacer(Modifier.height(18.dp))
                ShakeOrb(level = if (listening) motion else 0f, burst = burst.value, active = listening || state == "searching", matched = state == "matched")
                Spacer(Modifier.height(16.dp))
                // status chip
                val (dot, label) = when (state) {
                    "searching" -> androidx.compose.ui.graphics.Color(0xFFFFC94D) to tr("กำลังหาคนที่เขย่าพร้อมกัน…")
                    "timeout" -> androidx.compose.ui.graphics.Color(0xFFFF8A8A) to tr("ไม่พบใคร ลองเขย่าอีกครั้ง")
                    "error" -> androidx.compose.ui.graphics.Color(0xFFFF5D73) to tr("เชื่อมต่อไม่สำเร็จ (ยืนยันอีเมลแล้วหรือยัง?)")
                    "matched" -> androidx.compose.ui.graphics.Color(0xFF34D399) to tr("เจอแล้ว! 🎉")
                    else -> if (listening) androidx.compose.ui.graphics.Color(0xFF34D399) to tr("พร้อมแล้ว — เขย่าได้เลย") else androidx.compose.ui.graphics.Color(0xFF9A90C2) to tr("ยังไม่ได้เปิดรับการเขย่า")
                }
                Row(Modifier.clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.Color(0x26FFFFFF)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(androidx.compose.foundation.shape.CircleShape).background(dot)); Spacer(Modifier.width(8.dp))
                    Text(label, color = PureWhite, fontSize = 13.sp)
                    if (state == "searching") { Spacer(Modifier.width(8.dp)); CircularProgressIndicator(color = PureWhite, strokeWidth = 2.dp, modifier = Modifier.size(14.dp)) }
                }
                Spacer(Modifier.height(16.dp))
                // This button only toggles the sensor listener; it never starts a search by itself.
                Box(Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(16.dp))
                    .then(if (listening) Modifier.background(androidx.compose.ui.graphics.Color(0x33FFFFFF)) else Modifier.background(grad))
                    .clickable { listening = !listening; if (!listening && state != "searching") state = "idle" }, contentAlignment = Alignment.Center) {
                    Text(if (listening) tr("หยุดรับการเขย่า") else tr("เริ่มรับการเขย่า"), color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
        anonRoom?.let { room -> AnonShakeChatDialog(room) { anonRoom = null; state = "idle" } }
        // match result (older server: reveals the person directly)
        found?.let { p ->
            Column(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(AppThemeMode.cardSolid)
                .border(1.5.dp, grad, RoundedCornerShape(22.dp)).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clip(androidx.compose.foundation.shape.CircleShape).background(grad).padding(3.dp)) { Avatar(p.avatar, p.displayName, 80) }
                Text(p.displayName, color = AppThemeMode.text, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
                Text(tr("เขย่าพร้อมกับคุณ"), color = AppThemeMode.muted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(14.dp)).background(grad).clickable { showProfile = true }.padding(horizontal = 18.dp, vertical = 11.dp)) {
                        Text(tr("ดูโปรไฟล์ · เริ่มแชท"), color = PureWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    GiftToUserButton(p.uid)
                }
            }
            if (showProfile) UserProfileDialog(p.uid, null) { showProfile = false }
        }
        // distance
        Column(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(AppThemeMode.cardSolid)
            .border(1.dp, AppThemeMode.divider, RoundedCornerShape(20.dp)).padding(vertical = 12.dp, horizontal = 8.dp)) {
            Text(tr("ระยะจับคู่"), color = AppThemeMode.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
            RadiusPicker(radius) { radius = it; NearbyRepo.saveRadius(ctx, it) }
            if (!granted) {
                Text(tr("ยังไม่อนุญาตตำแหน่ง: จะจับคู่กับคนที่เขย่าโดยไม่เปิดตำแหน่งเหมือนกัน (ที่ไหนก็ได้)"),
                    color = Amber400, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp))
                LocationPermissionCard { granted = true }
            }
        }
        // how it works
        Row(Modifier.padding(top = 14.dp, bottom = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("1" to tr("กดเริ่มรับการเขย่า"), "2" to tr("เขย่าแรง ๆ 2–3 ครั้ง"), "3" to tr("คุยนิรนาม กดหัวใจตรงกันเพื่อรู้จักกัน")).forEach { (n, t) ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(AppThemeMode.cardSolid).border(1.dp, AppThemeMode.divider, RoundedCornerShape(16.dp))
                    .padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(24.dp).clip(androidx.compose.foundation.shape.CircleShape).background(grad), contentAlignment = Alignment.Center) { Text(n, color = PureWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    Text(t, color = AppThemeMode.text, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

/** Glowing gradient orb with a vibration icon: wiggles with the phone's motion, pulses rings while listening, bursts on a shake. */
@Composable
private fun ShakeOrb(level: Float, burst: Float, active: Boolean, matched: Boolean) {
    val t = rememberInfiniteTransition(label = "orb")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "pulse")
    val wiggle = (if (active) level * 14f else 0f) * kotlin.math.sin(pulse * 40f)
    Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2); val r0 = size.minDimension * 0.27f
            // soft glow
            drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(androidx.compose.ui.graphics.Color(0x66FF2FA8), androidx.compose.ui.graphics.Color.Transparent), c, size.minDimension / 2), size.minDimension / 2, c)
            if (active) for (k in 0 until 3) {
                val f = (pulse + k / 3f) % 1f
                drawCircle(androidx.compose.ui.graphics.Color.White.copy(alpha = (1f - f) * 0.35f), r0 + f * size.minDimension * 0.22f, c, style = Stroke(2.dp.toPx()))
            }
            if (burst > 0f) drawCircle(androidx.compose.ui.graphics.Color(0xFFFFC94D).copy(alpha = burst * 0.6f), r0 + (1f - burst) * size.minDimension * 0.25f, c, style = Stroke(4.dp.toPx()))
        }
        Box(Modifier.size(104.dp).graphicsRotation(wiggle).clip(androidx.compose.foundation.shape.CircleShape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFFFF4FB8), androidx.compose.ui.graphics.Color(0xFF7B3DFF)))),
            contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(if (matched) androidx.compose.material.icons.Icons.Rounded.Favorite else androidx.compose.material.icons.Icons.Rounded.Vibration,
                null, tint = PureWhite, modifier = Modifier.size(50.dp))
        }
    }
}

private fun Modifier.graphicsRotation(deg: Float): Modifier = this.then(Modifier.graphicsLayer { rotationZ = deg })
