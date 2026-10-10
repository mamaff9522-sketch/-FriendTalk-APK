package com.example.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.BuildConfig
import com.example.network.BrainApi
import com.example.ui.theme.*
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewarded.ServerSideVerificationOptions
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

val FEATURE_LABELS = linkedMapOf(
    "aiBotMessagesPerDay" to "คุยกับ AI", "radarPerDay" to "เรดาร์", "shakePerDay" to "เขย่าเจอ",
    "randomMatchPerDay" to "สุ่มคุย", "searchPerDay" to "ค้นหา", "personalityMatchPerDay" to "จับคู่นิสัย", "chatMessagesPerDay" to "แชทกับคน"
)

data class FeatureQuota(val limit: Int, val used: Int, val remaining: Int?, val unlimited: Boolean)
data class Limits(
    val tier: String, val features: Map<String, FeatureQuota>, val adsWatched: Int, val adsMax: Int,
    val perAd: Map<String, Int>, val showAds: Boolean, val rewardEnabled: Boolean, val bannerEnabled: Boolean,
    val interstitialEnabled: Boolean, val interstitialEverySwipes: Int, val ssvRequired: Boolean,
    /** canPost to reason */
    val posting: Pair<Boolean, String> = true to ""
)

/** Server limits (/limits) + Ad Room navigation state. All numbers come from the server. */
object AdRoom {
    /** Feature whose quota just ran out (set by BrainApi on 429 {code:'quota'}). */
    val quotaHit = MutableStateFlow<String?>(null)
    /** Non-null = Ad Room open, value = feature to reward. Closing returns to the screen underneath unchanged. */
    val open = MutableStateFlow<String?>(null)
    val limits = MutableStateFlow<Limits?>(null)

    suspend fun refresh(): Limits? {
        val u = FirebaseAuth.getInstance().currentUser ?: return null
        val r = BrainApi.call(u, "GET", "/limits")
        val j = r.json?.takeIf { r.code == 200 } ?: return null
        return parse(j).also { limits.value = it }
    }
    fun parse(j: JSONObject): Limits {
        val f = j.optJSONObject("features") ?: JSONObject()
        val fm = f.keys().asSequence().associateWith { k -> f.getJSONObject(k).let {
            FeatureQuota(it.optInt("limit"), it.optInt("used"), if (it.isNull("remaining")) null else it.optInt("remaining"), it.optBoolean("unlimited"))
        } }
        val a = j.optJSONObject("ads") ?: JSONObject()
        val per = a.optJSONObject("perAd") ?: JSONObject()
        return Limits(j.optString("tier"), fm, a.optInt("watched"), a.optInt("max"),
            per.keys().asSequence().associateWith { per.optInt(it) }, a.optBoolean("showAds"), a.optBoolean("rewardEnabled"),
            a.optBoolean("bannerEnabled"), a.optBoolean("interstitialEnabled"), a.optInt("interstitialEverySwipes"), a.optBoolean("ssvRequired"),
            j.optJSONObject("posting")?.let { it.optBoolean("canPost", true) to it.optString("reason") } ?: (true to ""))
    }
    /** Returns null on success, else a Thai error message. */
    suspend fun claimReward(feature: String): String? {
        val u = FirebaseAuth.getInstance().currentUser ?: return "กรุณาเข้าสู่ระบบ"
        val r = BrainApi.call(u, "POST", "/quota/ad-reward", JSONObject().put("feature", feature))
        return when {
            r.code == 200 -> { r.json?.let { limits.value = parse(it) }; null }
            r.code == 202 -> { delay(3000); refresh(); null } // credited by AdMob SSV callback
            r.json?.optString("code") == "ad_cap" -> "ดูโฆษณาครบจำนวนของวันนี้แล้ว"
            r.json?.optString("code") == "ad_rate" -> "รอสักครู่ก่อนดูโฆษณาถัดไป"
            r.code == 403 -> "ระบบโฆษณาปิดอยู่"
            else -> "ให้รางวัลไม่สำเร็จ (${r.code})"
        }
    }
}

private fun Context.activity(): Activity? { var c: Context? = this; while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }; return null }

object AdsManager {
    @Volatile private var started = false
    /** UMP consent (required in EEA, no-op elsewhere) then MobileAds.initialize. */
    fun init(activity: Activity) {
        if (started) return; started = true
        val ci = UserMessagingPlatform.getConsentInformation(activity)
        ci.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ -> if (ci.canRequestAds()) MobileAds.initialize(activity) {} }
        }, { _ -> MobileAds.initialize(activity) {} })
        if (ci.canRequestAds()) MobileAds.initialize(activity) {}
    }

    /** Loads + shows a rewarded ad. Returns true if the user earned the reward. */
    suspend fun showRewarded(activity: Activity, feature: String): Boolean {
        val ad = suspendCancellableCoroutine<RewardedAd?> { c ->
            RewardedAd.load(activity, BuildConfig.ADMOB_REWARDED_ID, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
                override fun onAdLoaded(a: RewardedAd) { c.resume(a) }
                override fun onAdFailedToLoad(e: LoadAdError) { c.resume(null) }
            })
        } ?: return false
        FirebaseAuth.getInstance().currentUser?.uid?.let {
            ad.setServerSideVerificationOptions(ServerSideVerificationOptions.Builder().setUserId(it).setCustomData(feature).build())
        }
        return suspendCancellableCoroutine { c ->
            var earned = false
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() { if (c.isActive) c.resume(earned) }
                override fun onAdFailedToShowFullScreenContent(e: AdError) { if (c.isActive) c.resume(false) }
            }
            ad.show(activity) { earned = true }
        }
    }

    private var swipeCount = 0
    /** Call on every swipe; shows an interstitial every N swipes if the admin enabled it (never for VIP). */
    fun onSwipe(activity: Activity?) {
        val l = AdRoom.limits.value ?: return
        if (activity == null || !l.showAds || !l.interstitialEnabled || l.interstitialEverySwipes <= 0) return
        if (++swipeCount % l.interstitialEverySwipes != 0) return
        InterstitialAd.load(activity, BuildConfig.ADMOB_INTERSTITIAL_ID, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(a: InterstitialAd) { a.show(activity) }
        })
    }
}

/** Small banner for the bottom of the Feed only. Hidden for VIP / when disabled by admin. */
@Composable
fun FeedBanner(modifier: Modifier = Modifier) {
    val l by AdRoom.limits.collectAsState()
    val lim = l ?: return
    if (!lim.showAds || !lim.bannerEnabled) return
    AndroidView(modifier = modifier.fillMaxWidth(), factory = { ctx ->
        AdView(ctx).apply { setAdSize(AdSize.BANNER); adUnitId = BuildConfig.ADMOB_BANNER_ID; loadAd(AdRequest.Builder().build()) }
    })
}

/** Quota chip (e.g. in a chat header) that opens the Ad Room before the quota runs out. */
@Composable
fun QuotaChip(feature: String) {
    val l by AdRoom.limits.collectAsState()
    LaunchedEffect(Unit) { AdRoom.refresh() }
    val q = l?.features?.get(feature)
    AssistChip(onClick = { AdRoom.open.value = feature },
        label = { Text(if (q == null || q.unlimited) "🎁" else "🎁 ${q.remaining ?: 0}/${q.limit}", fontSize = 11.sp) })
}

/** Global host: quota-exhausted dialog + Ad Room. Put once at the app root. */
@Composable
fun AdRoomHost() {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { ctx.activity()?.let { AdsManager.init(it) }; AdRoom.refresh() }
    val hit by AdRoom.quotaHit.collectAsState()
    val open by AdRoom.open.collectAsState()
    hit?.let { f ->
        AlertDialog(onDismissRequest = { AdRoom.quotaHit.value = null },
            title = { Text("โควตาหมดแล้ว") },
            text = { Text(if (f == "aiBotMessagesPerDay") "โควตาคุยกับ AI วันนี้หมดแล้ว 😅 ข้อความที่พิมพ์ไว้ยังอยู่ครบ เติมโควตาแล้วกดส่งได้เลย"
                          else "โควตา${FEATURE_LABELS[f] ?: f}ของวันนี้หมดแล้ว 😅 ดูโฆษณาเพื่อรับเพิ่ม หรือรอพรุ่งนี้") },
            confirmButton = { TextButton(onClick = { AdRoom.quotaHit.value = null; AdRoom.open.value = f }) { Text("ดูโฆษณา +โควตา") } },
            dismissButton = { TextButton(onClick = { AdRoom.quotaHit.value = null; AdRoom.open.value = f }) { Text("VIP") } })
    }
    open?.let { f -> AdRoomScreen(f) { AdRoom.open.value = null } }
}

@Composable
fun AdRoomScreen(feature: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val l by AdRoom.limits.collectAsState()
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var vipInfo by remember { mutableStateOf(false) }
    var target by remember(feature) { mutableStateOf(feature) }
    LaunchedEffect(Unit) { AdRoom.refresh() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = Slate950) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🎁 ห้องโฆษณา", color = White, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("กลับ", color = Slate300) }
                }
                val lim = l
                if (lim == null) { CircularProgressIndicator(color = Pink500); return@Column }
                Text(if (lim.tier == "vip") "สถานะ: VIP ⭐ (ไม่มีโฆษณา)" else "สถานะ: ฟรี", color = Slate300, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text("โควตาวันนี้", color = White, fontWeight = FontWeight.Bold)
                FEATURE_LABELS.forEach { (k, label) ->
                    val q = lim.features[k] ?: return@forEach
                    Surface(shape = RoundedCornerShape(10.dp), color = if (k == target) Slate700 else Slate800,
                        onClick = { target = k }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Row(Modifier.padding(10.dp)) {
                            Text(label, color = White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(if (q.unlimited) "ไม่จำกัด" else "เหลือ ${q.remaining} / ${q.limit}", color = if (q.remaining == 0) Rose500 else Emerald400, fontSize = 13.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (lim.showAds && lim.rewardEnabled) {
                    Text("ดูโฆษณาวันนี้ ${lim.adsWatched} / ${lim.adsMax}", color = Slate300, fontSize = 13.sp)
                    val n = lim.perAd[target] ?: 0
                    Button(enabled = !busy && n > 0 && lim.adsWatched < lim.adsMax, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Pink500), onClick = {
                            val act = ctx.activity() ?: return@Button
                            busy = true; msg = ""
                            scope.launch {
                                val earned = AdsManager.showRewarded(act, target)
                                if (!earned) { msg = "ยังไม่ได้รับรางวัล (ดูโฆษณาไม่จบ หรือโหลดโฆษณาไม่ได้)"; busy = false; return@launch }
                                val err = AdRoom.claimReward(target)
                                busy = false
                                if (err == null) onClose() // back to where the user was (draft kept)
                                else msg = err
                            }
                        }) { Text(if (busy) "กำลังโหลดโฆษณา…" else "ดูโฆษณา +$n ${FEATURE_LABELS[target] ?: ""}") }
                } else if (lim.tier != "vip") Text("ตอนนี้ยังไม่เปิดให้ดูโฆษณารับโควตา", color = Slate400, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { vipInfo = true }, modifier = Modifier.fillMaxWidth()) { Text("⭐ สมัคร VIP", color = Amber400) }
                if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
    if (vipInfo) AlertDialog(onDismissRequest = { vipInfo = false }, title = { Text("VIP เร็ว ๆ นี้") },
        text = { Text("ยังไม่เปิดให้ซื้อ VIP ในแอป หากต้องการ VIP กรุณาติดต่อแอดมิน") },
        confirmButton = { TextButton(onClick = { vipInfo = false }) { Text("ตกลง") } })
}
