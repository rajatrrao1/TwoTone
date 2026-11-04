package com.hmj.twotone.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hmj.twotone.clock.AlarmStore
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Shown while the phone charges: a large clock, the date, the charge and the
 * next alarm, and nothing else. It changes once a minute, which E-ink does
 * without a flicker. Tap anywhere to go back to home.
 */
@Composable
fun ChargingScreen(battery: Int, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val is24h = DateFormat.is24HourFormat(ctx)
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(com.hmj.twotone.Visible.on) {
        AlarmStore.load(ctx)
        while (com.hmj.twotone.Visible.on) {
            now = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
        }
    }
    val next = AlarmStore.nextRingMs()

    PageColumn {
        Column(
            Modifier.fillMaxSize().press(onPress = onDismiss).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PixelText(now.format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm")), cell = 11.dp)
            Spacer(Modifier.height(18.dp))
            BasicText(now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())).lowercase(), style = textStyle(20f, fixed = true), maxLines = 1)
            Spacer(Modifier.height(28.dp))
            DitherRule(Modifier.padding(horizontal = 40.dp))
            Spacer(Modifier.height(18.dp))
            BasicText(
                if (battery >= 100) "charged · 100%" else "charging · $battery%",
                style = textStyle(18f, fixed = true), maxLines = 1
            )
            if (next != null) {
                Spacer(Modifier.height(6.dp))
                BasicText(
                    "alarm " + Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern(if (is24h) "EEE HH:mm" else "EEE h:mm a", Locale.getDefault())).lowercase(),
                    style = textStyle(18f, fixed = true), maxLines = 1
                )
            }
            Spacer(Modifier.height(40.dp))
            BasicText("tap to go back", style = textStyle(14f, fixed = true))
        }
    }
}
