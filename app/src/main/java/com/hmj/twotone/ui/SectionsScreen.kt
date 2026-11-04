package com.hmj.twotone.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hmj.twotone.AppEntry
import com.hmj.twotone.Section

/** Every built-in section and what it currently opens. */
@Composable
fun SectionsScreen(handlerName: (Section) -> String, onSection: (Section) -> Unit, onBack: () -> Unit) {
    PageColumn {
        PageHeader("sections", onBack)
        PagedList(Section.entries, Modifier.weight(1f)) { s ->
            ListRow(s.label, trailing = bracket(handlerName(s))) { onSection(s) }
        }
    }
}

/** One section: what it opens, and whether it is on the home list. */
@Composable
fun SectionDetailScreen(
    section: Section,
    handlerName: String,
    onHome: Boolean,
    onPickApp: () -> Unit,
    onToggleHome: () -> Unit,
    onOpen: () -> Unit,
    onBack: () -> Unit
) {
    PageColumn {
        PageHeader(section.label, onBack)
        ListRow("opens", trailing = bracket(handlerName), onPress = onPickApp)
        ListRow("on home list", trailing = bracket(if (onHome) "yes" else "no"), onPress = onToggleHome)
        ListRow("open it now", onPress = onOpen)
        Hint("The row keeps the name \u201c${section.label}\u201d whichever app it opens.")
    }
}

/** Pick one installed app, or the first choice ([noAppLabel]: built-in / automatic). */
@Composable
fun PickAppScreen(
    title: String,
    noAppLabel: String,
    apps: List<AppEntry>,
    currentKey: String?,
    onPick: (AppEntry?) -> Unit,
    onBack: () -> Unit
) {
    val choices: List<AppEntry?> = listOf<AppEntry?>(null) + apps
    PageColumn {
        PageHeader(title, onBack)
        PagedList(choices, Modifier.weight(1f)) { app ->
            if (app == null) {
                ListRow(noAppLabel, trailing = if (currentKey == null) "•" else "") { onPick(null) }
            } else {
                ListRow(app.label.lowercase(), trailing = if (app.key == currentKey) "•" else "") { onPick(app) }
            }
        }
    }
}
