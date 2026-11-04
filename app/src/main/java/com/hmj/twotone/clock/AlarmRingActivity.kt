package com.hmj.twotone.clock

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hmj.twotone.Prefs
import com.hmj.twotone.ui.FontSlot
import com.hmj.twotone.ui.InkOnPaper
import com.hmj.twotone.ui.LocalFontFamily
import com.hmj.twotone.ui.LocalPalette
import com.hmj.twotone.ui.LocalPixelGrids
import com.hmj.twotone.ui.LocalSmallFontFamily
import com.hmj.twotone.ui.PaperOnInk
import com.hmj.twotone.ui.PixelGrids
import com.hmj.twotone.ui.PixelText
import com.hmj.twotone.ui.pixelGrid
import com.hmj.twotone.ui.press
import com.hmj.twotone.ui.resolveFont
import com.hmj.twotone.ui.textStyle
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** The screen shown over everything while an alarm rings: stop, or snooze. */
class AlarmRingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val prefs = Prefs(this)

        setContent {
            val palette = if (prefs.inverted) PaperOnInk else InkOnPaper
            val big = remember { resolveFont(this, prefs.font, FontSlot.LARGE) }
            val smallChoice = if (prefs.font == "unscii16" && com.hmj.twotone.ui.isAssetFontPresent(this, "unscii8")) "unscii8" else prefs.font
            val small = remember { resolveFont(this, smallChoice, FontSlot.LARGE) }
            CompositionLocalProvider(
                LocalPalette provides palette,
                LocalFontFamily provides big,
                LocalSmallFontFamily provides small,
                LocalPixelGrids provides PixelGrids(
                    pixelGrid(prefs.font, FontSlot.LARGE, prefs.pixelSnap),
                    pixelGrid(smallChoice, FontSlot.LARGE, prefs.pixelSnap)
                )
            ) {
                var now by remember { mutableStateOf(LocalTime.now()) }
                LaunchedEffect(Unit) {
                    while (true) {
                        now = LocalTime.now()
                        if (AlarmRingService.ringing == null) { finish(); break }
                        delay(1_000)
                    }
                }
                BackHandler { /* stop or snooze, not back */ }
                Column(
                    Modifier.fillMaxSize().background(palette.paper).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PixelText(now.format(DateTimeFormatter.ofPattern("HH:mm")), cell = 10.dp)
                    Spacer(Modifier.height(16.dp))
                    BasicText(AlarmRingService.ringing ?: "alarm", style = textStyle(24f, bold = true, fixed = true), maxLines = 2)
                    Spacer(Modifier.height(40.dp))
                    Box(
                        Modifier.fillMaxWidth().height(72.dp).background(palette.ink)
                            .press { AlarmRingService.send(this@AlarmRingActivity, AlarmRingService.ACTION_STOP); finish() },
                        contentAlignment = Alignment.Center
                    ) { BasicText("stop", style = textStyle(30f, bold = true, color = palette.paper, fixed = true)) }
                    Spacer(Modifier.height(16.dp))
                    Box(
                        Modifier.fillMaxWidth().height(64.dp).border(4.dp, palette.ink)
                            .press { AlarmRingService.send(this@AlarmRingActivity, AlarmRingService.ACTION_SNOOZE); finish() },
                        contentAlignment = Alignment.Center
                    ) { BasicText("snooze ${AlarmStore.SNOOZE_MINUTES} min", style = textStyle(24f, bold = true, fixed = true)) }
                }
            }
        }
    }
}
