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
import androidx.compose.foundation.layout.height
import kotlinx.coroutines.launch
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
    var logging by remember { mutableStateOf(false) }
    var byDay by remember { mutableStateOf(false) }
    var brewsVersion by remember { mutableStateOf(0) }
    val brews by produceState(emptyList<com.hmj.twotone.coffee.DailyBrew>(), tick, brewsVersion) {
        value = withContext(Dispatchers.IO) { try { KohiLink.brews(ctx) } catch (e: Exception) { emptyList() } }
    }
    val result by produceState<Result<List<KohiBean>>?>(null, tick) {
        value = withContext(Dispatchers.IO) { runCatching { KohiLink.read(ctx) } }
    }

    val beansNow = result?.getOrNull().orEmpty()
    if (logging) {
        LogBrewPage(beansNow.filter { !it.finished }, brews, onSaved = { logging = false; brewsVersion++ }, onBack = { logging = false })
        return
    }
    if (byDay) {
        BrewsByDayPage(brews, onDelete = { id ->
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try { KohiLink.saveBrews(ctx, removeId = id) } catch (e: Exception) { }
                withContext(Dispatchers.Main) { brewsVersion++ }
            }
        }, onBack = { byDay = false })
        return
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
                val today = java.time.LocalDate.now().toString()
                val todays = brews.count { it.date == today }
                val rows: List<KohiBean?> = listOf<KohiBean?>(KohiBean("+log", "", "", false, emptyList(), 0), KohiBean("+days", "", "", false, emptyList(), 0), null) + shown
                PagedList(rows, Modifier.weight(1f), resetKey = shown.size) { b ->
                    if (b?.id == "+log") ListRow("+ log a brew", bold = true, trailing = if (todays > 0) "$todays today" else "") { logging = true }
                    else if (b?.id == "+days") ListRow("brews by day", trailing = "${brews.size} ▸") { byDay = true }
                    else if (b == null) ListRow("add or change beans in coffee") {
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


/** "today", "yesterday" or "thu 5 nov" for an ISO date. */
private fun dayLabel(iso: String): String {
    val d = try { java.time.LocalDate.parse(iso) } catch (e: Exception) { return iso }
    val today = java.time.LocalDate.now()
    return when (d) {
        today -> "today"
        today.minusDays(1) -> "yesterday"
        else -> d.format(java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.getDefault())).lowercase()
    }
}

/**
 * Log a cup: choose the beans (from Coffee's list), and the recipe Coffee keeps for
 * them fills in; change anything, set the day (today unless you say), save.
 */
@Composable
private fun LogBrewPage(beans: List<KohiBean>, past: List<com.hmj.twotone.coffee.DailyBrew>, onSaved: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    BackHandler { onBack() }
    // Start from the beans you brewed last, if they're still in use.
    val startBean = past.firstOrNull()?.let { p -> beans.firstOrNull { it.id == p.beanId } } ?: beans.firstOrNull()
    var bean by remember { mutableStateOf(startBean) }
    var picking by remember { mutableStateOf(false) }
    fun fromBean(b: KohiBean?, key: String) = b?.recipe?.get(key).orEmpty()
    var method by remember { mutableStateOf(fromBean(startBean, "method")) }
    var temp by remember { mutableStateOf(fromBean(startBean, "temp")) }
    var grind by remember { mutableStateOf(fromBean(startBean, "grind")) }
    var dose by remember { mutableStateOf(fromBean(startBean, "dose")) }
    var water by remember { mutableStateOf(fromBean(startBean, "water")) }
    var rating by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf("") }
    var daysAgo by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    if (picking) {
        BackHandler { picking = false }
        PageColumn {
            PageHeader("which beans", onBack = { picking = false })
            PagedList(beans, Modifier.weight(1f)) { b ->
                ListRow(b.name.lowercase(), trailing = if (b.id == bean?.id) "•" else b.roaster.lowercase().take(12)) {
                    bean = b
                    method = fromBean(b, "method"); temp = fromBean(b, "temp"); grind = fromBean(b, "grind")
                    dose = fromBean(b, "dose"); water = fromBean(b, "water")
                    picking = false
                }
            }
        }
        return
    }

    val date = java.time.LocalDate.now().minusDays(daysAgo.toLong())
    val rows = listOf<@Composable () -> Unit>(
        { ListRow("beans", trailing = bracket(bean?.name?.lowercase() ?: "choose")) { picking = true } },
        {
            ListRow("day", trailing = bracket(dayLabel(date.toString())), onLongPress = { daysAgo = 0 }) {
                daysAgo = (daysAgo + 1) % 8   // tap: a day earlier (up to a week); hold: today
            }
        },
        { KohiField("method", method) { method = it } },
        { KohiField("water °c", temp) { temp = it } },
        { KohiField("grind", grind) { grind = it } },
        { KohiField("coffee g", dose) { dose = it } },
        { KohiField("water ml", water) { water = it } },
        { ListRow("rating", trailing = if (rating == 0) "[none]" else "[" + "★".repeat(rating) + "☆".repeat(5 - rating) + "]") { rating = (rating + 1) % 6 } },
        { KohiField("note", note) { note = it } },
        {
            ListRow("save", bold = true, trailing = "") {
                val b = bean ?: run { message = "Choose the beans first."; return@ListRow }
                val now = System.currentTimeMillis()
                val brew = com.hmj.twotone.coffee.DailyBrew(
                    now, b.id, b.name, date.toString(), if (daysAgo == 0) now else date.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    method.trim(), temp.trim(), grind.trim(), dose.trim(), water.trim(), rating, note.trim()
                )
                scope.launch {
                    message = try { withContext(Dispatchers.IO) { KohiLink.saveBrews(ctx, add = brew) }; null }
                    catch (e: SecurityException) {
                        // Linked before brews existed, with permission to read only: ask for the folder again.
                        KohiLink.unlink(ctx)
                        "Two Tone may only read that folder so far. Go back, and choose the same folder again to allow writing the brews file."
                    }
                    catch (e: Exception) { e.message ?: "The brew couldn't be saved." }
                    if (message == null) onSaved()
                }
            }
        }
    )
    PageColumn {
        PageHeader("log a brew", onBack)
        message?.let { Hint(it) }
        PagedList(rows, Modifier.weight(1f)) { it() }
        FooterLine("tap day for a day earlier · hold it for today")
    }
}

@Composable
private fun KohiField(label: String, value: String, onChange: (String) -> Unit) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth().height(rowHeight()).padding(horizontal = 14.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        BasicText(label, Modifier.width(100.dp), style = textStyle(14f), maxLines = 1, softWrap = false)
        androidx.compose.foundation.text.BasicTextField(
            value = value, onValueChange = { onChange(it.replace("\n", "")) }, singleLine = true,
            textStyle = textStyle(18f), cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.ink),
            modifier = Modifier.weight(1f)
        )
    }
}

/** Every logged cup, under its day, newest first. Hold one to delete it. */
@Composable
private fun BrewsByDayPage(brews: List<com.hmj.twotone.coffee.DailyBrew>, onDelete: (Long) -> Unit, onBack: () -> Unit) {
    BackHandler { onBack() }
    val rows = mutableListOf<@Composable () -> Unit>()
    brews.groupBy { it.date }.toList().sortedByDescending { it.first }.forEach { (day, list) ->
        rows.add(@Composable { SectionBar(dayLabel(day), if (list.size == 1) "1 cup" else "${list.size} cups") })
        list.forEach { b ->
            rows.add(@Composable {
                val detail = listOf(b.method, b.temp.takeIf { it.isNotBlank() }?.let { "$it °c" }, b.grind.takeIf { it.isNotBlank() }?.let { "grind $it" },
                    b.dose.takeIf { it.isNotBlank() }?.let { "$it g" }, b.water.takeIf { it.isNotBlank() }?.let { "$it ml" })
                    .filter { !it.isNullOrBlank() }.joinToString(" · ")
                androidx.compose.foundation.layout.Column(
                    Modifier.fillMaxWidth().height(rowHeight(26f)).press(onLongPress = { onDelete(b.id) }) { }.padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Row {
                        BasicText(b.beanName.lowercase(), Modifier.weight(1f), style = textStyle(18f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        if (b.rating > 0) BasicText("★".repeat(b.rating), style = textStyle(15f), maxLines = 1)
                    }
                    BasicText(listOf(detail, b.note).filter { it.isNotBlank() }.joinToString(" · "), style = textStyle(13f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                }
            })
        }
    }
    PageColumn {
        PageHeader("brews by day", onBack)
        if (rows.isEmpty()) Hint("No brews logged yet.")
        PagedList(rows, Modifier.weight(1f), resetKey = brews.size) { it() }
        FooterLine("kept in coffee-brews.json beside coffee's beans · hold a cup to delete it")
    }
}
