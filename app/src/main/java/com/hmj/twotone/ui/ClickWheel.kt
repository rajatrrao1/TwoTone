package com.hmj.twotone.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hmj.twotone.music.MusicSettings
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.atan2

private const val NONE = 0
private const val MENU = 1
private const val NEXT = 2
private const val PLAY = 3
private const val PREV = 4
private const val CENTER = 5

/**
 * A ring you turn with your thumb. Every 1/16 of a turn is one step
 * (clockwise = +1) and clicks. Tapping the ring's top, right, bottom or left
 * presses menu, next, play/pause or previous; the middle is select, and holding
 * the middle is [onCenterHold]. Whatever you press flashes solid for a moment.
 */
@Composable
fun ClickWheel(
    size: Dp,
    onStep: (Int) -> Unit,
    onCenter: () -> Unit,
    onMenu: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
    onCenterHold: (() -> Unit)? = null
) {
    val p = LocalPalette.current
    val ink = p.ink
    val view = LocalView.current
    val step by rememberUpdatedState(onStep)
    val centerTap by rememberUpdatedState(onCenter)
    val centerHold by rememberUpdatedState(onCenterHold)
    val menu by rememberUpdatedState(onMenu)
    val prev by rememberUpdatedState(onPrevious)
    val next by rememberUpdatedState(onNext)
    val play by rememberUpdatedState(onPlayPause)

    // Which part is flashing, and a counter so a second press restarts the flash.
    var flash by remember { mutableIntStateOf(NONE) }
    var flashCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(flashCount) {
        if (flash != NONE) { delay(160); flash = NONE }
    }
    fun press(part: Int) { flash = part; flashCount++; MusicSettings.tick(view) }

    Box(
        modifier.size(size).pointerInput(Unit) {
            val stepAngle = (PI / 8).toFloat()
            val twoPi = (2 * PI).toFloat()
            awaitEachGesture {
                val down = awaitFirstDown()
                val c = Offset(this.size.width / 2f, this.size.height / 2f)
                val start = down.position - c
                val inMiddle = start.getDistance() < this.size.width * 0.19f
                var last = atan2(start.y, start.x)
                var turned = 0f
                var moved = false
                var held = false

                if (inMiddle) {
                    // Hold the middle for half a second = options.
                    val released = withTimeoutOrNull(500L) {
                        while (true) {
                            val e = awaitPointerEvent()
                            val ch = e.changes.firstOrNull { it.id == down.id }
                            if (ch == null || !ch.pressed) break
                        }
                        true
                    }
                    if (released == null && centerHold != null) {
                        held = true
                        press(CENTER)
                        centerHold?.invoke()
                        // wait for the finger to lift
                        while (true) {
                            val e = awaitPointerEvent()
                            val ch = e.changes.firstOrNull { it.id == down.id }
                            if (ch == null || !ch.pressed) break
                        }
                    } else if (released == null) {
                        while (true) {
                            val e = awaitPointerEvent()
                            val ch = e.changes.firstOrNull { it.id == down.id }
                            if (ch == null || !ch.pressed) break
                        }
                    }
                } else {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val v = change.position - c
                        val a = atan2(v.y, v.x)
                        var d = a - last
                        if (d > PI) d -= twoPi
                        if (d < -PI) d += twoPi
                        last = a
                        turned += d
                        while (turned > stepAngle) { step(1); MusicSettings.tick(view); turned -= stepAngle; moved = true }
                        while (turned < -stepAngle) { step(-1); MusicSettings.tick(view); turned += stepAngle; moved = true }
                        change.consume()
                    }
                }

                if (!moved && !held) {
                    if (inMiddle) { press(CENTER); centerTap() }
                    else {
                        // 0° points right and angles grow clockwise, because screen y grows downwards.
                        val deg = Math.toDegrees(atan2(start.y, start.x).toDouble())
                        when {
                            deg >= -135 && deg < -45 -> { press(MENU); menu() }
                            deg >= -45 && deg < 45 -> { press(NEXT); next() }
                            deg >= 45 && deg < 135 -> { press(PLAY); play() }
                            else -> { press(PREV); prev() }
                        }
                    }
                }
            }
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            val w = 6.dp.toPx()
            val inner = r * 0.37f

            // The pressed quarter of the ring, filled solid
            if (flash in MENU..PREV) {
                val startAngle = when (flash) { MENU -> -135f; NEXT -> -45f; PLAY -> 45f; else -> 135f }
                val band = r - inner
                val mid = inner + band / 2f
                drawArc(
                    ink, startAngle, 90f, useCenter = false,
                    topLeft = Offset(center.x - mid, center.y - mid),
                    size = Size(mid * 2, mid * 2),
                    style = Stroke(width = band)
                )
            }
            drawCircle(ink, radius = r - w / 2, style = Stroke(width = w))
            if (flash == CENTER) drawCircle(ink, radius = inner)
            else {
                drawCircle(ink, radius = inner, style = Stroke(width = w * 0.8f))
                drawCircle(ink, radius = r * 0.29f, style = Stroke(width = w * 0.5f))
            }
            // One square dot per step of the wheel, skipping the four labels.
            val dot = 4.dp.toPx()
            for (i in 0 until 16) {
                if (i % 4 == 0) continue
                val a = (i * PI / 8).toFloat()
                val x = center.x + kotlin.math.cos(a) * r * 0.80f
                val y = center.y + kotlin.math.sin(a) * r * 0.80f
                var deg = i * 22.5f
                if (deg >= 180f) deg -= 360f
                val part = when {
                    deg >= -135f && deg < -45f -> MENU
                    deg >= -45f && deg < 45f -> NEXT
                    deg >= 45f && deg < 135f -> PLAY
                    else -> PREV
                }
                drawRect(if (flash == part) p.paper else ink, Offset(x - dot / 2, y - dot / 2), Size(dot, dot))
            }
        }
        fun labelColor(part: Int) = if (flash == part) p.paper else ink
        val ls = if (size > 240.dp) 18f else 15f
        BasicText("menu", Modifier.align(Alignment.TopCenter).padding(top = size * 0.08f), style = textStyle(ls, bold = true, color = labelColor(MENU), fixed = true))
        BasicText("◂◂", Modifier.align(Alignment.CenterStart).padding(start = size * 0.08f), style = textStyle(ls, bold = true, color = labelColor(PREV), fixed = true))
        BasicText("▸▸", Modifier.align(Alignment.CenterEnd).padding(end = size * 0.08f), style = textStyle(ls, bold = true, color = labelColor(NEXT), fixed = true))
        BasicText("▸ ‖", Modifier.align(Alignment.BottomCenter).padding(bottom = size * 0.08f), style = textStyle(ls, bold = true, color = labelColor(PLAY), fixed = true))
    }
}
