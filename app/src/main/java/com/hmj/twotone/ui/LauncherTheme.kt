package com.hmj.twotone.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.hmj.twotone.Prefs

/**
 * The launcher's look (colours, font, text size) for pages that open outside
 * the home screen, such as the music player on the lock screen.
 */
@Composable
fun LauncherTheme(ctx: Context, prefs: Prefs, resumeTick: Int, content: @Composable () -> Unit) {
    val palette = if (prefs.inverted) PaperOnInk else InkOnPaper
    val font = remember(prefs.font, prefs.fontStamp) { resolveFont(ctx, prefs.font, FontSlot.LARGE) }
    val smallChoice = if (prefs.font == "unscii16" && isAssetFontPresent(ctx, "unscii8")) "unscii8" else prefs.font
    val small = remember(smallChoice, prefs.fontStamp) { resolveFont(ctx, smallChoice, FontSlot.LARGE) }
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalTextScale provides TEXT_SIZES[prefs.textSize.coerceIn(0, TEXT_SIZES.lastIndex)].second,
        LocalTextSizeStep provides prefs.textSize,
        LocalFontFamily provides font,
        LocalSmallFontFamily provides small,
        LocalOwnStatusBar provides false,
        LocalResumeTick provides resumeTick,
        LocalPixelGrids provides PixelGrids(
            pixelGrid(prefs.font, FontSlot.LARGE, prefs.pixelSnap),
            pixelGrid(smallChoice, FontSlot.LARGE, prefs.pixelSnap)
        )
    ) { content() }
}
