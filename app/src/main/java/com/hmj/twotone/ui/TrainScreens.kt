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
import androidx.compose.foundation.border
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
fun TrainsScreen(
    onStation: (Station) -> Unit,
    onAdd: () -> Unit,
    onKey: () -> Unit,
    onBack: () -> Unit,
    onPlan: () -> Unit = {},
    onTrips: () -> Unit = {}
) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { Ns.load(ctx) }
    PageColumn {
        PageHeader("trains", onBack)
        if (Ns.key == null) {
            Hint("Departures come straight from NS. They ask for a free personal key; it takes two minutes and you enter it once.")
            ListRow("add your NS key", bold = true, onPress = onKey)
            return@PageColumn
        }
        val rows = buildList<@Composable () -> Unit> {
            add { ListRow("plan a trip", bold = true, size = 26f, onPress = onPlan) }
            if (Ns.recentTrips.isNotEmpty()) {
                add { SectionBar("recent trips") }
                Ns.recentTrips.forEach { (a, b) ->
                    add {
                        ListRow("${(a.short.ifBlank { a.name }).lowercase()} → ${(b.short.ifBlank { b.name }).lowercase()}", trailing = "▸") {
                            TripDraft.from = a; TripDraft.to = b; TripDraft.at = null; TripDraft.arriveBy = false
                            onTrips()
                        }
                    }
                }
            }
            add { SectionBar("departures") }
            Ns.favourites.forEach { s ->
                add { ListRow(s.name.lowercase(), trailing = "", size = 26f, onLongPress = { Ns.removeFavourite(ctx, s.code) }) { onStation(s) } }
            }
            add { ListRow("+ add a station", onPress = onAdd) }
        }
        PagedList(rows, Modifier.weight(1f), resetKey = Ns.favourites.size + 10 * Ns.recentTrips.size) { it() }
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

/** Find a station by name; [onPick] gets it (add to your list, or use in a trip). */
@Composable
fun AddStationScreen(onBack: () -> Unit, title: String = "add a station", onPick: ((Station) -> Unit)? = null) {
    val ctx = LocalContext.current
    val palette = LocalPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val all by produceState<Result<List<Station>>?>(null) {
        value = withContext(Dispatchers.IO) { runCatching { Ns.stations(ctx) } }
    }
    val q = query.trim()
    PageColumn {
        PageHeader(title, onBack)
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
                    ListRow(s.name.lowercase(), trailing = s.code.lowercase()) {
                        if (onPick != null) onPick(s) else { Ns.addFavourite(ctx, s); onBack() }
                    }
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


/** The trip being planned: kept while you pick stations and come back. */
object TripDraft {
    var from by mutableStateOf<Station?>(null)
    var to by mutableStateOf<Station?>(null)
    /** null = now */
    var at by mutableStateOf<java.time.LocalDateTime?>(null)
    var arriveBy by mutableStateOf(false)
}

/**
 * From, to, when; then search. Times step by 15 minutes (hold for an hour);
 * "now" goes back to leaving straight away.
 */
@Composable
fun PlanTripScreen(onPickFrom: () -> Unit, onPickTo: () -> Unit, onSearch: () -> Unit, onBack: () -> Unit) {
    val fmt = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", java.util.Locale.getDefault())
    val shownTime = TripDraft.at?.format(fmt)?.lowercase() ?: "now"
    fun shift(min: Long) {
        val base = TripDraft.at ?: java.time.LocalDateTime.now().withSecond(0).withNano(0)
        TripDraft.at = base.plusMinutes(min)
    }
    PageColumn {
        PageHeader("plan a trip", onBack)
        ListRow("from", trailing = bracket(TripDraft.from?.name?.lowercase() ?: "choose"), onPress = onPickFrom)
        ListRow("to", trailing = bracket(TripDraft.to?.name?.lowercase() ?: "choose"), onPress = onPickTo)
        ListRow("swap from and to", trailing = "⇅") { val a = TripDraft.from; TripDraft.from = TripDraft.to; TripDraft.to = a }
        SolidRule()
        ListRow(if (TripDraft.arriveBy) "arrive by" else "leave at", trailing = bracket(shownTime)) { TripDraft.arriveBy = !TripDraft.arriveBy }
        val p = LocalPalette.current
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
            listOf("−15" to -15L, "+15" to 15L, "now" to 0L).forEach { (label, m) ->
                androidx.compose.foundation.layout.Box(
                    Modifier.weight(1f).height(42.dp).border(3.dp, p.ink)
                        .press(onLongPress = if (m != 0L) ({ shift(m * 4) }) else null) { if (m == 0L) TripDraft.at = null else shift(m) },
                    contentAlignment = Alignment.Center
                ) { BasicText(label, style = textStyle(18f, bold = true, fixed = true)) }
            }
        }
        BasicText("tap leave at / arrive by to switch · hold ±15 for an hour", Modifier.padding(horizontal = 14.dp), style = textStyle(13f), maxLines = 1)
        Spacer(Modifier.height(8.dp))
        SolidRule()
        ListRow("search", bold = true, trailing = "▸") { if (TripDraft.from != null && TripDraft.to != null) onSearch() }
        Spacer(Modifier.weight(1f))
        FooterLine("live from ns")
    }
}

private fun hm(t: java.time.OffsetDateTime?) =
    t?.atZoneSameInstant(java.time.ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--:--"

private fun late(m: Long) = if (m > 0) " +$m" else ""

/** The ways there: each with times, how long, changes and the first train. Tap one for its legs. */
@Composable
fun TripResultsScreen(onTrip: (com.hmj.twotone.trains.Trip) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val from = TripDraft.from; val to = TripDraft.to
    var refresh by remember { mutableStateOf(0) }
    val result by produceState<Result<List<com.hmj.twotone.trains.Trip>>?>(null, from?.code, to?.code, TripDraft.at, TripDraft.arriveBy, refresh) {
        if (from == null || to == null) { value = Result.success(emptyList()); return@produceState }
        val at = TripDraft.at?.atZone(java.time.ZoneId.systemDefault())?.toOffsetDateTime()
        value = withContext(Dispatchers.IO) { runCatching { Ns.trips(from.code, to.code, at, TripDraft.arriveBy) } }
        if (value?.isSuccess == true) Ns.rememberTrip(ctx, from, to)
    }
    PageColumn {
        PageHeader("${(from?.short?.ifBlank { from.name } ?: "?").lowercase()} → ${(to?.short?.ifBlank { to.name } ?: "?").lowercase()}", onBack)
        val r = result
        when {
            r == null -> Hint("Asking NS.")
            r.isFailure -> { Hint(r.exceptionOrNull()?.message ?: "The trips couldn't be fetched."); ListRow("try again", trailing = "") { refresh++ } }
            r.getOrThrow().isEmpty() -> Hint("No trips found.")
            else -> PagedList(r.getOrThrow(), Modifier.weight(1f), rowSize = 30f) { t ->
                val first = t.legs.firstOrNull { !it.walk }
                Column(Modifier.fillMaxWidth().height(rowHeight(30f)).press { onTrip(t) }.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            hm(t.departs.planned) + late(t.departs.delayMinutes) + "  →  " + hm(t.arrives.planned) + late(t.arrives.delayMinutes),
                            Modifier.weight(1f), style = textStyle(20f, bold = true), maxLines = 1, softWrap = false
                        )
                        BasicText(if (t.cancelled) "cancelled" else "${t.minutes} min", style = textStyle(16f, bold = t.cancelled), maxLines = 1)
                    }
                    BasicText(
                        listOfNotNull(
                            if (t.transfers == 0) "direct" else if (t.transfers == 1) "1 change" else "${t.transfers} changes",
                            first?.let { (it.kind.lowercase() + " " + it.direction.lowercase()).trim() },
                            first?.from?.track?.takeIf { it.isNotBlank() }?.let { "platform " + (if (first.from.trackChanged) "→" else "") + it },
                            crowdWord(t.crowd)
                        ).joinToString(" · "),
                        style = textStyle(13f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
                    )
                }
                SolidRule(Modifier.padding(horizontal = 14.dp), thickness = 1.dp)
            }
        }
        if (r == null || r.isFailure || r.getOrNull().isNullOrEmpty()) Spacer(Modifier.weight(1f))
        Column(Modifier.press { refresh++ }) {
            FooterLine((if (TripDraft.arriveBy) "arrive by " else "leave ") + (TripDraft.at?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "now") + " · tap to update")
        }
    }
}

private fun crowdWord(c: String) = when (c.uppercase()) { "LOW" -> "quiet"; "MEDIUM" -> "busy"; "HIGH" -> "very busy"; else -> null }

/** One trip, leg by leg: leaving, platform, the train, arriving; changes in between. */
@Composable
fun TripDetailScreen(trip: com.hmj.twotone.trains.Trip, onBack: () -> Unit) {
    val rows = mutableListOf<@Composable () -> Unit>()
    trip.legs.forEachIndexed { i, l ->
        if (i > 0) {
            val prev = trip.legs[i - 1]
            val wait = if (prev.to.actual != null && l.from.actual != null) java.time.Duration.between(prev.to.actual, l.from.actual).toMinutes() else null
            rows.add(@Composable { SectionBar("change at ${l.from.name.lowercase()}", wait?.let { "$it min" } ?: "") })
        }
        rows.add(@Composable { LegStopRow(hm(l.from.planned), l.from.delayMinutes, l.from.name, l.from.track, l.from.trackChanged, "leave") })
        rows.add(@Composable {
            BasicText(
                if (l.walk) "walk" else listOfNotNull(
                    (l.kind.lowercase() + " to " + l.direction.lowercase()).trim(),
                    if (l.cancelled) "cancelled" else null,
                    crowdWord(l.crowd),
                    l.note.takeIf { it.isNotBlank() }?.lowercase()
                ).joinToString(" · "),
                Modifier.fillMaxWidth().padding(start = 82.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                style = textStyle(15f), maxLines = 2, overflow = TextOverflow.Ellipsis
            )
        })
        rows.add(@Composable { LegStopRow(hm(l.to.planned), l.to.delayMinutes, l.to.name, l.to.track, l.to.trackChanged, "arrive") })
    }
    PageColumn {
        PageHeader("${trip.minutes} min" + if (trip.transfers > 0) " · ${trip.transfers} change" + (if (trip.transfers > 1) "s" else "") else " · direct", onBack)
        PagedList(rows, Modifier.weight(1f)) { it() }
        FooterLine(if (trip.cancelled) "part of this trip is cancelled" else "live from ns")
    }
}

@Composable
private fun LegStopRow(time: String, delayMin: Long, station: String, track: String, changed: Boolean, what: String) {
    Row(Modifier.fillMaxWidth().height(rowHeight()).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(68.dp)) {
            BasicText(time, style = textStyle(20f, bold = true), maxLines = 1, softWrap = false)
            if (delayMin > 0) BasicText("+$delayMin", style = textStyle(14f, bold = true), maxLines = 1)
        }
        Column(Modifier.weight(1f)) {
            BasicText(station.lowercase(), style = textStyle(18f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            BasicText(what, style = textStyle(13f), maxLines = 1)
        }
        if (track.isNotBlank()) BasicText((if (changed) "→" else "") + track, style = textStyle(20f, bold = true), maxLines = 1)
    }
}
