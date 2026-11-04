package com.hmj.twotone.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hmj.twotone.clock.AlarmStore
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the phone is doing right now, for the status bar and the status page. */
data class PhoneStatus(
    val battery: Int = -1,
    val charging: Boolean = false,
    val wifi: Boolean = false,
    val wifiLevel: Int = 0,
    val mobile: Boolean = false,
    val mobileLevel: Int = 0,
    val airplane: Boolean = false,
    val bluetooth: Boolean = false,
    val bluetoothAudio: Boolean = false,
    val ringer: Int = AudioManager.RINGER_MODE_NORMAL,
    val nextAlarm: Long? = null
)

/** The icons you can show, in the order they appear from left to right. */
val STATUS_ITEMS = listOf(
    "notifications" to "notifications (left side)",
    "date" to "date (left side)",
    "alarm" to "next alarm",
    "sound" to "silent / vibrate",
    "bluetooth" to "bluetooth",
    "wifi" to "wi-fi",
    "mobile" to "mobile signal",
    "battery" to "battery icon"
    // The battery percentage lives in the home page's bottom line, not up here.
)

/** Reads the phone's state. Quick enough to call every half minute. */
@SuppressLint("MissingPermission")
fun readStatus(ctx: Context, battery: Int, charging: Boolean): PhoneStatus {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    var wifi = false; var wifiLevel = 0; var mobile = false
    try {
        cm.allNetworks.mapNotNull { cm.getNetworkCapabilities(it) }.forEach { caps ->
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                wifi = true
                val rssi = if (Build.VERSION.SDK_INT >= 29) caps.signalStrength else Int.MIN_VALUE
                wifiLevel = when {
                    rssi == Int.MIN_VALUE -> 3
                    rssi > -60 -> 3
                    rssi > -70 -> 2
                    rssi > -80 -> 1
                    else -> 0
                }
            }
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) mobile = true
        }
    } catch (e: Exception) { }
    val mobileLevel = try {
        val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        if (Build.VERSION.SDK_INT >= 28) tm.signalStrength?.level ?: 0 else 0
    } catch (e: Exception) { 0 }
    val bt = try {
        (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.isEnabled == true
    } catch (e: Exception) { false }
    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val btAudio = try {
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    } catch (e: Exception) { false }
    val airplane = Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    val alarm = AlarmStore.nextRingMs()
        ?: (ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager).nextAlarmClock?.triggerTime
    return PhoneStatus(battery, charging, wifi, wifiLevel, mobile || mobileLevel > 0, mobileLevel, airplane, bt, btAudio, am.ringerMode, alarm)
}

// ---- pixel icons, 2 dp per pixel ----------------------------------------------

private val ICON_BT = listOf("  #  ", "  ## ", "# # #", " ### ", "  #  ", " ### ", "# # #", "  ## ", "  #  ")
private val ICON_BT_AUDIO = listOf("  #   #", "  ##   ", "# # #  ", " ###  #", "  #    ", " ###  #", "# # #  ", "  ##   ", "  #   #")
private val ICON_VIBRATE = listOf("  ###  ", "# # # #", "# # # #", "# # # #", "# # # #", "# # # #", "  ###  ")
private val ICON_SILENT = listOf("#       ", " #  #   ", "  #### ", "  ##### ", "  ## ## ", "  ###  #", " #######", "   ##   ")
private val ICON_ALARM = listOf("##   ##", " ##### ", "#  #  #", "#  ## #", "#     #", " ##### ", "#     #")
private val ICON_BELL = listOf("   #   ", "  ###  ", " ##### ", " ##### ", " ##### ", "#######", "       ", "  ###  ")
private val ICON_PLANE = listOf("   #   ", "   #   ", "  ###  ", "#######", "   #   ", "   #   ", "  ###  ")

private fun wifiIcon(level: Int): List<String> {
    val all = listOf("  #######  ", " #       # ", "#  #####  #", "  #     #  ", "    ###    ", "     #     ")
    // Arcs that aren't reached are drawn dotted.
    return all.mapIndexed { i, row ->
        val arc = when (i) { 0, 1 -> 3; 2, 3 -> 2; 4 -> 1; else -> 0 }
        if (arc > level) row.mapIndexed { j, c -> if (c == '#' && j % 2 == 1) ' ' else c }.joinToString("") else row
    }
}

private fun signalIcon(level: Int): List<String> = (0 until 8).map { r ->
    (0 until 4).joinToString(" ") { bar ->
        val h = 2 + bar * 2
        val filled = bar < level.coerceIn(0, 4)
        when {
            r < 8 - h -> "  "
            filled -> "##"
            r == 7 || r == 8 - h -> "##"
            else -> "# "
        }
    }
}

/**
 * The launcher's own status bar: optional date on the left, the time in the
 * middle, your chosen icons on the right. Tap it for the status page.
 */
@Composable
fun LauncherStatusBar(status: PhoneStatus, items: Set<String>, notificationCount: Int, onBell: () -> Unit, onPress: () -> Unit) {
    val ctx = LocalContext.current
    val tick = LocalResumeTick.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(tick) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
        }
    }
    val is24h = DateFormat.is24HourFormat(ctx)
    val small = textStyle(14f, fixed = true)
    Column {
        Row(
            Modifier.fillMaxWidth().height(30.dp).press(onPress = onPress).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // The bell opens the notification list; it only shows when there is something.
                if ("notifications" in items && notificationCount > 0) {
                    Row(
                        Modifier.press(onPress = onBell).padding(end = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PixelGrid(ICON_BELL, cell = 2.dp)
                        BasicText("$notificationCount", style = small, maxLines = 1)
                    }
                }
                if ("date" in items) {
                    BasicText(now.format(DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())).lowercase(), style = small, maxLines = 1)
                }
            }
            BasicText(
                now.format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm")),
                style = textStyle(18f, bold = true, fixed = true), maxLines = 1, softWrap = false
            )
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (status.airplane) PixelGrid(ICON_PLANE, cell = 2.dp)
                if ("alarm" in items && status.nextAlarm != null) PixelGrid(ICON_ALARM, cell = 2.dp)
                if ("sound" in items) when (status.ringer) {
                    AudioManager.RINGER_MODE_VIBRATE -> PixelGrid(ICON_VIBRATE, cell = 2.dp)
                    AudioManager.RINGER_MODE_SILENT -> PixelGrid(ICON_SILENT, cell = 2.dp)
                    else -> Unit
                }
                if ("bluetooth" in items && status.bluetooth) PixelGrid(if (status.bluetoothAudio) ICON_BT_AUDIO else ICON_BT, cell = 2.dp)
                if ("wifi" in items && status.wifi) PixelGrid(wifiIcon(status.wifiLevel), cell = 2.dp)
                if ("mobile" in items && !status.airplane) PixelGrid(signalIcon(status.mobileLevel), cell = 2.dp)
                if ("battery" in items && status.battery >= 0) BatteryIcon(status.battery, status.charging)
            }
        }
        SolidRule(thickness = 2.dp)
    }
}

/** A battery whose fill matches the charge, with a notch cut out while charging. */
@Composable
fun BatteryIcon(percent: Int, charging: Boolean) {
    val p = LocalPalette.current
    Canvas(Modifier.width(24.dp).height(12.dp)) {
        val w = 2.dp.toPx()
        val bodyW = size.width - 3.dp.toPx()
        drawRect(p.ink, Offset(w / 2, w / 2), Size(bodyW - w, size.height - w), style = Stroke(w))
        drawRect(p.ink, Offset(bodyW, size.height * 0.3f), Size(3.dp.toPx(), size.height * 0.4f))
        val inner = (bodyW - 3 * w) * percent.coerceIn(0, 100) / 100f
        drawRect(p.ink, Offset(w * 1.5f, w * 1.5f), Size(inner, size.height - 3 * w))
        if (charging) {
            // A small lightning cut-out in the middle
            val cx = bodyW / 2; val cy = size.height / 2; val u = 1.5.dp.toPx()
            drawRect(p.paper, Offset(cx - u, cy - 2 * u), Size(u * 1.5f, u * 2))
            drawRect(p.paper, Offset(cx - u * 0.5f, cy - u * 0.5f), Size(u * 1.5f, u))
            drawRect(p.paper, Offset(cx, cy), Size(u * 1.5f, u * 2))
        }
    }
}
