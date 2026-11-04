package com.hmj.twotone.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hmj.twotone.notify.Note
import com.hmj.twotone.notify.Notifications

private fun ago(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60_000L
    return when { m < 1 -> "now"; m < 60 -> "$m min"; m < 48 * 60 -> "${m / 60} h"; else -> "${m / (24 * 60)} d" }
}

/** Your notifications as a list. Tap one to open it; hold one to dismiss it. */
@Composable
fun NotificationsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val access = remember(LocalResumeTick.current) { Notifications.hasAccess(ctx) }
    PageColumn {
        PageHeader("notifications", onBack)
        if (!access) {
            Hint(
                "To list your notifications here, Android needs you to switch on Notification access for Two Tone. " +
                    "It only reads them to show them on this page; nothing leaves the phone."
            )
            ListRow("open notification access") {
                try {
                    ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) { }
            }
            return@PageColumn
        }
        val list = Notifications.list
        if (list.isEmpty()) {
            Hint("No notifications.")
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        } else {
            val rows: List<Note?> = list + listOf<Note?>(null)
            PagedList(rows, Modifier.weight(1f), resetKey = list.size, rowSize = 30f) { n ->
                if (n == null) {
                    if (list.any { it.clearable }) ListRow("clear all", trailing = "") { Notifications.clearAll() }
                } else NoteRow(n)
            }
        }
        FooterLine(if (list.isEmpty()) "tap the bell to come back here" else "tap to open · hold to dismiss")
    }
}

/** Three lines: app and time, the title, the first line of the message. */
@Composable
private fun NoteRow(n: Note) {
    Column(
        Modifier.fillMaxWidth().height(rowHeight(30f))
            .press(onLongPress = { if (n.clearable) Notifications.dismiss(n) }) { Notifications.open(n) }
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row {
            BasicText(n.app.lowercase(), Modifier.weight(1f), style = textStyle(14f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            BasicText(ago(n.time), style = textStyle(14f), maxLines = 1)
        }
        BasicText(n.title, style = textStyle(18f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        if (n.text.isNotBlank()) BasicText(n.text, style = textStyle(14f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
    SolidRule(Modifier.padding(horizontal = 14.dp), thickness = 1.dp)
}
