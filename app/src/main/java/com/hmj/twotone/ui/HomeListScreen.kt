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
    onAdd: () -> Unit,
    onBack: () -> Unit
) {
    PageColumn {
        PageHeader("home list", onBack)
        ListRow("+ add to the home list", bold = true, onPress = onAdd)
        SolidRule()
        if (pinned.isEmpty()) {
            Hint("Nothing here yet.")
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


/**
 * Everything that isn't on the home list yet: the built-in sections first,
 * then every installed app. Tap one to put it at the bottom of the list.
 */
@Composable
fun AddToHomeScreen(
    sections: List<Pair<String, String>>,
    apps: List<Pair<String, String>>,
    onAdd: (String) -> Unit,
    onBack: () -> Unit
) {
    val rows: List<Pair<String, String>?> = (if (sections.isEmpty()) emptyList() else listOf<Pair<String, String>?>("" to "")) +
        sections + listOf<Pair<String, String>?>(null) + apps
    PageColumn {
        PageHeader("add to home", onBack)
        PagedList(rows, Modifier.weight(1f)) { r ->
            if (r == null) SectionBar("apps")
            else if (r.first.isEmpty()) SectionBar("sections")
            else ListRow(r.second, trailing = if (r.first.startsWith("sec:")) "built-in" else "") { onAdd(r.first) }
        }
    }
}
