package com.hmj.twotone.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hmj.twotone.notify.NotificationReader

/** What another app is playing, for the home now-playing bar. */
data class OtherMedia(
    val app: String,
    val pkg: String,
    val title: String,
    val artist: String,
    val playing: Boolean,
    val durationMs: Long,
    val positionMs: Long,
    /** When [positionMs] was measured (elapsed realtime), so the time left can count down. */
    val positionAt: Long,
    val art: Bitmap?,
    val key: String
)

/**
 * Follows whatever another app is playing (Spotify, a podcast app, a browser…)
 * through Android's media sessions. Needs the same Notification access as the
 * bell. Two Tone's own music is left out; it has its own bar.
 */
object OtherPlayers {
    var now by mutableStateOf<OtherMedia?>(null)
        private set
    /** When each item was last seen playing. */
    private val lastPlayed = HashMap<String, Long>()

    /** Shown while playing, and for ten minutes after it was last playing. */
    fun showOnHome(m: OtherMedia): Boolean =
        m.playing || (lastPlayed[m.key]?.let { System.currentTimeMillis() - it < 10 * 60_000L } ?: false)

    /** Swiped away: hidden until something else starts playing. */
    private var hiddenKey: String? = null

    private var manager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var appContext: Context? = null
    private val main = Handler(Looper.getMainLooper())

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list) }
    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = update()
        override fun onMetadataChanged(metadata: MediaMetadata?) = update()
        override fun onSessionDestroyed() { controller = null; now = null; manager?.let { pick(currentSessions()) } }
    }

    /** Starts following. Safe to call again; does nothing without Notification access. */
    fun start(ctx: Context) {
        appContext = ctx.applicationContext
        if (manager != null) { pick(currentSessions()); return }
        try {
            val m = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val me = ComponentName(ctx, NotificationReader::class.java)
            m.addOnActiveSessionsChangedListener(sessionsChanged, me, main)
            manager = m
            pick(currentSessions())
        } catch (e: SecurityException) {
            // Notification access isn't on yet
        } catch (e: Exception) { }
    }

    private fun currentSessions(): List<MediaController> = try {
        val ctx = appContext ?: return emptyList()
        manager?.getActiveSessions(ComponentName(ctx, NotificationReader::class.java)).orEmpty()
    } catch (e: Exception) { emptyList() }

    /** The session to show: one that's playing, else the most recent one with a title. */
    private fun pick(list: List<MediaController>?) {
        val mine = appContext?.packageName
        val others = list.orEmpty().filter { it.packageName != mine && it.metadata != null }
        val best = others.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: others.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = best
            best?.registerCallback(callback, main)
        }
        update()
    }

    private fun update() {
        val c = controller
        val md = c?.metadata
        val st = c?.playbackState
        if (c == null || md == null) { now = null; return }
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: ""
        if (title.isBlank()) { now = null; return }
        val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        val art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        val pm = appContext?.packageManager
        val app = try { pm?.getApplicationLabel(pm.getApplicationInfo(c.packageName, 0))?.toString() } catch (e: Exception) { null } ?: c.packageName
        val playing = st?.state == PlaybackState.STATE_PLAYING || st?.state == PlaybackState.STATE_BUFFERING
        val key = "${c.packageName}|$title|$artist"
        if (playing) lastPlayed[key] = System.currentTimeMillis()
        // A swiped-away item stays hidden until the song changes or it starts again after a pause.
        if (hiddenKey != null && hiddenKey != key) hiddenKey = null
        if (key == hiddenKey && !playing) { now = null; return }
        if (key == hiddenKey && playing) hiddenKey = null
        now = OtherMedia(
            app, c.packageName, title, artist, playing,
            md.getLong(MediaMetadata.METADATA_KEY_DURATION),
            st?.position ?: 0L, st?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            art, key
        )
    }

    fun toggle() { val c = controller ?: return; if (now?.playing == true) c.transportControls.pause() else c.transportControls.play() }
    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }

    /** Swipe away: pause it and hide the bar until something new plays. */
    fun dismiss() {
        controller?.transportControls?.pause()
        hiddenKey = now?.key
        now = null
    }

    /** Opens the app that's playing. */
    fun open(ctx: Context) {
        val c = controller ?: return
        try {
            c.sessionActivity?.send() ?: run {
                ctx.packageManager.getLaunchIntentForPackage(c.packageName)?.let {
                    ctx.startActivity(it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        } catch (e: Exception) { }
    }

    /** Where the song is now: the last reported position plus the time since, while playing. */
    fun positionNow(m: OtherMedia): Long =
        if (m.playing) m.positionMs + (SystemClock.elapsedRealtime() - m.positionAt) else m.positionMs
}
