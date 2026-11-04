package com.hmj.twotone.clock

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hmj.twotone.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * One alarm. [days] is a set of weekdays as bits (Monday = bit 0 … Sunday = bit 6);
 * no days means it rings once and then switches itself off.
 */
data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val days: Int = 0,
    val enabled: Boolean = true,
    val label: String = "",
    /** How it rings: see [RING_LABELS]. */
    val ring: Int = RING_BOTH
) {
    fun repeatsOn(d: DayOfWeek) = days and (1 shl (d.value - 1)) != 0

    /** The next moment this alarm should ring, after [from]. */
    fun nextAfter(from: LocalDateTime): LocalDateTime {
        var t = from.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!t.isAfter(from)) t = t.plusDays(1)
        if (days != 0) while (!repeatsOn(t.dayOfWeek)) t = t.plusDays(1)
        return t
    }
}

const val RING_BOTH = 0
const val RING_VIBRATE = 1
const val RING_SOUND = 2
val RING_LABELS = listOf("sound and vibration", "vibration only", "sound only")

/**
 * Alarms, the running timer and the chosen sound, kept on the phone, and the
 * scheduling that makes Android wake us up at the right time.
 */
object AlarmStore {
    const val TIMER_ID = 900_001
    const val SNOOZE_ID = 900_002
    const val SNOOZE_MINUTES = 10

    var alarms by mutableStateOf<List<Alarm>>(emptyList())
        private set
    /** When the running timer ends (epoch ms), or 0. */
    var timerEndsAt by mutableStateOf(0L)
        private set
    /** How the timer rings (see [RING_LABELS]). */
    var timerRing by mutableStateOf(RING_BOTH)
        private set

    fun setTimerRing(ctx: Context, v: Int) { timerRing = v; prefs(ctx).edit().putInt("timerRing", v).apply() }

    /** Alarm sound, or null for the phone's default alarm sound. */
    var soundUri by mutableStateOf<String?>(null)
        private set

    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val sp = prefs(ctx)
        alarms = try {
            val arr = JSONArray(sp.getString("alarms", "[]"))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Alarm(o.getInt("id"), o.getInt("hour"), o.getInt("minute"), o.optInt("days"), o.optBoolean("enabled", true), o.optString("label"), o.optInt("ring", RING_BOTH))
            }
        } catch (e: Exception) { emptyList() }
        timerEndsAt = sp.getLong("timerEndsAt", 0L).takeIf { it > System.currentTimeMillis() } ?: 0L
        timerRing = sp.getInt("timerRing", RING_BOTH)
        soundUri = sp.getString("sound", null)
    }

    fun byId(id: Int) = alarms.firstOrNull { it.id == id }

    fun save(ctx: Context, a: Alarm) {
        alarms = (alarms.filter { it.id != a.id } + a).sortedWith(compareBy({ it.hour }, { it.minute }))
        persist(ctx)
        schedule(ctx, a)
    }

    fun delete(ctx: Context, id: Int) {
        cancel(ctx, id)
        alarms = alarms.filter { it.id != id }
        persist(ctx)
    }

    fun newId(): Int = (alarms.maxOfOrNull { it.id } ?: 0) + 1

    fun setSound(ctx: Context, uri: Uri?) {
        soundUri = uri?.toString()
        prefs(ctx).edit().putString("sound", soundUri).apply()
    }

    // ---- timer --------------------------------------------------------------

    fun startTimer(ctx: Context, durationMs: Long) {
        timerEndsAt = System.currentTimeMillis() + durationMs
        prefs(ctx).edit().putLong("timerEndsAt", timerEndsAt).apply()
        setAt(ctx, TIMER_ID, timerEndsAt, "timer", timerRing)
    }

    fun cancelTimer(ctx: Context) {
        timerEndsAt = 0L
        prefs(ctx).edit().putLong("timerEndsAt", 0L).apply()
        cancel(ctx, TIMER_ID)
    }

    fun timerFinished(ctx: Context) {
        timerEndsAt = 0L
        prefs(ctx).edit().putLong("timerEndsAt", 0L).apply()
    }

    fun snooze(ctx: Context, label: String, ring: Int) =
        setAt(ctx, SNOOZE_ID, System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L, label, ring)

    // ---- scheduling ---------------------------------------------------------

    /** Re-arms everything: after a restart, or when the app starts. */
    fun scheduleAll(ctx: Context) {
        load(ctx)
        alarms.forEach { schedule(ctx, it) }
        if (timerEndsAt > System.currentTimeMillis()) setAt(ctx, TIMER_ID, timerEndsAt, "timer", timerRing)
    }

    fun schedule(ctx: Context, a: Alarm) {
        if (!a.enabled) { cancel(ctx, a.id); return }
        val at = a.nextAfter(LocalDateTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        setAt(ctx, a.id, at, a.label, a.ring)
    }

    /** After an alarm rang: a repeating one is set for next time, a one-off is switched off. */
    fun afterRinging(ctx: Context, id: Int) {
        load(ctx)
        val a = byId(id) ?: return
        if (a.days == 0) save(ctx, a.copy(enabled = false)) else schedule(ctx, a)
    }

    /** The next alarm that will ring, as epoch ms, or null. */
    fun nextRingMs(): Long? = alarms.filter { it.enabled }
        .map { it.nextAfter(LocalDateTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }
        .minOrNull()

    private fun setAt(ctx: Context, id: Int, atMs: Long, label: String, ring: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val fire = firePendingIntent(ctx, id, label, ring)
        val show = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_CLOCK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val exactAllowed = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (exactAllowed) am.setAlarmClock(AlarmManager.AlarmClockInfo(atMs, show), fire)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, fire)
    }

    private fun cancel(ctx: Context, id: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(firePendingIntent(ctx, id, "", RING_BOTH))
    }

    private fun firePendingIntent(ctx: Context, id: Int, label: String, ring: Int): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, id,
            Intent(ctx, AlarmReceiver::class.java).putExtra(EXTRA_ID, id).putExtra(EXTRA_LABEL, label).putExtra(EXTRA_RING, ring),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun persist(ctx: Context) {
        val arr = JSONArray()
        alarms.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("hour", it.hour).put("minute", it.minute)
                    .put("days", it.days).put("enabled", it.enabled).put("label", it.label).put("ring", it.ring)
            )
        }
        prefs(ctx).edit().putString("alarms", arr.toString()).apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("twotone_clock", Context.MODE_PRIVATE)

    const val EXTRA_ID = "alarmId"
    const val EXTRA_LABEL = "alarmLabel"
    const val EXTRA_RING = "alarmRing"
}

/** A stopwatch that keeps going while you look at other apps. */
object Stopwatch {
    var startedAt by mutableStateOf(0L)   // elapsedRealtime when running, else 0
        private set
    var banked by mutableStateOf(0L)      // time from earlier runs
        private set
    var laps by mutableStateOf<List<Long>>(emptyList())
        private set

    val running: Boolean get() = startedAt != 0L
    fun elapsed(): Long = banked + if (running) android.os.SystemClock.elapsedRealtime() - startedAt else 0L

    fun startStop() {
        if (running) { banked = elapsed(); startedAt = 0L } else startedAt = android.os.SystemClock.elapsedRealtime()
    }
    fun lap() { if (running) laps = listOf(elapsed()) + laps }
    fun reset() { startedAt = 0L; banked = 0L; laps = emptyList() }
}
