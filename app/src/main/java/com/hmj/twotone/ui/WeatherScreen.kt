package com.hmj.twotone.ui

import android.Manifest
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
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

    PageColumn {
        PageHeader("weather", onBack)
        if (loading) Hint("Updating the forecast.")
        if (!hasPlace) {
            Hint("Choose a place to see its weather.")
            ListRow("choose place", onPress = onChangePlace)
            return@PageColumn
        }
        if (error != null) {
            Hint(error)
            ConnectionCheck()
        }
        if (forecast == null) {
            if (!loading) ListRow("try again", trailing = "", onPress = onRefresh)
            ListRow("change place", onPress = onChangePlace)
            return@PageColumn
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PixelGrid(WeatherIcons.forSymbol(forecast.nowSymbol), cell = 6.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                BasicText("${forecast.nowTemp.roundToInt()}°", style = textStyle(38f, bold = true))
                BasicText(describe(forecast.nowSymbol), style = textStyle(16f))
                BasicText(forecast.place.substringBefore(","), style = textStyle(14f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        DitherRule()

        val hourFmt = DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h a", Locale.getDefault())
        val dayFmt = DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())
        val rows: List<@Composable () -> Unit> =
            forecast.hours.map { h ->
                @Composable {
                    ForecastRow(
                        Instant.ofEpochMilli(h.epochMs).atZone(zone).format(hourFmt).lowercase(),
                        h.symbol,
                        "${h.temp.roundToInt()}°"
                    )
                }
            } + forecast.days.map { d ->
                @Composable {
                    ForecastRow(d.date.format(dayFmt).lowercase(), d.symbol, "${d.max.roundToInt()}° / ${d.min.roundToInt()}°")
                }
            } + listOf<@Composable () -> Unit>(
                { ListRow("refresh", trailing = "", onPress = onRefresh) },
                { ListRow("change place", onPress = onChangePlace) }
            )
        PagedList(rows, Modifier.weight(1f)) { it() }
        BasicText(
            "Weather data by Open-Meteo.com",
            Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            style = textStyle(12f),
            maxLines = 1
        )
    }
}

@Composable
private fun ForecastRow(label: String, symbol: String, value: String) {
    Row(
        Modifier.fillMaxWidth().height(rowHeight()).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(label, Modifier.width(96.dp), style = textStyle(17f), maxLines = 1, softWrap = false)
        PixelGrid(WeatherIcons.forSymbol(symbol), cell = 3.dp)
        Spacer(Modifier.width(12.dp))
        BasicText(describe(symbol), Modifier.weight(1f), style = textStyle(14f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        BasicText(value, style = textStyle(17f, bold = true), maxLines = 1, softWrap = false)
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
