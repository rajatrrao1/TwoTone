package com.hmj.twotone.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hmj.twotone.coffee.BREW_METHODS
import com.hmj.twotone.coffee.Bean
import com.hmj.twotone.coffee.BeanImporter
import com.hmj.twotone.coffee.Brew
import com.hmj.twotone.coffee.CoffeeStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun stars(n: Int) = if (n <= 0) "unrated" else "★".repeat(n) + "☆".repeat(5 - n)
private fun grams(g: Double) = if (g % 1.0 == 0.0) "${g.toInt()} g" else "%.1f g".format(Locale.US, g)

/** Coffee home: log a brew, your beans, and past brews newest first. */
@Composable
fun CoffeeScreen(
    store: CoffeeStore,
    onNewBrew: () -> Unit,
    onBeans: () -> Unit,
    onBrew: (Long) -> Unit,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val fmt = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(ctx)) "d MMM HH:mm" else "d MMM h:mm a", Locale.getDefault())
    val rows: List<Long?> = listOf<Long?>(-1L, -2L) + store.brews.map { it.id }
    PageColumn {
        PageHeader("coffee", onBack)
        PagedList(rows, Modifier.weight(1f), resetKey = store.brews.size) { id ->
            when (id) {
                -1L -> ListRow("log a brew", bold = true, onPress = onNewBrew)
                -2L -> ListRow("beans", trailing = "${store.beans.size} ▸", onPress = onBeans)
                else -> {
                    val b = store.brew(id ?: 0L)
                    if (b != null) {
                        val bean = store.bean(b.beanId)?.title ?: "beans unknown"
                        ListRow(
                            "${bean.lowercase()} · ${b.method}",
                            trailing = Instant.ofEpochMilli(b.time).atZone(ZoneId.systemDefault()).format(fmt).lowercase()
                        ) { onBrew(b.id) }
                    }
                }
            }
        }
    }
}

/** Every bag of beans you've logged, plus ways to add one. */
@Composable
fun BeansScreen(store: CoffeeStore, onImport: () -> Unit, onAdd: () -> Unit, onBean: (Long) -> Unit, onBack: () -> Unit) {
    val rows: List<Long?> = listOf<Long?>(-1L, -2L) + store.beans.map { it.id }
    PageColumn {
        PageHeader("beans", onBack)
        PagedList(rows, Modifier.weight(1f), resetKey = store.beans.size) { id ->
            when (id) {
                -1L -> ListRow("add from a shop link", bold = true, onPress = onImport)
                -2L -> ListRow("add by hand", onPress = onAdd)
                else -> store.bean(id)?.let { b ->
                    ListRow(b.title.lowercase(), trailing = b.roaster.lowercase().take(14)) { onBean(b.id) }
                }
            }
        }
    }
}

/** Paste a product link; whatever can be read from the page opens in the edit page. */
@Composable
fun ImportBeanScreen(onImported: (Bean) -> Unit, onBack: () -> Unit) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    var link by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun fetch() {
        if (link.isBlank() || busy) return
        busy = true
        message = "Reading the shop page."
        scope.launch {
            try {
                val bean = withContext(Dispatchers.IO) { BeanImporter.fetch(link, System.currentTimeMillis()) }
                busy = false
                onImported(bean)
            } catch (e: Exception) {
                busy = false
                message = "Couldn't read that page. Check the link and that the phone is online, or add the beans by hand."
            }
        }
    }

    PageColumn {
        PageHeader("add from a shop link", onBack)
        Hint("Paste the address of the coffee's page in the shop.")
        BasicTextField(
            value = link,
            onValueChange = { link = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
            textStyle = textStyle(16f),
            cursorBrush = SolidColor(palette.ink),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { fetch() })
        )
        SolidRule(Modifier.padding(horizontal = 18.dp))
        ListRow(if (busy) "reading…" else "read the page", trailing = "") { fetch() }
        message?.let { Hint(it) }
    }
}

/** Every detail of a bag of beans, one editable line each. */
@Composable
fun BeanEditScreen(store: CoffeeStore, beanId: Long, draft: Bean?, onDone: () -> Unit, onBack: () -> Unit) {
    val existing = store.bean(beanId)
    var b by remember(beanId) { mutableStateOf(existing ?: draft?.copy(id = beanId) ?: Bean(beanId)) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        BackHandler { confirmDelete = false }
        PageColumn {
            PageHeader(b.title.lowercase(), onBack = { confirmDelete = false })
            Hint("Brews already logged with these beans are kept.")
            ListRow("delete these beans", trailing = "") { store.deleteBean(beanId); onDone() }
            ListRow("keep them", trailing = "") { confirmDelete = false }
        }
        return
    }

    val rows = listOf<@Composable () -> Unit>(
        { FieldRow("name", b.name) { b = b.copy(name = it) } },
        { FieldRow("roaster", b.roaster) { b = b.copy(roaster = it) } },
        { FieldRow("origin", b.origin) { b = b.copy(origin = it) } },
        { FieldRow("process", b.process) { b = b.copy(process = it) } },
        { FieldRow("altitude", b.altitude) { b = b.copy(altitude = it) } },
        { FieldRow("roast", b.roast) { b = b.copy(roast = it) } },
        { FieldRow("notes", b.notes) { b = b.copy(notes = it) } },
        { FieldRow("link", b.link) { b = b.copy(link = it) } },
        { ListRow("save", bold = true, trailing = "") { store.saveBean(b); onDone() } }
    ) + if (existing != null) listOf<@Composable () -> Unit>({ ListRow("delete", trailing = "") { confirmDelete = true } }) else emptyList()

    PageColumn {
        PageHeader(if (existing == null) "new beans" else "beans", onBack)
        PagedList(rows, Modifier.weight(1f)) { it() }
    }
}

/** Log a brew. The numbers start from your last brew of the same beans. */
@Composable
fun BrewScreen(store: CoffeeStore, onSaved: () -> Unit, onBack: () -> Unit) {
    var brew by remember { mutableStateOf(store.startingPoint(store.brews.firstOrNull()?.beanId)) }
    var picking by remember { mutableStateOf(false) }

    if (picking) {
        BackHandler { picking = false }
        val choices: List<Bean?> = listOf<Bean?>(null) + store.beans
        PageColumn {
            PageHeader("which beans", onBack = { picking = false })
            PagedList(choices, Modifier.weight(1f)) { bean ->
                if (bean == null) {
                    ListRow("none", trailing = if (brew.beanId == null) "•" else "") { brew = brew.copy(beanId = null); picking = false }
                } else {
                    ListRow(bean.title.lowercase(), trailing = if (bean.id == brew.beanId) "•" else "") {
                        // Switching beans brings back the settings you used for them last time.
                        val start = store.startingPoint(bean.id)
                        brew = start.copy(beanId = bean.id, note = brew.note)
                        picking = false
                    }
                }
            }
        }
        return
    }

    val beanName = store.bean(brew.beanId)?.title?.lowercase() ?: "none"
    val rows = listOf<@Composable () -> Unit>(
        { ListRow("beans", trailing = beanName) { picking = true } },
        {
            ListRow("method", trailing = brew.method) {
                brew = brew.copy(method = BREW_METHODS[(BREW_METHODS.indexOf(brew.method) + 1).mod(BREW_METHODS.size)])
            }
        },
        { Stepper("water", "${brew.waterTempC} °C", { brew = brew.copy(waterTempC = (brew.waterTempC - 1).coerceAtLeast(60)) }, { brew = brew.copy(waterTempC = (brew.waterTempC + 1).coerceAtMost(100)) }) },
        { Stepper("grind", "${brew.grind}", { brew = brew.copy(grind = (brew.grind - 1).coerceAtLeast(0)) }, { brew = brew.copy(grind = brew.grind + 1) }) },
        { Stepper("coffee", grams(brew.doseG), { brew = brew.copy(doseG = (brew.doseG - 0.5).coerceAtLeast(0.5)) }, { brew = brew.copy(doseG = brew.doseG + 0.5) }) },
        { Stepper("water amount", "${brew.waterMl} ml", { brew = brew.copy(waterMl = (brew.waterMl - 10).coerceAtLeast(10)) }, { brew = brew.copy(waterMl = brew.waterMl + 10) }) },
        { ListRow("rating", trailing = stars(brew.rating)) { brew = brew.copy(rating = (brew.rating + 1) % 6) } },
        { FieldRow("note", brew.note) { brew = brew.copy(note = it) } },
        { ListRow("save brew", bold = true, trailing = "") { store.addBrew(brew.copy(time = System.currentTimeMillis())); onSaved() } }
    )

    PageColumn {
        PageHeader("log a brew", onBack)
        PagedList(rows, Modifier.weight(1f)) { it() }
    }
}

/** One logged brew, with its beans' details. */
@Composable
fun BrewDetailScreen(store: CoffeeStore, brewId: Long, onBrewAgain: () -> Unit, onDeleted: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val b = store.brew(brewId)
    PageColumn {
        PageHeader("brew", onBack)
        if (b == null) { Hint("This brew was deleted."); return@PageColumn }
        val bean = store.bean(b.beanId)
        val fmt = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(ctx)) "EEE d MMM, HH:mm" else "EEE d MMM, h:mm a", Locale.getDefault())
        val lines = listOfNotNull(
            "when" to Instant.ofEpochMilli(b.time).atZone(ZoneId.systemDefault()).format(fmt).lowercase(),
            "beans" to (bean?.title?.lowercase() ?: "unknown"),
            bean?.roaster?.takeIf { it.isNotBlank() }?.let { "roaster" to it.lowercase() },
            bean?.origin?.takeIf { it.isNotBlank() }?.let { "origin" to it.lowercase() },
            bean?.altitude?.takeIf { it.isNotBlank() }?.let { "altitude" to it },
            "method" to b.method,
            "water" to "${b.waterTempC} °C, ${b.waterMl} ml",
            "grind" to "${b.grind}",
            "coffee" to grams(b.doseG),
            "ratio" to if (b.doseG > 0) "1 : %.1f".format(Locale.US, b.waterMl / b.doseG) else "–",
            "rating" to stars(b.rating),
            b.note.takeIf { it.isNotBlank() }?.let { "note" to it }
        )
        val rows = lines.map<Pair<String, String>, @Composable () -> Unit> { (k, v) -> @Composable { InfoRow(k, v) } } +
            listOf<@Composable () -> Unit>(
                { ListRow("brew this again", bold = true, onPress = onBrewAgain) },
                { ListRow("delete this brew", trailing = "") { store.deleteBrew(b.id); onDeleted() } }
            )
        PagedList(rows, Modifier.weight(1f)) { it() }
    }
}

// ---- rows used by the coffee pages -------------------------------------------

/** "label  value" on one line, not tappable. */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().height(rowHeight()).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, Modifier.width(100.dp), style = textStyle(14f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        BasicText(value, Modifier.weight(1f), style = textStyle(18f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** "label  [text field]" on one line. */
@Composable
private fun FieldRow(label: String, value: String, onChange: (String) -> Unit) {
    val palette = LocalPalette.current
    Row(Modifier.fillMaxWidth().height(rowHeight()).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, Modifier.width(100.dp), style = textStyle(14f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = textStyle(18f),
                cursorBrush = SolidColor(palette.ink),
                modifier = Modifier.fillMaxWidth()
            )
            SolidRule(Modifier.align(Alignment.BottomStart).height(1.dp))
        }
    }
}

/** "label   −  value  +" : big targets, no typing needed. */
@Composable
private fun Stepper(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(rowHeight()).padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicText(label, Modifier.weight(1f), style = textStyle(18f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        Box(Modifier.press(onPress = onMinus).padding(horizontal = 14.dp, vertical = 8.dp)) { BasicText("−", style = textStyle(22f, bold = true)) }
        BasicText(value, Modifier.width(96.dp), style = textStyle(17f, bold = true, fixed = true).copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center), maxLines = 1, softWrap = false)
        Box(Modifier.press(onPress = onPlus).padding(horizontal = 14.dp, vertical = 8.dp)) { BasicText("+", style = textStyle(22f, bold = true)) }
    }
}
