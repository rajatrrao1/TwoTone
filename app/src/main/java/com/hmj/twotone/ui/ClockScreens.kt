package com.hmj.twotone.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hmj.twotone.clock.Alarm
import com.hmj.twotone.clock.AlarmStore
import com.hmj.twotone.clock.Stopwatch
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private fun hm(h: Int, m: Int, is24h: Boolean): String =
    LocalTime.of(h, m).format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", Locale.getDefault())).lowercase()

private fun daysText(a: Alarm): String {
    if (a.days == 0) return "once"
    if (a.days == 0b1111111) return "every day"
    if (a.days == 0b0011111) return "weekdays"
    if (a.days == 0b1100000) return "weekends"
    return DayOfWeek.values().filter { a.repeatsOn(it) }
        .joinToString(" ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()).lowercase().take(2) }
}

private fun duration(ms: Long, tenths: Boolean = false): String {
    val total = ms / 1000
    val h = total / 3600; val m = (total / 60) % 60; val s = total % 60
    val base = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    return if (tenths) base + ".%d".format((ms / 100) % 10) else base
}

/** Big time and date, alarms, then timer, stopwatch and the alarm sound. */
@Composable
fun ClockScreen(onEditAlarm: (Int) -> Unit, onTimer: () -> Unit, onStopwatch: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val is24h = DateFormat.is24HourFormat(ctx)
    val tick = LocalResumeTick.current
    LaunchedEffect(Unit) { AlarmStore.load(ctx) }
    AskForNotificationsOnce()

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(tick) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
        }
    }

    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri: Uri? = r.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            AlarmStore.setSound(ctx, uri)
        }
    }
    val soundName = remember(AlarmStore.soundUri) {
        val uri = AlarmStore.soundUri?.let { Uri.parse(it) } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        try { RingtoneManager.getRingtone(ctx, uri)?.getTitle(ctx)?.lowercase() ?: "default" } catch (e: Exception) { "default" }
    }

    PageColumn {
        PageHeader("clock", onBack)
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
            ClockFace(ClockStyle.TYPE, now.format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm")))
            BasicText(now.format(DateTimeFormatter.ofPattern("EEE d MMMM", Locale.getDefault())).lowercase(), style = textStyle(18f), maxLines = 1)
        }

        val timerRunning = AlarmStore.timerEndsAt > System.currentTimeMillis()
        val rows = buildList<@Composable () -> Unit> {
            add { SectionBar("alarms", if (AlarmStore.alarms.isEmpty()) "" else "${AlarmStore.alarms.count { it.enabled }} on") }
            add { ListRow("+ new alarm", bold = true) { onEditAlarm(0) } }
            if (AlarmStore.alarms.isEmpty()) add { TextRow("No alarms yet.", size = 15f) }
            AlarmStore.alarms.forEach { a ->
                add {
                    AlarmRow(
                        time = hm(a.hour, a.minute, is24h),
                        detail = listOf(daysText(a), a.label, if (a.ring == com.hmj.twotone.clock.RING_VIBRATE) "vibrate only" else if (a.ring == com.hmj.twotone.clock.RING_SOUND) "sound only" else "")
                            .filter { it.isNotBlank() }.joinToString(" · "),
                        on = a.enabled,
                        onToggle = { AlarmStore.save(ctx, a.copy(enabled = !a.enabled)) },
                        onOpen = { onEditAlarm(a.id) }
                    )
                }
            }
            add { SectionBar("tools") }
            add { ListRow("timer", trailing = if (timerRunning) "running ▸" else "▸", onPress = onTimer) }
            add { ListRow("stopwatch", trailing = if (Stopwatch.running) "running ▸" else "▸", onPress = onStopwatch) }
            add {
                ListRow("sound", trailing = soundName) {
                    soundPicker.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, AlarmStore.soundUri?.let { Uri.parse(it) })
                    )
                }
            }
        }
        PagedList(rows, Modifier.weight(1f)) { it() }

        val next = AlarmStore.nextRingMs()
        FooterLine(
            if (next == null) "no alarms set"
            else "next: " + java.time.Instant.ofEpochMilli(next).atZone(java.time.ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern(if (is24h) "EEE HH:mm" else "EEE h:mm a", Locale.getDefault())).lowercase()
        )
    }
}

@Composable
private fun AlarmRow(time: String, detail: String, on: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().height(rowHeight()), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).press(onPress = onOpen).padding(start = 18.dp)) {
            BasicText(time, style = textStyle(22f, bold = true), maxLines = 1, softWrap = false)
            BasicText(detail, style = textStyle(13f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
        // An on/off box: solid when on.
        Box(
            Modifier.padding(end = 14.dp).press(onPress = onToggle).width(56.dp).height(30.dp)
                .border(3.dp, p.ink).background(if (on) p.ink else p.paper),
            contentAlignment = Alignment.Center
        ) {
            BasicText(if (on) "on" else "off", style = textStyle(15f, bold = true, color = if (on) p.paper else p.ink, fixed = true))
        }
    }
}

/** Set the time with big − / + steps, pick the days, add a name. [alarmId] 0 = new. */
@Composable
fun AlarmEditScreen(alarmId: Int, onDone: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val is24h = DateFormat.is24HourFormat(ctx)
    val existing = AlarmStore.byId(alarmId)
    val start = existing ?: LocalTime.now().plusHours(1).let { Alarm(AlarmStore.newId(), it.hour, 0) }
    var hour by rememberSaveable { mutableIntStateOf(start.hour) }
    var minute by rememberSaveable { mutableIntStateOf(start.minute) }
    var days by rememberSaveable { mutableIntStateOf(start.days) }
    var label by rememberSaveable { mutableStateOf(start.label) }
    var ring by rememberSaveable { mutableIntStateOf(start.ring) }
    val p = LocalPalette.current

    PageColumn {
        PageHeader(if (existing == null) "new alarm" else "alarm", onBack)
        // The time, with steppers above and below each half
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            TimeStepper(hour.toString().padStart(2, '0'), onUp = { hour = (hour + 1) % 24 }, onDown = { hour = (hour + 23) % 24 })
            BasicText(":", Modifier.padding(horizontal = 6.dp), style = textStyle(48f, bold = true, fixed = true))
            TimeStepper(minute.toString().padStart(2, '0'), onUp = { minute = (minute + 5) % 60 }, onDown = { minute = (minute + 55) % 60 },
                onUpLong = { minute = (minute + 1) % 60 }, onDownLong = { minute = (minute + 59) % 60 })
        }
        if (!is24h) BasicText(hm(hour, minute, false), Modifier.fillMaxWidth(), style = textStyle(15f).copy(textAlign = TextAlign.Center))
        BasicText("tap ± for 5 minutes, hold for 1", Modifier.fillMaxWidth(), style = textStyle(13f).copy(textAlign = TextAlign.Center), maxLines = 1)
        Spacer(Modifier.height(10.dp))

        SectionBar("repeat", if (days == 0) "once" else "")
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DayOfWeek.values().forEach { d ->
                val bit = 1 shl (d.value - 1)
                val on = days and bit != 0
                Box(
                    Modifier.weight(1f).height(38.dp).border(3.dp, p.ink).background(if (on) p.ink else p.paper)
                        .press { days = days xor bit },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        d.getDisplayName(TextStyle.NARROW, Locale.getDefault()).lowercase(),
                        style = textStyle(17f, bold = true, color = if (on) p.paper else p.ink, fixed = true)
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(rowHeight()).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText("name", Modifier.width(80.dp), style = textStyle(14f))
            BasicTextField(
                value = label,
                onValueChange = { label = it },
                singleLine = true,
                textStyle = textStyle(18f),
                cursorBrush = SolidColor(p.ink),
                modifier = Modifier.weight(1f)
            )
        }
        SolidRule()
        ListRow("rings with", trailing = com.hmj.twotone.clock.RING_LABELS[ring]) { ring = (ring + 1) % 3 }
        ListRow("save", bold = true, trailing = "") {
            AlarmStore.save(ctx, Alarm(start.id, hour, minute, days, true, label.trim(), ring))
            onDone()
        }
        if (existing != null) ListRow("delete", trailing = "") { AlarmStore.delete(ctx, existing.id); onDone() }
    }
}

@Composable
private fun TimeStepper(value: String, onUp: () -> Unit, onDown: () -> Unit, onUpLong: (() -> Unit)? = null, onDownLong: (() -> Unit)? = null) {
    val ink = LocalPalette.current.ink
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(88.dp).height(40.dp).border(3.dp, ink).press(onLongPress = onUpLong, onPress = onUp), contentAlignment = Alignment.Center) {
            BasicText("+", style = textStyle(24f, bold = true, fixed = true))
        }
        BasicText(value, Modifier.padding(vertical = 4.dp), style = textStyle(52f, bold = true, fixed = true), maxLines = 1, softWrap = false)
        Box(Modifier.width(88.dp).height(40.dp).border(3.dp, ink).press(onLongPress = onDownLong, onPress = onDown), contentAlignment = Alignment.Center) {
            BasicText("−", style = textStyle(24f, bold = true, fixed = true))
        }
    }
}

/** Set minutes and seconds, start; while running, the time left counts down. */
@Composable
fun TimerScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { AlarmStore.load(ctx) }
    var minutes by rememberSaveable { mutableIntStateOf(5) }
    var seconds by rememberSaveable { mutableIntStateOf(0) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val running = AlarmStore.timerEndsAt > nowMs
    LaunchedEffect(running) {
        while (running) { nowMs = System.currentTimeMillis(); delay(1_000) }
    }

    PageColumn {
        PageHeader("timer", onBack)
        if (running) {
            Box(Modifier.fillMaxWidth().padding(vertical = 28.dp), contentAlignment = Alignment.Center) {
                PixelText(duration(AlarmStore.timerEndsAt - nowMs).let { if (it.length > 5) it else it }, cell = 9.dp)
            }
            Hint("It rings even if you leave this page.")
            ListRow("cancel", bold = true, trailing = "") { AlarmStore.cancelTimer(ctx); nowMs = System.currentTimeMillis() }
        } else {
            Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                TimeStepper(minutes.toString().padStart(2, '0'), onUp = { minutes = (minutes + 1).coerceAtMost(180) }, onDown = { minutes = (minutes - 1).coerceAtLeast(0) },
                    onUpLong = { minutes = (minutes + 10).coerceAtMost(180) }, onDownLong = { minutes = (minutes - 10).coerceAtLeast(0) })
                BasicText(":", Modifier.padding(horizontal = 6.dp), style = textStyle(48f, bold = true, fixed = true))
                TimeStepper(seconds.toString().padStart(2, '0'), onUp = { seconds = (seconds + 5) % 60 }, onDown = { seconds = (seconds + 55) % 60 })
            }
            BasicText("minutes : seconds · hold minutes for 10", Modifier.fillMaxWidth(), style = textStyle(13f).copy(textAlign = TextAlign.Center), maxLines = 1)
            Spacer(Modifier.height(10.dp))
            SolidRule()
            ListRow("rings with", trailing = com.hmj.twotone.clock.RING_LABELS[AlarmStore.timerRing]) {
                AlarmStore.setTimerRing(ctx, (AlarmStore.timerRing + 1) % 3)
            }
            ListRow("start", bold = true, trailing = "") {
                val ms = (minutes * 60L + seconds) * 1000L
                if (ms > 0) { AlarmStore.startTimer(ctx, ms); nowMs = System.currentTimeMillis() }
            }
        }
    }
}

/** Start/stop, lap and reset; laps listed newest first. */
@Composable
fun StopwatchScreen(onBack: () -> Unit) {
    var nowTick by remember { mutableLongStateOf(0L) }
    val running = Stopwatch.running
    // Tenths of a second would flicker an E-ink screen; whole seconds while running.
    LaunchedEffect(running) {
        while (running) { nowTick = Stopwatch.elapsed(); delay(1_000) }
        nowTick = Stopwatch.elapsed()
    }
    val p = LocalPalette.current

    PageColumn {
        PageHeader("stopwatch", onBack)
        Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            PixelText(duration(if (running) nowTick else Stopwatch.elapsed()), cell = 9.dp)
        }
        if (!running && Stopwatch.elapsed() > 0) {
            BasicText(duration(Stopwatch.elapsed(), tenths = true), Modifier.fillMaxWidth(), style = textStyle(15f).copy(textAlign = TextAlign.Center))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).height(52.dp).background(p.ink).press { Stopwatch.startStop() },
                contentAlignment = Alignment.Center
            ) { BasicText(if (running) "stop" else "start", style = textStyle(20f, bold = true, color = p.paper, fixed = true)) }
            Box(
                Modifier.weight(1f).height(52.dp).border(3.dp, p.ink).press { if (running) Stopwatch.lap() else Stopwatch.reset() },
                contentAlignment = Alignment.Center
            ) { BasicText(if (running) "lap" else "reset", style = textStyle(20f, bold = true, fixed = true)) }
        }
        if (Stopwatch.laps.isNotEmpty()) {
            SectionBar("laps", "${Stopwatch.laps.size}")
            val laps = Stopwatch.laps
            PagedList(laps.indices.toList(), Modifier.weight(1f)) { i ->
                val split = laps[i] - (laps.getOrNull(i + 1) ?: 0L)
                Row(Modifier.fillMaxWidth().height(rowHeight()).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicText("lap ${laps.size - i}", Modifier.weight(1f), style = textStyle(18f), maxLines = 1)
                    BasicText(duration(split, true), Modifier.padding(end = 12.dp), style = textStyle(15f), maxLines = 1)
                    BasicText(duration(laps[i], true), style = textStyle(18f, bold = true), maxLines = 1)
                }
            }
        }
    }
}
