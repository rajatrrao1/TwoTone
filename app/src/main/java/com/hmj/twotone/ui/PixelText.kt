package com.hmj.twotone.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** A hand-drawn 5×7 pixel font for the clock. '#' is a lit pixel. */
private val GLYPHS: Map<Char, List<String>> = mapOf(
    '0' to listOf(" ### ", "#   #", "#  ##", "# # #", "##  #", "#   #", " ### "),
    '1' to listOf("  #  ", " ##  ", "  #  ", "  #  ", "  #  ", "  #  ", " ### "),
    '2' to listOf(" ### ", "#   #", "    #", "   # ", "  #  ", " #   ", "#####"),
    '3' to listOf("#####", "   # ", "  #  ", "   # ", "    #", "#   #", " ### "),
    '4' to listOf("   # ", "  ## ", " # # ", "#  # ", "#####", "   # ", "   # "),
    '5' to listOf("#####", "#    ", "#### ", "    #", "    #", "#   #", " ### "),
    '6' to listOf("  ## ", " #   ", "#    ", "#### ", "#   #", "#   #", " ### "),
    '7' to listOf("#####", "    #", "   # ", "  #  ", " #   ", " #   ", " #   "),
    '8' to listOf(" ### ", "#   #", "#   #", " ### ", "#   #", "#   #", " ### "),
    '9' to listOf(" ### ", "#   #", "#   #", " ####", "    #", "   # ", " ##  "),
    ':' to listOf(" ", " ", "#", " ", "#", " ", " ")
)

/** Draws [text] in the pixel font, each pixel [cell] wide. */
@Composable
fun PixelText(text: String, modifier: Modifier = Modifier, cell: Dp = 9.dp) {
    val glyphs = text.mapNotNull { GLYPHS[it] }
    val rows = List(7) { r -> glyphs.joinToString(" ") { it[r] } }
    PixelGrid(rows, modifier, cell)
}

/** Draws any grid of '#' pixels. Each pixel has a hairline gap, like an LCD. */
@Composable
fun PixelGrid(rows: List<String>, modifier: Modifier = Modifier, cell: Dp = 3.dp) {
    val ink = LocalPalette.current.ink
    val cols = rows.maxOfOrNull { it.length } ?: 0
    Canvas(modifier.size(cell * cols, cell * rows.size)) {
        val c = cell.toPx()
        val gap = c * 0.12f
        rows.forEachIndexed { r, line ->
            line.forEachIndexed { col, ch ->
                if (ch == '#') drawRect(ink, Offset(col * c, r * c), Size(c - gap, c - gap))
            }
        }
    }
}

/** 9×9 weather pictures, drawn by hand. */
object WeatherIcons {
    val SUN = listOf(
        "    #    ",
        " #     # ",
        "   ###   ",
        "  #####  ",
        "# ##### #",
        "  #####  ",
        "   ###   ",
        " #     # ",
        "    #    "
    )
    val MOON = listOf(
        "   ####  ",
        "  ##     ",
        " ##      ",
        " ##      ",
        " ##      ",
        " ##      ",
        " ##      ",
        "  ##     ",
        "   ####  "
    )
    val PARTLY = listOf(
        "  #      ",
        "#   #    ",
        " ###     ",
        " ## ###  ",
        "# ###### ",
        " ########",
        "#########",
        " ####### ",
        "         "
    )
    val CLOUD = listOf(
        "         ",
        "         ",
        "   ###   ",
        "  #####  ",
        " ####### ",
        "#########",
        "#########",
        " ####### ",
        "         "
    )
    val RAIN = listOf(
        "   ###   ",
        "  #####  ",
        " ####### ",
        "#########",
        " ####### ",
        "         ",
        " #  #  # ",
        "#  #  #  ",
        "         "
    )
    val SNOW = listOf(
        "   ###   ",
        "  #####  ",
        " ####### ",
        "#########",
        " ####### ",
        "         ",
        " # # # # ",
        "         ",
        "# # # #  "
    )
    val FOG = listOf(
        "         ",
        "#######  ",
        "         ",
        "  #######",
        "         ",
        "#######  ",
        "         ",
        "  #######",
        "         "
    )
    val THUNDER = listOf(
        "   ###   ",
        "  #####  ",
        " ####### ",
        "#########",
        " ###  ## ",
        "    #    ",
        "   ##    ",
        "    #    ",
        "   #     "
    )

    fun forSymbol(code: String): List<String> = when {
        "thunder" in code -> THUNDER
        "fog" in code -> FOG
        "snow" in code || "sleet" in code -> SNOW
        "rain" in code -> RAIN
        "night" in code && ("clearsky" in code || "fair" in code) -> MOON
        "partlycloudy" in code || "fair" in code -> PARTLY
        "clearsky" in code -> SUN
        else -> CLOUD
    }
}
