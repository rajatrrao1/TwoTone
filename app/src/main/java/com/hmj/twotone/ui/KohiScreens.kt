package com.hmj.twotone.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hmj.twotone.coffee.KohiBean
import com.hmj.twotone.coffee.KohiLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Your Coffee app's beans, read from its backup file. Tap one for everything about it. */
@Composable
fun KohiScreen(onBack: () -> Unit, onUnlinked: () -> Unit) {
    val ctx = LocalContext.current
    val tick = LocalResumeTick.current
    var showFinished by rememberSaveable { mutableStateOf(false) }
    var open by remember { mutableStateOf<KohiBean?>(null) }
    val result by produceState<Result<List<KohiBean>>?>(null, tick) {
        value = withContext(Dispatchers.IO) { runCatching { KohiLink.read(ctx) } }
    }

    val bean = open
    if (bean != null) {
        BackHandler { open = null }
        PageColumn {
            PageHeader(bean.name.lowercase(), onBack = { open = null })
            val rows: List<Pair<String, String>?> = bean.lines + listOf<Pair<String, String>?>(null)
            PagedList(rows, Modifier.weight(1f)) { line ->
                if (line == null) ListRow("change it in coffee") { KohiLink.openApp(ctx) }
                else Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                    BasicText(line.first, Modifier.width(110.dp), style = textStyle(14f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    BasicText(line.second, Modifier.weight(1f), style = textStyle(18f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            FooterLine(if (bean.finished) "finished bag" else "in use")
        }
        return
    }

    PageColumn {
        PageHeader("coffee", onBack)
        val r = result
        when {
            r == null -> Hint("Reading your Coffee app's beans.")
            r.isFailure -> {
                Hint(r.exceptionOrNull()?.message ?: "The beans couldn't be read.")
                ListRow("stop using the coffee app's beans", trailing = "") { KohiLink.unlink(ctx); onUnlinked() }
            }
            else -> {
                val all = r.getOrThrow()
                val shown = all.filter { showFinished || !it.finished }
                val rows: List<KohiBean?> = listOf<KohiBean?>(null) + shown
                PagedList(rows, Modifier.weight(1f), resetKey = shown.size) { b ->
                    if (b == null) ListRow("add or change beans in coffee", bold = true) {
                        if (!KohiLink.openApp(ctx)) android.widget.Toast.makeText(ctx, "The Coffee app isn't installed.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    else ListRow(b.name.lowercase(), trailing = if (b.finished) "finished" else b.roaster.lowercase().take(14)) { open = b }
                }
                androidx.compose.foundation.layout.Column(Modifier.press { showFinished = !showFinished }) {
                    FooterLine("${all.count { !it.finished }} in use · ${all.count { it.finished }} finished · tap to " + if (showFinished) "hide finished" else "show finished")
                }
            }
        }
    }
}

/** Choose the folder where the Coffee app keeps coffee-beans.json. */
@Composable
fun KohiLinkScreen(onLinked: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) { KohiLink.link(ctx, tree); onLinked() }
    }
    PageColumn {
        PageHeader("your coffee app", onBack)
        Hint(
            "Two Tone can show the beans from your Coffee app. In Coffee, open More and choose a backup folder " +
                "(it keeps coffee-beans.json there after every change). Then choose the same folder here."
        )
        ListRow("choose the folder", onPress = { pick.launch(null) })
        Hint("Adding and editing stay in Coffee; Two Tone shows what's in the file.")
    }
}
