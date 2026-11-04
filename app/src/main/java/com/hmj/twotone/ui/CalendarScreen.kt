package com.hmj.twotone.ui

import android.Manifest
import android.content.ContentUris
import android.content.Intent
import android.provider.CalendarContract
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hmj.twotone.calendar.CalEvent
import com.hmj.twotone.calendar.loadEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * A month grid on top (◂ ▸ change month, tap a day to see it, hold a day to
 * add an event on it) and that day's events underneath.
 */
@Composable
fun CalendarScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val gate = rememberPermissionGate(listOf(Manifest.permission.READ_CALENDAR))
    val tick = LocalResumeTick.current
    val is24h = DateFormat.is24HourFormat(ctx)
    val zone = ZoneId.systemDefault()
    val locale = Locale.getDefault()

    fun openIntent(intent: Intent) {
        try { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
    }
    fun newEventOn(day: LocalDate) {
        val begin = day.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        openIntent(
            Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, begin + 3_600_000L)
        )
    }

    PageColumn {
        PageHeader("calendar", onBack)
        if (!gate.granted) {
            AskPermission("Calendar needs permission to read the events on this phone.", gate)
            return@PageColumn
        }

        var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
        var dayText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
        val month = YearMonth.parse(monthText)
        val selected = LocalDate.parse(dayText)

        // The grid always shows six weeks, starting on the locale's first day of the week.
        val firstDow: DayOfWeek = WeekFields.of(locale).firstDayOfWeek
        val gridStart = month.atDay(1).let { d -> d.minusDays(((d.dayOfWeek.value - firstDow.value + 7) % 7).toLong()) }
        val gridEnd = gridStart.plusDays(42)

        var events by remember { mutableStateOf<List<CalEvent>>(emptyList()) }
        LaunchedEffect(monthText, tick) {
            val from = gridStart.atStartOfDay(zone).toInstant().toEpochMilli()
            val to = gridEnd.atStartOfDay(zone).toInstant().toEpochMilli()
            events = withContext(Dispatchers.IO) { loadEvents(ctx, from, to) }
        }
        val byDay = remember(events) { events.groupBy { it.startDate() } }

        // Month bar
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.press(onPress = { monthText = month.minusMonths(1).toString() }).padding(horizontal = 18.dp, vertical = 8.dp)) {
                BasicText("◂", style = textStyle(18f, bold = true, fixed = true))
            }
            BasicText(
                month.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale)).lowercase(),
                Modifier.weight(1f).press(onPress = {
                    monthText = YearMonth.now().toString(); dayText = LocalDate.now().toString()
                }),
                style = textStyle(18f, bold = true, fixed = true).copy(textAlign = TextAlign.Center),
                maxLines = 1
            )
            Box(Modifier.press(onPress = { monthText = month.plusMonths(1).toString() }).padding(horizontal = 18.dp, vertical = 8.dp)) {
                BasicText("▸", style = textStyle(18f, bold = true, fixed = true))
            }
        }
        SolidRule()

        // Weekday initials
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)) {
            for (i in 0 until 7) {
                val dow = firstDow.plus(i.toLong())
                BasicText(
                    dow.getDisplayName(TextStyle.NARROW, locale).lowercase(),
                    Modifier.weight(1f),
                    style = textStyle(13f, fixed = true).copy(textAlign = TextAlign.Center),
                    maxLines = 1
                )
            }
        }

        // Day grid
        Column(Modifier.padding(horizontal = 8.dp)) {
            for (week in 0 until 6) {
                Row(Modifier.fillMaxWidth()) {
                    for (i in 0 until 7) {
                        val day = gridStart.plusDays((week * 7 + i).toLong())
                        DayCell(
                            day = day,
                            inMonth = day.month == month.month,
                            isToday = day == LocalDate.now(),
                            isSelected = day == selected,
                            eventCount = byDay[day]?.size ?: 0,
                            modifier = Modifier.weight(1f),
                            onPress = {
                                dayText = day.toString()
                                if (day.month != month.month) monthText = YearMonth.from(day).toString()
                            },
                            onLongPress = { newEventOn(day) }
                        )
                    }
                }
            }
        }
        DitherRule()

        // The selected day's events
        val dayEvents = byDay[selected].orEmpty()
        val heading = selected.format(DateTimeFormatter.ofPattern("EEE d MMM", locale)).lowercase()
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 4.dp)) {
            BasicText(heading, Modifier.weight(1f), style = textStyle(17f, bold = true), maxLines = 1, softWrap = false)
            BasicText(
                when (dayEvents.size) { 0 -> "free"; 1 -> "1 event"; else -> "${dayEvents.size} events" },
                style = textStyle(14f),
                maxLines = 1
            )
        }
        val rows = dayEvents.map<CalEvent, @Composable () -> Unit> { e ->
            @Composable {
                ListRow("${e.startTime(is24h)}  ${e.title}", trailing = "") {
                    openIntent(Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, e.eventId)))
                }
            }
        } + listOf<@Composable () -> Unit>({ ListRow("+ new event", onPress = { newEventOn(selected) }) })
        PagedList(rows, Modifier.weight(1f), resetKey = dayText) { it() }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    eventCount: Int,
    modifier: Modifier,
    onPress: () -> Unit,
    onLongPress: () -> Unit
) {
    val p = LocalPalette.current
    val fg = if (isSelected) p.paper else p.ink
    var m = modifier.height(32.dp).padding(1.dp)
    m = when {
        isSelected -> m.background(p.ink)
        isToday -> m.border(2.dp, p.ink)
        else -> m
    }
    Box(m.press(onLongPress = onLongPress, onPress = onPress), contentAlignment = Alignment.Center) {
        if (inMonth || isSelected) {
            BasicText(
                day.dayOfMonth.toString(),
                style = textStyle(if (inMonth) 17f else 13f, bold = isToday, color = fg, fixed = true),
                maxLines = 1
            )
        }
        // Up to three dots for events
        if (eventCount > 0) {
            Canvas(Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).width(14.dp).height(2.dp)) {
                val d = 2.dp.toPx()
                val n = minOf(eventCount, 3)
                val startX = (size.width - (n * 2 - 1) * d) / 2
                for (k in 0 until n) drawRect(fg, Offset(startX + k * 2 * d, 0f), Size(d, d))
            }
        }
    }
}
