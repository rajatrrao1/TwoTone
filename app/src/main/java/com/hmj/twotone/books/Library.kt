package com.hmj.twotone.books

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Book(
    val id: String,
    val uri: String,
    val title: String,
    val author: String,
    val chapter: Int = 0,
    /** Position in the chapter (a character count) where you stopped. */
    val offset: Int = 0,
    val percent: Int = 0,
    val lastRead: Long = 0L
)

/**
 * Your books: EPUB files from the folders (or single files) you added. A copy of
 * each is kept inside the launcher so it opens quickly and keeps working offline.
 */
object Library {
    var books by mutableStateOf<List<Book>>(emptyList())
        private set
    var folders by mutableStateOf<List<String>>(emptyList())
        private set
    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val sp = prefs(ctx)
        books = try {
            val arr = JSONArray(sp.getString("books", "[]"))
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Book(
                    o.getString("id"), o.getString("uri"), o.getString("title"), o.optString("author"),
                    o.optInt("chapter"), o.optInt("offset"), o.optInt("percent"), o.optLong("lastRead")
                )
            }
        } catch (e: Exception) { emptyList() }
        folders = try {
            val arr = JSONArray(sp.getString("folders", "[]"))
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) { emptyList() }
    }

    fun byId(id: String) = books.firstOrNull { it.id == id }

    fun file(ctx: Context, b: Book) = File(dir(ctx), "${b.id}.epub")
    fun coverFile(ctx: Context, id: String) = File(dir(ctx), "$id.cover")

    /** Adds one EPUB (copies it in and reads its title). Call off the main thread. */
    fun addFile(ctx: Context, uri: Uri): Book? {
        val id = Integer.toHexString(uri.toString().hashCode())
        val dest = File(dir(ctx), "$id.epub")
        byId(id)?.let { known ->
            if (dest.exists()) return known
            // Known (from a backup) but its copy is missing: copy it in again, keep your place.
            return try {
                ctx.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
                known
            } catch (e: Exception) { null }
        }
        return try {
            ctx.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
            Epub(dest).use { e ->
                e.coverBytes()?.let { coverFile(ctx, id).writeBytes(it) }
                val b = Book(id, uri.toString(), e.title, e.author)
                books = books + b
                persist(ctx)
                b
            }
        } catch (e: Exception) {
            dest.delete()
            null
        }
    }

    /** Remembers a folder (with permission to read it later) and adds every EPUB in it. */
    fun addFolder(ctx: Context, tree: Uri): Int {
        try {
            ctx.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) { }
        if (tree.toString() !in folders) { folders = folders + tree.toString(); persist(ctx) }
        return scan(ctx, tree)
    }

    /** Looks for new books in every added folder. Call off the main thread. */
    fun rescan(ctx: Context): Int = folders.sumOf { try { scan(ctx, Uri.parse(it)) } catch (e: Exception) { 0 } }

    private fun scan(ctx: Context, tree: Uri): Int {
        val found = mutableListOf<Uri>()
        fun walk(docId: String, depth: Int) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            ctx.contentResolver.query(
                children,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                ), null, null, null
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1).orEmpty()
                    val mime = c.getString(2).orEmpty()
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) { if (depth < 4) walk(id, depth + 1) }
                    else if (name.endsWith(".epub", ignoreCase = true) || mime == "application/epub+zip") {
                        found += DocumentsContract.buildDocumentUriUsingTree(tree, id)
                    }
                }
            }
        }
        walk(DocumentsContract.getTreeDocumentId(tree), 0)
        val before = books.size
        found.forEach { addFile(ctx, it) }
        return books.size - before
    }

    // ---- bookmarks ----------------------------------------------------------

    data class Mark(val chapter: Int, val offset: Int, val snippet: String, val time: Long)

    fun marks(ctx: Context, id: String): List<Mark> = try {
        val arr = JSONArray(prefs(ctx).getString("marks_$id", "[]"))
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Mark(o.getInt("chapter"), o.getInt("offset"), o.optString("snippet"), o.optLong("time"))
        }.sortedWith(compareBy({ it.chapter }, { it.offset }))
    } catch (e: Exception) { emptyList() }

    fun setMarks(ctx: Context, id: String, marks: List<Mark>) {
        val arr = JSONArray()
        marks.forEach { arr.put(JSONObject().put("chapter", it.chapter).put("offset", it.offset).put("snippet", it.snippet).put("time", it.time)) }
        prefs(ctx).edit().putString("marks_$id", arr.toString()).apply()
    }

    fun saveProgress(ctx: Context, id: String, chapter: Int, offset: Int, percent: Int) {
        books = books.map {
            if (it.id == id) it.copy(chapter = chapter, offset = offset, percent = percent, lastRead = System.currentTimeMillis()) else it
        }
        persist(ctx)
    }

    fun remove(ctx: Context, id: String) {
        books = books.filter { it.id != id }
        File(dir(ctx), "$id.epub").delete()
        coverFile(ctx, id).delete()
        persist(ctx)
    }

    private fun dir(ctx: Context) = File(ctx.filesDir, "books").apply { mkdirs() }

    private fun persist(ctx: Context) {
        val arr = JSONArray()
        books.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("uri", it.uri).put("title", it.title).put("author", it.author)
                    .put("chapter", it.chapter).put("offset", it.offset).put("percent", it.percent).put("lastRead", it.lastRead)
            )
        }
        prefs(ctx).edit().putString("books", arr.toString()).putString("folders", JSONArray(folders).toString()).apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("twotone_books", Context.MODE_PRIVATE)
}
