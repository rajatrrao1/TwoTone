package com.hmj.twotone.music

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class Repeat(val label: String) { OFF("off"), ALL("all"), ONE("one song") }

/** Settings for the music section, and the little click the wheel makes. */
object MusicSettings {
    var clickSound by mutableStateOf(true)
        private set
    var haptics by mutableStateOf(false)
        private set
    /** Only songs in this folder (and the folders inside it) are shown, or null for all. */
    var folder by mutableStateOf<String?>(null)
        private set
    var repeat by mutableStateOf(Repeat.OFF)
        private set

    private var loaded = false
    private var click: AudioTrack? = null

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val sp = prefs(ctx)
        clickSound = sp.getBoolean("clickSound", true)
        haptics = sp.getBoolean("haptics", false)
        folder = sp.getString("folder", null)
        repeat = Repeat.entries.firstOrNull { it.name == sp.getString("repeat", null) } ?: Repeat.OFF
    }

    fun setClickSound(ctx: Context, v: Boolean) { clickSound = v; prefs(ctx).edit().putBoolean("clickSound", v).apply() }
    fun setHaptics(ctx: Context, v: Boolean) { haptics = v; prefs(ctx).edit().putBoolean("haptics", v).apply() }
    fun setFolder(ctx: Context, v: String?) { folder = v; prefs(ctx).edit().putString("folder", v).apply() }
    fun setRepeat(ctx: Context, v: Repeat) { repeat = v; prefs(ctx).edit().putString("repeat", v.name).apply() }

    /** The songs to show, after the folder setting. */
    fun visible(all: List<Track>): List<Track> {
        val f = folder ?: return all
        return all.filter { it.folder.startsWith(f) }
    }

    /**
     * One tick of feedback: a short synthesised click (made here, not a recording)
     * and/or a haptic tick, as chosen in the music settings.
     */
    fun tick(view: View) {
        if (haptics) buzz(view.context)
        if (!clickSound) return
        val t = click ?: buildClick().also { click = it } ?: return
        try {
            if (t.playState == AudioTrack.PLAYSTATE_PLAYING) t.stop()
            t.reloadStaticData()
            t.play()
        } catch (e: Exception) { }
    }

    /**
     * A firm pulse from the vibration motor itself. Small phone motors need about
     * 50 ms to spin up, so anything much shorter can't be felt at all.
     */
    private fun buzz(ctx: Context, ms: Long = 55) {
        try {
            val v = vibrator(ctx) ?: return
            val effect = android.os.VibrationEffect.createOneShot(ms, 255)
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                v.vibrate(effect, android.os.VibrationAttributes.createForUsage(android.os.VibrationAttributes.USAGE_MEDIA))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build())
            }
        } catch (e: Exception) { }
    }

    private fun vibrator(ctx: Context): android.os.Vibrator? =
        if (android.os.Build.VERSION.SDK_INT >= 31)
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager)?.defaultVibrator
        else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator)

    /** Buzzes for a third of a second and says what the phone reports about its motor. */
    fun testVibration(ctx: Context): String {
        val v = vibrator(ctx) ?: return "Android reports no vibration motor."
        if (!v.hasVibrator()) return "Android reports no vibration motor on this phone."
        buzz(ctx, 350)
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        return when (am.ringerMode) {
            android.media.AudioManager.RINGER_MODE_SILENT ->
                "Sent a buzz. The phone is on silent, which can stop vibration; if you felt nothing, switch silent off."
            else -> "Sent a buzz. If you felt nothing, check Android settings > Sound & vibration > Vibration & haptics."
        }
    }

    /** A 6 ms click: a quick high blip that dies away, much like a mechanical wheel. */
    private fun buildClick(): AudioTrack? = try {
        val rate = 44_100
        val n = rate * 6 / 1000
        val pcm = ShortArray(n) { i ->
            val t = i.toDouble() / rate
            val env = exp(-t * 900.0)
            (sin(2 * PI * 3_200.0 * t) * env * 0.5 * Short.MAX_VALUE).toInt().toShort()
        }
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    // On the music stream: it follows the music volume and plays in your headphones.
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(n * 2)
            .build()
            .also { it.write(pcm, 0, n) }
    } catch (e: Exception) {
        null
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("twotone_music", Context.MODE_PRIVATE)
}
