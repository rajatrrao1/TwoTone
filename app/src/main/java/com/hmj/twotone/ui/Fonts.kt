package com.hmj.twotone.ui

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import java.io.File

enum class FontSlot(val label: String) { LARGE("large text"), SMALL("small text") }

/** A built-in font choice: its id, the words shown, its pixel grid (0 = not a pixel font). */
class FontChoice(val id: String, val label: String, val grid: Float, val note: String, val asset: String? = null)

/**
 * UNSCII comes in several faces from the same project (viznut.fi/unscii, public domain).
 * Each is used if its file is in app/src/main/assets/fonts.
 */
val FONTS = listOf(
    FontChoice("unscii16", "unscii 16", 16f, "8×16 · the one to pick", "unscii-16.ttf"),
    FontChoice("tall", "unscii tall", 16f, "8×16 · 8×8 stretched tall", "unscii-8-tall.ttf"),
    FontChoice("unscii8", "unscii 8", 8f, "8×8 · square and blocky", "unscii-8.ttf"),
    FontChoice("thin", "unscii thin", 8f, "8×8 · thin strokes", "unscii-8-thin.ttf"),
    FontChoice("mcr", "unscii mcr", 8f, "8×8 · machine-readable look", "unscii-8-mcr.ttf"),
    FontChoice("fantasy", "unscii fantasy", 8f, "8×8 · decorative", "unscii-8-fantasy.ttf"),
    FontChoice("mono", "mono", 0f, "the phone's typewriter font"),
    FontChoice("sans", "sans", 0f, "the phone's plain font"),
    FontChoice("serif", "serif", 0f, "the phone's book font"),
    FontChoice("hand", "handwriting", 0f, "the phone's script font")
)

/** Kept for older callers: id → label pairs. */
val FONT_CHOICES = FONTS.map { it.id to it.label }

fun assetFontFile(choice: String): String? = FONTS.firstOrNull { it.id == choice }?.asset

fun isAssetFontPresent(context: Context, choice: String): Boolean {
    val name = assetFontFile(choice) ?: return true
    return try { context.assets.list("fonts")?.contains(name) == true } catch (e: Exception) { false }
}

fun fontLabel(choice: String): String =
    FONTS.firstOrNull { it.id == choice }?.label ?: if (choice == "custom") "your file" else "mono"

/** The pixel grid of a font in px, or 0 for an ordinary font. */
fun pixelGrid(choice: String, slot: FontSlot, snapCustom: Boolean): Float =
    FONTS.firstOrNull { it.id == choice }?.grid
        ?: if (choice == "custom" && snapCustom) 16f else 0f

fun customFontFile(context: Context, slot: FontSlot) =
    File(context.filesDir, if (slot == FontSlot.LARGE) "custom_font" else "custom_font_small")

fun resolveFont(context: Context, choice: String, slot: FontSlot): FontFamily = try {
    when (choice) {
        "sans" -> FontFamily.SansSerif
        "serif" -> FontFamily.Serif
        "hand" -> FontFamily.Cursive
        "custom" -> FontFamily(Typeface.createFromFile(customFontFile(context, slot)))
        "mono" -> FontFamily.Monospace
        else -> assetFontFile(choice)?.let { FontFamily(Typeface.createFromAsset(context.assets, "fonts/$it")) }
            ?: FontFamily.Monospace
    }
} catch (e: Exception) {
    FontFamily.Monospace
}

/** Checks a copied file really is a font before switching to it. */
fun isUsableFont(file: File): Boolean = try {
    file.length() > 0 && Typeface.createFromFile(file) != null
} catch (e: Exception) {
    false
}
