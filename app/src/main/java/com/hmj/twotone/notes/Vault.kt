package com.hmj.twotone.notes

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import java.io.File
import java.io.FileNotFoundException

/**
 * Where note files live: inside the launcher, or in a folder you chose on the
 * phone. Paths are relative, with "/" between folders: "Shopping.md", "Archive/Old.md".
 */
interface Vault {
    /** Every file, as (path, last modified). */
    fun list(): List<Pair<String, Long>>
    fun read(path: String): String
    fun write(path: String, text: String)
    fun delete(path: String)
    fun move(from: String, to: String)
    fun exists(path: String): Boolean
    /** For recordings: a file to write into, and later to play. */
    fun openForWriting(path: String, mime: String): ParcelFileDescriptor
    fun uri(path: String): Uri
}

/** The launcher's own storage: files/notes. */
class LocalVault(private val root: File) : Vault {
    init { root.mkdirs() }
    private fun f(path: String) = File(root, path)

    override fun list(): List<Pair<String, Long>> =
        root.walkTopDown().maxDepth(4).filter { it.isFile && !it.name.startsWith(".") }
            .map { it.relativeTo(root).invariantSeparatorsPath to it.lastModified() }.toList()

    override fun read(path: String) = f(path).readText()
    override fun write(path: String, text: String) { f(path).parentFile?.mkdirs(); f(path).writeText(text) }
    override fun delete(path: String) { f(path).delete() }
    override fun move(from: String, to: String) { f(to).parentFile?.mkdirs(); if (!f(from).renameTo(f(to))) { f(to).writeBytes(f(from).readBytes()); f(from).delete() } }
    override fun exists(path: String) = f(path).exists()
    override fun openForWriting(path: String, mime: String): ParcelFileDescriptor {
        f(path).parentFile?.mkdirs()
        return ParcelFileDescriptor.open(f(path), ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_WRITE_ONLY)
    }
    override fun uri(path: String): Uri = Uri.fromFile(f(path))
}

/** A folder chosen through Android's picker (for example an Obsidian vault). */
class TreeVault(private val ctx: Context, private val tree: Uri) : Vault {
    private val cr = ctx.contentResolver
    private val rootId = DocumentsContract.getTreeDocumentId(tree)
    /** path → document id, filled as folders are looked through */
    private val ids = HashMap<String, String>()

    private fun docUri(id: String) = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    private class Child(val id: String, val name: String, val dir: Boolean, val modified: Long)

    private fun children(parentId: String): List<Child> {
        val out = mutableListOf<Child>()
        cr.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId),
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            ), null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                out += Child(c.getString(0), c.getString(1).orEmpty(), c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR, c.getLong(3))
            }
        }
        return out
    }

    override fun list(): List<Pair<String, Long>> {
        ids.clear()
        val out = mutableListOf<Pair<String, Long>>()
        fun walk(id: String, prefix: String, depth: Int) {
            children(id).forEach { ch ->
                if (ch.name.startsWith(".")) return@forEach
                val p = prefix + ch.name
                ids[p] = ch.id
                if (ch.dir) { if (depth < 3) walk(ch.id, "$p/", depth + 1) } else out += p to ch.modified
            }
        }
        walk(rootId, "", 0)
        return out
    }

    /** The document id for [path], looking it up if needed; null if it doesn't exist. */
    private fun idOf(path: String): String? {
        ids[path]?.let { return it }
        var id = rootId
        var sofar = ""
        for (seg in path.split('/')) {
            sofar = if (sofar.isEmpty()) seg else "$sofar/$seg"
            id = ids[sofar] ?: children(id).firstOrNull { it.name == seg }?.id?.also { ids[sofar] = it } ?: return null
        }
        return id
    }

    private fun ensureFolder(folder: String): String {
        if (folder.isEmpty()) return rootId
        idOf(folder)?.let { return it }
        val parent = ensureFolder(folder.substringBeforeLast('/', ""))
        val made = DocumentsContract.createDocument(cr, docUri(parent), DocumentsContract.Document.MIME_TYPE_DIR, folder.substringAfterLast('/'))
            ?: throw FileNotFoundException("couldn't make the folder $folder")
        return DocumentsContract.getDocumentId(made).also { ids[folder] = it }
    }

    private fun create(path: String, mime: String): Uri {
        val parent = ensureFolder(path.substringBeforeLast('/', ""))
        val made = DocumentsContract.createDocument(cr, docUri(parent), mime, path.substringAfterLast('/'))
            ?: throw FileNotFoundException("couldn't make $path")
        ids[path] = DocumentsContract.getDocumentId(made)
        return made
    }

    override fun read(path: String): String {
        val id = idOf(path) ?: throw FileNotFoundException(path)
        return cr.openInputStream(docUri(id))?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
    }

    override fun write(path: String, text: String) {
        val uri = idOf(path)?.let { docUri(it) } ?: create(path, "text/markdown")
        cr.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    override fun delete(path: String) {
        idOf(path)?.let { DocumentsContract.deleteDocument(cr, docUri(it)) }
        ids.remove(path)
    }

    override fun move(from: String, to: String) {
        // Same folder: a rename. Anywhere else: copy, then remove the old one.
        val id = idOf(from) ?: return
        if (from.substringBeforeLast('/', "") == to.substringBeforeLast('/', "")) {
            val renamed = DocumentsContract.renameDocument(cr, docUri(id), to.substringAfterLast('/'))
            ids.remove(from)
            if (renamed != null) ids[to] = DocumentsContract.getDocumentId(renamed)
            return
        }
        val bytes = cr.openInputStream(docUri(id))?.use { it.readBytes() } ?: return
        val target = create(to, if (to.endsWith(".md")) "text/markdown" else "application/octet-stream")
        cr.openOutputStream(target, "wt")?.use { it.write(bytes) }
        delete(from)
    }

    override fun exists(path: String) = idOf(path) != null

    override fun openForWriting(path: String, mime: String): ParcelFileDescriptor {
        val uri = idOf(path)?.let { docUri(it) } ?: create(path, mime)
        return cr.openFileDescriptor(uri, "rwt") ?: throw FileNotFoundException(path)
    }

    override fun uri(path: String): Uri = idOf(path)?.let { docUri(it) } ?: throw FileNotFoundException(path)
}
