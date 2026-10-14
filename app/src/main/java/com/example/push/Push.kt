package com.example.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.network.BrainApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Push notifications (Firebase Cloud Messaging) for new chat messages, friend requests, matches, gifts and top-ups.
 * The device token is stored by friendtalk-brain (POST /push/token); the server sends the pushes.
 */
object PushRepo {
    const val CHANNEL = "ft_default"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL) == null)
                nm.createNotificationChannel(NotificationChannel(CHANNEL, "ข้อความและการแจ้งเตือน", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "แชทใหม่ คำขอเป็นเพื่อน แมตช์ ของขวัญ และการเติมเงิน"
                })
        }
    }

    private suspend fun currentToken(): String? = suspendCancellableCoroutine { c ->
        runCatching { FirebaseMessaging.getInstance().token.addOnCompleteListener { t -> if (c.isActive) c.resume(if (t.isSuccessful) t.result else null) } }
            .onFailure { if (c.isActive) c.resume(null) }
    }

    /** Sends this device's token to the server (after sign-in and whenever FCM rotates it). */
    fun register(token: String? = null) {
        scope.launch {
            val u = FirebaseAuth.getInstance().currentUser ?: return@launch
            val t = token ?: currentToken() ?: return@launch
            BrainApi.call(u, "POST", "/push/token", JSONObject().put("token", t))
        }
    }

    /** Before sign-out: stop pushes to this device for the account. */
    suspend fun unregister() {
        val u = FirebaseAuth.getInstance().currentUser ?: return
        val t = currentToken() ?: return
        BrainApi.call(u, "DELETE", "/push/token", JSONObject().put("token", t))
    }

    /** After a chat message is written: ask the server to notify the other member. */
    fun notifyChat(chatId: String) {
        scope.launch { FirebaseAuth.getInstance().currentUser?.let { BrainApi.call(it, "POST", "/push/chat/$chatId") } }
    }

    @android.annotation.SuppressLint("MissingPermission") // checked just below (POST_NOTIFICATIONS on Android 13+)
    /** Shows a notification (used while the app is open; in the background Android shows the server's notification itself). */
    fun show(ctx: Context, title: String, body: String, data: Map<String, String>) {
        ensureChannel(ctx)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        data.forEach { (k, v) -> open.putExtra(k, v) }
        val pi = PendingIntent.getActivity(ctx, (data["chatId"] ?: title).hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(com.example.R.mipmap.ic_launcher)
            .setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true).setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH).build()
        runCatching { NotificationManagerCompat.from(ctx).notify((data["chatId"] ?: title).hashCode(), n) }
    }
}

class FtMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) { PushRepo.register(token) }
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: "FriendTalk"
        val body = message.notification?.body ?: message.data["body"] ?: ""
        PushRepo.show(applicationContext, title, body, message.data)
    }
}
