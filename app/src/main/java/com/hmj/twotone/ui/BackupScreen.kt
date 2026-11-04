package com.hmj.twotone.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.hmj.twotone.backup.Backup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Choose where backups go, back up now or every day, or put one back. */
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var folderName by remember { mutableStateOf(Backup.folderName(ctx)) }
    var daily by remember { mutableStateOf(Backup.daily(ctx)) }
    var confirmRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var last by remember { mutableStateOf(Backup.lastAt(ctx)) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) try { Backup.setFolder(ctx, tree); folderName = Backup.folderName(ctx); message = null }
        catch (e: Exception) { message = "That folder can't be used. Make one inside Documents and choose that." }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) confirmRestore = uri }

    val restoring = confirmRestore
    PageColumn {
        PageHeader("backup", onBack)
        if (restoring != null) {
            Hint("This replaces everything in the launcher now (settings, alarms, notes, playlists, coffee, bookmarks, news) with what's in the backup, then the launcher starts again.")
            ListRow("yes, put the backup back", trailing = "") {
                message = "Putting it back."
                scope.launch {
                    try { withContext(Dispatchers.IO) { Backup.restore(ctx, restoring) } }
                    catch (e: Exception) { message = e.message ?: "That backup couldn't be read."; confirmRestore = null }
                }
            }
            ListRow("no, keep things as they are", trailing = "") { confirmRestore = null }
            message?.let { Hint(it) }
            return@PageColumn
        }
        ListRow("backup folder", trailing = bracket(folderName ?: "choose")) { pickFolder.launch(null) }
        ListRow("back up now", trailing = "") {
            if (folderName == null) { pickFolder.launch(null); return@ListRow }
            message = "Backing up."
            scope.launch {
                message = try {
                    val n = withContext(Dispatchers.IO) { Backup.now(ctx) }
                    last = Backup.lastAt(ctx); "Saved $n."
                } catch (e: Exception) { e.message ?: "The backup couldn't be saved." }
            }
        }
        ListRow("back up every day", trailing = bracket(if (daily) "on" else "off")) { daily = !daily; Backup.setDaily(ctx, daily) }
        ListRow("put a backup back") { pickFile.launch(arrayOf("application/zip", "application/octet-stream")) }
        message?.let { Hint(it) }
        Hint(
            (if (last > 0) "Last backup: " + Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.getDefault())).lowercase() + ". " else "") +
                "The newest seven are kept. Book files aren't included; they come back from your book folders."
        )
    }
}
