package com.hmj.twotone.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import com.hmj.twotone.MainActivity
import com.hmj.twotone.R
import java.util.concurrent.Executors

/**
 * Keeps music going in the background and connects it to the rest of the phone:
 * Bluetooth and headset buttons, the lock screen, and a notification with
 * previous / play-pause / next / stop. Unplugging headphones pauses.
 */
class MusicService : Service() {

    private lateinit var session: MediaSession
    private val artLoader = Executors.newSingleThreadExecutor()
    private var art: Bitmap? = null
    private var artFor: Long = -1

    private val noisy = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) Music.pause()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val nm = getSystemService(NotificationManager::class.java)
        // Silent, but not a "low" channel: Android hides low ones from the lock screen.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Music player", NotificationManager.IMPORTANCE_DEFAULT).apply {
                setShowBadge(false); setSound(null, null); enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        session = MediaSession(this, "TwoTone").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = Music.resume(this@MusicService)
                override fun onPause() = Music.pause()
                override fun onSkipToNext() = Music.next(this@MusicService)
                override fun onSkipToPrevious() = Music.previous(this@MusicService)
                override fun onStop() = Music.stop(this@MusicService)
                override fun onSeekTo(pos: Long) { Music.seekTo(pos); refresh() }
            })
            setSessionActivity(openAppIntent())
            isActive = true
        }
        registerReceiver(noisy, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> Music.toggle(this)
            ACTION_NEXT -> Music.next(this)
            ACTION_PREVIOUS -> Music.previous(this)
            ACTION_STOP -> { Music.stop(this); return START_NOT_STICKY }
        }
        refresh()
        return START_NOT_STICKY
    }

    /** Re-reads the player and updates the session and the notification. */
    fun refresh() {
        val t = Music.current
        if (t == null) {
            shutDown()
            return
        }
        if (artFor != t.id) {
            artFor = t.id
            art = null
            artLoader.execute {
                val bmp = AlbumArt.dithered(this, t, 96, 0xFF000000.toInt(), 0xFFFFFFFF.toInt())
                if (Music.current?.id == t.id) {
                    art = bmp
                    android.os.Handler(mainLooper).post { refresh() }
                }
            }
        }

        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, t.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, t.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, t.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, t.durationMs)
                .apply { art?.let { putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }
                .build()
        )
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO
                )
                .setState(
                    if (Music.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    Music.positionMs(),
                    if (Music.isPlaying) 1f else 0f
                )
                .build()
        )

        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(t.title)
            .setContentText(t.artist)
            .setLargeIcon(art)
            .setContentIntent(openAppIntent())
            .setDeleteIntent(serviceIntent(ACTION_STOP, 4))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setOngoing(Music.isPlaying)
            .addAction(action(R.drawable.ic_previous, "previous", ACTION_PREVIOUS, 1))
            .addAction(
                if (Music.isPlaying) action(R.drawable.ic_pause, "pause", ACTION_TOGGLE, 2)
                else action(R.drawable.ic_play, "play", ACTION_TOGGLE, 2)
            )
            .addAction(action(R.drawable.ic_next, "next", ACTION_NEXT, 3))
            .addAction(action(R.drawable.ic_stop, "stop", ACTION_STOP, 4))
            .setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .build()

        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(NOTIFICATION_ID, n)
            // While paused the notification may be swiped away, which stops the music.
            if (!Music.isPlaying) stopForeground(STOP_FOREGROUND_DETACH)
        } catch (e: Exception) {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)
        }
    }

    private fun shutDown() {
        try { stopForeground(STOP_FOREGROUND_REMOVE) } catch (e: Exception) { }
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        session.isActive = false
        stopSelf()
    }

    override fun onDestroy() {
        instance = null
        try { unregisterReceiver(noisy) } catch (e: Exception) { }
        session.release()
        artLoader.shutdown()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_MUSIC)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun serviceIntent(action: String, code: Int): PendingIntent =
        PendingIntent.getForegroundService(
            this, code, Intent(this, MusicService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun action(icon: Int, title: String, action: String, code: Int) =
        Notification.Action.Builder(Icon.createWithResource(this, icon), title, serviceIntent(action, code)).build()

    companion object {
        private const val CHANNEL = "music_player"
        private const val NOTIFICATION_ID = 41
        const val ACTION_TOGGLE = "com.hmj.twotone.music.TOGGLE"
        const val ACTION_NEXT = "com.hmj.twotone.music.NEXT"
        const val ACTION_PREVIOUS = "com.hmj.twotone.music.PREVIOUS"
        const val ACTION_STOP = "com.hmj.twotone.music.STOP"

        private var instance: MusicService? = null

        /** Called by [Music] after every change. Starts the service when something is queued. */
        fun sync(context: Context) {
            val s = instance
            if (s != null) { s.refresh(); return }
            if (Music.current == null) return
            try {
                context.startForegroundService(Intent(context, MusicService::class.java))
            } catch (e: Exception) {
                // Not allowed from the background on some Android versions; the next tap in the app starts it.
            }
        }

        fun stop(context: Context) {
            instance?.refresh() ?: Unit
        }
    }
}
