package com.hmj.twotone.music

import android.content.ContentUris
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.PowerManager
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val trackNo: Int,
    val durationMs: Long,
    /** Folder the file is in, like "Music/Geese/" (used for the "music folder" setting). */
    val folder: String = ""
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
}

/**
 * Plays the audio files already on the phone. When the queue ends, it stops:
 * nothing is queued up after the album you chose.
 *
 * Playback runs inside [MusicService], which gives it a media session
 * (Bluetooth / headset buttons, lock screen, notification).
 */
object Music {
    var tracks by mutableStateOf<List<Track>>(emptyList())
        private set
    var queue by mutableStateOf<List<Track>>(emptyList())
        private set
    var index by mutableIntStateOf(-1)
        private set
    var isPlaying by mutableStateOf(false)
        private set

    val current: Track? get() = queue.getOrNull(index)

    /** When the music last stopped playing (0 = not since it was brought back after a restart). */
    var pausedAt by mutableStateOf(0L)
        private set

    /** The home bar shows while playing, and for ten minutes after a pause. */
    val showOnHome: Boolean get() = current != null && (isPlaying || (pausedAt > 0 && System.currentTimeMillis() - pausedAt < 10 * 60_000L))

    /** When the sleep timer stops the music (epoch ms), or 0. */
    var sleepAt by mutableStateOf(0L)
        private set
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val sleepStop = Runnable { sleepAt = 0L; pause() }

    /** Stop the music after [minutes]; 0 cancels. */
    fun setSleepTimer(minutes: Int) {
        handler.removeCallbacks(sleepStop)
        sleepAt = if (minutes > 0) System.currentTimeMillis() + minutes * 60_000L else 0L
        if (minutes > 0) handler.postDelayed(sleepStop, minutes * 60_000L)
    }

    /** Put [t] straight after the song that is playing (or start it if nothing is). */
    fun playNext(context: Context, t: Track) {
        if (current == null) { play(context, listOf(t), 0); return }
        queue = queue.toMutableList().apply { add(index + 1, t) }
        MusicService.sync(context.applicationContext)
    }

    private var player: MediaPlayer? = null
    private var appContext: Context? = null
    private var focusRequest: AudioFocusRequest? = null
    private var pausedByFocusLoss = false

    private val audioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    suspend fun load(context: Context) {
        appContext = context.applicationContext
        tracks = withContext(Dispatchers.IO) { query(context) }
        restore(context.applicationContext)
    }

    // ---- picking up where you left off -------------------------------------

    /** Position to jump to when the restored song first starts. */
    private var pendingSeek = 0L

    private val saver = object : Runnable {
        override fun run() {
            appContext?.let { save(it) }
            if (isPlaying) handler.postDelayed(this, 15_000L)
        }
    }

    /** Remembers the queue, the song and the position (on every pause, change and every 15 s). */
    private fun save(ctx: Context) {
        val sp = ctx.getSharedPreferences("twotone_music", Context.MODE_PRIVATE)
        if (queue.isEmpty()) {
            sp.edit().remove("resumeQueue").remove("resumeIndex").remove("resumePos").apply()
            return
        }
        sp.edit()
            .putString("resumeQueue", queue.joinToString(",") { it.id.toString() })
            .putInt("resumeIndex", index)
            .putLong("resumePos", if (player != null) positionMs() else pendingSeek)
            .apply()
    }

    /** After a restart: the last queue comes back paused, at the same place. */
    private fun restore(ctx: Context) {
        if (queue.isNotEmpty() || tracks.isEmpty()) return
        val sp = ctx.getSharedPreferences("twotone_music", Context.MODE_PRIVATE)
        val ids = sp.getString("resumeQueue", null)?.split(',')?.mapNotNull { it.toLongOrNull() } ?: return
        val byId = tracks.associateBy { it.id }
        val saved = ids.mapNotNull { byId[it] }
        if (saved.isEmpty()) return
        val savedIndex = sp.getInt("resumeIndex", 0)
        val savedTrackId = ids.getOrNull(savedIndex)
        queue = saved
        index = saved.indexOfFirst { it.id == savedTrackId }.takeIf { it >= 0 } ?: 0
        pendingSeek = if (saved.getOrNull(index)?.id == savedTrackId) sp.getLong("resumePos", 0L) else 0L
        isPlaying = false
    }

    /** The queue in its own order, kept while shuffle is on so it can be put back. */
    private var unshuffled: List<Track> = emptyList()

    fun play(context: Context, list: List<Track>, start: Int) {
        pendingSeek = 0L
        unshuffled = list
        if (MusicSettings.shuffle && list.size > 1) {
            // The song you chose plays first, the rest follow in a random order.
            val chosen = list.getOrNull(start)
            queue = listOfNotNull(chosen) + list.filterIndexed { i, _ -> i != start }.shuffled()
            startAt(context.applicationContext, 0)
            return
        }
        queue = list
        startAt(context.applicationContext, start)
    }

    /** Shuffle on or off, also for what's queued now; the song that's playing carries on. */
    fun setShuffle(context: Context, on: Boolean) {
        MusicSettings.saveShuffle(context, on)
        val cur = current ?: return
        if (on) {
            unshuffled = queue
            queue = listOf(cur) + queue.filterIndexed { i, _ -> i != index }.shuffled()
            index = 0
        } else if (unshuffled.isNotEmpty()) {
            queue = unshuffled
            index = unshuffled.indexOfFirst { it.id == cur.id }.coerceAtLeast(0)
        }
        save(context.applicationContext)
    }

    /** Off → all → one song → off. */
    fun cycleRepeat(context: Context) {
        val all = Repeat.entries
        MusicSettings.setRepeat(context, all[(all.indexOf(MusicSettings.repeat) + 1) % all.size])
    }

    fun toggle(context: Context) = if (isPlaying) pause() else resume(context)

    fun resume(context: Context) {
        val ctx = context.applicationContext
        val p = player
        if (p == null) {
            if (current != null) startAt(ctx, index)
            return
        }
        if (!requestFocus(ctx)) return
        p.start()
        isPlaying = true
        handler.removeCallbacks(saver); handler.postDelayed(saver, 15_000L)
        MusicService.sync(ctx)
    }

    fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
        if (isPlaying) pausedAt = System.currentTimeMillis()
        isPlaying = false
        appContext?.let { save(it); MusicService.sync(it) }
    }

    /** Stops and clears the queue: the home bar and the notification go away. */
    fun stop(context: Context) {
        player?.release()
        player = null
        isPlaying = false
        queue = emptyList()
        index = -1
        pendingSeek = 0L
        save(context.applicationContext)
        abandonFocus(context.applicationContext)
        MusicService.stop(context.applicationContext)
    }

    fun next(context: Context) {
        if (index < queue.lastIndex) startAt(context.applicationContext, index + 1)
    }

    fun previous(context: Context) {
        val p = player
        if (p != null && p.currentPosition > 3_000) { p.seekTo(0); MusicService.sync(context.applicationContext) }
        else if (index > 0) startAt(context.applicationContext, index - 1)
        else p?.seekTo(0)
    }

    fun seekTo(ms: Long) {
        val p = player
        if (p != null) p.seekTo(ms.toInt()) else pendingSeek = ms.coerceAtLeast(0L)
    }

    fun positionMs(): Long = player?.currentPosition?.toLong() ?: pendingSeek

    private fun startAt(ctx: Context, i: Int) {
        appContext = ctx
        player?.release()
        player = null
        if (queue.getOrNull(i) == null) {
            isPlaying = false
            MusicService.sync(ctx)
            return
        }
        index = i
        if (!requestFocus(ctx)) { isPlaying = false; MusicService.sync(ctx); return }
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(audioAttributes)
            mp.setWakeMode(ctx, PowerManager.PARTIAL_WAKE_LOCK)
            mp.setDataSource(ctx, queue[i].uri)
            mp.setOnCompletionListener {
                when {
                    MusicSettings.repeat == Repeat.ONE -> startAt(ctx, index)
                    index < queue.lastIndex -> startAt(ctx, index + 1)
                    MusicSettings.repeat == Repeat.ALL && queue.isNotEmpty() -> startAt(ctx, 0)
                    else -> { isPlaying = false; player?.release(); player = null; MusicService.sync(ctx) }
                }
            }
            mp.prepare()
            if (pendingSeek > 0) { mp.seekTo(pendingSeek.toInt()); pendingSeek = 0L }
            mp.start()
            player = mp
            isPlaying = true
            handler.removeCallbacks(saver); handler.postDelayed(saver, 15_000L)
        } catch (e: Exception) {
            isPlaying = false
        }
        save(ctx)
        MusicService.sync(ctx)
    }

    // ---- audio focus: pause for calls and other apps, resume after a short interruption

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> { pausedByFocusLoss = false; pause() }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (isPlaying) { pausedByFocusLoss = true; pause() }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (pausedByFocusLoss) { pausedByFocusLoss = false; appContext?.let { resume(it) } }
            }
        }
    }

    private fun requestFocus(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
            .also { focusRequest = it }
        return am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus(ctx: Context) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        focusRequest?.let { am.abandonAudioFocusRequest(it) }
    }

    private fun query(context: Context): List<Track> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DURATION,
            if (android.os.Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA
        )
        val out = mutableListOf<Track>()
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.IS_MUSIC} != 0",
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { c ->
                while (c.moveToNext()) {
                    out += Track(
                        id = c.getLong(0),
                        title = c.getString(1) ?: "untitled",
                        artist = c.getString(2)?.takeUnless { it == "<unknown>" } ?: "unknown artist",
                        album = c.getString(3) ?: "unknown album",
                        albumId = c.getLong(4),
                        trackNo = c.getInt(5) % 1000,
                        durationMs = c.getLong(6),
                        folder = c.getString(7)?.let { path ->
                            if (android.os.Build.VERSION.SDK_INT >= 29) path
                            else path.substringBeforeLast('/', "").substringAfter("/emulated/0/", path.substringBeforeLast('/', "")) + "/"
                        }.orEmpty()
                    )
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        }
        return out
    }
}
