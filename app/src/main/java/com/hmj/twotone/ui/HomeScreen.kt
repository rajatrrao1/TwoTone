package com.hmj.twotone.ui

import android.content.Context
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hmj.twotone.calendar.CalEvent
import com.hmj.twotone.music.Music
import com.hmj.twotone.weather.Forecast
import com.hmj.twotone.weather.describe
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** One row of the home list. */
class HomeRow(val key: String, val label: String, val open: () -> Unit)

/** The hours the day timeline covers. */
private const val DAY_START_HOUR = 7
private const val DAY_END_HOUR = 23

/** Home list rows are bigger than lists elsewhere. */
private const val HOME_ROW_SIZE = 28f

@Composable
fun HomeScreen(
    rows: List<HomeRow>,
    clockStyle: ClockStyle,
    showWeather: Boolean,
    showCalendar: Boolean,
    forecast: Forecast?,
    hasPlace: Boolean,
    events: List<CalEvent>?,
    onClockTap: () -> Unit,
    onClockLongPress: () -> Unit,
    onWeather: () -> Unit,
    onCalendar: () -> Unit,
    onMusic: () -> Unit
) {
    val context = LocalContext.current
    val tick = LocalResumeTick.current

    // Redraw once a minute, on the minute; restart when the phone wakes.
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(tick) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
        }
    }

    val is24h = DateFormat.is24HourFormat(context)
    val time = now.format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm"))
    val date = now.format(DateTimeFormatter.ofPattern("EEE d MMMM", Locale.getDefault())).lowercase()
    val battery = remember(now) {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    PageColumn {
        // ── Band 1: time, date, weather ───────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f).press(onLongPress = onClockLongPress, onPress = onClockTap)) {
                ClockFace(clockStyle, time)
                Spacer(Modifier.height(4.dp))
                BasicText(date, style = textStyle(18f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            }
            if (showWeather) WeatherBadge(forecast, hasPlace, onWeather)
        }

        // ── Band 2: today ─────────────────────────────────────────────────
        if (showCalendar) TodayPanel(events, is24h, now, onCalendar)

        // ── Band 3: your list (bigger type, a page at a time, dots on the right)
        SolidRule(thickness = 3.dp)
        PagedList(rows, Modifier.weight(1f), resetKey = rows.size, rowSize = HOME_ROW_SIZE, indicator = PageIndicator.DOTS) { r ->
            ListRow(r.label, trailing = "", size = HOME_ROW_SIZE, onPress = r.open)
        }

        // ── Band 4: what's playing, then the status line ──────────────────
        if (Music.current != null) MiniPlayer(onMusic)
        StatusLine(battery, if (showWeather) null else forecast)
    }
}

/** Battery on the left, the weather in words on the right. */
@Composable
private fun StatusLine(battery: Int, forecast: Forecast?) {
    Column {
        SolidRule(thickness = 3.dp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (battery in 0..100) {
                BatteryGlyph(battery)
                Spacer(Modifier.width(6.dp))
                BasicText("$battery%", style = textStyle(14f))
            }
            Spacer(Modifier.weight(1f))
            if (forecast != null) {
                BasicText(
                    "${forecast.nowTemp.roundToInt()}° ${describe(forecast.nowSymbol)}",
                    style = textStyle(14f),
                    maxLines = 1
                )
            }
        }
    }
}

/** A small pixel battery whose fill matches the charge. */
@Composable
private fun BatteryGlyph(percent: Int) {
    val ink = LocalPalette.current.ink
    Canvas(Modifier.width(22.dp).height(11.dp)) {
        val w = 2.dp.toPx()
        val bodyW = size.width - 3.dp.toPx()
        drawRect(ink, Offset(w / 2, w / 2), Size(bodyW - w, size.height - w), style = Stroke(w))
        drawRect(ink, Offset(bodyW, size.height * 0.3f), Size(3.dp.toPx(), size.height * 0.4f))
        val inner = (bodyW - 3 * w) * percent / 100f
        drawRect(ink, Offset(w * 1.5f, w * 1.5f), Size(inner, size.height - 3 * w))
    }
}

@Composable
private fun WeatherBadge(forecast: Forecast?, hasPlace: Boolean, onPress: () -> Unit) {
    Column(Modifier.press(onPress = onPress).padding(start = 10.dp), horizontalAlignment = Alignment.End) {
        if (forecast != null) {
            PixelGrid(WeatherIcons.forSymbol(forecast.nowSymbol), cell = 5.dp)
            Spacer(Modifier.height(2.dp))
            BasicText("${forecast.nowTemp.roundToInt()}°", style = textStyle(20f, bold = true, fixed = true), maxLines = 1, softWrap = false)
        } else {
            BasicText(if (hasPlace) "…" else "set\nweather", style = textStyle(13f))
        }
    }
}

/**
 * Today: a heading you can tap to open the calendar, the waking day as a bar
 * (dithered = gone, solid = events, notch = now), then what is still to come.
 */
@Composable
private fun TodayPanel(events: List<CalEvent>?, is24h: Boolean, now: LocalDateTime, onPress: () -> Unit) {
    val nowMs = now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val today = LocalDate.now()
    val todays = events?.filter { it.startDate() == today }.orEmpty()
    val timed = todays.filter { !it.allDay }
    val upcoming = todays.filter { it.allDay || it.end > nowMs }

    Column(Modifier.fillMaxWidth().press(onPress = onPress)) {
        val count = when {
            events == null || todays.isEmpty() -> ""
            todays.size == 1 -> "1 event  "
            else -> "${todays.size} events  "
        }
        SectionBar("today", "$count▸")
        Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
            DayTimeline(timed, nowMs)
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                BasicText("%02d".format(DAY_START_HOUR), Modifier.weight(1f), style = textStyle(12f))
                BasicText("%02d".format(DAY_END_HOUR), style = textStyle(12f))
            }
            Spacer(Modifier.height(4.dp))
            when {
                events == null -> BasicText("Tap to show your calendar here.", style = textStyle(15f))
                upcoming.isEmpty() -> BasicText(if (todays.isEmpty()) "Nothing on today." else "Nothing else today.", style = textStyle(15f))
                else -> upcoming.take(2).forEach { e ->
                    val whenText = when {
                        e.allDay -> "all day"
                        e.begin <= nowMs -> "now"
                        else -> e.startTime(is24h)
                    }
                    Row(Modifier.fillMaxWidth()) {
                        BasicText(whenText, Modifier.width(76.dp), style = textStyle(15f), maxLines = 1, softWrap = false)
                        BasicText(e.title, Modifier.weight(1f), style = textStyle(15f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun DayTimeline(events: List<CalEvent>, nowMs: Long) {
    val ink = LocalPalette.current.ink
    val zone = ZoneId.systemDefault()
    val start = LocalDate.now().atTime(DAY_START_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
    val end = LocalDate.now().atTime(DAY_END_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
    fun frac(t: Long) = ((t - start).toFloat() / (end - start)).coerceIn(0f, 1f)

    Canvas(Modifier.fillMaxWidth().height(18.dp)) {
        val line = 3.dp.toPx()
        val top = 3.dp.toPx()
        val h = size.height - 2 * top
        val c = 2.dp.toPx()

        // Past: checkerboard
        val pastW = size.width * frac(nowMs)
        var x = 0f
        var col = 0
        while (x < pastW) {
            var y = top
            var row = 0
            while (y < top + h) {
                if ((col + row) % 2 == 0) drawRect(ink, Offset(x, y), Size(minOf(c, pastW - x), minOf(c, top + h - y)))
                y += c; row++
            }
            x += c; col++
        }
        // Events: solid blocks, at least a few pixels wide
        events.forEach { e ->
            val a = size.width * frac(e.begin)
            val b = maxOf(size.width * frac(e.end), a + 3.dp.toPx())
            drawRect(ink, Offset(a, top), Size(b - a, h))
        }
        // Hour ticks along the bottom
        val hours = DAY_END_HOUR - DAY_START_HOUR
        for (i in 1 until hours) {
            val tx = size.width * i / hours
            drawRect(ink, Offset(tx, top + h - 3.dp.toPx()), Size(1.dp.toPx(), 3.dp.toPx()))
        }
        // Frame
        drawRect(ink, Offset(line / 2, top), Size(size.width - line, h), style = Stroke(line))
        // Now: a notch that sticks out above and below
        val nx = size.width * frac(nowMs)
        drawRect(ink, Offset(nx - line / 2, 0f), Size(line, size.height))
    }
}

/**
 * Shown while something is queued in music: cover, title, time left and the
 * controls. Tap the words to open music; swipe it sideways to stop and clear it.
 */
@Composable
private fun MiniPlayer(onOpen: () -> Unit) {
    val ctx = LocalContext.current
    val t = Music.current ?: return
    val ink = LocalPalette.current.ink
    var pos by remember { mutableStateOf(0L) }
    LaunchedEffect(t, Music.isPlaying) {
        while (true) { pos = Music.positionMs(); if (!Music.isPlaying) break; delay(5_000) }
    }
    Column {
        SolidRule(thickness = 3.dp)
        Row(
            Modifier.fillMaxWidth().height(58.dp).pointerInput(t.id) {
                var dx = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dx = 0f },
                    onDragEnd = { if (kotlin.math.abs(dx) > size.width * 0.3f) Music.stop(ctx) },
                    onHorizontalDrag = { change, d -> dx += d; change.consume() }
                )
            },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.padding(start = 8.dp)) { AlbumArtImage(t, 46.dp, dots = 46) }
            Column(Modifier.weight(1f).press(onPress = onOpen).padding(start = 10.dp)) {
                BasicText(t.title, style = textStyle(17f, bold = true), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                Row {
                    BasicText(t.artist, Modifier.weight(1f), style = textStyle(13f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    BasicText(" -" + mmss((t.durationMs - pos).coerceAtLeast(0)), style = textStyle(13f), maxLines = 1)
                }
            }
            // Controls in boxes, divided by heavy lines
            Box(Modifier.width(3.dp).fillMaxHeight().background(ink))
            PlayerKey("◂◂") { Music.previous(ctx) }
            Box(Modifier.width(3.dp).fillMaxHeight().background(ink))
            PlayerKey(if (Music.isPlaying) "‖" else "▸") { Music.toggle(ctx) }
            Box(Modifier.width(3.dp).fillMaxHeight().background(ink))
            PlayerKey("▸▸") { Music.next(ctx) }
        }
    }
}

private fun mmss(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

@Composable
private fun PlayerKey(symbol: String, onPress: () -> Unit) {
    Box(Modifier.width(48.dp).fillMaxHeight().press(onPress = onPress), contentAlignment = Alignment.Center) {
        BasicText(symbol, style = textStyle(18f, bold = true, fixed = true))
    }
}
