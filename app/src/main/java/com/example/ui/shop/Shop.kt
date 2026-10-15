package com.example.ui.shop

import com.example.ui.components.dialogNavPadding

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.example.ui.components.ft.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.android.billingclient.api.*
import com.example.network.BrainApi
import com.example.social.Wallet
import com.example.ui.components.tr
import com.example.ui.theme.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

data class Pack(val productId: String, val amount: Int, val bonus: Int, val label: String, val priceTHB: Double, val enabled: Boolean, val order: Double)

/** Store ('ร้านค้า'): Google Play Billing. Prices shown come from Play ProductDetails (= what is charged). Server verifies + credits. */
object Shop {
    val open = MutableStateFlow<String?>(null) // "diamonds" | "coins"
}

private suspend fun connect(client: BillingClient): Boolean = suspendCancellableCoroutine { c ->
    client.startConnection(object : BillingClientStateListener {
        override fun onBillingSetupFinished(r: BillingResult) { if (c.isActive) c.resume(r.responseCode == BillingClient.BillingResponseCode.OK) }
        override fun onBillingServiceDisconnected() { if (c.isActive) c.resume(false) }
    })
}

@Composable
fun ShopHost() {
    val tab by Shop.open.collectAsState()
    tab?.let { ShopDialog(it) { Shop.open.value = null } }
}

@Composable
private fun ShopDialog(initial: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(initial) }
    var enabled by remember { mutableStateOf(false) }
    var packs by remember { mutableStateOf<Map<String, List<Pack>>>(emptyMap()) }
    var details by remember { mutableStateOf<Map<String, ProductDetails>>(emptyMap()) }
    var status by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    fun verify(p: Purchase) { scope.launch {
        val u = FirebaseAuth.getInstance().currentUser ?: return@launch
        val r = BrainApi.call(u, "POST", "/shop/verify", JSONObject().put("productId", p.products.firstOrNull().orEmpty()).put("purchaseToken", p.purchaseToken))
        status = if (r.code == 200) tr("เติมสำเร็จ 🎉") else (r.json?.optString("error")?.ifBlank { null } ?: "ตรวจสอบการซื้อไม่สำเร็จ (${r.code})")
    } }
    val client = remember {
        BillingClient.newBuilder(ctx).enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .setListener { r, list -> if (r.responseCode == BillingClient.BillingResponseCode.OK) list?.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }?.forEach { verify(it) }
                else if (r.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) status = "ซื้อไม่สำเร็จ (${r.responseCode})" }
            .build()
    }
    DisposableEffect(Unit) { onDispose { runCatching { client.endConnection() } } }
    LaunchedEffect(Unit) {
        val u = FirebaseAuth.getInstance().currentUser
        val r = if (u != null) BrainApi.call(u, "GET", "/shop/products") else null
        val j = r?.json
        enabled = j?.optBoolean("enabled") == true
        fun parse(k: String) = j?.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.map {
            Pack(it.optString("productId"), it.optInt("amount"), it.optInt("bonus"), it.optString("label"), it.optDouble("priceTHB", 0.0), it.optBoolean("enabled", true), it.optDouble("order", 0.0)) }
            .filter { it.enabled }.sortedBy { it.order } } ?: emptyList()
        packs = mapOf("diamonds" to parse("diamonds"), "coins" to parse("coins"))
        if (enabled && packs.values.any { it.isNotEmpty() } && connect(client)) {
            val prods = packs.values.flatten().map { QueryProductDetailsParams.Product.newBuilder().setProductId(it.productId).setProductType(BillingClient.ProductType.INAPP).build() }
            val res = client.queryProductDetails(QueryProductDetailsParams.newBuilder().setProductList(prods).build())
            details = res.productDetailsList.orEmpty().associateBy { it.productId }
            // finish any purchase that was paid but not yet credited
            client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()).purchasesList
                .filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }.forEach { verify(it) }
        }
        loading = false
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding().dialogNavPadding().imePadding(), color = Slate950) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🛍 " + tr("ร้านค้า"), color = White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text(tr("ปิด") + " ✕") }
                }
                Text("🪙 ${Wallet.coins}   💎 ${Wallet.diamonds}", color = Slate300, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = tab == "diamonds", onClick = { tab = "diamonds" }, label = { Text("💎 " + tr("ซื้อเพชร")) })
                    FilterChip(selected = tab == "coins", onClick = { tab = "coins" }, label = { Text("🪙 " + tr("ซื้อเหรียญ")) })
                }
                if (tab == "diamonds") Text(tr("เพชรใช้ส่งของขวัญและคุยกับเพื่อนคุย"), color = Slate400, fontSize = 12.sp)
                val list = packs[tab].orEmpty()
                when {
                    loading -> CircularProgressIndicator(color = Pink500)
                    // Google Play billing not available (app not on Play yet / no products) → bank-transfer top-up checked by admins
                    !enabled || list.isEmpty() -> TopupSection(tab)
                    // Play products configured but this copy was not installed from Google Play (test APK) → offer the other way
                    details.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ℹ️ " + tr("ซื้อผ่าน Google Play ได้เมื่อติดตั้งแอปจาก Play Store — ตอนนี้เติมแบบโอนหรือให้แอดมินเติมให้ได้"), color = Slate400, fontSize = 12.sp)
                        TopupSection(tab)
                    }
                    else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(list) { p ->
                            val d = details[p.productId]
                            Surface(shape = RoundedCornerShape(14.dp), color = Slate900, modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text((if (tab == "diamonds") "💎 " else "🪙 ") + p.amount + if (p.bonus > 0) "  +${p.bonus} โบนัส" else "", color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        if (p.label.isNotBlank()) Text(p.label, color = Slate400, fontSize = 12.sp)
                                    }
                                    Button(enabled = d != null, onClick = {
                                        val dd = d ?: return@Button
                                        val params = BillingFlowParams.newBuilder()
                                            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(dd).build()))
                                            .setObfuscatedAccountId(FirebaseAuth.getInstance().currentUser?.uid.orEmpty().take(64)).build()
                                        (ctx as? Activity)?.let { client.launchBillingFlow(it, params) }
                                    }, colors = ButtonDefaults.buttonColors(containerColor = Pink500)) {
                                        Text(d?.oneTimePurchaseOfferDetails?.formattedPrice ?: tr("ยังไม่เปิดขาย"), color = PureWhite)
                                    }
                                }
                            }
                        }
                    }
                }
                if (status.isNotBlank()) Text(status, color = Amber400)
            }
        }
    }
}
