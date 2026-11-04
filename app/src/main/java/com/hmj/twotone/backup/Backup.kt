package com.hmj.twotone.backup

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.hmj.twotone.MainActivity
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Everything the launcher keeps, in one .zip in a folder you choose: settings,
 * the home list, alarms, notes kept in the launcher, the coffee log, playlists,
 * bookmarks and reading places, news sources and saved stories.
 * Book files themselves are left out (they can be large); they come back from your
 * book folders the next time books opens.
 */
object Backup {
    private const val PREFIX = "twotone-backup-"
    private const val KEEP = 7

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("twotone_backup", Context.MODE_PRIVATE)

    fun folder(ctx: Context): String? = prefs(ctx).getString("folder", null)
    fun folderName(ctx: Context): String? = prefs(ctx).getString("folderName", null)
    fun lastAt(ctx: Context): Long = prefs(ctx).getLong("lastAt", 0L)
    fun daily(ctx: Context): Boolean = prefs(ctx).getBoolean("daily", true)
    fun setDaily(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("daily", v).apply()

    fun setFolder(ctx: Context, tree: Uri) {
        ctx.contentResolver.takePersistableUriPermission(
            tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        val name = tree.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')?.ifBlank { null } ?: "your folder"
        prefs(ctx).edit().putString("folder", tree.toString()).putString("folderName", name).apply()
    }

    /** Writes a new backup and keeps the newest seven. Call off the main thread. Returns the file name. */
    fun now(ctx: Context): String {
        val tree = Uri.parse(folder(ctx) ?: throw IllegalStateException("Choose a backup folder first."))
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val name = PREFIX + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")) + ".zip"
        val doc = DocumentsContract.createDocument(ctx.contentResolver, root, "application/zip", name)
            ?: throw IllegalStateException("The backup file couldn't be made in that folder.")
        ctx.contentResolver.openOutputStream(doc, "wt")?.use { out ->
            ZipOutputStream(out).use { zip ->
                val data = ctx.filesDir.parentFile ?: return@use
                fun add(f: File, as_: String) {
                    zip.putNextEntry(ZipEntry(as_))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                File(data, "shared_prefs").listFiles()?.filter { it.name.endsWith(".xml") && !it.name.startsWith("twotone_backup") }
                    ?.forEach { add(it, "prefs/${it.name}") }
                ctx.filesDir.walkTopDown().filter { it.isFile }.forEach { f ->
                    val rel = f.relativeTo(ctx.filesDir).invariantSeparatorsPath
                    // Book copies are big and come back from your book folders.
                    if (rel.startsWith("books/") && rel.endsWith(".epub")) return@forEach
                    add(f, "files/$rel")
                }
            }
        }
        prefs(ctx).edit().putLong("lastAt", System.currentTimeMillis()).apply()
        prune(ctx, tree)
        return name
    }

    /** Once a day, when the launcher comes to the front. Call off the main thread. */
    fun dailyIfDue(ctx: Context) {
        if (!daily(ctx) || folder(ctx) == null) return
        if (System.currentTimeMillis() - lastAt(ctx) < 24 * 3_600_000L) return
        try { now(ctx) } catch (e: Exception) { }
    }

    private fun prune(ctx: Context, tree: Uri) {
        try {
            val treeId = DocumentsContract.getTreeDocumentId(tree)
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId)
            val found = mutableListOf<Pair<String, String>>()
            ctx.contentResolver.query(
                children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { c -> while (c.moveToNext()) { val n = c.getString(1).orEmpty(); if (n.startsWith(PREFIX)) found += c.getString(0) to n } }
            found.sortedByDescending { it.second }.drop(KEEP).forEach { (id, _) ->
                DocumentsContract.deleteDocument(ctx.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, id))
            }
        } catch (e: Exception) { }
    }

    /**
     * Puts a backup back, replacing what's there now, then restarts the launcher.
     * Call off the main thread.
     */
    fun restore(ctx: Context, file: Uri) {
        val data = ctx.filesDir.parentFile ?: return
        val keepBackupPrefs = File(data, "shared_prefs/twotone_backup.xml").takeIf { it.exists() }?.readBytes()
        ctx.contentResolver.openInputStream(file)?.use { input ->
            ZipInputStream(input).use { zip ->
                var e = zip.nextEntry
                var any = false
                while (e != null) {
                    val n = e.name
                    if (!e.isDirectory && !n.contains("..")) {
                        val target = when {
                            n.startsWith("prefs/") -> File(data, "shared_prefs/" + n.removePrefix("prefs/"))
                            n.startsWith("files/") -> File(ctx.filesDir, n.removePrefix("files/"))
                            else -> null
                        }
                        if (target != null) {
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                            any = true
                        }
                    }
                    e = zip.nextEntry
                }
                if (!any) throw IllegalStateException("That file isn't a Two Tone backup.")
            }
        }
        // The backup folder setting itself stays as it is on this phone.
        keepBackupPrefs?.let { File(data, "shared_prefs/twotone_backup.xml").writeBytes(it) }
        // Start again in a moment, so everything is read fresh from the restored files.
        val again = PendingIntent.getActivity(
            ctx, 77, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT
        )
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).set(AlarmManager.RTC, System.currentTimeMillis() + 800, again)
        android.os.Process.killProcess(android.os.Process.myPid())
    }
}
