package com.hmj.twotone.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.hmj.twotone.PageKeys
import kotlin.math.ceil
import kotlin.math.max

/** Tap / long-press with no ripple or fade: nothing animates on E-ink. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.press(
    onLongPress: (() -> Unit)? = null,
    interaction: MutableInteractionSource? = null,
    onPress: () -> Unit
): Modifier =
    this.combinedClickable(
        interactionSource = interaction ?: remember { MutableInteractionSource() },
        indication = null,
        onLongClick = onLongPress,
        onClick = onPress
    )

/** Height of one list row at the current font size. Every paged list uses it. */
@Composable
@ReadOnlyComposable
fun rowHeight(size: Float = 20f): Dp = with(LocalDensity.current) { textStyle(size).fontSize.toDp() * 1.2f } + 22.dp

/** Full-page background used by every screen. */
@Composable
fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    // The window always fills the screen; only the bars' space is padded, and the top only
    // when Android's own status bar is the one showing. That keeps pages from jumping.
    val top = if (LocalOwnStatusBar.current) Modifier else Modifier.statusBarsPadding()
    Column(
        Modifier.fillMaxSize().background(LocalPalette.current.paper)
            .then(top).navigationBarsPadding().imePadding()
    ) { content() }
}

/** Settings values in square brackets, so they stand apart from the names: [unscii 16]. */
fun bracket(v: String): String = if (v.isBlank() || v.endsWith("▸") || v == "•") v else "[$v]"

/** A row of a list: a word on the left, a marker on the right. */
@Composable
fun ListRow(
    label: String,
    trailing: String = "▸",
    bold: Boolean = false,
    selected: Boolean = false,
    labelFont: FontFamily? = null,
    size: Float = 20f,
    onLongPress: (() -> Unit)? = null,
    onPress: () -> Unit
) {
    val p = LocalPalette.current
    val solid = selected
    val fg = if (solid) p.paper else p.ink
    val labelStyle = textStyle(size, bold, fg).let { if (labelFont != null) it.copy(fontFamily = labelFont) else it }
    Row(
        Modifier
            .fillMaxWidth()
            .height(rowHeight(size))
            .background(if (solid) p.ink else Color.Transparent)
            .press(onLongPress, onPress = onPress)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(label, Modifier.weight(1f), style = labelStyle, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        if (trailing.isNotEmpty()) {
            BasicText(
                trailing,
                Modifier.padding(start = 10.dp).widthIn(max = 170.dp),
                style = textStyle(15f, color = fg),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** A non-tappable line of text with the same height as a row (for headings inside lists). */
@Composable
fun TextRow(text: String, bold: Boolean = false, size: Float = 17f) {
    Box(Modifier.fillMaxWidth().height(rowHeight()).padding(horizontal = 18.dp), contentAlignment = Alignment.CenterStart) {
        BasicText(text, style = textStyle(size, bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A short explanatory paragraph. */
@Composable
fun Hint(text: String, modifier: Modifier = Modifier) {
    BasicText(text, modifier.padding(horizontal = 18.dp, vertical = 12.dp), style = textStyle(15f))
}

/**
 * A list that never scrolls. It shows as many rows as fit, and a swipe up
 * (or volume down) moves to the next whole page; swipe down goes back.
 */
enum class PageIndicator { NUMBERS, DOTS }

@Composable
fun <T> PagedList(
    items: List<T>,
    modifier: Modifier = Modifier,
    resetKey: Any? = null,
    rowSize: Float = 20f,
    indicator: PageIndicator = PageIndicator.NUMBERS,
    /** Open on the page that holds this item (changing it jumps there). */
    showIndex: Int = 0,
    /** A swipe down while already on the first page. */
    onPullDown: (() -> Unit)? = null,
    row: @Composable (T) -> Unit
) {
    val rowH = rowHeight(rowSize)
    val footerH = if (indicator == PageIndicator.NUMBERS) 34.dp else 0.dp
    // While the keyboard is open the page is shorter. Re-counting the rows then would
    // reflow the page and throw away the box being typed in (closing the keyboard again),
    // so the count from before the keyboard opened is kept until it closes.
    val imeOpen = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var steadyPerPage by remember { mutableIntStateOf(0) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val fitsWithoutFooter = max(1, (maxHeight / rowH).toInt())
        val counted = if (items.size <= fitsWithoutFooter) fitsWithoutFooter
        else max(1, ((maxHeight - footerH) / rowH).toInt())
        val perPage = if (imeOpen && steadyPerPage > 0) steadyPerPage else counted
        androidx.compose.runtime.SideEffect { if (!imeOpen) steadyPerPage = counted }
        val pages = max(1, ceil(items.size / perPage.toFloat()).toInt())

        var page by remember(resetKey, perPage, showIndex) { mutableIntStateOf(showIndex.coerceAtLeast(0) / perPage) }
        val shown = page.coerceIn(0, pages - 1)
        fun turn(by: Int) {
            val next = (page.coerceIn(0, pages - 1) + by).coerceIn(0, pages - 1)
            if (next != page) com.hmj.twotone.Ghosting.pageTurned()
            page = next
        }

        DisposableEffect(Unit) {
            PageKeys.listening++
            onDispose { PageKeys.listening-- }
        }
        LaunchedEffect(pages, perPage) { PageKeys.turns.collect { turn(it) } }

        Box(
            Modifier.fillMaxSize().pointerInput(pages, perPage) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -40f) turn(1)
                        else if (total > 40f) { if (page <= 0 && onPullDown != null) onPullDown() else turn(-1) }
                    },
                    onVerticalDrag = { change, dy -> total += dy; change.consume() }
                )
            }
        ) {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).padding(end = if (indicator == PageIndicator.DOTS && pages > 1) 16.dp else 0.dp)) {
                    items.drop(shown * perPage).take(perPage).forEach { row(it) }
                }
                if (pages > 1 && indicator == PageIndicator.NUMBERS) {
                    PageFooter(shown, pages, footerH, onPrev = { turn(-1) }, onNext = { turn(1) })
                }
            }
            if (pages > 1 && indicator == PageIndicator.DOTS) {
                PageDots(shown, pages, Modifier.align(Alignment.CenterEnd).padding(end = 5.dp))
            }
        }
    }
}

/** A column of dots, one per page: solid for the page you are on. */
@Composable
private fun PageDots(page: Int, pages: Int, modifier: Modifier) {
    val ink = LocalPalette.current.ink
    Column(modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp)) {
        for (i in 0 until pages) {
            Canvas(Modifier.width(8.dp).height(8.dp)) {
                val r = size.minDimension / 2f
                if (i == page) drawCircle(ink, r)
                else drawCircle(ink, r - 1.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            }
        }
    }
}

@Composable
private fun PageFooter(page: Int, pages: Int, height: Dp, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(height), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.press(onPress = onPrev).padding(horizontal = 18.dp, vertical = 4.dp)) {
            BasicText(if (page > 0) "◂" else " ", style = textStyle(18f, bold = true, fixed = true))
        }
        BasicText(
            "${page + 1} / $pages",
            Modifier.weight(1f),
            style = textStyle(14f, fixed = true).copy(textAlign = TextAlign.Center),
            maxLines = 1
        )
        Box(Modifier.press(onPress = onNext).padding(horizontal = 18.dp, vertical = 4.dp)) {
            BasicText(if (page < pages - 1) "▸" else " ", style = textStyle(18f, bold = true, fixed = true))
        }
    }
}

/**
 * The bar at the top of every page: "◂ back" on the left, where you are in
 * the middle, the time on the right.
 */
@Composable
fun PageHeader(title: String, onBack: (() -> Unit)?) {
    Column {
        Row(Modifier.fillMaxWidth().height(50.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (onBack != null) {
                    Box(Modifier.press(onPress = onBack).padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
                        BasicText("◂ back", style = textStyle(16f, bold = true, fixed = true), maxLines = 1, softWrap = false)
                    }
                }
            }
            BoxWithConstraints(Modifier.weight(2f), contentAlignment = Alignment.Center) {
                val big = textStyle(24f, bold = true, fixed = true).copy(textAlign = TextAlign.Center)
                val normal = textStyle(20f, bold = true, fixed = true).copy(textAlign = TextAlign.Center)
                val measurer = androidx.compose.ui.text.rememberTextMeasurer()
                val fits = remember(title, big, constraints.maxWidth) {
                    measurer.measure(title, big, softWrap = false).size.width <= constraints.maxWidth
                }
                BasicText(
                    title,
                    style = if (fits) big else normal,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(Modifier.weight(1f).padding(end = 14.dp), contentAlignment = Alignment.CenterEnd) {
                // The launcher's own status bar already shows the time.
                if (!LocalOwnStatusBar.current) HeaderClock()
            }
        }
        SolidRule(thickness = 3.dp)
    }
}

@Composable
private fun HeaderClock() {
    val ctx = LocalContext.current
    val tick = LocalResumeTick.current
    var now by remember { mutableStateOf(java.time.LocalTime.now()) }
    LaunchedEffect(tick, com.hmj.twotone.Visible.on) {
        while (com.hmj.twotone.Visible.on) {
            now = java.time.LocalTime.now()
            kotlinx.coroutines.delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
        }
    }
    val pattern = if (android.text.format.DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm"
    BasicText(now.format(java.time.format.DateTimeFormatter.ofPattern(pattern)), style = textStyle(16f, bold = true, fixed = true), maxLines = 1, softWrap = false)
}

/** A line at the bottom of a page, for counts and status ("60 apps"). */
@Composable
fun FooterLine(text: String) {
    Column {
        SolidRule(thickness = 3.dp)
        BasicText(
            text,
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp),
            style = textStyle(14f),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A checkerboard strip: the 1-bit way to draw grey. */
@Composable
fun DitherRule(modifier: Modifier = Modifier, rows: Int = 2) {
    val ink = LocalPalette.current.ink
    Canvas(modifier.fillMaxWidth().height((rows * 2).dp)) {
        val c = 2.dp.toPx()
        val cols = (size.width / c).toInt() + 1
        for (r in 0 until rows) for (i in 0 until cols) {
            if ((i + r) % 2 == 0) drawRect(ink, Offset(i * c, r * c), Size(c, c))
        }
    }
}

/** A plain solid line. */
@Composable
fun SolidRule(modifier: Modifier = Modifier, thickness: Dp = 2.dp) {
    Box(modifier.fillMaxWidth().height(thickness).background(LocalPalette.current.ink))
}

/** A heading drawn as a solid bar with the words cut out of it, e.g. "today", "alarms". */
@Composable
fun SectionBar(title: String, trailing: String = "", onPress: (() -> Unit)? = null) {
    val p = LocalPalette.current
    val base = Modifier.fillMaxWidth().height(34.dp).background(p.ink)
    Row(
        (if (onPress != null) base.press(onPress = onPress) else base).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(title, Modifier.weight(1f), style = textStyle(18f, bold = true, color = p.paper, fixed = true), maxLines = 1, softWrap = false)
        if (trailing.isNotEmpty()) {
            BasicText(trailing, style = textStyle(15f, color = p.paper, fixed = true), maxLines = 1, softWrap = false)
        }
    }
}

/**
 * A list that scrolls, used on the home page. A fling always comes to rest on a
 * row edge, and a bar on the right shows where you are.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> ScrollList(
    items: List<T>,
    modifier: Modifier = Modifier,
    key: ((T) -> Any)? = null,
    row: @Composable (T) -> Unit
) {
    val state = rememberLazyListState()
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val ink = LocalPalette.current.ink
    Box(modifier.fillMaxWidth()) {
        LazyColumn(Modifier.fillMaxSize().padding(end = 6.dp), state = state, flingBehavior = fling) {
            if (key != null) items(items, key = key) { row(it) } else items(items) { row(it) }
        }
        // Scroll bar: only when there is more than fits.
        val info = state.layoutInfo
        val visible = info.visibleItemsInfo.size
        val total = info.totalItemsCount
        if (total > 0 && visible < total) {
            Canvas(Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(4.dp)) {
                val thumbH = size.height * visible / total
                val top = (size.height - thumbH) * state.firstVisibleItemIndex / (total - visible).coerceAtLeast(1)
                drawRect(ink, Offset(0f, top.coerceIn(0f, size.height - thumbH)), Size(size.width, thumbH))
            }
        }
    }
}

// ---- permissions ------------------------------------------------------------

class PermissionGate(val granted: Boolean, val request: () -> Unit)

/** Whether [perms] are granted, re-checked every time the launcher returns to the front. */
@Composable
fun rememberPermissionGate(perms: List<String>): PermissionGate {
    val ctx = LocalContext.current
    val tick = LocalResumeTick.current
    fun check() = perms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }
    var granted by remember(perms, tick) { mutableStateOf(check()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = check()
    }
    return PermissionGate(granted) { launcher.launch(perms.toTypedArray()) }
}

/** Shown in place of a section until its permission is given. */
@Composable
fun AskPermission(message: String, gate: PermissionGate) {
    val ctx = LocalContext.current
    Hint(message)
    ListRow("allow") { gate.request() }
    ListRow("open app permissions") {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}


/**
 * Asks once for permission to show notifications (Android 13+), which the music
 * controls and alarms use. Saying no is fine: everything else still works.
 */
@Composable
fun AskForNotificationsOnce() {
    if (android.os.Build.VERSION.SDK_INT < 33) return
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val perm = android.Manifest.permission.POST_NOTIFICATIONS
        val sp = ctx.getSharedPreferences("twotone", android.content.Context.MODE_PRIVATE)
        val granted = ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
        if (!granted && !sp.getBoolean("askedNotifications", false)) {
            sp.edit().putBoolean("askedNotifications", true).apply()
            launcher.launch(perm)
        }
    }
}
