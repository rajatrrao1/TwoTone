package com.hmj.twotone.notes

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** One note: a Markdown file. Its title is its file name. */
data class Note(val path: String, val text: String, val modified: Long) {
    val title: String get() = path.substringAfterLast('/').removeSuffix(".md")
    val folder: String get() = path.substringBeforeLast('/', "")
    val archived: Boolean get() = folder == ARCHIVE || folder.startsWith("$ARCHIVE/")
    /** Ticked or not, for each "- [ ] …" line. */
    val tasks: List<Boolean> get() = text.lineSequence().mapNotNull { TASK.find(it)?.groupValues?.get(2)?.let { m -> m != " " } }.toList()
    val isList: Boolean get() = tasks.isNotEmpty()
    /** The recording linked the Obsidian way, ![[Voice ….m4a]], if any. */
    val recording: String? get() = AUDIO.find(text)?.groupValues?.get(1)

    companion object {
        const val ARCHIVE = "Archive"
        val TASK = Regex("^(\\s*[-*] )\\[( |x|X)] ?(.*)$")
        val AUDIO = Regex("!\\[\\[([^\\]]+\\.(?:m4a|mp3|aac|ogg|wav|opus))]]", RegexOption.IGNORE_CASE)
    }
}

/**
 * Your notes, as Markdown files: in the launcher, or in a folder you choose
 * (they then open in Obsidian or any editor as they are). Recordings sit beside
 * the note that links them. Pins are remembered by the launcher.
 */
class NotesStore(private val ctx: Context) {
    private val sp = ctx.getSharedPreferences("twotone_notes", Context.MODE_PRIVATE)

    var notes by mutableStateOf<List<Note>>(emptyList())
        private set
    var pinned by mutableStateOf(sp.getStringSet("pinned", emptySet())!!.toSet())
        private set
    /** The chosen folder, or null for the launcher's own storage. */
    var folderUri by mutableStateOf(sp.getString("folder", null))
        private set
    var folderName by mutableStateOf(sp.getString("folderName", null))
        private set
    /** Recording files and other things beside the notes, by path. */
    private var otherFiles: Set<String> = emptySet()

    private var vault: Vault = makeVault()

    private fun makeVault(): Vault {
        val u = folderUri
        return if (u != null) try { TreeVault(ctx, Uri.parse(u)) } catch (e: Exception) { LocalVault(File(ctx.filesDir, "notes")) }
        else LocalVault(File(ctx.filesDir, "notes"))
    }

    /** Reads the folder again. Call off the main thread. */
    fun reload() {
        migrateOldNotes()
        val files = try { vault.list() } catch (e: Exception) { emptyList() }
        otherFiles = files.map { it.first }.filterNot { it.endsWith(".md", true) }.toSet()
        notes = files.filter { it.first.endsWith(".md", true) }.mapNotNull { (p, m) ->
            try { Note(p, vault.read(p), m) } catch (e: Exception) { null }
        }.sortedByDescending { it.modified }
    }

    fun get(path: String) = notes.firstOrNull { it.path == path }

    /** A new note (or list) named [title], unique in its folder. Call off the main thread. */
    fun create(title: String, text: String, folder: String = ""): Note {
        val base = safeName(title)
        var name = base; var n = 2
        fun pathOf(nm: String) = (if (folder.isEmpty()) "" else "$folder/") + "$nm.md"
        while (notes.any { it.path.equals(pathOf(name), true) } || vault.exists(pathOf(name))) name = "$base ${n++}"
        val note = Note(pathOf(name), text, System.currentTimeMillis())
        vault.write(note.path, text)
        notes = listOf(note) + notes
        return note
    }

    /** Saves [text]; renames the file if [title] changed. Returns the note's path. */
    fun save(path: String, title: String, text: String): String {
        val old = get(path) ?: return path
        var newPath = path
        val wanted = safeName(title.ifBlank { firstLine(text) })
        if (wanted != old.title && wanted.isNotBlank()) {
            val candidate = (if (old.folder.isEmpty()) "" else "${old.folder}/") + "$wanted.md"
            if (notes.none { it.path.equals(candidate, true) && it.path != path }) {
                try { vault.move(path, candidate); newPath = candidate; renamePin(path, candidate) } catch (e: Exception) { }
            }
        }
        if (old.text != text || newPath != path) {
            try { vault.write(newPath, text) } catch (e: Exception) { }
            notes = listOf(Note(newPath, text, System.currentTimeMillis())) + notes.filter { it.path != path }
        }
        return newPath
    }

    fun delete(path: String) {
        val n = get(path)
        try { vault.delete(path) } catch (e: Exception) { }
        // Its recording goes with it.
        n?.recording?.let { rec -> otherPath(n, rec)?.let { try { vault.delete(it) } catch (e: Exception) { } } }
        notes = notes.filter { it.path != path }
        renamePin(path, null)
    }

    fun archive(path: String, toArchive: Boolean): String {
        val n = get(path) ?: return path
        val target = if (toArchive) "${Note.ARCHIVE}/${n.title}.md" else "${n.title}.md"
        if (target == path) return path
        return try {
            vault.move(path, target)
            notes = listOf(n.copy(path = target)) + notes.filter { it.path != path }
            renamePin(path, target)
            target
        } catch (e: Exception) { path }
    }

    fun togglePin(path: String) {
        pinned = if (path in pinned) pinned - path else pinned + path
        sp.edit().putStringSet("pinned", pinned).apply()
    }

    // ---- recordings -------------------------------------------------------------

    /** Where a new recording goes; the note is made afterwards with [create]. */
    fun recordingTarget(name: String) = vault.openForWriting(name, "audio/mp4")

    /** The recording linked by [n], to play. */
    fun recordingUri(n: Note): Uri? = n.recording?.let { rec -> otherPath(n, rec)?.let { try { vault.uri(it) } catch (e: Exception) { null } } }

    private fun otherPath(n: Note, file: String): String? {
        val beside = (if (n.folder.isEmpty()) "" else "${n.folder}/") + file
        return when {
            beside in otherFiles || vault.exists(beside) -> beside
            file in otherFiles || vault.exists(file) -> file
            else -> otherFiles.firstOrNull { it.endsWith("/$file") }
        }
    }

    // ---- where notes are kept ---------------------------------------------------

    /**
     * Keep notes in [tree] (null = back inside the launcher). With [bringAlong], every
     * note and recording is copied there; nothing is removed from the old place, and a
     * different note with the same name is kept beside it as "name (other copy)".
     * Call off the main thread.
     */
    fun moveTo(tree: Uri?, name: String?, bringAlong: Boolean) {
        val from = vault
        if (tree != null) {
            try {
                ctx.contentResolver.takePersistableUriPermission(
                    tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) { }
        }
        folderUri = tree?.toString(); folderName = name
        sp.edit().putString("folder", folderUri).putString("folderName", folderName).apply()
        val to = makeVault()
        if (bringAlong) {
            val theirs = try { to.list().map { it.first }.toSet() } catch (e: Exception) { emptySet() }
            (try { from.list() } catch (e: Exception) { emptyList() }).forEach { (p, _) ->
                try {
                    if (p.endsWith(".md", true)) {
                        val text = from.read(p)
                        val target = if (p in theirs && (try { to.read(p) } catch (e: Exception) { "" }) != text)
                            p.removeSuffix(".md") + " (other copy).md" else p
                        if (target !in theirs || target != p) to.write(target, text)
                    } else if (p !in theirs) {
                        val bytes = from.uri(p).let { u -> ctx.contentResolver.openInputStream(u)?.use { it.readBytes() } } ?: return@forEach
                        to.openForWriting(p, "application/octet-stream").use { pfd ->
                            java.io.FileOutputStream(pfd.fileDescriptor).use { it.write(bytes) }
                        }
                    }
                } catch (e: Exception) { }
            }
        }
        vault = to
        reload()
    }

    // ---- helpers ----------------------------------------------------------------

    private fun renamePin(from: String, to: String?) {
        if (from !in pinned) return
        pinned = pinned - from + listOfNotNull(to)
        sp.edit().putStringSet("pinned", pinned).apply()
    }

    /** Notes from before v1.1 (one file of JSON) become Markdown files, once. */
    private fun migrateOldNotes() {
        val old = File(ctx.filesDir, "notes.json")
        if (!old.exists()) return
        try {
            val arr = JSONArray(old.readText())
            val local = LocalVault(File(ctx.filesDir, "notes"))
            val taken = local.list().map { it.first }.toMutableSet()
            for (i in 0 until arr.length()) {
                val text = arr.getJSONObject(i).getString("text")
                if (text.isBlank()) continue
                var name = safeName(firstLine(text)); var n = 2
                while ("$name.md" in taken) name = "${safeName(firstLine(text))} ${n++}"
                local.write("$name.md", text)
                taken += "$name.md"
            }
            old.renameTo(File(ctx.filesDir, "notes.json.old"))
        } catch (e: Exception) { }
    }

    companion object {
        fun firstLine(text: String) = text.lineSequence().map { it.trim().trimStart('#', ' ', '-', '*') }.firstOrNull { it.isNotBlank() }?.take(60) ?: ""

        fun safeName(s: String): String =
            s.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "-").trim().trim('.').take(80)
                .ifBlank { "Note " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm")) }
    }
}
