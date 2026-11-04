package com.hmj.twotone.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hmj.twotone.PageKeys
import com.hmj.twotone.news.Article
import com.hmj.twotone.news.News
import com.hmj.twotone.news.Story
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "3 min", "5 h", "2 d" since [t]. */
private fun age(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60_000L
    return when {
        m < 1 -> "now"
        m < 60 -> "$m min"
        m < 48 * 60 -> "${m / 60} h"
        else -> "${m / (24 * 60)} d"
    }
}

/** News: latest stories from your sources, or the ones you saved. */
@Composable
fun NewsScreen(onStory: (Story) -> Unit, onSaved: (String) -> Unit, onSources: () -> Unit, onReadLink: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }   // 0 latest, 1 saved
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        if (busy) return
        busy = true
        scope.launch {
            val n = try { withContext(Dispatchers.IO) { News.refresh(ctx) } } catch (e: Exception) { -1 }
            busy = false
            message = when { n < 0 -> "Couldn't update. Check the connection."; n == 0 -> null; n == 1 -> "1 new story."; else -> "$n new stories." }
        }
    }
    LaunchedEffect(Unit) {
        News.load(ctx)
        // Fetch again when the list is more than 20 minutes old
        if (News.feeds.isNotEmpty() && System.currentTimeMillis() - News.updatedAt > 20 * 60_000L) refresh()
    }

    PageColumn {
        PageHeader("news", onBack)
        val p = LocalPalette.current
        Row(Modifier.fillMaxWidth()) {
            listOf("latest", "saved ${News.saved.size}").forEachIndexed { i, label ->
                val on = tab == i
                Box(Modifier.weight(1f).height(38.dp).background(if (on) p.ink else p.paper).press { tab = i }, contentAlignment = Alignment.Center) {
                    BasicText(label, style = textStyle(17f, bold = true, color = if (on) p.paper else p.ink, fixed = true), maxLines = 1)
                }
            }
        }
        SolidRule(thickness = 3.dp)
        message?.let { Hint(it) }

        if (tab == 0) {
            val unread = News.stories.count { it.id !in News.read }
            val rows: List<Story?> = listOf<Story?>(null) + News.stories
            PagedList(rows, Modifier.weight(1f), resetKey = News.stories.size) { s ->
                if (s == null) {
                    if (News.feeds.isEmpty()) ListRow("+ add a news site or feed", onPress = onSources)
                    else ListRow(if (busy) "updating…" else "update now", trailing = "") { refresh() }
                } else {
                    val isRead = s.id in News.read
                    ListRow(
                        (if (isRead) "" else "• ") + s.title,
                        trailing = age(s.time),
                        onPress = { onStory(s) }
                    )
                }
            }
            FooterLine("${News.feeds.size} sources · $unread unread" + if (News.updatedAt > 0) " · updated ${age(News.updatedAt)} ago" else "")
        } else {
            val rows: List<Article?> = listOf<Article?>(null) + News.saved
            PagedList(rows, Modifier.weight(1f), resetKey = News.saved.size) { a ->
                if (a == null) ListRow("+ read a link", onPress = onReadLink)
                else ListRow(a.title, trailing = a.site.lowercase().take(12), onLongPress = { News.unsave(ctx, a.id) }) { onSaved(a.id) }
            }
            FooterLine("saved stories stay on the phone · hold one to delete it")
        }
        if (tab == 0) {
            Row(Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f).press(onPress = onSources).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    BasicText("sources ▸", style = textStyle(15f, fixed = true))
                }
                Box(Modifier.weight(1f).press(onPress = onReadLink).padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    BasicText("read a link ▸", style = textStyle(15f, fixed = true))
                }
            }
        }
    }
}

/** Add or remove news sources. */
@Composable
fun NewsSourcesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = LocalPalette.current
    var address by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { News.load(ctx) }

    fun add() {
        if (address.isBlank() || busy) return
        busy = true
        message = "Looking for its news feed."
        scope.launch {
            message = try {
                val f = withContext(Dispatchers.IO) { News.addSource(ctx, address) }
                address = ""
                "Added \u201c${f.title}\u201d."
            } catch (e: Exception) {
                e.message ?: "That address couldn't be added."
            }
            busy = false
        }
    }

    PageColumn {
        PageHeader("news sources", onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("add: ", style = textStyle(18f, bold = true), maxLines = 1, softWrap = false)
            BasicTextField(
                value = address,
                onValueChange = { address = it },
                singleLine = true,
                textStyle = textStyle(17f),
                cursorBrush = SolidColor(palette.ink),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { add() }),
                modifier = Modifier.weight(1f)
            )
        }
        SolidRule()
        ListRow(if (busy) "adding…" else "add this source", trailing = "") { add() }
        Hint(message ?: "A website (nos.nl, theguardian.com) or a feed address both work.")
        SectionBar("your sources", "${News.feeds.size}")
        PagedList(News.feeds, Modifier.weight(1f), resetKey = News.feeds.size) { f ->
            ListRow(f.title.lowercase(), trailing = "${News.stories.count { it.feedUrl == f.url }}", onLongPress = { News.removeFeed(ctx, f.url) }) { }
        }
        FooterLine("hold a source to remove it")
    }
}

/** Paste one article's address to read it as text. */
@Composable
fun NewsLinkScreen(onRead: (String) -> Unit, onBack: () -> Unit) {
    val palette = LocalPalette.current
    var address by rememberSaveable { mutableStateOf("") }
    PageColumn {
        PageHeader("read a link", onBack)
        Hint("Paste an article's address. You can also share a page to Two Tone from the browser.")
        BasicTextField(
            value = address,
            onValueChange = { address = it },
            textStyle = textStyle(17f),
            cursorBrush = SolidColor(palette.ink),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { if (address.isNotBlank()) onRead(address.trim()) }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
        )
        SolidRule()
        ListRow("read it", trailing = "") { if (address.isNotBlank()) onRead(address.trim().let { if (it.startsWith("http")) it else "https://$it" }) }
    }
}

/**
 * One story as text, a page at a time. Tap the left third to go back, the rest
 * to go forward; volume keys too. Tap the bottom bar for save, size and browser.
 */
@Composable
fun ArticleScreen(link: String, storyId: String?, savedId: String?, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val palette = LocalPalette.current
    val sp = remember { ctx.getSharedPreferences("twotone_books", android.content.Context.MODE_PRIVATE) }
    var sizeStep by remember { mutableIntStateOf(sp.getInt("readerSize", 1)) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { News.load(ctx) }
    val story = storyId?.let { id -> News.stories.firstOrNull { it.id == id } }
    val article by produceState<Result<Article>?>(null, link, savedId) {
        val saved = savedId?.let { News.savedArticle(it) }
        value = saved?.let { Result.success(it) }
            ?: withContext(Dispatchers.IO) { runCatching { News.readArticle(link, story) } }
        story?.let { News.markRead(ctx, it.id) }
    }

    val r = article
    if (r == null) { PageColumn { PageHeader("news", onBack); Hint("Fetching the story and keeping only its text.") }; return }
    val a = r.getOrNull()
    if (a == null) {
        PageColumn {
            PageHeader("news", onBack)
            Hint("This page couldn't be read. " + com.hmj.twotone.weather.WeatherApi.explain(r.exceptionOrNull() as? Exception ?: Exception()))
            ListRow("open it in the browser") {
                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
            }
        }
        return
    }

    if (menu) {
        BackHandler { menu = false }
        val sizes = if (LocalPixelGrids.current.large > 0f) 4 else 5
        PageColumn {
            PageHeader(a.site.lowercase().ifBlank { "story" }, onBack = { menu = false })
            ListRow(if (News.isSaved(a.id)) "saved · tap to unsave" else "save for later", trailing = "") {
                if (News.isSaved(a.id)) News.unsave(ctx, a.id) else News.save(ctx, a)
            }
            ListRow("text size", trailing = "${sizeStep + 1} of $sizes") {
                sizeStep = (sizeStep + 1) % sizes; sp.edit().putInt("readerSize", sizeStep).apply()
            }
            ListRow("open in the browser") {
                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
            }
            ListRow("back to the story", trailing = "") { menu = false }
            ListRow("close", trailing = "") { onBack() }
        }
        return
    }

    // The story as one styled text: title, source line, then the blocks.
    val text: AnnotatedString = remember(a) {
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(a.title) }
            append("\n")
            append(listOfNotNull(a.site.takeIf { it.isNotBlank() }, age(a.time) + " ago").joinToString(" · "))
            append("\n\n")
            a.blocks.forEachIndexed { i, b ->
                if (b.startsWith("# ")) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(b.removePrefix("# ")) }
                else append(b)
                if (i < a.blocks.lastIndex) append("\n\n")
            }
        }
    }

    val grid = LocalPixelGrids.current.large
    val fontSize = if (grid > 0f) {
        val px = listOf(16f, 24f, 32f, 48f)[sizeStep.coerceIn(0, 3)]
        (maxOf(grid, kotlin.math.floor(px / grid) * grid) / (density.density * density.fontScale)).sp
    } else listOf(15f, 17f, 19f, 22f, 26f)[sizeStep.coerceIn(0, 4)].sp
    val style = androidx.compose.ui.text.TextStyle(
        color = palette.ink, fontFamily = LocalFontFamily.current, fontSize = fontSize,
        lineHeight = fontSize * (if (grid > 0f) 1.25f else 1.4f), lineBreak = LineBreak.Simple
    )
    val measurer = rememberTextMeasurer(cacheSize = 2)
    var page by remember(a.id, sizeStep) { mutableIntStateOf(0) }
    var footer by remember { mutableStateOf("") }

    BackHandler { onBack() }
    DisposableEffect(Unit) { PageKeys.listening++; onDispose { PageKeys.listening-- } }

    PageColumn {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
            val wPx = constraints.maxWidth
            val hPx = constraints.maxHeight
            val layout = remember(text, style, wPx) { measurer.measure(text, style, constraints = Constraints(maxWidth = wPx.coerceAtLeast(1))) }
            val starts = remember(layout, hPx) {
                val out = mutableListOf(0)
                var top = 0f
                for (line in 0 until layout.lineCount) {
                    if (layout.getLineBottom(line) - top > hPx && line > out.last()) { out += line; top = layout.getLineTop(line) }
                }
                out
            }
            val pages = starts.size
            val shown = page.coerceIn(0, pages - 1)
            fun turn(by: Int) {
                val n = (shown + by).coerceIn(0, pages - 1)
                if (n != shown) { com.hmj.twotone.Ghosting.pageTurned(); page = n }
            }
            LaunchedEffect(shown, pages) { PageKeys.turns.collect { turn(it) } }
            val firstLine = starts[shown]
            val lastLine = (starts.getOrNull(shown + 1) ?: layout.lineCount) - 1
            Canvas(Modifier.fillMaxSize().pointerInput(shown, pages) {
                detectTapGestures { o -> if (o.x < size.width / 3f) turn(-1) else turn(1) }
            }) {
                if (layout.lineCount == 0) return@Canvas
                val top = layout.getLineTop(firstLine)
                clipRect(0f, 0f, size.width, layout.getLineBottom(lastLine) - top) { translate(top = -top) { drawText(layout) } }
            }
            val f = "${shown + 1} / $pages" + (if (News.isSaved(a.id)) " · saved" else "") + " · ${a.site.lowercase()}"
            SideEffect { footer = f }
        }
        Column(Modifier.press { menu = true }) {
            SolidRule(thickness = 3.dp)
            BasicText(footer, Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), style = textStyle(14f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}
