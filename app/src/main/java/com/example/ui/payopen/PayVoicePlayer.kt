package com.example.ui.payopen

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.speech.tts.TextToSpeech
import com.example.social.PayOpenRepo
import com.example.social.PayVoice
import java.util.Locale

/**
 * Voices of เปย์เปิดใจ. A line with an uploaded audio file plays that file; a line without one is spoken on the phone
 * (Android TextToSpeech, Thai, free and offline-capable) with a higher, cuter pitch for female and a lower one for male.
 * The list comes from the server (admin-managed) and is refreshed every few minutes — no APK update needed.
 */
object PayVoicePlayer {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pending: (() -> Unit)? = null
    private var mp: MediaPlayer? = null
    @Volatile private var cache: List<PayVoice> = emptyList()
    private var cacheAt = 0L

    suspend fun voices(force: Boolean = false): List<PayVoice> {
        if (force || cache.isEmpty() || System.currentTimeMillis() - cacheAt > 5 * 60_000) {
            val v = runCatching { PayOpenRepo.voices() }.getOrDefault(emptyList())
            if (v.isNotEmpty() || force) { cache = v; cacheAt = System.currentTimeMillis() }
        }
        return cache
    }

    /** Random line of [kind] in [gender] (falls back to the other gender, then to [fallbackText]). */
    suspend fun playRandom(ctx: Context, kind: String, gender: String, fallbackText: String) {
        val all = voices().filter { it.kind == kind }
        val pick = all.filter { it.gender == gender }.randomOrNull() ?: all.randomOrNull()
        if (pick != null) play(ctx, pick) else speak(ctx, fallbackText, gender)
    }

    fun play(ctx: Context, v: PayVoice) {
        if (v.audioUrl.isNotBlank()) playUrl(ctx, v.audioUrl) { speak(ctx, v.text, v.gender) } else speak(ctx, v.text, v.gender)
    }

    private fun playUrl(ctx: Context, url: String, onError: () -> Unit) {
        stop()
        runCatching {
            val p = MediaPlayer(); mp = p
            p.setDataSource(ctx.applicationContext, Uri.parse(url))
            p.setOnPreparedListener { it.start() }
            p.setOnCompletionListener { runCatching { it.release() }; if (mp === it) mp = null }
            p.setOnErrorListener { m, _, _ -> runCatching { m.release() }; if (mp === m) mp = null; onError(); true }
            p.prepareAsync()
        }.onFailure { onError() }
    }

    fun speak(ctx: Context, text: String, gender: String) {
        val say = {
            tts?.let { t ->
                t.setPitch(if (gender == "male") 0.75f else 1.55f)
                t.setSpeechRate(if (gender == "male") 0.95f else 1.1f)
                t.speak(text.replace("~", ""), TextToSpeech.QUEUE_FLUSH, null, "payopen")
            }
        }
        if (ttsReady) { say(); return }
        pending = say
        if (tts == null) tts = TextToSpeech(ctx.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("th", "TH"); ttsReady = true
                pending?.invoke(); pending = null
            }
        }
    }

    fun stop() {
        mp?.let { runCatching { it.stop() }; runCatching { it.release() } }; mp = null
        runCatching { tts?.stop() }
    }
}
