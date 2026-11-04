package com.hmj.twotone.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hmj.twotone.PageKeys
import com.hmj.twotone.books.Book
import com.hmj.twotone.books.Epub
import com.hmj.twotone.books.Library
import com.hmj.twotone.music.AlbumArt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---- library ------------------------------------------------------------------

/** Your books: the one you're reading on top with its cover, then the rest. */
@Composable
fun BooksScreen(onOpen: (String) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var removing by remember { mutableStateOf<Book?>(null) }

    LaunchedEffect(Unit) {
        Library.load(ctx)
        val added = withContext(Dispatchers.IO) { Library.rescan(ctx) }
        if (added > 0) message = if (added == 1) "Found 1 new book." else "Found $added new books."
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) scope.launch {
            message = "Looking for books."
            val n = withContext(Dispatchers.IO) { Library.addFolder(ctx, tree) }
            message = if (n == 0) "No new EPUB books in that folder." else if (n == 1) "Added 1 book." else "Added $n books."
        }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val b = withContext(Dispatchers.IO) { Library.addFile(ctx, uri) }
            message = if (b == null) "That file couldn't be read as an EPUB book." else "Added \u201c${b.title}\u201d."
        }
    }

    val target = removing
    if (target != null) {
        BackHandler { removing = null }
        PageColumn {
            PageHeader(target.title.lowercase(), onBack = { removing = null })
            Hint("This removes the launcher's copy. The file in your folder is not touched.")
            ListRow("remove from books", trailing = "") { Library.remove(ctx, target.id); removing = null }
            ListRow("keep it", trailing = "") { removing = null }
        }
        return
    }

    val sorted = Library.books.sortedWith(compareByDescending<Book> { it.lastRead }.thenBy { it.title.lowercase() })
    val reading = sorted.firstOrNull { it.lastRead > 0 }
    val rows: List<Book?> = listOf<Book?>(null) + sorted

    PageColumn {
        PageHeader("books", onBack)
        message?.let { Hint(it) }
        if (reading != null) {
            ContinueReading(reading) { onOpen(reading.id) }
            SolidRule(thickness = 3.dp)
        }
        PagedList(rows, Modifier.weight(1f), resetKey = Library.books.size) { b ->
            if (b == null) {
                ListRow("+ add a folder of books") { pickFolder.launch(null) }
            } else {
                ListRow(
                    b.title.lowercase(),
                    trailing = if (b.percent > 0) "${b.percent}%" else "",
                    onLongPress = { removing = b }
                ) { onOpen(b.id) }
            }
        }
        if (Library.books.isEmpty()) {
            ListRow("+ add one book file") { pickFile.launch(arrayOf("application/epub+zip", "application/octet-stream")) }
        }
        FooterLine(if (Library.books.size == 1) "1 book" else "${Library.books.size} books")
    }
}

@Composable
private fun ContinueReading(b: Book, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().press(onPress = onOpen).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        BookCover(b, 64.dp, 92.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            BasicText("continue", style = textStyle(14f), maxLines = 1)
            BasicText(b.title, style = textStyle(20f, bold = true), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (b.author.isNotBlank()) BasicText(b.author, style = textStyle(14f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            ProgressBar(b.percent / 100f)
        }
    }
}

/** The book's cover, dithered to two colours like album covers. */
@Composable
private fun BookCover(b: Book, w: Dp, h: Dp) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val img by produceState<ImageBitmap?>(null, b.id, p) {
        value = withContext(Dispatchers.IO) {
            try {
                val f = Library.coverFile(ctx, b.id)
                if (!f.exists()) null
                else BitmapFactory.decodeFile(f.path)?.let { src ->
                    // Dither the middle square; it stands in well for a portrait cover.
                    AlbumArt.dither(src, 72, p.ink.toArgb(), p.paper.toArgb()).asImageBitmap()
                }
            } catch (e: Exception) { null }
        }
    }
    Box(Modifier.size(w, h).border(2.dp, p.ink), contentAlignment = Alignment.Center) {
        val i = img
        if (i != null) Image(i, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, filterQuality = FilterQuality.None)
        else BasicText(b.title.take(1).uppercase(), style = textStyle(30f, bold = true, fixed = true))
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    val ink = LocalPalette.current.ink
    Canvas(Modifier.fillMaxWidth().height(10.dp)) {
        val line = 2.dp.toPx()
        drawRect(ink, style = androidx.compose.ui.graphics.drawscope.Stroke(line))
        drawRect(ink, size = androidx.compose.ui.geometry.Size(size.width * fraction.coerceIn(0f, 1f), size.height))
    }
}

// ---- reader -------------------------------------------------------------------

/** Reading sizes: for pixel fonts exact pixel sizes, otherwise sp. */
private val READER_PX = listOf(16f, 24f, 32f, 48f)
private val READER_SP = listOf(15f, 17f, 19f, 22f, 26f)

/**
 * One page at a time, never scrolling. Tap the left third to go back a page and
 * the rest to go forward; volume keys turn pages too. Tap the bar at the bottom
 * for contents and text size. Your place is saved on every page.
 */
@Composable
fun ReaderScreen(bookId: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val palette = LocalPalette.current
    LaunchedEffect(Unit) { Library.load(ctx) }
    val book = Library.byId(bookId)
    if (book == null) {
        PageColumn { PageHeader("book", onBack); Hint("This book isn't in your library any more.") }
        return
    }

    val sp = remember { ctx.getSharedPreferences("twotone_books", android.content.Context.MODE_PRIVATE) }
    var sizeStep by remember { mutableIntStateOf(sp.getInt("readerSize", 1)) }
    var fontChoice by remember { mutableStateOf(sp.getString("readerFont", "same") ?: "same") }

    val epub = remember(bookId) { try { Epub(Library.file(ctx, book)) } catch (e: Exception) { null } }
    DisposableEffect(epub) { onDispose { try { epub?.close() } catch (e: Exception) { } } }
    if (epub == null) {
        PageColumn { PageHeader("book", onBack); Hint("This book couldn't be opened. Try removing it and adding it again.") }
        return
    }

    var chapter by remember { mutableIntStateOf(book.chapter.coerceIn(0, (epub.spine.size - 1).coerceAtLeast(0))) }
    /** Where to land when a chapter has been laid out: a character offset, or -1 = its last page. */
    var landAt by remember { mutableIntStateOf(book.offset) }
    var page by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var contents by remember { mutableStateOf(false) }
    var showMarks by remember { mutableStateOf(false) }
    var marks by remember(bookId) { mutableStateOf(Library.marks(ctx, bookId)) }
    /** A word being looked up (hold a word on the page). */
    var lookup by remember { mutableStateOf<String?>(null) }
    /** Your reading speed in characters a minute, learnt as you read. */
    var cpm by remember { mutableStateOf(sp.getFloat("readerCpm", 1000f)) }

    // null while a chapter is being read from the file
    val text by produceState<AnnotatedString?>(null, chapter) {
        value = null
        value = withContext(Dispatchers.IO) { try { epub.chapter(chapter) } catch (e: Exception) { AnnotatedString("This part couldn't be read.") } }
    }

    // ---- dictionary
    val word = lookup
    if (word != null) {
        DictionaryPage(word, epub.language) { lookup = null }
        return
    }

    // ---- bookmarks
    if (showMarks) {
        BackHandler { showMarks = false }
        PageColumn {
            PageHeader("bookmarks", onBack = { showMarks = false })
            if (marks.isEmpty()) Hint("No bookmarks yet. Tap the top right corner of a page to add one.")
            PagedList(marks, Modifier.weight(1f), resetKey = marks.size) { m ->
                ListRow(
                    m.snippet.ifBlank { "part ${m.chapter + 1}" },
                    trailing = "part ${m.chapter + 1}",
                    onLongPress = { marks = marks - m; Library.setMarks(ctx, bookId, marks) }
                ) {
                    chapter = m.chapter; landAt = m.offset; showMarks = false; menu = false
                }
            }
            FooterLine("tap to go there · hold to delete")
        }
        return
    }

    // ---- contents
    if (contents) {
        BackHandler { contents = false }
        val names by produceState(emptyList<String>(), epub) {
            value = withContext(Dispatchers.IO) { epub.spine.indices.map { i -> try { epub.chapterName(i) } catch (e: Exception) { "part ${i + 1}" } } }
        }
        PageColumn {
            PageHeader("contents", onBack = { contents = false })
            if (names.isEmpty()) Hint("Reading the contents.")
            PagedList(names.indices.toList(), Modifier.weight(1f), showIndex = chapter) { i ->
                ListRow(names[i].lowercase(), trailing = if (i == chapter) "•" else "") {
                    chapter = i; landAt = 0; contents = false; menu = false
                }
            }
        }
        return
    }

    // ---- reading options
    if (menu) {
        BackHandler { menu = false }
        val sizes = if (LocalPixelGrids.current.large > 0f) READER_PX.size else READER_SP.size
        PageColumn {
            PageHeader(book.title.lowercase(), onBack = { menu = false })
            ListRow("contents") { contents = true }
            val here = currentOffsetHolder.intValue
            val marked = marks.firstOrNull { it.chapter == chapter && it.offset == here }
            ListRow(if (marked != null) "remove this page's bookmark" else "bookmark this page", trailing = "") {
                marks = if (marked != null) marks - marked
                else marks + Library.Mark(chapter, here, footerSnippet.value, System.currentTimeMillis())
                Library.setMarks(ctx, bookId, marks)
                menu = false
            }
            ListRow("bookmarks", trailing = "${marks.size} ▸") { showMarks = true }
            ListRow("text size", trailing = "${sizeStep + 1} of $sizes") {
                sizeStep = (sizeStep + 1) % sizes; sp.edit().putInt("readerSize", sizeStep).apply(); landAt = currentOffsetHolder.intValue
            }
            ListRow("font", trailing = when (fontChoice) { "serif" -> "serif"; "sans" -> "sans"; else -> "same as launcher" }) {
                fontChoice = when (fontChoice) { "same" -> "serif"; "serif" -> "sans"; else -> "same" }
                sp.edit().putString("readerFont", fontChoice).apply(); landAt = currentOffsetHolder.intValue
            }
            ListRow("back to reading", trailing = "") { menu = false }
            ListRow("close the book", trailing = "") { onBack() }
        }
        return
    }

    // ---- the page
    val grid = LocalPixelGrids.current.large
    val family: FontFamily = when (fontChoice) {
        "serif" -> FontFamily.Serif
        "sans" -> FontFamily.SansSerif
        else -> LocalFontFamily.current
    }
    val usePixels = grid > 0f && fontChoice == "same"
    val fontSize = if (usePixels) {
        val px = READER_PX[sizeStep.coerceIn(0, READER_PX.lastIndex)]
        (maxOf(grid, kotlin.math.floor(px / grid) * grid) / (density.density * density.fontScale)).sp
    } else READER_SP[sizeStep.coerceIn(0, READER_SP.lastIndex)].sp
    val style = androidx.compose.ui.text.TextStyle(
        color = palette.ink,
        fontFamily = family,
        fontSize = fontSize,
        lineHeight = fontSize * (if (usePixels) 1.25f else 1.4f),
        // Simple line breaking: page breaks line up exactly with how lines were laid out.
        lineBreak = LineBreak.Simple
    )
    val measurer = rememberTextMeasurer(cacheSize = 2)

    BackHandler { onBack() }
    DisposableEffect(Unit) {
        PageKeys.listening++
        onDispose { PageKeys.listening-- }
    }

    val loaded = text
    if (loaded == null) {
        PageColumn { Spacer(Modifier.weight(1f)); Hint("Opening."); Spacer(Modifier.weight(1f)) }
        return
    }

    PageColumn {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            val wPx = constraints.maxWidth
            val hPx = constraints.maxHeight
            val layout: TextLayoutResult = remember(loaded, style, wPx) {
                measurer.measure(loaded, style, constraints = Constraints(maxWidth = wPx.coerceAtLeast(1)))
            }
            // First line of every page.
            val starts: List<Int> = remember(layout, hPx) {
                val out = mutableListOf(0)
                var top = 0f
                for (line in 0 until layout.lineCount) {
                    if (layout.getLineBottom(line) - top > hPx && line > out.last()) {
                        out += line
                        top = layout.getLineTop(line)
                    }
                }
                out
            }
            val pages = starts.size
            // Land on the right page whenever the chapter or the layout changes.
            LaunchedEffect(layout, starts) {
                page = if (landAt < 0) pages - 1
                else starts.indexOfLast { layout.getLineStart(it) <= landAt }.coerceAtLeast(0)
                landAt = 0
            }
            val shown = page.coerceIn(0, pages - 1)
            val firstLine = starts[shown]
            val lastLine = (starts.getOrNull(shown + 1) ?: layout.lineCount) - 1
            val offset = if (layout.lineCount > 0) layout.getLineStart(firstLine) else 0

            // Save the place on every page.
            val chapters = epub.spine.size.coerceAtLeast(1)
            val percent = (((chapter + (shown + 1f) / pages) / chapters) * 100).toInt().coerceIn(0, 100)
            LaunchedEffect(chapter, offset) { Library.saveProgress(ctx, book.id, chapter, offset, percent) }

            // Reading speed: how long you spent on the page you just left, if it looks like reading.
            val pageChars = (if (shown + 1 < pages) layout.getLineStart(starts[shown + 1]) else loaded.length) - offset
            val shownAt = remember(chapter, shown) { System.currentTimeMillis() }
            fun learnSpeed() {
                val secs = (System.currentTimeMillis() - shownAt) / 1000f
                if (secs in 4f..180f && pageChars > 200) {
                    val thisPage = pageChars / (secs / 60f)
                    cpm = (cpm * 0.85f + thisPage.coerceIn(300f, 4000f) * 0.15f)
                    sp.edit().putFloat("readerCpm", cpm).apply()
                }
            }
            fun forward() {
                learnSpeed()
                com.hmj.twotone.Ghosting.pageTurned()
                if (shown < pages - 1) page = shown + 1
                else if (chapter < epub.spine.lastIndex) { chapter++; landAt = 0 }
            }
            fun backward() {
                com.hmj.twotone.Ghosting.pageTurned()
                if (shown > 0) page = shown - 1
                else if (chapter > 0) { chapter--; landAt = -1 }
            }
            val isMarked = marks.any { it.chapter == chapter && it.offset == offset }
            fun toggleMark() {
                marks = if (isMarked) marks.filterNot { it.chapter == chapter && it.offset == offset }
                else marks + Library.Mark(chapter, offset, loaded.substring(offset, (offset + 40).coerceAtMost(loaded.length)).replace('\n', ' ').trim(), System.currentTimeMillis())
                Library.setMarks(ctx, bookId, marks)
            }
            LaunchedEffect(shown, pages, chapter) { PageKeys.turns.collect { if (it > 0) forward() else backward() } }

            Canvas(
                Modifier.fillMaxSize().pointerInput(pages, chapter, shown, isMarked) {
                    detectTapGestures(
                        onTap = { o ->
                            when {
                                // Top right corner: bookmark
                                o.x > size.width * 0.8f && o.y < size.height * 0.1f -> toggleMark()
                                o.x < size.width / 3f -> backward()
                                else -> forward()
                            }
                        },
                        onLongPress = { o ->
                            // Hold a word: look it up
                            if (layout.lineCount > 0) {
                                val at = layout.getOffsetForPosition(Offset(o.x, o.y + layout.getLineTop(firstLine)))
                                com.hmj.twotone.books.Dictionary.wordAt(loaded.text, at)?.let { lookup = it }
                            }
                        }
                    )
                }
            ) {
                if (layout.lineCount == 0) return@Canvas
                val top = layout.getLineTop(firstLine)
                val bottom = layout.getLineBottom(lastLine)
                clipRect(0f, 0f, size.width, bottom - top) {
                    translate(top = -top) { drawText(layout) }
                }
                if (isMarked) {
                    // A folded corner
                    val c = 22.dp.toPx()
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width - c, 0f); lineTo(size.width, 0f); lineTo(size.width, c); close()
                    }
                    drawPath(path, palette.ink)
                }
            }

            val charsLeft = (loaded.length - offset - pageChars).coerceAtLeast(0)
            val minutesLeft = kotlin.math.ceil(charsLeft / cpm).toInt()
            val timeLeft = if (charsLeft == 0) "end of part" else if (minutesLeft <= 1) "1 min left in part" else "$minutesLeft min left in part"
            val footer = "${shown + 1} / $pages · $timeLeft · $percent%"
            val snippet = loaded.substring(offset, (offset + 40).coerceAtMost(loaded.length)).replace('\n', ' ').trim()
            androidx.compose.runtime.SideEffect {
                currentOffsetHolder.intValue = offset
                footerState.value = footer
                footerSnippet.value = snippet
            }
        }
        Column(Modifier.press { menu = true }) {
            SolidRule(thickness = 3.dp)
            BasicText(
                footerState.value,
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                style = textStyle(14f),
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Shared between the page and the options so a size change keeps your place. */
private val currentOffsetHolder = mutableIntStateOf(0)
private val footerState = mutableStateOf("")
private val footerSnippet = mutableStateOf("")

/** The meaning of a word, from Wiktionary, with a way to try other dictionary apps. */
@Composable
private fun DictionaryPage(word: String, language: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler { onClose() }
    val result by produceState<Result<List<com.hmj.twotone.books.Dictionary.Sense>>?>(null, word) {
        value = withContext(Dispatchers.IO) { runCatching { com.hmj.twotone.books.Dictionary.lookUp(word, language) } }
    }
    // Any dictionary apps on the phone that accept a word
    val otherApps = remember {
        val i = android.content.Intent(android.content.Intent.ACTION_PROCESS_TEXT).setType("text/plain")
        try { ctx.packageManager.queryIntentActivities(i, 0).isNotEmpty() } catch (e: Exception) { false }
    }
    PageColumn {
        PageHeader(word.lowercase(), onBack = onClose)
        val r = result
        val rows = mutableListOf<@Composable () -> Unit>()
        when {
            r == null -> rows.add(@Composable { TextRow("Looking it up.", size = 15f) })
            r.isFailure -> rows.add(@Composable { Hint(com.hmj.twotone.weather.WeatherApi.explain(r.exceptionOrNull() as? Exception ?: Exception())) })
            r.getOrNull().isNullOrEmpty() -> rows.add(@Composable { Hint("No entry found for \u201c$word\u201d.") })
            else -> r.getOrThrow().forEach { sense ->
                rows.add(@Composable { SectionBar(listOf(sense.partOfSpeech, sense.language.lowercase()).filter { it.isNotBlank() }.joinToString(" · ")) })
                sense.definitions.forEachIndexed { i, d ->
                    rows.add(@Composable {
                        BasicText(
                            "${i + 1}. $d",
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                            style = textStyle(17f), maxLines = 4, overflow = TextOverflow.Ellipsis
                        )
                    })
                }
            }
        }
        // Long definitions don't fit fixed-height rows, so this page simply lists them in order.
        Column(Modifier.weight(1f)) { rows.take(9).forEach { it() } }
        if (otherApps) {
            ListRow("look up in another app") {
                try {
                    ctx.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_PROCESS_TEXT).setType("text/plain")
                            .putExtra(android.content.Intent.EXTRA_PROCESS_TEXT, word)
                            .putExtra(android.content.Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) { }
            }
        }
        FooterLine("from wiktionary · back to return to your page")
    }
}
