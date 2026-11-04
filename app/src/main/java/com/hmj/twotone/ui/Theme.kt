package com.hmj.twotone.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/** The whole interface is drawn with exactly these two colours. */
data class Palette(val ink: Color, val paper: Color)

val InkOnPaper = Palette(ink = Color.Black, paper = Color.White)
val PaperOnInk = Palette(ink = Color.White, paper = Color.Black)

val LocalPalette = staticCompositionLocalOf { InkOnPaper }
val LocalTextScale = staticCompositionLocalOf { 1f }
/** Font for text 17sp and larger. */
val LocalFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }
/** Font for text smaller than 17sp. */
val LocalSmallFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }
/**
 * Pixel grid of the large and small fonts, in px (0 = not a pixel font).
 * Text sizes snap to whole multiples of it so pixel fonts stay sharp.
 */
data class PixelGrids(val large: Float, val small: Float)
val LocalPixelGrids = staticCompositionLocalOf { PixelGrids(0f, 0f) }

/** True when the launcher draws its own status bar (so page headers leave the time out). */
val LocalOwnStatusBar = staticCompositionLocalOf { false }

/** Goes up by one every time the launcher comes back to the front. */
val LocalResumeTick = compositionLocalOf { 0 }

const val SMALL_TEXT_BELOW = 17f

/** Text sizes offered in settings, as multipliers of the normal size. */
val TEXT_SIZES = listOf("small" to 0.9f, "medium" to 1.1f, "large" to 1.35f, "extra large" to 1.6f)

/** Which text-size step is chosen (index into [TEXT_SIZES]). */
val LocalTextSizeStep = staticCompositionLocalOf { 1 }

/**
 * Every piece of text has a role, worked out from the size it was designed at:
 * small (hints, counts), normal (list rows, bars), big (home rows, page titles),
 * huge (clocks).
 */
private fun roleOf(size: Float): Int = when {
    size < SMALL_TEXT_BELOW -> 0
    size < 24f -> 1
    size < 40f -> 2
    else -> 3
}

/**
 * With a pixel font, each role is drawn at a fixed number of screen pixels,
 * one row per text-size step. Every value is a whole multiple of 16, so it suits
 * 8×8 and 8×16 fonts alike and nothing is ever stretched between pixels.
 */
private val PIXEL_SIZES = listOf(
    //        small normal big  huge
    floatArrayOf(24f, 32f, 32f, 64f),   // small
    floatArrayOf(24f, 32f, 48f, 96f),   // medium
    floatArrayOf(32f, 48f, 48f, 96f),   // large
    floatArrayOf(32f, 48f, 64f, 128f)   // extra large
)

/** Small text in its own face (UNSCII 8 beside UNSCII 16), one value per text-size step. */
private val SMALL_FACE_SIZES = floatArrayOf(16f, 16f, 24f, 24f)

/**
 * The one place text sizes are decided.
 *  - [size] is the design size in sp; it picks the role (see [roleOf]).
 *  - With an ordinary font, the text-size setting scales it (small text half as much).
 *  - With a pixel font, the role and the setting pick an exact pixel size from [PIXEL_SIZES].
 *  - [fixed] ignores the setting, for things drawn into a fixed space
 *    (clock, keypad, calendar grid, top bar, wheel).
 */
@Composable
@ReadOnlyComposable
fun textStyle(size: Float, bold: Boolean = false, color: Color? = null, fixed: Boolean = false): TextStyle {
    val role = roleOf(size)
    val density = LocalDensity.current
    val grids = LocalPixelGrids.current
    // Small text may use its own face: UNSCII 8 next to UNSCII 16, so 24 px stays sharp (3 × 8).
    val grid = if (role == 0) grids.small else grids.large
    val sizeSp = if (grid > 0f) {
        val step = if (fixed) 1 else LocalTextSizeStep.current.coerceIn(0, PIXEL_SIZES.lastIndex)
        // Snap down to the face's grid, never below one grid step.
        // When small text has its own face (UNSCII 8 beside UNSCII 16) it gets its own sizes:
        // UNSCII 8's letters fill more of their size, so 16 px of it already reads
        // clearly larger than 16 px of UNSCII 16, and clearly smaller than 32 px of it.
        val separateSmallFace = role == 0 && grids.small != grids.large
        val wanted = if (separateSmallFace) SMALL_FACE_SIZES[step] else PIXEL_SIZES[step][role]
        val px = maxOf(grid, kotlin.math.floor(wanted / grid) * grid)
        px / (density.density * density.fontScale)
    } else {
        val userScale = if (fixed) 1f else LocalTextScale.current
        size * (if (role == 0) 1f + (userScale - 1f) * 0.5f else userScale)
    }
    return TextStyle(
        color = color ?: LocalPalette.current.ink,
        fontSize = sizeSp.sp,
        fontFamily = if (role == 0) LocalSmallFontFamily.current else LocalFontFamily.current,
        // Pixel fonts have no bold; faking it smears the pixels.
        fontWeight = if (bold && grid == 0f) FontWeight.Bold else FontWeight.Normal
    )
}
