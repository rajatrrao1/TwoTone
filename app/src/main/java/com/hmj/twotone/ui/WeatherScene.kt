package com.hmj.twotone.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp

/**
 * A Dutch canal-side street drawn in big pixels, under a sky that matches
 * the weather: sun or moon and stars, clouds, rain, snow, a thunderbolt, or fog.
 */
private const val W = 96
private const val H = 40
private const val GROUND = 33

// Buildings, drawn by hand. '#' is a dark pixel. Bottom row stands on the ground.
private val STEP_GABLE = listOf(
    "     #     ",
    "    ###    ",
    "    # #    ",
    "   ## ##   ",
    "   #   #   ",
    "  ##   ##  ",
    "  #     #  ",
    " ##     ## ",
    " # ## ## # ",
    " # ## ## # ",
    " #       # ",
    " # ## ## # ",
    " # ## ## # ",
    " #       # ",
    " #  ###  # ",
    " #  # #  # "
)
private val BELL_GABLE = listOf(
    "    ###    ",
    "   #   #   ",
    "   # # #   ",
    "  ##   ##  ",
    " #       # ",
    " # ## ## # ",
    " # ## ## # ",
    " #       # ",
    " # ## ## # ",
    " # ## ## # ",
    " #       # ",
    " #  ###  # ",
    " #  # #  # "
)
private val NECK_GABLE = listOf(
    "   ###   ",
    "   # #   ",
    "   ###   ",
    " ### ### ",
    " #     # ",
    " # # # # ",
    " #     # ",
    " # # # # ",
    " #     # ",
    " # # # # ",
    " #     # ",
    " # ### # ",
    " # # # # "
)
private val TREE = listOf(
    "  ###  ",
    " ##### ",
    "#######",
    "#######",
    " ##### ",
    "  ###  ",
    "   #   ",
    "   #   ",
    "   #   "
)
private val CLOUD = listOf(
    "    ####     ",
    "  ########   ",
    " ########### ",
    "#############"
)
private val CLOUD_SMALL = listOf(
    "  ###   ",
    " ###### ",
    "########"
)

private class Grid {
    val cells = BooleanArray(W * H)
    fun set(x: Int, y: Int) { if (x in 0 until W && y in 0 until H) cells[y * W + x] = true }
    fun clear(x: Int, y: Int) { if (x in 0 until W && y in 0 until H) cells[y * W + x] = false }
    fun get(x: Int, y: Int) = x in 0 until W && y in 0 until H && cells[y * W + x]
    fun stamp(art: List<String>, left: Int, bottom: Int, solid: Boolean = false) {
        val top = bottom - art.size + 1
        art.forEachIndexed { r, line ->
            line.forEachIndexed { c, ch -> if (ch == '#') set(left + c, top + r) }
            if (solid) {
                // fill the inside of the outline so clouds hide what's behind them
                val first = line.indexOf('#'); val last = line.lastIndexOf('#')
                if (first >= 0) for (c in first..last) set(left + c, top + r)
            }
        }
    }
    /** Blank out a shape first (for clouds drawn in front of the sun). */
    fun erase(art: List<String>, left: Int, bottom: Int) {
        val top = bottom - art.size + 1
        art.forEachIndexed { r, line ->
            val first = line.indexOf('#'); val last = line.lastIndexOf('#')
            if (first >= 0) for (c in first - 1..last + 1) clear(left + c, top + r)
        }
    }
}

private fun windmill(g: Grid, cx: Int) {
    val hubY = GROUND - 14
    // Tower: widens towards the ground
    for (y in hubY + 2..GROUND) {
        val half = 1 + (y - hubY - 2) / 4
        g.set(cx - half, y); g.set(cx + half, y)
    }
    for (x in cx - 1..cx + 1) g.set(x, hubY + 1)
    g.set(cx, GROUND - 1); g.set(cx, GROUND - 2)
    // Sails: an X, each arm two pixels wide with a lattice
    for (d in 1..9) {
        g.set(cx + d, hubY - d); g.set(cx - d, hubY + d)
        g.set(cx - d, hubY - d); g.set(cx + d, hubY + d)
        if (d >= 3 && d % 2 == 1) {
            g.set(cx + d + 1, hubY - d); g.set(cx - d - 1, hubY + d)
            g.set(cx - d, hubY - d - 1); g.set(cx + d, hubY + d + 1)
        }
    }
    g.set(cx, hubY)
}

private fun sun(g: Grid, cx: Int, cy: Int) {
    for (y in -3..3) for (x in -3..3) if (x * x + y * y <= 10) g.set(cx + x, cy + y)
    listOf(0 to -6, 0 to 6, -6 to 0, 6 to 0, 4 to 4, -4 to -4, 4 to -4, -4 to 4).forEach { (dx, dy) ->
        g.set(cx + dx, cy + dy)
        g.set(cx + dx - dx.coerceIn(-1, 1), cy + dy - dy.coerceIn(-1, 1))
    }
}

private fun moon(g: Grid, cx: Int, cy: Int) {
    for (y in -4..4) for (x in -4..4) {
        val inMoon = x * x + y * y <= 17
        val bite = (x - 2) * (x - 2) + (y + 1) * (y + 1) <= 12
        if (inMoon && !bite) g.set(cx + x, cy + y)
    }
}

private fun buildScene(symbol: String, isDay: Boolean): Grid {
    val g = Grid()
    val night = !isDay || "night" in symbol
    val clear = "clearsky" in symbol || "fair" in symbol
    val partly = "partlycloudy" in symbol || "fair" in symbol
    val rain = "rain" in symbol || "sleet" in symbol
    val snow = "snow" in symbol || "sleet" in symbol
    val thunder = "thunder" in symbol
    val fog = "fog" in symbol
    val overcast = "cloudy" == symbol.substringBefore('_') || rain || snow || thunder

    // Sky: sun or moon, stars on clear nights, birds on fine days
    if (!overcast && !fog) {
        if (night) {
            moon(g, 78, 7)
            listOf(12 to 4, 26 to 9, 41 to 3, 58 to 6, 66 to 12, 90 to 14, 5 to 13).forEach { (x, y) -> g.set(x, y) }
        } else {
            sun(g, 78, 8)
            if (clear) listOf(30 to 7, 36 to 5).forEach { (x, y) -> g.set(x, y); g.set(x - 1, y - 1); g.set(x + 1, y - 1) }
        }
    }
    // Clouds
    if (partly && !overcast) { g.erase(CLOUD, 66, 12); g.stamp(CLOUD, 66, 12, solid = true) }
    if (overcast || fog) {
        g.stamp(CLOUD, 6, 7, solid = true)
        g.stamp(CLOUD_SMALL, 30, 5, solid = true)
        g.stamp(CLOUD, 48, 9, solid = true)
        g.stamp(CLOUD_SMALL, 74, 6, solid = true)
    }

    // The street: houses, a tree and a windmill on the far side of the canal
    g.stamp(TREE, 2, GROUND)
    g.stamp(STEP_GABLE, 10, GROUND)
    g.stamp(NECK_GABLE, 21, GROUND)
    g.stamp(BELL_GABLE, 30, GROUND)
    g.stamp(STEP_GABLE, 41, GROUND)
    windmill(g, 64)
    g.stamp(BELL_GABLE, 76, GROUND)
    g.stamp(TREE, 88, GROUND)
    for (x in 0 until W) g.set(x, GROUND)
    // Canal: ripples
    for (y in GROUND + 2 until H step 2) for (x in 0 until W) if ((x + y) % 6 < 2) g.set(x, y)

    // Where the rooftops are, so rain and snow fall in front of the sky, not inside houses
    val roof = IntArray(W) { x -> (13 until GROUND).firstOrNull { y -> g.get(x, y) } ?: GROUND }
    fun inSky(x: Int, y: Int) = x in 0 until W && y < roof[x] - 1 && !g.get(x, y)

    // Weather in front of the street
    if (rain) for (y in 11 until GROUND step 3) for (x in 0 until W step 5) {
        val xx = x + (y / 3) % 5
        if (inSky(xx, y) && inSky(xx - 1, y + 1)) { g.set(xx, y); g.set(xx - 1, y + 1) }
    }
    if (snow && !rain) for (y in 11 until GROUND step 4) for (x in 0 until W step 6) {
        val xx = x + (y * 3) % 6
        if (inSky(xx, y)) g.set(xx, y)
    }
    if (thunder) {
        val bolt = listOf(54 to 10, 53 to 11, 52 to 12, 53 to 12, 54 to 12, 53 to 13, 52 to 14, 51 to 15)
        bolt.forEach { (x, y) -> g.set(x, y) }
    }
    if (fog) for (y in 14 until GROUND step 3) for (x in 0 until W) {
        if ((x / 4 + y) % 2 == 0) { if (g.get(x, y)) g.clear(x, y) else g.set(x, y) }
    }
    return g
}

@Composable
fun WeatherScene(symbol: String, isDay: Boolean, height: Dp) {
    val ink = LocalPalette.current.ink
    val grid = remember(symbol, isDay) { buildScene(symbol, isDay) }
    Canvas(Modifier.fillMaxWidth().height(height)) {
        // Whole pixels only, centred, so every square is the same size on E-ink.
        val cell = kotlin.math.floor(minOf(size.width / W, size.height / H))
        if (cell < 1f) return@Canvas
        val left = (size.width - cell * W) / 2f
        val top = (size.height - cell * H) / 2f
        for (y in 0 until H) for (x in 0 until W) {
            if (grid.cells[y * W + x]) drawRect(ink, Offset(left + x * cell, top + y * cell), Size(cell, cell))
        }
    }
}
