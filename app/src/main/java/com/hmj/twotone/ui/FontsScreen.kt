package com.hmj.twotone.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hmj.twotone.Prefs

/**
 * One font for everything; the launcher decides the sizes. Each choice is shown
 * in its own face with a sample line. The chosen one is drawn inverted.
 */
@Composable
fun FontsScreen(prefs: Prefs, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    val hasCustom = remember(prefs.fontStamp) { customFontFile(ctx, FontSlot.LARGE).exists() }
    fun choose(id: String) { prefs.setFont(id); prefs.setFontSmall(id); message = null }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val file = customFontFile(ctx, FontSlot.LARGE)
        val ok = try {
            ctx.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
            isUsableFont(file)
        } catch (e: Exception) {
            false
        }
        if (ok) { prefs.bumpFontStamp(); choose("custom") }
        else { file.delete(); message = "That file isn't a font this phone can read. Try a .ttf or .otf file." }
    }

    val rows: List<FontChoice?> = FONTS +
        (if (hasCustom) listOf(FontChoice("custom", "your font file", 0f, "loaded from the phone")) else emptyList()) +
        listOf<FontChoice?>(null)

    PageColumn {
        PageHeader("font", onBack)
        message?.let { Hint(it) }
        PagedList(rows, Modifier.weight(1f), resetKey = hasCustom, rowSize = 26f) { c ->
            if (c == null) {
                ListRow("load a font file") {
                    picker.launch(arrayOf("font/ttf", "font/otf", "font/sfnt", "application/x-font-ttf", "application/octet-stream"))
                }
            } else {
                val present = remember(c.id) { isAssetFontPresent(ctx, c.id) }
                val family = remember(c.id, prefs.fontStamp, present) { resolveFont(ctx, c.id, FontSlot.LARGE) }
                val on = prefs.font == c.id
                val p = LocalPalette.current
                val fg = if (on) p.paper else p.ink
                Column(
                    Modifier.fillMaxWidth().height(rowHeight(26f)).background(if (on) p.ink else Color.Transparent)
                        .press {
                            if (present) choose(c.id)
                            else message = "To use ${c.label}, put ${c.asset} in app/src/main/assets/fonts and press Run again."
                        }
                        .padding(horizontal = 14.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(c.label, Modifier.weight(1f), style = textStyle(20f, color = fg, fixed = true).copy(fontFamily = family), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        BasicText(if (present) c.note.substringBefore(" ·") else "missing", style = textStyle(14f, color = fg, fixed = true), maxLines = 1)
                    }
                    BasicText(
                        "0123 12:45 [on] " + c.note.substringAfter("· ", c.note),
                        style = textStyle(14f, color = fg, fixed = true).copy(fontFamily = family),
                        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        FooterLine(fontLabel(prefs.font))
    }
}
