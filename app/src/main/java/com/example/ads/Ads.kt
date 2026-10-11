package com.example.ads

import com.example.ui.components.tr
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
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
    "aiBotMessagesPerDay" to tr("คุยกับ AI"), "radarPerDay" to tr("เรดาร์"), "shakePerDay" to tr("เขย่าเจอ"),
    "randomMatchPerDay" to tr("สุ่มคุย"), "searchPerDay" to tr("ค้นหา"), "personalityMatchPerDay" to tr("จับคู่นิสัย"), "chatMessagesPerDay" to tr("แชทกับคน")
)

/** Admin-configured ad offer (AdMob picks the creative; we pick format, est. duration label and reward). */
data class AdOffer(val id: String, val title: String, val adFormat: String, val durationLabel: String, val description: String,
                   val validityHours: Int, val feature: String, val amount: Int)

fun rewardText(feature: String, n: Int) = when (feature) {
    "aiBotMessagesPerDay" -> "คุยกับ AI ได้เพิ่ม $n ข้อความ"
    "chatMessagesPerDay" -> "แชทกับคนได้เพิ่ม $n ข้อความ"
    "radarPerDay" -> "ใช้เรดาร์ได้เพิ่ม $n ครั้ง"
    "shakePerDay" -> "เขย่าเจอเพื่อนได้เพิ่ม $n ครั้ง"
    else -> "${FEATURE_LABELS[feature] ?: feature} ได้เพิ่ม $n ครั้ง"
}
fun validityText(h: Int) = if (h <= 0) tr("ใช้ได้ถึงเที่ยงคืนวันนี้") else "ใช้ได้ $h ชั่วโมง"

data class FeatureQuota(val limit: Int, val used: Int, val remaining: Int?, val unlimited: Boolean)
data class Limits(
    val tier: String, val features: Map<String, FeatureQuota>, val adsWatched: Int, val adsMax: Int,
    val perAd: Map<String, Int>, val showAds: Boolean, val rewardEnabled: Boolean, val bannerEnabled: Boolean,
    val interstitialEnabled: Boolean, val interstitialEverySwipes: Int, val ssvRequired: Boolean,
    /** canPost to reason */
    val posting: Pair<Boolean, String> = true to "",
    val offers: List<AdOffer> = emptyList()
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
            j.optJSONObject("posting")?.let { it.optBoolean("canPost", true) to it.optString("reason") } ?: (true to ""),
            a.optJSONArray("offers")?.let { arr -> (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let { o ->
                val rw = o.optJSONObject("reward") ?: JSONObject()
                AdOffer(o.optString("id"), o.optString("title"), o.optString("adFormat", "rewarded"), o.optString("estDurationLabel"),
                    o.optString("description"), o.optInt("validityHours"), rw.optString("feature"), rw.optInt("amount"))
            } } } ?: emptyList())
    }
    /** Returns null on success, else a Thai error message. */
    suspend fun claimReward(feature: String, offerId: String? = null): String? {
        val u = FirebaseAuth.getInstance().currentUser ?: return tr("กรุณาเข้าสู่ระบบ")
        val r = BrainApi.call(u, "POST", "/quota/ad-reward", if (offerId != null) JSONObject().put("offerId", offerId) else JSONObject().put("feature", feature))
        return when {
            r.code == 200 -> { r.json?.let { limits.value = parse(it) }; null }
            r.code == 202 -> { delay(3000); refresh(); null } // credited by AdMob SSV callback
            r.json?.optString("code") == "ad_cap" -> tr("ดูโฆษณาครบจำนวนของวันนี้แล้ว")
            r.json?.optString("code") == "ad_rate" -> tr("รอสักครู่ก่อนดูโฆษณาถัดไป")
            r.code == 403 -> tr("ระบบโฆษณาปิดอยู่")
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
    suspend fun showRewarded(activity: Activity, feature: String): Boolean = showRewardedFormat(activity, "rewarded", feature)

    /** Loads (only now, after the user's tap) and shows one ad of [format]. customData goes to SSV (e.g. "offer:standard"). */
    suspend fun showRewardedFormat(activity: Activity, format: String, customData: String): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (format == "rewarded_interstitial") {
            val ad = suspendCancellableCoroutine<com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd?> { c ->
                com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd.load(activity, AdUnits.unit(BuildConfig.ADMOB_REWARDED_INTERSTITIAL_ID, BuildConfig.ADMOB_TEST_REWARDED_INTERSTITIAL_ID), AdRequest.Builder().build(),
                    object : com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback() {
                        override fun onAdLoaded(a: com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd) { c.resume(a) }
                        override fun onAdFailedToLoad(e: LoadAdError) { c.resume(null) }
                    })
            } ?: return false
            uid?.let { ad.setServerSideVerificationOptions(ServerSideVerificationOptions.Builder().setUserId(it).setCustomData(customData).build()) }
            return suspendCancellableCoroutine { c ->
                var earned = false
                ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() { if (c.isActive) c.resume(earned) }
                    override fun onAdFailedToShowFullScreenContent(e: AdError) { if (c.isActive) c.resume(false) }
                }
                ad.show(activity) { earned = true }
            }
        }
        val feature = customData
        val ad = suspendCancellableCoroutine<RewardedAd?> { c ->
            RewardedAd.load(activity, AdUnits.unit(BuildConfig.ADMOB_REWARDED_ID, BuildConfig.ADMOB_TEST_REWARDED_ID), AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
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
        if (AdUnits.unit(BuildConfig.ADMOB_INTERSTITIAL_ID, BuildConfig.ADMOB_TEST_INTERSTITIAL_ID).isBlank()) return // no real interstitial unit configured
        InterstitialAd.load(activity, AdUnits.unit(BuildConfig.ADMOB_INTERSTITIAL_ID, BuildConfig.ADMOB_TEST_INTERSTITIAL_ID), AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
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
        AdView(ctx).apply { setAdSize(AdSize.BANNER); adUnitId = AdUnits.unit(BuildConfig.ADMOB_BANNER_ID, BuildConfig.ADMOB_TEST_BANNER_ID); loadAd(AdRequest.Builder().build()) }
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
            title = { Text(tr("โควตาหมดแล้ว")) },
            text = { Text(if (f == "aiBotMessagesPerDay") tr("โควตาคุยกับ AI วันนี้หมดแล้ว 😅 ข้อความที่พิมพ์ไว้ยังอยู่ครบ เติมโควตาแล้วกดส่งได้เลย")
                          else "โควตา${FEATURE_LABELS[f] ?: f}ของวันนี้หมดแล้ว 😅 ดูโฆษณาเพื่อรับเพิ่ม หรือรอพรุ่งนี้") },
            confirmButton = { TextButton(onClick = { AdRoom.quotaHit.value = null; AdRoom.open.value = f }) { Text(tr("ดูโฆษณา +โควตา")) } },
            dismissButton = { TextButton(onClick = { AdRoom.quotaHit.value = null; AdRoom.open.value = f }) { Text("VIP") } })
    }
    open?.let { f -> AdRoomScreen(f) { AdRoom.open.value = null } }
}

/** Deep-link from a quota-exhausted dialog: same content; after a reward it closes and returns to the origin (draft kept). */
@Composable
fun AdRoomScreen(feature: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding(), color = Slate950) { AdsPageContent(feature, onClose = onClose, returnAfterReward = true) }
    }
}

/** Top-level 'หน้าโฆษณา' (bottom navigation). */
@Composable
fun AdsPage() { AdsPageContent(null, onClose = null, returnAfterReward = false) }

/**
 * Browsable list of ad offers. Nothing autoplays: an ad is loaded and shown only after the user taps an offer card.
 * Each card shows the estimated duration, reward, validity and what it unlocks before tapping.
 */
@Composable
fun AdsPageContent(feature: String?, onClose: (() -> Unit)?, returnAfterReward: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val l by AdRoom.limits.collectAsState()
    var busy by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf("") }
    var vipInfo by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { AdRoom.refresh() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        val lim = l
        val vip = lim?.tier == "vip"
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("🎁 หน้าโฆษณา"), color = White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                Row { Text(tr("✦ สถานะ: "), color = Slate300, fontSize = 14.sp); Text(if (vip) tr("VIP ⭐ (ไม่มีโฆษณา)") else tr("ฟรี"), color = Emerald400, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                if (onClose != null) TextButton(onClick = onClose, contentPadding = PaddingValues(0.dp)) { Text(tr("‹ กลับ"), color = Slate300) }
            }
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.example.R.drawable.art_gift), null, modifier = Modifier.width(170.dp).height(110.dp))
        }
        if (lim == null) { CircularProgressIndicator(color = Pink500); return@Column }
        if (feature != null) Text("โควตาที่หมด: ${FEATURE_LABELS[feature] ?: feature}", color = Amber400, fontSize = 12.sp)
        if (!vip && lim.showAds && lim.rewardEnabled) {
            Spacer(Modifier.height(10.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = Slate900, border = androidx.compose.foundation.BorderStroke(1.dp, Slate700.copy(alpha = 0.6f)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Row { Text(tr("เลือกโฆษณาที่ต้องการ (ดูวันนี้แล้ว "), color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("${lim.adsWatched} / ${lim.adsMax}", color = Pink400, fontWeight = FontWeight.Bold, fontSize = 15.sp); Text(")", color = White, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                    Text(tr("โฆษณาจะเริ่มเมื่อคุณกดเลือกเท่านั้น ระยะเวลาเป็นค่าประมาณ"), color = Slate400, fontSize = 12.sp)
                }
            }
            val capped = lim.adsMax > 0 && lim.adsWatched >= lim.adsMax
            val offers = if (feature != null) lim.offers.sortedByDescending { it.feature == feature } else lim.offers
            if (offers.isEmpty()) Text(tr("ยังไม่มีโฆษณาให้เลือกตอนนี้"), color = Slate400, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            offers.forEachIndexed { idx, o ->
                Surface(shape = RoundedCornerShape(20.dp), color = Slate900,
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, androidx.compose.ui.graphics.Brush.linearGradient(listOf(Pink500.copy(alpha = 0.8f), Purple500.copy(alpha = 0.6f)))),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Box(Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Purple600.copy(alpha = 0.22f), Color.Transparent, Pink600.copy(alpha = 0.18f))))) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(o.title, color = White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, maxLines = 1)
                                    if (idx == 0 || o.feature == feature) { Spacer(Modifier.width(8.dp))
                                        Surface(shape = RoundedCornerShape(12.dp), color = Purple600.copy(alpha = 0.5f), border = androidx.compose.foundation.BorderStroke(1.dp, Pink400)) {
                                            Text(tr("👑 แนะนำ"), color = PureWhite, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) } }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text("▶  ดูโฆษณา ${o.durationLabel}".trim(), color = Slate200, fontSize = 12.sp, maxLines = 1, softWrap = false)
                                Text("🎁  ${rewardText(o.feature, o.amount)}", color = Emerald400, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                                Text("⏳  ${validityText(o.validityHours)}", color = Slate200, fontSize = 12.sp, maxLines = 1, softWrap = false)
                                if (o.description.isNotBlank()) Text("✨  ${o.description}", color = Slate300, fontSize = 12.sp, maxLines = 1, softWrap = false)
                            }
                            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(if (o.adFormat == "rewarded_interstitial") com.example.R.drawable.art_ad_short else com.example.R.drawable.art_ad_standard), null,
                                modifier = Modifier.padding(top = 18.dp).width(120.dp).height(80.dp))
                        }
                        val en = busy == null && !capped
                        Box(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(26.dp))
                            .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Pink500, Pink400))).alpha(if (en) 1f else 0.5f)
                            .clickable(enabled = en) {
                                val act = ctx.activity() ?: return@clickable
                                busy = o.id; msg = ""
                                scope.launch {
                                    val earned = AdsManager.showRewardedFormat(act, o.adFormat, "offer:${o.id}")
                                    if (!earned) { msg = tr("ยังไม่ได้รับรางวัล (ดูโฆษณาไม่จบ หรือโหลดโฆษณาไม่ได้)"); busy = null; return@launch }
                                    val err = AdRoom.claimReward(o.feature, o.id)
                                    busy = null
                                    if (err != null) msg = err
                                    else if (returnAfterReward) onClose?.invoke() // back to where the user was (draft kept)
                                    else msg = "ได้รับแล้ว: ${rewardText(o.feature, o.amount)}"
                                }
                            }.padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                            Text(if (busy == o.id) tr("กำลังโหลดโฆษณา…") else if (capped) tr("ดูครบจำนวนวันนี้แล้ว") else tr("▶  ดูโฆษณานี้"), color = PureWhite, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            Text("›", color = PureWhite, fontSize = 20.sp, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp))
                        }
                    }
                    }
                }
            }
        } else if (!vip) Text(tr("ตอนนี้ยังไม่เปิดให้ดูโฆษณารับโควตา"), color = Slate400, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        if (msg.isNotBlank()) Text(msg, color = Amber400, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(12.dp))
        Text(tr("โควตาวันนี้"), color = White, fontWeight = FontWeight.Bold)
        FEATURE_LABELS.forEach { (k, label) ->
            val q = lim.features[k] ?: return@forEach
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(label, color = White, fontSize = 13.sp, maxLines = 2, modifier = Modifier.weight(1f))
                Text(if (q.unlimited) tr("ไม่จำกัด") else "เหลือ ${q.remaining} / ${q.limit}", color = if (q.remaining == 0) Rose500 else Emerald400, fontSize = 13.sp)
            }
        }
        if (!vip) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { vipInfo = true }, modifier = Modifier.fillMaxWidth()) { Text(tr("⭐ สมัคร VIP"), color = Amber400) }
            Spacer(Modifier.height(8.dp))
            FeedBanner(Modifier.fillMaxWidth())
        }
    }
    if (vipInfo) AlertDialog(onDismissRequest = { vipInfo = false }, title = { Text(tr("VIP เร็ว ๆ นี้")) },
        text = { Text(tr("ยังไม่เปิดให้ซื้อ VIP ในแอป หากต้องการ VIP กรุณาติดต่อแอดมิน")) },
        confirmButton = { TextButton(onClick = { vipInfo = false }) { Text(tr("ตกลง")) } })
}

/** Admin/superadmin/moderator accounts always get Google TEST units, so the owner never views/clicks live ads. */
object AdUnits {
    @Volatile var adminTestAds = true // safe default until /me confirms a normal USER role
    fun unit(real: String, test: String) = if (adminTestAds) test else real
}

/** Decorative illustration: big emoji with glow + coins (bundled vector-free; uses system emoji). */
@Composable
fun AdIllustration(main: String, size: Int) {
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size((size * 0.85f).dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Pink500.copy(alpha = 0.45f), Purple600.copy(alpha = 0.15f), Color.Transparent))))
        Text(main, fontSize = (size * 0.5f).sp)
        Text("🪙", fontSize = (size * 0.2f).sp, modifier = Modifier.align(Alignment.BottomStart))
        Text("🪙", fontSize = (size * 0.16f).sp, modifier = Modifier.align(Alignment.TopEnd))
        Text("✦", color = Pink400, fontSize = (size * 0.12f).sp, modifier = Modifier.align(Alignment.TopStart))
    }
}
