package com.hmj.twotone.ui

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.hmj.twotone.Prefs
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private class StatusRow(val label: String, val trailing: String, val action: () -> Unit)

private fun openFirst(ctx: Context, vararg intents: Intent) {
    for (i in intents) {
        try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (e: Exception) { }
    }
    Toast.makeText(ctx, "That setting can't be opened on this phone.", Toast.LENGTH_SHORT).show()
}

/** Asks Android to pull its own notification shade down. Not every phone allows it. */
@Suppress("WrongConstant")
private fun pullDownShade(ctx: Context): Boolean = try {
    val sbm = ctx.getSystemService("statusbar")
    Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sbm)
    true
} catch (e: Exception) { false }

/** The torch, if the phone has one. */
private object Torch {
    var on = false
    fun toggle(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            ?: return false
        on = !on
        cm.setTorchMode(id, on)
        return true
    }
}

/**
 * Tapping the status bar opens this: every status in words, each opening its
 * setting. Notifications pulls down Android's own panel where that's allowed.
 */
@Composable
fun StatusPanelScreen(status: PhoneStatus, onClock: () -> Unit, onNotifications: () -> Unit, onKompakt: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var torchOn by remember { mutableStateOf(Torch.on) }
    val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    var ringer by remember { mutableStateOf(am.ringerMode) }
    val panel = Build.VERSION.SDK_INT >= 29

    val rows = listOf(
        StatusRow("notifications", "${com.hmj.twotone.notify.Notifications.list.size} ▸", onNotifications),
        StatusRow("android's notification shade", "▸") {
            if (!pullDownShade(ctx)) openFirst(ctx, Intent(Settings.ACTION_SETTINGS))
        },
        StatusRow("wi-fi", if (status.wifi) "connected" else "off") {
            openFirst(ctx, *listOfNotNull(if (panel) Intent(Settings.Panel.ACTION_WIFI) else null, Intent(Settings.ACTION_WIFI_SETTINGS)).toTypedArray())
        },
        StatusRow("mobile data", if (status.airplane) "airplane mode" else if (status.mobile) "signal ${status.mobileLevel} of 4" else "none") {
            openFirst(ctx, *listOfNotNull(if (panel) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else null, Intent(Settings.ACTION_WIRELESS_SETTINGS)).toTypedArray())
        },
        StatusRow("bluetooth", when { status.bluetoothAudio -> "connected"; status.bluetooth -> "on"; else -> "off" }) {
            openFirst(ctx, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        },
        StatusRow("sound", when (ringer) { AudioManager.RINGER_MODE_VIBRATE -> "vibrate"; AudioManager.RINGER_MODE_SILENT -> "silent"; else -> "on" }) {
            // on → vibrate → silent → on. Silent needs "Do not disturb" access on newer Android.
            val next = when (ringer) {
                AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                else -> AudioManager.RINGER_MODE_NORMAL
            }
            try { am.ringerMode = next; ringer = am.ringerMode }
            catch (e: SecurityException) {
                Toast.makeText(ctx, "Allow Two Tone \u201cDo not disturb\u201d access to switch to silent.", Toast.LENGTH_LONG).show()
                openFirst(ctx, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }
        },
        StatusRow("torch", if (torchOn) "on" else "off") {
            try { if (Torch.toggle(ctx)) torchOn = Torch.on else Toast.makeText(ctx, "This phone has no torch.", Toast.LENGTH_SHORT).show() }
            catch (e: Exception) { Toast.makeText(ctx, "The torch couldn't be switched.", Toast.LENGTH_SHORT).show() }
        },
        StatusRow("battery", if (status.battery >= 0) "${status.battery}%" + if (status.charging) " charging" else "" else "?") {
            openFirst(ctx, Intent(Intent.ACTION_POWER_USAGE_SUMMARY), Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        },
        StatusRow("next alarm", status.nextAlarm?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE HH:mm", Locale.getDefault())).lowercase()
        } ?: "none", onClock),
        StatusRow("kompakt settings", "▸", onKompakt),
        StatusRow("all settings", "▸") { openFirst(ctx, Intent(Settings.ACTION_SETTINGS)) }
    )
    PageColumn {
        PageHeader("status", onBack)
        PagedList(rows, Modifier.weight(1f)) { r -> ListRow(r.label, trailing = r.trailing, onPress = r.action) }
    }
}

/** Use the launcher's bar or Android's, and pick what the launcher's bar shows. */
@Composable
fun StatusBarSettingsScreen(prefs: Prefs, onBack: () -> Unit) {
    val rows: List<Pair<String, String>?> = listOf<Pair<String, String>?>(null) + STATUS_ITEMS
    PageColumn {
        PageHeader("status bar", onBack)
        PagedList(rows, Modifier.weight(1f)) { item ->
            if (item == null) {
                ListRow("bar at the top", trailing = bracket(if (prefs.ownStatusBar) "launcher's" else "android's")) {
                    prefs.setOwnStatusBar(!prefs.ownStatusBar)
                }
            } else {
                ListRow(item.second, trailing = bracket(if (item.first in prefs.statusItems) "on" else "off")) {
                    prefs.toggleStatusItem(item.first)
                }
            }
        }
        FooterLine(if (prefs.ownStatusBar) "the time is always in the middle" else "android's bar is shown")
    }
}

/**
 * E-ink: what KompaktOS does (it owns the screen modes) and what the launcher adds
 * (clean refreshes that wipe ghosting).
 */
@Composable
fun EinkScreen(prefs: Prefs, onKompakt: () -> Unit, onBack: () -> Unit) {
    val modes = listOf("off", "after other apps", "+ every 10 page turns", "+ every 5 page turns")
    PageColumn {
        PageHeader("e-ink", onBack)
        ListRow("clean refresh", trailing = bracket(modes[prefs.refreshMode.coerceIn(0, 3)])) {
            prefs.setRefreshMode((prefs.refreshMode + 1) % modes.size)
        }
        ListRow("kompakt display settings", onPress = onKompakt)
        SectionBar("which kompakt mode")
        Hint(
            "The screen modes belong to KompaktOS, so set them there: Display > Per-app e-ink modes > Two Tone. " +
                "Fast suits this launcher best, since it only uses black and white. " +
                "Auto is a good choice if you read books in it. Text or Quality give cleaner pages but blink."
        )
        Hint("Hold the clock on the home page for a clean refresh at any time.")
    }
}
