package com.hmj.twotone.ui

import android.Manifest
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hmj.twotone.weather.Forecast
import com.hmj.twotone.weather.Place
import com.hmj.twotone.weather.WeatherApi
import com.hmj.twotone.weather.describe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private fun compass(fromDeg: Double): String {
    val names = listOf("n", "ne", "e", "se", "s", "sw", "w", "nw")
    return names[(((fromDeg % 360 + 360) % 360 + 22.5) / 45).toInt() % 8]
}

/** The Beaufort scale in words. */
private fun beaufort(kmh: Double): String = when {
    kmh < 2 -> "calm"
    kmh < 6 -> "light air"
    kmh < 12 -> "light breeze"
    kmh < 20 -> "gentle breeze"
    kmh < 29 -> "moderate breeze"
    kmh < 39 -> "fresh breeze"
    kmh < 50 -> "strong breeze"
    kmh < 62 -> "near gale"
    kmh < 75 -> "gale"
    kmh < 89 -> "strong gale"
    else -> "storm"
}

/** One line about rain for the rest of today. */
private fun rainLine(f: Forecast, is24h: Boolean): String {
    val zone = ZoneId.systemDefault()
    val today = java.time.LocalDate.now(zone)
    val fmt = DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h a", Locale.getDefault())
    val left = f.hours.filter { Instant.ofEpochMilli(it.epochMs).atZone(zone).toLocalDate() == today }
    if (left.isEmpty()) return ""
    val wet = left.map { it.rainMm >= 0.1 }
    return when {
        wet.none { it } -> if (left.size >= 20) "dry all day" else "dry for the rest of the day"
        wet.first() -> {
            val stop = left.indexOfFirst { it.rainMm < 0.1 }
            if (stop < 0) "rain for the rest of the day"
            else "rain until " + Instant.ofEpochMilli(left[stop].epochMs).atZone(zone).format(fmt).lowercase()
        }
        else -> "rain from " + Instant.ofEpochMilli(left[wet.indexOf(true)].epochMs).atZone(zone).format(fmt).lowercase()
    }
}

/**
 * Two pages: now (the scene, the temperature, wind, rain and the next hours)
 * and the week. Swipe up and down between them; dots on the right show where you are.
 * Tap the bottom line to refresh or change the place.
 */
@Composable
fun WeatherScreen(
    forecast: Forecast?,
    hasPlace: Boolean,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onChangePlace: () -> Unit,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val is24h = DateFormat.is24HourFormat(ctx)
    val zone = ZoneId.systemDefault()
    var page by remember { mutableStateOf(0) }
    var options by remember { mutableStateOf(false) }

    if (options) {
        androidx.activity.compose.BackHandler { options = false }
        PageColumn {
            PageHeader("weather", onBack = { options = false })
            ListRow(if (loading) "updating…" else "update now", trailing = "") { onRefresh(); options = false }
            ListRow("change place", onPress = { options = false; onChangePlace() })
        }
        return
    }

    PageColumn {
        PageHeader("weather", onBack)
        if (!hasPlace) {
            Hint("Choose a place to see its weather.")
            ListRow("choose place", onPress = onChangePlace)
            return@PageColumn
        }
        if (error != null) { Hint(error); ConnectionCheck() }
        if (forecast == null) {
            if (loading) Hint("Getting the forecast.")
            else ListRow("try again", trailing = "", onPress = onRefresh)
            ListRow("change place", onPress = onChangePlace)
            return@PageColumn
        }

        androidx.compose.foundation.layout.Box(
            Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -40f) page = 1 else if (total > 40f) page = 0 },
                    onVerticalDrag = { ch, dy -> total += dy; ch.consume() }
                )
            }
        ) {
            Column(Modifier.fillMaxSize().padding(end = 14.dp)) {
                if (page == 0) NowPage(forecast, is24h) else WeekPage(forecast)
            }
            // Page dots
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(7.dp)
            ) {
                val ink = LocalPalette.current.ink
                for (i in 0..1) androidx.compose.foundation.Canvas(Modifier.width(8.dp).height(8.dp).press { page = i }) {
                    val r = size.minDimension / 2f
                    if (i == page) drawCircle(ink, r) else drawCircle(ink, r - 1.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
                }
            }
        }
        val asOf = Instant.ofEpochMilli(forecast.fetchedAt).atZone(zone)
            .format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", Locale.getDefault())).lowercase()
        Column(Modifier.press { options = true }) {
            FooterLine("${forecast.place.substringBefore(",").lowercase()} · " + if (loading) "updating" else "as of $asOf")
        }
    }
}

@Composable
private fun NowPage(f: Forecast, is24h: Boolean) {
    val zone = ZoneId.systemDefault()
    val today = f.days.firstOrNull()
    WeatherScene(f.nowSymbol, f.isDay, 110.dp)
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText("${f.nowTemp.roundToInt()}°", style = textStyle(30f, bold = true, fixed = true), maxLines = 1, softWrap = false)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            BasicText(describe(f.nowSymbol), style = textStyle(15f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            if (today != null) BasicText("${today.max.roundToInt()}° / ${today.min.roundToInt()}°", style = textStyle(15f), maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WindArrow(f.windFromDeg)
                Spacer(Modifier.width(6.dp))
                BasicText("${compass(f.windFromDeg)} ${f.windKmh.roundToInt()} km/h", style = textStyle(15f), maxLines = 1)
            }
            BasicText(beaufort(f.windKmh), style = textStyle(15f), maxLines = 1)
        }
    }
    val line = rainLine(f, is24h)
    if (line.isNotEmpty()) {
        BasicText(line, Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = textStyle(20f), maxLines = 1, softWrap = false)
    }
    SectionBar("the next hours")
    val fmt = DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h a", Locale.getDefault())
    // Every other hour, as many as fit
    val picks = f.hours.filterIndexed { i, _ -> i % 2 == 0 }.take(6)
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        picks.forEachIndexed { i, h ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText(
                    if (i == 0) "now" else Instant.ofEpochMilli(h.epochMs).atZone(zone).format(fmt).lowercase(),
                    style = textStyle(13f, fixed = true), maxLines = 1, softWrap = false
                )
                Spacer(Modifier.height(4.dp))
                PixelGrid(WeatherIcons.forSymbol(h.symbol), cell = 2.dp)
                Spacer(Modifier.height(4.dp))
                BasicText("${h.temp.roundToInt()}°", style = textStyle(14f, fixed = true), maxLines = 1)
            }
        }
    }
}

@Composable
private fun WeekPage(f: Forecast) {
    val lo = f.days.minOfOrNull { it.min } ?: 0.0
    val hi = f.days.maxOfOrNull { it.max } ?: 1.0
    SectionBar("the week", "${f.days.size} days")
    val today = java.time.LocalDate.now()
    f.days.take(7).forEach { d ->
        Row(Modifier.fillMaxWidth().height(rowHeight()).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                if (d.date == today) "today" else d.date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).lowercase(),
                Modifier.width(64.dp), style = textStyle(18f), maxLines = 1, softWrap = false
            )
            PixelGrid(WeatherIcons.forSymbol(d.symbol), cell = 2.dp)
            Spacer(Modifier.width(8.dp))
            BasicText("${d.min.roundToInt()}°", Modifier.width(34.dp), style = textStyle(14f, fixed = true), maxLines = 1)
            RangeBar(d.min, d.max, lo, hi, Modifier.weight(1f))
            BasicText("${d.max.roundToInt()}°", Modifier.padding(start = 6.dp).width(34.dp), style = textStyle(14f, fixed = true), maxLines = 1)
        }
    }
    val wetDays = f.days.take(7).filter { it.rainMm >= 1.0 }
    if (wetDays.isNotEmpty()) {
        BasicText(
            "rain: " + wetDays.joinToString(", ") { it.date.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault())).lowercase() + " ${it.rainMm.roundToInt()} mm" },
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = textStyle(14f), maxLines = 2
        )
    }
}

/** A thin line for the week's range, with a solid bar for this day's low to high. */
@Composable
private fun RangeBar(min: Double, max: Double, lo: Double, hi: Double, modifier: Modifier) {
    val ink = LocalPalette.current.ink
    androidx.compose.foundation.Canvas(modifier.height(10.dp)) {
        val span = (hi - lo).coerceAtLeast(1.0)
        val a = ((min - lo) / span).toFloat() * size.width
        val b = ((max - lo) / span).toFloat() * size.width
        val mid = size.height / 2f
        // dotted week line
        var x = 0f
        while (x < size.width) { drawRect(ink, androidx.compose.ui.geometry.Offset(x, mid - 1f), androidx.compose.ui.geometry.Size(2f, 2f)); x += 6f }
        drawRect(ink, androidx.compose.ui.geometry.Offset(a, mid - 3.dp.toPx()), androidx.compose.ui.geometry.Size((b - a).coerceAtLeast(3.dp.toPx()), 6.dp.toPx()))
    }
}

/** An arrow pointing where the wind blows to. */
@Composable
private fun WindArrow(fromDeg: Double) {
    val ink = LocalPalette.current.ink
    androidx.compose.foundation.Canvas(Modifier.width(18.dp).height(18.dp)) {
        val to = Math.toRadians(fromDeg + 180.0)
        val c = center
        val r = size.minDimension / 2f - 2.dp.toPx()
        // screen y grows downwards; north is up
        val dx = (kotlin.math.sin(to) * r).toFloat(); val dy = (-kotlin.math.cos(to) * r).toFloat()
        val tip = androidx.compose.ui.geometry.Offset(c.x + dx, c.y + dy)
        val tail = androidx.compose.ui.geometry.Offset(c.x - dx, c.y - dy)
        val w = 2.5.dp.toPx()
        drawLine(ink, tail, tip, strokeWidth = w)
        val back = Math.toRadians(fromDeg + 180.0 + 150.0)
        val back2 = Math.toRadians(fromDeg + 180.0 - 150.0)
        val l = r * 0.7f
        drawLine(ink, tip, androidx.compose.ui.geometry.Offset(tip.x + (kotlin.math.sin(back) * l).toFloat(), tip.y - (kotlin.math.cos(back) * l).toFloat()), strokeWidth = w)
        drawLine(ink, tip, androidx.compose.ui.geometry.Offset(tip.x + (kotlin.math.sin(back2) * l).toFloat(), tip.y - (kotlin.math.cos(back2) * l).toFloat()), strokeWidth = w)
    }
}

/** Search for a town by name, or use where the phone is. */
@Composable
fun WeatherPlaceScreen(onPicked: (Place) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = LocalPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    val locationGate = rememberPermissionGate(listOf(Manifest.permission.ACCESS_COARSE_LOCATION))

    fun search() {
        val q = query.trim()
        if (q.isEmpty()) return
        message = "Searching."
        scope.launch {
            try {
                val found = withContext(Dispatchers.IO) { WeatherApi.searchPlaces(q) }
                results = found
                message = if (found.isEmpty()) "No place called \u201c$q\u201d was found." else null
            } catch (e: Exception) {
                message = WeatherApi.explain(e)
            }
        }
    }

    PageColumn {
        PageHeader("weather place", onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("town: ", style = textStyle(18f, bold = true), maxLines = 1, softWrap = false)
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = textStyle(18f),
                cursorBrush = SolidColor(palette.ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() })
            )
        }
        SolidRule(Modifier.padding(horizontal = 18.dp))
        Spacer(Modifier.height(4.dp))
        message?.let { Hint(it) }
        if (message?.contains("internet") == true || message?.contains("couldn't", ignoreCase = true) == true) ConnectionCheck()

        val rows: List<Place?> = listOf<Place?>(null) + results
        PagedList(rows, Modifier.weight(1f), resetKey = results) { p ->
            if (p == null) {
                ListRow("use where the phone is", trailing = "") {
                    if (!locationGate.granted) {
                        locationGate.request()
                    } else {
                        val loc = WeatherApi.lastKnownLocation(ctx)
                        if (loc == null) message = "The phone doesn't know where it is yet. Search for a town instead."
                        else onPicked(Place("here", loc.latitude, loc.longitude))
                    }
                }
            } else {
                ListRow(p.name, trailing = "") { onPicked(p) }
            }
        }
    }
}


/** "check the connection": runs [NetCheck] and shows what it found, in plain words. */
@Composable
private fun ConnectionCheck() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<com.hmj.twotone.weather.NetCheck.Result?>(null) }
    var running by remember { mutableStateOf(false) }
    val r = result
    if (r == null) {
        ListRow(if (running) "checking…" else "check the connection", trailing = "") {
            if (running) return@ListRow
            running = true
            scope.launch {
                result = withContext(Dispatchers.IO) { com.hmj.twotone.weather.NetCheck.run(ctx) }
                running = false
            }
        }
    } else {
        androidx.compose.foundation.layout.Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
            r.lines.forEach { BasicText(it, style = textStyle(14f), maxLines = 1) }
        }
        Hint(r.advice)
        ListRow("open this app's android settings") {
            ctx.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", ctx.packageName, null))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
