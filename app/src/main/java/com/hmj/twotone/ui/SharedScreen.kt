package com.hmj.twotone.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hmj.twotone.notes.NotesStore

/**
 * Something shared to Two Tone from another app. A link can be read as text in
 * news, or kept as a note (named after the page, holding a link to it); any
 * other text becomes a note.
 */
@Composable
fun SharedScreen(text: String, subject: String, onRead: (String) -> Unit, onNote: (String, String) -> Unit, onBack: () -> Unit) {
    val url = Regex("https?://\\S+").find(text)?.value
    val onlyLink = url != null && text.replace(url, "").isBlank()
    val title = subject.ifBlank { if (url != null) url.substringAfter("://").substringBefore('/') else NotesStore.firstLine(text) }
    PageColumn {
        PageHeader("shared", onBack)
        BasicText(
            text, Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            style = textStyle(15f), maxLines = 4, overflow = TextOverflow.Ellipsis
        )
        SolidRule()
        if (url != null) ListRow("read it in news") { onRead(url) }
        ListRow("keep it as a note") {
            val body = if (onlyLink) "[${title}](${url})\n" else text
            onNote(title, body)
        }
        ListRow("cancel", trailing = "") { onBack() }
    }
}
