package com.hmj.twotone.clock

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import com.hmj.twotone.R
import com.hmj.twotone.music.Music

/**
 * Rings: plays the alarm sound on repeat, vibrates, and shows the ringing
 * screen over everything (or a notification with stop / snooze if the phone
 * is in use). Gives up by itself after ten minutes.
 */
class AlarmRingService : Service() {
    private var player: MediaPlayer? = null
    private var label = "alarm"
    private var ring = RING_BOTH
    private val handler = Handler(Looper.getMainLooper())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { finish(); return START_NOT_STICKY }
            ACTION_SNOOZE -> { AlarmStore.snooze(this, label, ring); finish(); return START_NOT_STICKY }
        }
        label = intent?.getStringExtra(EXTRA_LABEL) ?: "alarm"
        ring = intent?.getIntExtra(EXTRA_RING, RING_BOTH) ?: RING_BOTH
        ringing = label
        showNotification()
        startSound()
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ finish() }, 10 * 60_000L)
        return START_NOT_STICKY
    }

    private fun showNotification() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // the service plays the sound itself, on repeat
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        val ringScreen = PendingIntent.getActivity(
            this, 10, Intent(this, AlarmRingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(label)
            .setContentText("Tap to open")
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(ringScreen, true)
            .setContentIntent(ringScreen)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_stop), "stop", self(ACTION_STOP, 11)).build())
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.ic_stat_alarm), "snooze ${AlarmStore.SNOOZE_MINUTES} min", self(ACTION_SNOOZE, 12)).build())
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(ID, n)
        } catch (e: Exception) {
            nm.notify(ID, n)
        }
        // Also try to bring the ringing screen up directly (works when the screen is off).
        try { startActivity(Intent(this, AlarmRingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
    }

    private var ringing_ = false

    private fun startSound() {
        if (ringing_) return
        ringing_ = true
        Music.pause()
        if (ring != RING_SOUND) {
            val v = getSystemService(Vibrator::class.java)
            v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0))
        }
        if (ring == RING_VIBRATE) return
        val uri = AlarmStore.soundUri?.let { Uri.parse(it) }
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                setDataSource(this@AlarmRingService, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) { player = null }
    }

    private fun finish() {
        ringing_ = false
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        getSystemService(Vibrator::class.java)?.cancel()
        ringing = null
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (e: Exception) { }
        getSystemService(NotificationManager::class.java).cancel(ID)
        stopSelf()
    }

    override fun onDestroy() {
        player?.release()
        getSystemService(Vibrator::class.java)?.cancel()
        ringing = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun self(action: String, code: Int): PendingIntent = PendingIntent.getService(
        this, code, Intent(this, AlarmRingService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    companion object {
        private const val CHANNEL = "alarms"
        private const val ID = 42
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_RING = "ring"
        const val ACTION_STOP = "com.hmj.twotone.clock.STOP"
        const val ACTION_SNOOZE = "com.hmj.twotone.clock.SNOOZE"

        /** What is ringing right now, or null. The ringing screen closes when this goes null. */
        @Volatile var ringing: String? = null

        fun start(ctx: Context, label: String, ring: Int = RING_BOTH) {
            val i = Intent(ctx, AlarmRingService::class.java).putExtra(EXTRA_LABEL, label).putExtra(EXTRA_RING, ring)
            try { ctx.startForegroundService(i) } catch (e: Exception) { ctx.startService(i) }
        }

        fun send(ctx: Context, action: String) {
            ctx.startService(Intent(ctx, AlarmRingService::class.java).setAction(action))
        }
    }
}
