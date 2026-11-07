package com.hmj.twotone.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hmj.twotone.trains.Departure
import com.hmj.twotone.trains.Ns
import com.hmj.twotone.trains.Station
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter

/** Your stations. Tap one for its departures; hold one to remove it. */
@Composable
fun TrainsScreen(onStation: (Station) -> Unit, onAdd: () -> Unit, onKey: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { Ns.load(ctx) }
    PageColumn {
        PageHeader("trains", onBack)
        if (Ns.key == null) {
            Hint("Departures come straight from NS. They ask for a free personal key; it takes two minutes and you enter it once.")
            ListRow("add your NS key", bold = true, onPress = onKey)
            return@PageColumn
        }
        val rows: List<Station?> = Ns.favourites + listOf<Station?>(null)
        PagedList(rows, Modifier.weight(1f), resetKey = Ns.favourites.size, rowSize = 26f) { s ->
            if (s == null) ListRow("+ add a station", onPress = onAdd)
            else ListRow(s.name.lowercase(), trailing = "", size = 26f, onLongPress = { Ns.removeFavourite(ctx, s.code) }) { onStation(s) }
        }
        androidx.compose.foundation.layout.Column(Modifier.press(onPress = onKey)) {
            FooterLine("live from NS · hold a station to remove it · tap here for the key")
        }
    }
}

/** Departures from one station, a page at a time, refreshed every minute while you look. */
@Composable
fun DeparturesScreen(station: Station, onBack: () -> Unit) {
    val tick = LocalResumeTick.current
    var refreshCount by remember { mutableStateOf(0) }
    var updated by remember { mutableStateOf("") }
    val result by produceState<Result<List<Departure>>?>(null, station.code, refreshCount, tick) {
        value = withContext(Dispatchers.IO) { runCatching { Ns.departures(station.code) } }
        updated = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
    }
    LaunchedEffect(station.code, com.hmj.twotone.Visible.on) {
        while (com.hmj.twotone.Visible.on) { delay(60_000L); refreshCount++ }
    }
    PageColumn {
        PageHeader(station.short.ifBlank { station.name }.lowercase(), onBack)
        val r = result
        when {
            r == null -> Hint("Asking NS.")
            r.isFailure -> {
                Hint((r.exceptionOrNull() as? Exception)?.let {
                    if (it is java.io.IOException && it.message != null) it.message!! else com.hmj.twotone.weather.WeatherApi.explain(it)
                } ?: "Departures couldn't be fetched.")
                ListRow("try again", trailing = "") { refreshCount++ }
            }
            r.getOrThrow().isEmpty() -> Hint("No departures right now.")
            else -> PagedList(r.getOrThrow(), Modifier.weight(1f), resetKey = station.code, rowSize = 30f) { d -> DepartureRow(d) }
        }
        if (r == null || r.isFailure || r.getOrNull().isNullOrEmpty()) Spacer(Modifier.weight(1f))
        androidx.compose.foundation.layout.Column(Modifier.press { refreshCount++ }) {
            FooterLine(if (updated.isEmpty()) "ns" else "ns · updated $updated · tap to update")
        }
    }
}

/**
 *   15:31 +2   IC   den helder        5b
 *              via amsterdam c., alkmaar
 */
@Composable
private fun DepartureRow(d: Departure) {
    val fmt = DateTimeFormatter.ofPattern("HH:mm")
    val local = d.planned.atZoneSameInstant(java.time.ZoneId.systemDefault())
    Column(Modifier.fillMaxWidth().height(rowHeight(30f)).padding(horizontal = 14.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(local.format(fmt), Modifier.width(64.dp), style = textStyle(20f, bold = true), maxLines = 1, softWrap = false)
            BasicText(
                when { d.cancelled -> "x"; d.delayMinutes > 0 -> "+${d.delayMinutes}"; else -> "" },
                Modifier.width(40.dp), style = textStyle(18f, bold = true), maxLines = 1
            )
            BasicText(
                (if (d.cancelled) "cancelled · " else "") + d.direction.lowercase(),
                Modifier.weight(1f), style = textStyle(20f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
            )
            BasicText(
                (if (d.trackChanged) "→" else "") + d.track,
                Modifier.padding(start = 8.dp), style = textStyle(20f, bold = true), maxLines = 1
            )
        }
        BasicText(
            listOf(d.kind.lowercase(), d.via.takeIf { it.isNotBlank() }?.let { "via ${it.lowercase()}" }, d.note.takeIf { it.isNotBlank() }?.lowercase())
                .filterNotNull().joinToString(" · "),
            Modifier.padding(start = 104.dp), style = textStyle(13f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
        )
    }
    SolidRule(Modifier.padding(horizontal = 14.dp), thickness = 1.dp)
}

/** Find a station by name and add it to your list. */
@Composable
fun AddStationScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val all by produceState<Result<List<Station>>?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { Ns.stations(ctx) } }
    }
    val q = query.trim()
    PageColumn {
        PageHeader("add a station", onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("station: ", style = textStyle(18f, bold = true), maxLines = 1, softWrap = false)
            BasicTextField(
                value = query, onValueChange = { query = it.replace("\n", "") }, singleLine = true,
                textStyle = textStyle(18f), cursorBrush = SolidColor(palette.ink),
                modifier = Modifier.weight(1f).focusRequester(focus)
            )
        }
        SolidRule()
        val r = all
        when {
            r == null -> Hint("Getting the list of stations.")
            r.isFailure -> Hint(r.exceptionOrNull()?.message ?: "The stations couldn't be fetched.")
            q.isEmpty() -> Hint("Type part of a station's name.")
            else -> {
                val found = r.getOrThrow().filter { it.name.contains(q, true) || it.short.contains(q, true) || it.code.equals(q, true) }
                    .sortedBy { !it.name.startsWith(q, true) }.take(40)
                PagedList(found, Modifier.weight(1f), resetKey = q) { s ->
                    ListRow(s.name.lowercase(), trailing = s.code.lowercase()) { Ns.addFavourite(ctx, s); onBack() }
                }
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Enter (or change) the NS key, with how to get one. */
@Composable
fun NsKeyScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    var key by rememberSaveable { mutableStateOf(Ns.key.orEmpty()) }
    var message by remember { mutableStateOf<String?>(null) }
    PageColumn {
        PageHeader("ns key", onBack)
        Hint("On a computer: apiportal.ns.nl, sign up, Products, \u201cNs-App\u201d, subscribe. Your profile then shows a primary key. Type or paste it here.")
        BasicTextField(
            value = key, onValueChange = { key = it.trim() }, singleLine = true,
            textStyle = textStyle(16f), cursorBrush = SolidColor(palette.ink),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
        )
        SolidRule()
        ListRow("save and test", bold = true, trailing = "") {
            Ns.setKey(ctx, key)
            message = "Testing."
            scope.launch {
                message = try {
                    val n = withContext(Dispatchers.IO) { Ns.departures("UT").size }
                    "It works: $n departures from Utrecht Centraal."
                } catch (e: Exception) { e.message ?: "That didn't work." }
            }
        }
        ListRow("open the NS API portal") {
            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://apiportal.ns.nl")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
        }
        message?.let { Hint(it) }
    }
}
