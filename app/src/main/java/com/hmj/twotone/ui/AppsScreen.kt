package com.hmj.twotone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.hmj.twotone.AppEntry

/** Every installed app as a plain list of names, with a search line on top. */
@Composable
fun AppsScreen(
    apps: List<AppEntry>,
    pinned: List<String>,
    hidden: Set<String>,
    nameOf: (AppEntry) -> String,
    onRename: (AppEntry, String?) -> Unit,
    onHide: (AppEntry, Boolean) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onTogglePin: (AppEntry) -> Unit,
    onAppInfo: (AppEntry) -> Unit,
    onUninstall: (AppEntry) -> Unit,
    onBack: () -> Unit
) {
    val palette = LocalPalette.current
    var query by rememberSaveable { mutableStateOf("") }
    var optionsFor by remember { mutableStateOf<AppEntry?>(null) }

    var renaming by remember { mutableStateOf<AppEntry?>(null) }
    var showHidden by rememberSaveable { mutableStateOf(false) }

    val toRename = renaming
    if (toRename != null) {
        BackHandler { renaming = null }
        var name by remember(toRename.key) { mutableStateOf(nameOf(toRename)) }
        PageColumn {
            PageHeader("rename", onBack = { renaming = null })
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("name: ", style = textStyle(18f, bold = true), maxLines = 1, softWrap = false)
                BasicTextField(
                    value = name, onValueChange = { name = it.replace("\n", "") }, singleLine = true,
                    textStyle = textStyle(18f), cursorBrush = SolidColor(palette.ink), modifier = Modifier.weight(1f)
                )
            }
            SolidRule()
            ListRow("save", bold = true, trailing = "") { onRename(toRename, name); renaming = null }
            ListRow("use its own name", trailing = "[${toRename.label.lowercase()}]") { onRename(toRename, null); renaming = null }
            Hint("The new name is used on the home list, in apps and in search.")
        }
        return
    }

    val target = optionsFor
    if (target != null) {
        BackHandler { optionsFor = null }
        PageColumn {
            PageHeader(nameOf(target).lowercase(), onBack = { optionsFor = null })
            ListRow(if (target.key in pinned) "remove from home" else "add to home", trailing = "") {
                onTogglePin(target); optionsFor = null
            }
            ListRow("rename") { renaming = target; optionsFor = null }
            ListRow(if (target.key in hidden) "show in apps again" else "hide from apps", trailing = "") {
                onHide(target, target.key !in hidden); optionsFor = null
            }
            ListRow("app info") { onAppInfo(target); optionsFor = null }
            ListRow("uninstall") { onUninstall(target); optionsFor = null }
        }
        return
    }

    // Hidden apps stay out of the list (unless shown), but a search still finds them.
    val named = remember(apps, nameOf) { apps.map { it to nameOf(it) }.sortedBy { it.second.lowercase() } }
    val filtered = remember(named, query, hidden, showHidden) {
        val q = query.trim()
        if (q.isEmpty()) named.filter { showHidden || it.first.key !in hidden }.map { it.first }
        else named.filter { it.second.contains(q, ignoreCase = true) || it.first.label.contains(q, ignoreCase = true) }.map { it.first }
    }

    PageColumn {
        PageHeader("apps", onBack)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText("find: ", style = textStyle(18f, bold = true), maxLines = 1, softWrap = false)
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = textStyle(18f),
                cursorBrush = SolidColor(palette.ink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { filtered.firstOrNull()?.let(onLaunch) })
            )
        }
        SolidRule()
        if (filtered.isEmpty()) {
            Hint("No app matches \u201c${query.trim()}\u201d.")
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        } else {
            // Tap (or slide along) a letter on the right to jump to apps starting with it.
            var jumpTo by remember(query) { mutableStateOf(0) }
            var jumpCount by remember { mutableStateOf(0) }
            Row(Modifier.weight(1f).imePadding()) {
                PagedList(filtered, Modifier.weight(1f), resetKey = query to jumpCount, showIndex = jumpTo) { app ->
                    ListRow(
                        nameOf(app).lowercase(),
                        trailing = when { app.key in hidden -> "hidden"; app.key in pinned -> "on home"; else -> "" },
                        onLongPress = { optionsFor = app },
                        onPress = { onLaunch(app) }
                    )
                }
                if (query.isBlank()) {
                    LetterIndex(filtered.map { nameOf(it) }) { i -> jumpTo = i; jumpCount++ }
                }
            }
        }
        Column(Modifier.press { if (hidden.isNotEmpty()) showHidden = !showHidden }) {
            FooterLine(
                (if (query.isBlank()) "${apps.size - hidden.size} apps" else "${filtered.size} found") +
                    if (hidden.isEmpty()) "" else " · ${hidden.size} hidden · tap to " + if (showHidden) "leave them out" else "show them"
            )
        }
    }
}


private fun letterOf(label: String): Char {
    val c = label.trim().firstOrNull()?.uppercaseChar() ?: '#'
    return if (c in 'A'..'Z') c else '#'
}

/**
 * A–Z down the right edge. Letters with apps are solid; the rest are faint
 * (dotted). Tap one, or slide your finger along them, to jump.
 */
@Composable
private fun LetterIndex(labels: List<String>, onJump: (Int) -> Unit) {
    val letters = listOf('#') + ('A'..'Z')
    val firstIndex = letters.associateWith { l -> labels.indexOfFirst { letterOf(it) == l } }
    val p = LocalPalette.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val indexSize = with(density) { 16f.toDp().toSp() }
    var active by remember { mutableStateOf<Char?>(null) }
    fun jumpAt(y: Float, height: Int) {
        if (height <= 0) return
        val i = (y / height * letters.size).toInt().coerceIn(0, letters.lastIndex)
        // Nearest letter at or after the one touched that has apps
        val l = letters.drop(i).firstOrNull { (firstIndex[it] ?: -1) >= 0 }
            ?: letters.take(i).lastOrNull { (firstIndex[it] ?: -1) >= 0 } ?: return
        if (l != active) { active = l; onJump(firstIndex.getValue(l)) }
    }
    androidx.compose.foundation.layout.Column(
        Modifier.width(26.dp).fillMaxHeight().padding(vertical = 2.dp)
            .pointerInput(labels) {
                detectTapGestures(onPress = { o -> jumpAt(o.y, size.height); tryAwaitRelease(); active = null })
            }
            .pointerInput(labels) {
                detectVerticalDragGestures(
                    onDragEnd = { active = null },
                    onVerticalDrag = { change, _ -> change.consume(); jumpAt(change.position.y, size.height) }
                )
            },
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        letters.forEach { l ->
            val has = (firstIndex[l] ?: -1) >= 0
            val on = l == active
            Box(
                Modifier.fillMaxWidth().weight(1f).background(if (on) p.ink else androidx.compose.ui.graphics.Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    if (has) l.toString() else "·",
                    // Index letters are drawn at exactly 16 px so all 27 fit down the edge.
                    style = textStyle(14f, color = if (on) p.paper else p.ink, fixed = true).copy(fontSize = indexSize),
                    maxLines = 1
                )
            }
        }
    }
}
