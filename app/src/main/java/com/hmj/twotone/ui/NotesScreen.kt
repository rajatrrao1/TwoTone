package com.hmj.twotone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.hmj.twotone.notes.NotesStore
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun NotesScreen(store: NotesStore, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    var deleting by remember { mutableStateOf<Long?>(null) }
    val target = deleting?.let { store.get(it) }
    if (target != null) {
        BackHandler { deleting = null }
        PageColumn {
            PageHeader(target.title, onBack = { deleting = null })
            ListRow("delete this note", trailing = "") { store.delete(target.id); deleting = null }
            ListRow("keep it", trailing = "") { deleting = null }
        }
        return
    }

    val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    val rows: List<Long?> = listOf<Long?>(null) + store.notes.map { it.id }
    PageColumn {
        PageHeader("notes", onBack)
        PagedList(rows, Modifier.weight(1f), resetKey = store.notes.size) { id ->
            if (id == null) {
                ListRow("new note", bold = true) { onOpen(store.create()) }
            } else {
                val n = store.get(id)
                if (n != null) {
                    ListRow(
                        n.title,
                        trailing = Instant.ofEpochMilli(n.updated).atZone(ZoneId.systemDefault()).format(dayFmt).lowercase(),
                        onLongPress = { deleting = n.id },
                        onPress = { onOpen(n.id) }
                    )
                }
            }
        }
        FooterLine(if (store.notes.size == 1) "1 note" else "${store.notes.size} notes")
    }
}

/** A plain-text page. It saves as you type, and an empty note is removed when you leave. */
@Composable
fun NoteEditScreen(store: NotesStore, id: Long, onBack: () -> Unit) {
    val palette = LocalPalette.current
    var text by remember(id) { mutableStateOf(store.get(id)?.text.orEmpty()) }
    val latest by rememberUpdatedState(text)

    LaunchedEffect(text) {
        delay(700)
        store.update(id, text)
    }
    DisposableEffect(id) {
        onDispose {
            if (latest.isBlank()) store.delete(id) else store.update(id, latest)
        }
    }

    PageColumn {
        PageHeader("note", onBack)
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp),
            textStyle = textStyle(18f),
            cursorBrush = SolidColor(palette.ink)
        )
    }
}
