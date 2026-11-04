package com.hmj.twotone.ui

import android.Manifest
import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.hmj.twotone.music.Music
import com.hmj.twotone.music.MusicSettings
import com.hmj.twotone.music.Playlists
import com.hmj.twotone.music.Track
import kotlinx.coroutines.delay
import kotlin.math.max

private class MEntry(
    val label: String,
    val trailing: String = "▸",
    /** What holding the wheel's middle (or the row) does: usually the song's options. */
    val hold: (() -> Unit)? = null,
    val action: () -> Unit
)

/** One level of the menu. Its rows are rebuilt each time, so playlist changes show at once. */
private class MLevel(val title: String, val cover: Track? = null, val build: () -> List<MEntry>)

fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

/** The music player: a list above, a click wheel below. Menu goes back. */
@Composable
fun MusicScreen(onBack: () -> Unit, onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val gate = rememberPermissionGate(listOf(audioPermission()))
    if (!gate.granted) {
        PageColumn {
            PageHeader("music", onBack)
            AskPermission("Music plays the audio files stored on this phone, so it needs permission to read them.", gate)
        }
        return
    }

    LaunchedEffect(Unit) {
        MusicSettings.load(ctx)
        Playlists.load(ctx)
        Music.load(ctx)
    }
    AskForNotificationsOnce()

    val levels = remember { mutableStateListOf<MLevel>() }
    var nowPlaying by remember { mutableStateOf(Music.current != null) }
    var selected by remember { mutableIntStateOf(0) }
    /** When set, a page for typing a playlist name is shown; the lambda gets the name. */
    var naming by remember { mutableStateOf<((String) -> Unit)?>(null) }
    /** The find page (search songs, artists and albums by typing). */
    var finding by remember { mutableStateOf(false) }
    /** Scrubbing: on now playing, hold the middle, then turn to move through the song. */
    var scrubbing by remember { mutableStateOf(false) }
    var scrubPos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(scrubbing, scrubPos) {
        if (scrubbing) { delay(4_000); scrubbing = false }
    }
    val tracks = MusicSettings.visible(Music.tracks)

    fun push(level: MLevel) { levels.add(level); selected = 0 }
    fun pop(n: Int = 1) { repeat(n) { if (levels.isNotEmpty()) levels.removeAt(levels.lastIndex) }; selected = 0 }

    /** Options for one song: play now / next, add to a playlist, remove from this one. */
    fun options(t: Track, list: List<Track>, i: Int, playlistId: Long?) = MLevel(t.title.lowercase()) {
        buildList {
            add(MEntry("play now", "") { Music.play(ctx, list, i); pop(); nowPlaying = true })
            add(MEntry("play next", "") { Music.playNext(ctx, t); pop() })
            add(MEntry("add to a playlist") {
                push(MLevel("add to") {
                    listOf(MEntry("+ new playlist", "") {
                        naming = { name -> Playlists.create(name, t); pop(2) }
                    }) + Playlists.all.map { pl ->
                        MEntry(pl.name, if (t.id in pl.trackIds) "already in" else "") { Playlists.add(pl.id, t); pop(2) }
                    }
                })
            })
            if (playlistId != null) add(MEntry("remove from this playlist", "") { Playlists.remove(playlistId, t); pop() })
        }
    }

    fun songs(title: String, list: () -> List<Track>, playlistId: Long? = null, cover: Track? = null) = MLevel(title, cover) {
        val l = list()
        l.mapIndexed { i, t ->
            MEntry(t.title, "", hold = { push(options(t, l, i, playlistId)) }) { Music.play(ctx, l, i); nowPlaying = true }
        }
    }

    fun playlistLevel(id: Long): MLevel = MLevel(Playlists.byId(id)?.name ?: "playlist") {
        val pl = Playlists.byId(id) ?: return@MLevel emptyList()
        val l = Playlists.tracksOf(pl, Music.tracks)
        buildList {
            if (l.isNotEmpty()) {
                add(MEntry("play", "${l.size}") { Music.play(ctx, l, 0); nowPlaying = true })
                add(MEntry("shuffle", "") { Music.play(ctx, l.shuffled(), 0); nowPlaying = true })
            } else {
                add(MEntry("empty: hold a song to add it", "") { })
            }
            l.forEachIndexed { i, t ->
                add(MEntry(t.title, "", hold = { push(options(t, l, i, id)) }) { Music.play(ctx, l, i); nowPlaying = true })
            }
            add(MEntry("rename", "") { naming = { name -> Playlists.rename(id, name) } })
            add(MEntry("delete playlist") {
                push(MLevel("delete ${pl.name}?") {
                    listOf(
                        MEntry("yes, delete it", "") { Playlists.delete(id); pop(2) },
                        MEntry("keep it", "") { pop() }
                    )
                })
            })
        }
    }

    fun menu() {
        when {
            scrubbing -> scrubbing = false
            nowPlaying -> nowPlaying = false
            levels.isNotEmpty() -> pop()
            else -> onBack()
        }
    }

    // Songs, artists, albums, playlists, settings. "now playing" sits on top while something is queued.
    val root = MLevel("music") {
        buildList {
            Music.current?.let { cur ->
                add(MEntry("now playing", hold = { push(options(cur, Music.queue, Music.index, null)) }) { nowPlaying = true })
            }
            add(MEntry("songs", "${tracks.size}") {
                // Shuffle sits at the top of the song list.
                push(MLevel("songs") {
                    listOf(MEntry("find", "") { finding = true }, MEntry("shuffle all", "") {
                        if (tracks.isNotEmpty()) { Music.play(ctx, tracks.shuffled(), 0); nowPlaying = true }
                    }) + songs("songs", { tracks }).build()
                })
            })
            add(MEntry("artists", "${tracks.map { it.artist }.distinct().size}") {
                push(MLevel("artists") {
                    tracks.groupBy { it.artist }.toList().sortedBy { it.first.lowercase() }.map { (artist, ts) ->
                        MEntry(artist, "${ts.size}") { push(songs(artist, { ts.sortedWith(compareBy({ it.album.lowercase() }, { it.trackNo })) })) }
                    }
                })
            })
            add(MEntry("albums", "${tracks.map { it.album }.distinct().size}") {
                push(MLevel("albums") {
                    tracks.groupBy { it.album }.toList().sortedBy { it.first.lowercase() }.map { (album, ts) ->
                        MEntry(album, "${ts.size}") { val sorted = ts.sortedBy { it.trackNo }; push(songs(album, { sorted }, cover = sorted.first())) }
                    }
                })
            })
            add(MEntry("playlists", "${Playlists.all.size}") {
                push(MLevel("playlists") {
                    listOf(MEntry("+ new playlist", "") {
                        naming = { name -> val p = Playlists.create(name); push(playlistLevel(p.id)) }
                    }) + Playlists.all.map { pl -> MEntry(pl.name, "${pl.trackIds.size}") { push(playlistLevel(pl.id)) } }
                })
            })
            add(MEntry("settings") { onSettings() })
        }
    }
    val level = levels.lastOrNull() ?: root
    val entries = level.build()
    val count = entries.size

    // Finding a song
    if (finding) {
        MusicFindPage(
            tracks = tracks,
            onPlay = { list, i -> finding = false; Music.play(ctx, list, i); nowPlaying = true },
            onOptions = { list, i -> finding = false; push(options(list[i], list, i, null)) },
            onClose = { finding = false }
        )
        return
    }

    // Typing a playlist name
    val nameFor = naming
    if (nameFor != null) {
        PlaylistNamePage(onSave = { name -> naming = null; nameFor(name) }, onCancel = { naming = null })
        return
    }

    BackHandler { menu() }

    PageColumn {
        PageHeader(if (nowPlaying) "now playing" else level.title, onBack = { menu() })
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // The wheel takes a bit under half the page; the list gets the rest.
            val wheel = min(maxWidth * 0.68f, maxHeight * 0.46f)
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        nowPlaying -> NowPlaying(scrubbing, scrubPos)
                        tracks.isEmpty() && levels.isNotEmpty() ->
                            Hint("No music found. Copy audio files into the Music folder over USB.")
                        else -> Column(Modifier.fillMaxSize()) {
                            level.cover?.let { AlbumHeader(it, level.build().size) }
                            Box(Modifier.weight(1f)) {
                                WheelList(entries, selected) { i -> selected = i; entries[i].action() }
                            }
                        }
                    }
                }
                SolidRule(thickness = 3.dp)
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 3.dp)) {
                    val extras = listOfNotNull(
                        if (MusicSettings.repeat != com.hmj.twotone.music.Repeat.OFF) "repeat ${MusicSettings.repeat.label}" else null,
                        if (Music.sleepAt > 0) "sleep " + java.time.Instant.ofEpochMilli(Music.sleepAt).atZone(java.time.ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0) else null
                    ).joinToString(" · ").let { if (it.isEmpty()) "" else " · $it" }
                    val status = (Music.current?.let { (if (Music.isPlaying) "▸ " else "‖ ") + it.title } ?: "${tracks.size} songs") + extras
                    val where = if (scrubbing) "turn to move · press middle to finish · " else if (nowPlaying) "turn for volume · hold middle to scrub · " else if (count > 0) "${selected.coerceIn(0, count - 1) + 1} of $count · " else ""
                    BasicText(where + status, style = textStyle(14f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                    ClickWheel(
                        size = wheel,
                        onStep = { d ->
                            val t = Music.current
                            if (nowPlaying && scrubbing && t != null) {
                                scrubPos = (scrubPos + d * 5_000L).coerceIn(0L, t.durationMs)
                                Music.seekTo(scrubPos)
                            } else if (nowPlaying) adjustVolume(ctx, d)
                            else if (count > 0) selected = (selected + d).coerceIn(0, count - 1)
                        },
                        onCenter = {
                            if (scrubbing) scrubbing = false
                            else if (nowPlaying) Music.toggle(ctx)
                            else entries.getOrNull(selected)?.action?.invoke()
                        },
                        onMenu = { menu() },
                        onPrevious = { Music.previous(ctx) },
                        onNext = { Music.next(ctx) },
                        onPlayPause = { Music.toggle(ctx) },
                        onCenterHold = {
                            val t = Music.current
                            if (nowPlaying && t != null) { scrubPos = Music.positionMs(); scrubbing = true }
                            else entries.getOrNull(selected)?.hold?.invoke()
                        }
                    )
                }
            }
        }
    }
}

/** The page of the list that holds the selected row; the selected row is inverted. */
@Composable
private fun WheelList(entries: List<MEntry>, selected: Int, onTap: (Int) -> Unit) {
    val rowH = rowHeight()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val perPage = max(1, (maxHeight / rowH).toInt())
        val sel = selected.coerceIn(0, max(0, entries.lastIndex))
        val first = (sel / perPage) * perPage
        Column {
            entries.drop(first).take(perPage).forEachIndexed { j, e ->
                val i = first + j
                ListRow(e.label, trailing = e.trailing, selected = i == sel, onLongPress = e.hold) { onTap(i) }
            }
        }
    }
}

/**
 * Title, artist, a progress bar and the times: only what fits in the space the
 * wheel leaves, so nothing gets cut off. The album shows when there is room.
 */
@Composable
private fun NowPlaying(scrubbing: Boolean = false, scrubPos: Long = 0L) {
    val t = Music.current
    val playing = Music.isPlaying
    var pos by remember { mutableLongStateOf(0L) }
    // Every two seconds is enough for E-ink and saves battery.
    LaunchedEffect(t, playing) {
        while (true) {
            pos = Music.positionMs()
            if (!playing) break
            delay(2_000)
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 6.dp)) {
        val roomy = maxHeight > rowHeight() * 2.6f
        Column(Modifier.fillMaxSize(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
            if (t == null) {
                BasicText("Nothing is playing.", style = textStyle(18f))
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AlbumArtImage(t, if (roomy) 88.dp else 56.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    BasicText(t.title, style = textStyle(20f, bold = true), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    BasicText(t.artist, style = textStyle(15f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    if (roomy) BasicText(t.album, style = textStyle(15f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(8.dp))
            val ink = LocalPalette.current.ink
            val shownPos = if (scrubbing) scrubPos else pos
            val fraction = if (t.durationMs > 0) (shownPos.toFloat() / t.durationMs).coerceIn(0f, 1f) else 0f
            if (scrubbing) {
                // While scrubbing: an empty bar with a tall marker, like a playhead.
                androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(22.dp)) {
                    val line = 3.dp.toPx()
                    val top = 5.dp.toPx()
                    drawRect(ink, androidx.compose.ui.geometry.Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, size.height - 2 * top), style = androidx.compose.ui.graphics.drawscope.Stroke(line))
                    val x = size.width * fraction
                    drawRect(ink, androidx.compose.ui.geometry.Offset((x - 4.dp.toPx()).coerceAtLeast(0f), 0f), androidx.compose.ui.geometry.Size(8.dp.toPx(), size.height))
                }
            } else {
                Box(Modifier.fillMaxWidth().height(12.dp).border(3.dp, ink)) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(ink))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 3.dp)) {
                BasicText(clock(shownPos), Modifier.weight(1f), style = textStyle(14f), maxLines = 1)
                BasicText(if (scrubbing) "scrubbing" else if (playing) "playing" else "paused", Modifier.weight(1f), style = textStyle(14f).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center), maxLines = 1)
                BasicText("-" + clock((t.durationMs - shownPos).coerceAtLeast(0)), Modifier.weight(1f), style = textStyle(14f).copy(textAlign = androidx.compose.ui.text.style.TextAlign.End), maxLines = 1)
            }
        }
    }
}

private fun clock(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

private fun adjustVolume(ctx: Context, d: Int) {
    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    am.adjustStreamVolume(
        AudioManager.STREAM_MUSIC,
        if (d > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
        AudioManager.FLAG_SHOW_UI
    )
}


/** Type a name for a new (or renamed) playlist. */
@Composable
private fun PlaylistNamePage(onSave: (String) -> Unit, onCancel: () -> Unit) {
    val palette = LocalPalette.current
    var name by remember { mutableStateOf("") }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    BackHandler { onCancel() }
    PageColumn {
        PageHeader("playlist name", onBack = onCancel)
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("name: ", style = textStyle(18f, bold = true), maxLines = 1, softWrap = false)
            androidx.compose.foundation.text.BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = textStyle(18f),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.ink),
                modifier = Modifier.weight(1f).focusRequester(focus),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onSave(name.trim()) })
            )
        }
        SolidRule()
        ListRow("save", bold = true, trailing = "") { onSave(name.trim()) }
        ListRow("cancel", trailing = "") { onCancel() }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}


/** The album's cover, big, with its name, artist, number of songs and length. */
@Composable
private fun AlbumHeader(t: Track, songCount: Int) {
    val minutes = Music.tracks.filter { it.albumId == t.albumId }.sumOf { it.durationMs } / 60_000L
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AlbumArtImage(t, 84.dp, dots = 84)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                BasicText(t.album, style = textStyle(20f, bold = true), maxLines = 2, overflow = TextOverflow.Ellipsis)
                BasicText(t.artist, style = textStyle(15f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                BasicText("$songCount songs · $minutes min", style = textStyle(14f), maxLines = 1, softWrap = false)
            }
        }
        SolidRule(thickness = 3.dp)
    }
}


/**
 * Type part of a song title, artist or album. Matching songs are listed;
 * tap one to play it (the rest of the matches follow), hold it for its options.
 */
@Composable
private fun MusicFindPage(
    tracks: List<Track>,
    onPlay: (List<Track>, Int) -> Unit,
    onOptions: (List<Track>, Int) -> Unit,
    onClose: () -> Unit
) {
    val palette = LocalPalette.current
    var query by remember { mutableStateOf("") }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    BackHandler { onClose() }
    val q = query.trim()
    val found = remember(q, tracks) {
        if (q.isEmpty()) emptyList()
        else tracks.filter {
            it.title.contains(q, ignoreCase = true) || it.artist.contains(q, ignoreCase = true) || it.album.contains(q, ignoreCase = true)
        }.sortedWith(compareBy({ !it.title.startsWith(q, ignoreCase = true) }, { it.title.lowercase() }))
    }
    PageColumn {
        PageHeader("find music", onBack = onClose)
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("find: ", style = textStyle(18f, bold = true), maxLines = 1, softWrap = false)
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = textStyle(18f),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.ink),
                modifier = Modifier.weight(1f).focusRequester(focus)
            )
        }
        SolidRule()
        when {
            q.isEmpty() -> Hint("Type part of a song, artist or album.")
            found.isEmpty() -> Hint("Nothing matches \u201c$q\u201d.")
            else -> PagedList(found, Modifier.weight(1f), resetKey = q) { t ->
                val i = found.indexOf(t)
                ListRow(t.title.lowercase(), trailing = t.artist.lowercase().take(16), onLongPress = { onOptions(found, i) }) { onPlay(found, i) }
            }
        }
        if (q.isEmpty() || found.isEmpty()) androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        FooterLine(if (q.isEmpty()) "${tracks.size} songs" else "${found.size} found")
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}
