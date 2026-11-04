package com.hmj.twotone.calendar

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

data class CalEvent(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val location: String
) {
    /** All-day events are stored at midnight UTC, so they are read in UTC. */
    fun startDate(): LocalDate =
        Instant.ofEpochMilli(begin).atZone(if (allDay) ZoneOffset.UTC else ZoneId.systemDefault()).toLocalDate()

    fun startTime(is24h: Boolean): String =
        if (allDay) "all day"
        else Instant.ofEpochMilli(begin).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", Locale.getDefault()))
            .lowercase()
}

/** Events overlapping [fromMs, toMs], from every calendar on the phone. Needs READ_CALENDAR. */
fun loadEvents(context: Context, fromMs: Long, toMs: Long): List<CalEvent> {
    val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
        ContentUris.appendId(it, fromMs)
        ContentUris.appendId(it, toMs)
    }.build()
    val projection = arrayOf(
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.EVENT_LOCATION
    )
    val out = mutableListOf<CalEvent>()
    try {
        context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")
            ?.use { c ->
                while (c.moveToNext()) {
                    out += CalEvent(
                        eventId = c.getLong(0),
                        title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(no title)",
                        begin = c.getLong(2),
                        end = c.getLong(3),
                        allDay = c.getInt(4) == 1,
                        location = c.getString(5).orEmpty()
                    )
                }
            }
    } catch (e: SecurityException) {
        return emptyList()
    }
    return out.filter { it.end > fromMs }
}

/** "", "tomorrow " or "wed 5 " in front of an event's time. */
fun dayPrefix(date: LocalDate, today: LocalDate): String = when (date) {
    today -> ""
    today.plusDays(1) -> "tomorrow "
    else -> date.format(DateTimeFormatter.ofPattern("EEE d ", Locale.getDefault())).lowercase()
}
