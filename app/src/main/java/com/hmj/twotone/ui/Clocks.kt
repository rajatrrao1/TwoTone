package com.hmj.twotone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

enum class ClockStyle(val id: String, val label: String) {
    PIXEL("pixel", "pixel"),
    TYPE("type", "big type"),
    CARDS("cards", "cards");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: PIXEL
    }
}

@Composable
fun ClockFace(style: ClockStyle, time: String) {
    when (style) {
        ClockStyle.PIXEL -> PixelText(time, cell = 8.dp)
        ClockStyle.TYPE -> BasicText(time, style = textStyle(60f, bold = true, fixed = true), maxLines = 1, softWrap = false)
        ClockStyle.CARDS -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            time.forEach { ch ->
                if (ch == ':') BasicText(":", style = textStyle(36f, bold = true, fixed = true)) else DigitCard(ch)
            }
        }
    }
}

/** A solid card with the digit cut out of it and a hinge line across the middle. */
@Composable
private fun DigitCard(ch: Char) {
    val p = LocalPalette.current
    Box(
        Modifier
            .background(p.ink)
            .drawWithContent {
                drawContent()
                val y = size.height / 2f
                drawLine(p.paper, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
            }
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(ch.toString(), style = textStyle(44f, bold = true, color = p.paper, fixed = true))
    }
}
