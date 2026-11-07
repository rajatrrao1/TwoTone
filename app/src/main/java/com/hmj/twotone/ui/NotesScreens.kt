package com.hmj.twotone.ui

import android.Manifest
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.provider.CalendarContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hmj.twotone.notes.Note
import com.hmj.twotone.notes.NotesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val FILTERS = listOf("all", "lists", "voice", "pinned", "archive")
private val ORDERS = listOf("newest", "oldest", "title")

private fun ago(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60_000L
    return when { m < 1 -> "now"; m < 60 -> "$m min"; m < 48 * 60 -> "${m / 60} h"; else -> "${m / (24 * 60)} d" }
}

/** A small boxed button, for rows of actions. */
@Composable
private fun BoxButton(label: String, modifier: Modifier, solid: Boolean = false, onPress: () -> Unit) {
    val p = LocalPalette.current
    Box(
        modifier.height(40.dp).border(2.dp, p.ink).background(if (solid) p.ink else p.paper).press(onPress = onPress),
        contentAlignment = Alignment.Center
    ) { BasicText(label, style = textStyle(16f, bold = true, color = if (solid) p.paper else p.ink, fixed = true), maxLines = 1) }
}

/**
 * Every note, pinned ones first, then by the order you choose. Filter to lists,
 * voice notes, pinned or the archive; find searches titles and text.
 */
@Composable
fun NotesScreen(
    store: NotesStore,
    onOpen: (String) -> Unit,
    onRecord: () -> Unit,
    onPlace: () -> Unit,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tick = LocalResumeTick.current
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var order by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var finding by rememberSaveable { mutableStateOf(false) }
    var optionsFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tick) { withContext(Dispatchers.IO) { store.reload() } }

    fun newNote(list: Boolean) = scope.launch {
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm"))
        val n = withContext(Dispatchers.IO) { store.create(if (list) "List $stamp" else "Note $stamp", if (list) "- [ ] " else "") }
        onOpen(n.path)
    }

    val target = optionsFor?.let { store.get(it) }
    if (target != null) {
        NoteOptions(store, target, onDone = { optionsFor = null })
        return
    }

    val q = query.trim()
    val shown = store.notes.filter { n ->
        when (FILTERS[filter]) {
            "lists" -> n.isList && !n.archived
            "voice" -> n.recording != null && !n.archived
            "pinned" -> n.path in store.pinned
            "archive" -> n.archived
            else -> !n.archived || q.isNotEmpty()  // a search still finds archived notes
        }
    }.filter { q.isEmpty() || it.title.contains(q, true) || it.text.contains(q, true) }
        .let { l ->
            when (ORDERS[order]) {
                "oldest" -> l.sortedBy { it.modified }
                "title" -> l.sortedBy { it.title.lowercase() }
                else -> l.sortedByDescending { it.modified }
            }
        }.sortedByDescending { it.path in store.pinned }

    PageColumn {
        PageHeader("notes", onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BoxButton("+ note", Modifier.weight(1f)) { newNote(false) }
            BoxButton("+ list", Modifier.weight(1f)) { newNote(true) }
            BoxButton("● record", Modifier.weight(1f), solid = true, onPress = onRecord)
        }
        if (finding) {
            val palette = LocalPalette.current
            val focus = remember { androidx.compose.ui.focus.FocusRequester() }
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("find: ", style = textStyle(18f, bold = true), maxLines = 1, softWrap = false)
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = textStyle(18f), cursorBrush = SolidColor(palette.ink),
                    modifier = Modifier.weight(1f).focusRequester(focus)
                )
                Box(Modifier.press { finding = false; query = "" }.padding(horizontal = 8.dp)) { BasicText("×", style = textStyle(20f, bold = true, fixed = true)) }
            }
            LaunchedEffect(Unit) { focus.requestFocus() }
        } else {
            Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).press { filter = (filter + 1) % FILTERS.size }.padding(start = 14.dp)) {
                    BasicText("show [${FILTERS[filter]}]", style = textStyle(15f, fixed = true), maxLines = 1)
                }
                Box(Modifier.weight(1f).press { order = (order + 1) % ORDERS.size }) {
                    BasicText("order [${ORDERS[order]}]", style = textStyle(15f, fixed = true), maxLines = 1)
                }
                Box(Modifier.press { finding = true }.padding(horizontal = 14.dp)) { BasicText("find", style = textStyle(15f, bold = true, fixed = true)) }
            }
        }
        SolidRule(thickness = 3.dp)
        if (shown.isEmpty()) {
            Hint(if (q.isNotEmpty()) "Nothing matches \\u201c$q\\u201d." else "No notes here yet.")
            Spacer(Modifier.weight(1f))
        } else {
            PagedList(shown, Modifier.weight(1f), resetKey = "$filter/$order/$q/${shown.size}") { n ->
                val left = n.tasks.count { !it }
                val trailing = when {
                    n.isList -> if (left == 0) "done" else "$left left"
                    n.recording != null -> "voice"
                    n.folder.isNotEmpty() && !n.archived -> n.folder.lowercase().take(10)
                    else -> ago(n.modified)
                }
                ListRow(
                    (if (n.path in store.pinned) "* " else "") + n.title,
                    trailing = trailing,
                    onLongPress = { optionsFor = n.path }
                ) { onOpen(n.path) }
            }
        }
        Column(Modifier.press(onPress = onPlace)) {
            FooterLine("${shown.size} notes · kept in [${store.folderName ?: "the launcher"}] · hold a note for more")
        }
    }
}

/** Pin, archive, remind me, delete. */
@Composable
private fun NoteOptions(store: NotesStore, n: Note, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    BackHandler { onDone() }
    PageColumn {
        PageHeader(n.title, onBack = onDone)
        if (confirm) {
            Hint(if (n.recording != null) "This deletes the note and its recording." else "This deletes the note.")
            ListRow("delete it", trailing = "") { store.delete(n.path); onDone() }
            ListRow("keep it", trailing = "") { confirm = false }
            return@PageColumn
        }
        ListRow(if (n.path in store.pinned) "unpin" else "pin to the top", trailing = "") { store.togglePin(n.path); onDone() }
        ListRow(if (n.archived) "take out of the archive" else "archive", trailing = "") { store.archive(n.path, !n.archived); onDone() }
        ListRow("remind me", trailing = "calendar ▸") {
            // A new calendar event with the note in it; the day and time are chosen there.
            try {
                ctx.startActivity(
                    Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.Events.TITLE, n.title)
                        .putExtra(CalendarContract.Events.DESCRIPTION, n.text.take(2000))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) { }
            onDone()
        }
        ListRow("delete", trailing = "") { confirm = true }
    }
}

/**
 * A note. A note with "- [ ]" lines opens as a list: tap an item to tick it,
 * hold it to remove it, add new ones at the foot. "text" shows the words as
 * they are. A linked recording plays from the top. Everything saves as you go.
 */
@Composable
fun NoteEditScreen(store: NotesStore, path: String, onBack: () -> Unit) {
    // Read once: the file may be renamed while you type its title.
    val note = remember(path) { store.get(path) }
    if (note == null) {
        PageColumn { PageHeader("note", onBack); Hint("This note isn't there any more.") }
        return
    }
    var currentPath by remember(path) { mutableStateOf(path) }
    var title by remember(path) { mutableStateOf(note.title) }
    var text by remember(path) { mutableStateOf(note.text) }
    var asList by remember(path) { mutableStateOf(note.isList) }
    // The text editor works on the text plus the cursor, so Enter can continue lists.
    var field by remember(path) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(note.text)) }
    LaunchedEffect(asList) { if (!asList && field.text != text) field = androidx.compose.ui.text.input.TextFieldValue(text, androidx.compose.ui.text.TextRange(text.length)) }
    val latestTitle by rememberUpdatedState(title)
    val latestText by rememberUpdatedState(text)
    val palette = LocalPalette.current

    // Save a moment after typing stops, and when leaving.
    LaunchedEffect(text, title) {
        delay(800)
        val p = currentPath
        currentPath = withContext(Dispatchers.IO) { store.save(p, latestTitle, latestText) }
    }
    DisposableEffect(path) {
        onDispose {
            if (latestText.isBlank() && store.get(currentPath)?.recording == null) store.delete(currentPath)
            else store.save(currentPath, latestTitle, latestText)
        }
    }

    PageColumn {
        PageHeader(if (asList) "list" else "note", onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = title,
                onValueChange = { title = it.replace("\n", "") },
                singleLine = true,
                textStyle = textStyle(22f, bold = true),
                cursorBrush = SolidColor(palette.ink),
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            BoxButton(if (asList) "text" else "list", Modifier.width(64.dp)) {
                if (!asList && text.lines().none { Note.TASK.containsMatchIn(it) }) {
                    // Turn plain lines into list items
                    text = text.lines().filter { it.isNotBlank() }.joinToString("\n") { "- [ ] ${it.trim()}" }.ifBlank { "- [ ] " }
                }
                asList = !asList
            }
        }
        SolidRule()
        store.get(currentPath)?.let { n -> store.recordingUri(n)?.let { RecordingPlayer(it) } }
        if (asList) ChecklistView(text, onChange = { text = it })
        else BasicTextField(
            value = field,
            onValueChange = { new -> field = continueLists(field, new); text = field.text },
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
            textStyle = textStyle(18f),
            cursorBrush = SolidColor(palette.ink)
        )
    }
}

/** The list view: one row per "- [ ]" line, other lines shown as headings. */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ChecklistView(text: String, onChange: (String) -> Unit) {
    val lines = text.lines()
    val palette = LocalPalette.current
    var adding by remember { mutableStateOf("") }
    val addFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    // Rows: index of each non-empty line
    val rows = lines.indices.filter { lines[it].isNotBlank() && Note.TASK.find(lines[it])?.groupValues?.get(3)?.isNotBlank() != false }
    fun setLine(i: Int, v: String?) {
        val l = lines.toMutableList()
        if (v == null) l.removeAt(i) else l[i] = v
        onChange(l.joinToString("\n"))
    }
    fun add() {
        val item = adding.trim()
        if (item.isEmpty()) return
        val l = lines.filterNot { Note.TASK.find(it)?.groupValues?.get(3)?.isBlank() == true }.toMutableList()
        val lastTask = l.indexOfLast { Note.TASK.containsMatchIn(it) }
        l.add(if (lastTask < 0) l.size else lastTask + 1, "- [ ] $item")
        onChange(l.joinToString("\n"))
        adding = ""
        try { addFocus.requestFocus() } catch (e: Exception) { }
    }
    val ticked = lines.count { Note.TASK.find(it)?.groupValues?.get(2)?.let { m -> m != " " } == true }

    PagedList(rows, Modifier.weight(1f), resetKey = rows.size) { i ->
        val m = Note.TASK.find(lines[i])
        if (m == null) {
            TextRow(lines[i].trim().trimStart('#', ' '), bold = true)
        } else {
            val done = m.groupValues[2] != " "
            ListRow(
                (if (done) "[x] " else "[ ] ") + m.groupValues[3],
                trailing = "",
                onLongPress = { setLine(i, null) }
            ) { setLine(i, m.groupValues[1] + (if (done) "[ ] " else "[x] ") + m.groupValues[3]) }
        }
    }
    if (ticked > 0) {
        Box(Modifier.fillMaxWidth().press { onChange(lines.filterNot { Note.TASK.find(it)?.groupValues?.get(2)?.let { g -> g != " " } == true }.joinToString("\n")) }
            .padding(horizontal = 14.dp, vertical = 6.dp)) {
            BasicText("remove $ticked ticked", style = textStyle(15f), maxLines = 1)
        }
    }
    SolidRule(thickness = 3.dp)
    // Add at the foot, so the keyboard doesn't cover the list until you want it
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText("add: ", style = textStyle(18f, bold = true), maxLines = 1, softWrap = false)
        BasicTextField(
            value = adding,
            onValueChange = { v ->
                // Enter (on any keyboard) adds the item and leaves the box ready for the next one.
                if (v.contains('\n')) { adding = v.replace("\n", ""); add() } else adding = v
            },
            singleLine = true,
            textStyle = textStyle(18f), cursorBrush = SolidColor(palette.ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { add() }, onDone = { add() }),
            modifier = Modifier.weight(1f).focusRequester(addFocus)
        )
        BoxButton("add", Modifier.width(60.dp)) { add() }
    }
}

/** Play / pause a note's recording, with how far along it is. */
@Composable
private fun RecordingPlayer(uri: android.net.Uri) {
    val ctx = LocalContext.current
    val player = remember(uri) { MediaPlayer() }
    var ready by remember(uri) { mutableStateOf(false) }
    var playing by remember(uri) { mutableStateOf(false) }
    var pos by remember(uri) { mutableLongStateOf(0L) }
    DisposableEffect(uri) {
        try { player.setDataSource(ctx, uri); player.prepare(); ready = true } catch (e: Exception) { }
        player.setOnCompletionListener { playing = false; pos = 0L }
        onDispose { player.release() }
    }
    LaunchedEffect(playing) { while (playing) { pos = player.currentPosition.toLong(); delay(1_000) } }
    fun mmss(ms: Long) = "%d:%02d".format(ms / 60_000, (ms / 1000) % 60)
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        BoxButton(if (playing) "‖ pause" else "▸ play", Modifier.width(110.dp), solid = true) {
            if (!ready) return@BoxButton
            if (playing) { player.pause(); playing = false }
            else { com.hmj.twotone.music.Music.pause(); player.start(); playing = true }
        }
        Spacer(Modifier.width(12.dp))
        BasicText(
            if (ready) "recording · ${mmss(pos)} / ${mmss(player.duration.toLong())}" else "the recording couldn't be opened",
            style = textStyle(14f), maxLines = 1
        )
    }
    SolidRule()
}

/**
 * Records straight away. Stop makes a voice note with the recording linked
 * the way Obsidian links (![[Voice ….m4a]]). The screen stays on while it records.
 */
@Composable
fun RecordScreen(store: NotesStore, onSaved: (String) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val gate = rememberPermissionGate(listOf(Manifest.permission.RECORD_AUDIO))
    if (!gate.granted) {
        PageColumn {
            PageHeader("record", onBack)
            AskPermission("Voice notes need the microphone.", gate)
        }
        return
    }
    val stamp = remember { LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm")) }
    val fileName = "Voice $stamp.m4a"
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var fd by remember { mutableStateOf<android.os.ParcelFileDescriptor?>(null) }
    var started by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var failed by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        try {
            val pfd = store.recordingTarget(fileName)
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(96_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(pfd.fileDescriptor)
            r.prepare()
            com.hmj.twotone.music.Music.pause()
            r.start()
            recorder = r; fd = pfd; started = System.currentTimeMillis()
        } catch (e: Exception) {
            failed = "Recording couldn't start: ${e.message ?: e.javaClass.simpleName}"
        }
        (ctx as? android.app.Activity)?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            (ctx as? android.app.Activity)?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            try { recorder?.stop() } catch (e: Exception) { }
            recorder?.release()
            try { fd?.close() } catch (e: Exception) { }
        }
    }
    LaunchedEffect(started) { while (started > 0) { elapsed = System.currentTimeMillis() - started; delay(1_000) } }

    fun stopAndSave() {
        val r = recorder ?: return
        try { r.stop() } catch (e: Exception) { }
        r.release(); recorder = null
        try { fd?.close() } catch (e: Exception) { }
        fd = null
        scope.launch {
            val n = withContext(Dispatchers.IO) { store.create("Voice $stamp", "![[${fileName}]]\n\n") }
            onSaved(n.path)
        }
    }

    BackHandler { onBack() }
    PageColumn {
        PageHeader("record", onBack)
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            if (failed != null) { Hint(failed!!); return@Column }
            PixelText("%02d:%02d".format(elapsed / 60_000, (elapsed / 1000) % 60), cell = 10.dp)
            Spacer(Modifier.height(14.dp))
            BasicText("recording", style = textStyle(18f, fixed = true).copy(textAlign = TextAlign.Center))
            Spacer(Modifier.height(30.dp))
            BoxButton("■ stop and save", Modifier.padding(horizontal = 30.dp).fillMaxWidth(), solid = true) { stopAndSave() }
            Spacer(Modifier.height(10.dp))
            BoxButton("cancel", Modifier.padding(horizontal = 30.dp).fillMaxWidth()) { onBack() }
        }
    }
}

/** Keep notes inside the launcher, or in a folder (an Obsidian vault, a synced folder). */
@Composable
fun NotesPlaceScreen(store: NotesStore, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var bring by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree == null) return@rememberLauncherForActivityResult
        busy = true
        val name = tree.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')?.ifBlank { null } ?: "your folder"
        scope.launch { withContext(Dispatchers.IO) { store.moveTo(tree, name, bring) }; busy = false; onBack() }
    }
    PageColumn {
        PageHeader("notes kept in", onBack)
        Hint("Notes are plain Markdown files. In a folder of your own they open in Obsidian or any editor, and another app (such as Syncthing) can keep them in step with a computer.")
        ListRow("the launcher", trailing = if (store.folderUri == null) "•" else "") {
            if (store.folderUri == null) return@ListRow
            busy = true
            scope.launch { withContext(Dispatchers.IO) { store.moveTo(null, null, bring) }; busy = false; onBack() }
        }
        ListRow("a folder on the phone", trailing = if (store.folderUri != null) "[${store.folderName}]" else "▸") { pick.launch(null) }
        ListRow("bring these notes along", trailing = bracket(if (bring) "yes" else "no")) { bring = !bring }
        if (busy) Hint("Copying the notes.")
        Hint("The old place keeps every note. A different note with the same name is kept beside it as \\u201cname (other copy)\\u201d.")
    }
}


private val LIST_MARK = Regex("^(\\s*)([-*] \\[[ xX]] |[-*] |(\\d+)\\. )(.*)$")

/**
 * Enter at the end of a list line starts the next item the same way
 * ("- [ ] ", "- ", "2. "); Enter on an item left empty ends the list instead.
 */
private fun continueLists(
    old: androidx.compose.ui.text.input.TextFieldValue,
    new: androidx.compose.ui.text.input.TextFieldValue
): androidx.compose.ui.text.input.TextFieldValue {
    val cursor = new.selection.start
    val typedNewline = new.text.length == old.text.length + 1 && cursor > 0 && new.text[cursor - 1] == '\n' && new.selection.collapsed
    if (!typedNewline) return new
    val lineStart = new.text.lastIndexOf('\n', cursor - 2).let { if (it < 0) 0 else it + 1 }
    val line = new.text.substring(lineStart, cursor - 1)
    val m = LIST_MARK.find(line) ?: return new
    val (indent, mark, number, rest) = m.destructured
    if (rest.isBlank()) {
        // An empty item: take the marker away and stop the list there.
        val t = new.text.substring(0, lineStart) + new.text.substring(cursor)
        return androidx.compose.ui.text.input.TextFieldValue(t, androidx.compose.ui.text.TextRange(lineStart))
    }
    val next = when {
        number.isNotEmpty() -> "${number.toInt() + 1}. "
        mark.contains("[") -> mark.substring(0, 2) + "[ ] "
        else -> mark
    }
    val insert = indent + next
    val t = new.text.substring(0, cursor) + insert + new.text.substring(cursor)
    return androidx.compose.ui.text.input.TextFieldValue(t, androidx.compose.ui.text.TextRange(cursor + insert.length))
}
