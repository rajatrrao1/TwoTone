package com.hmj.twotone.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Reorder or remove the rows on the home list. [labelOf] names apps and sections alike. */
@Composable
fun HomeListScreen(
    pinned: List<String>,
    labelOf: (String) -> String,
    onChange: (List<String>) -> Unit,
    onBack: () -> Unit
) {
    PageColumn {
        PageHeader("home list", onBack)
        if (pinned.isEmpty()) {
            Hint("Nothing here yet. Open apps and long-press one to add it, or add a section in settings.")
            return@PageColumn
        }
        PagedList(pinned, Modifier.weight(1f)) { k ->
            val i = pinned.indexOf(k)
            Row(
                Modifier.fillMaxWidth().height(rowHeight()).padding(start = 18.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(labelOf(k), Modifier.weight(1f), style = textStyle(20f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                Mark("↑") { if (i > 0) onChange(pinned.toMutableList().apply { add(i - 1, removeAt(i)) }) }
                Mark("↓") { if (i < pinned.lastIndex) onChange(pinned.toMutableList().apply { add(i + 1, removeAt(i)) }) }
                Mark("×") { onChange(pinned - k) }
            }
        }
    }
}

@Composable
private fun Mark(symbol: String, onPress: () -> Unit) {
    Box(Modifier.press(onPress = onPress).padding(horizontal = 12.dp, vertical = 8.dp)) {
        BasicText(symbol, style = textStyle(20f, bold = true, fixed = true))
    }
}
