package com.hmj.twotone.coffee

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One bean from the Coffee app's file, as label → value lines, in a sensible order. */
data class KohiBean(
    val id: String, val name: String, val roaster: String, val finished: Boolean,
    val lines: List<Pair<String, String>>, val updated: Long,
    /** The recipe Coffee keeps for these beans, used to fill in a new brew. */
    val recipe: Map<String, String> = emptyMap()
)

/**
 * One cup, as written to coffee-brews.json:
 * { "version": 1, "brews": [ { "id", "beanId", "beanName", "date": "2026-11-06",
 *   "time" (ms), "method", "temp", "grind", "dose", "water", "rating" (0–5), "note" } ] }
 */
data class DailyBrew(
    val id: Long, val beanId: String, val beanName: String, val date: String, val time: Long,
    val method: String, val temp: String, val grind: String, val dose: String, val water: String,
    val rating: Int, val note: String
)

/**
 * Reads your Coffee app's beans from the copy it keeps in its backup folder
 * (coffee-beans.json, rewritten after every change). Two Tone only reads it:
 * adding and editing happen in Coffee, which keeps its beans in its own storage.
 */
object KohiLink {
    private const val FILE = "coffee-beans.json"
    /** Brews logged from the launcher; Coffee can read the same file. */
    const val BREWS = "coffee-brews.json"
    const val PACKAGE = "app.kohi"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("twotone_coffee", Context.MODE_PRIVATE)
    fun folder(ctx: Context): String? = prefs(ctx).getString("kohiFolder", null)
    fun linked(ctx: Context) = folder(ctx) != null

    fun link(ctx: Context, tree: Uri) {
        // Read the beans; write the brews file beside them.
        try {
            ctx.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (e: Exception) {
            try { ctx.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (e2: Exception) { }
        }
        prefs(ctx).edit().putString("kohiFolder", tree.toString()).apply()
    }

    fun unlink(ctx: Context) = prefs(ctx).edit().remove("kohiFolder").apply()

    /** The beans, newest first. Call off the main thread. Throws with a reason if the file can't be read. */
    fun read(ctx: Context): List<KohiBean> {
        val tree = Uri.parse(folder(ctx) ?: throw IllegalStateException("No folder chosen."))
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        var fileId: String? = null
        ctx.contentResolver.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId),
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { c -> while (c.moveToNext()) if (c.getString(1) == FILE) fileId = c.getString(0) }
        val id = fileId ?: throw IllegalStateException("There's no $FILE in that folder. In Coffee, open More, choose a folder, and pick the same one here.")
        val text = ctx.contentResolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, id))?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: throw IllegalStateException("$FILE couldn't be read.")
        val trimmed = text.trimStart()
        val arr = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).let { o ->
            o.optJSONArray("beans") ?: o.optJSONArray("items") ?: o.keys().asSequence().mapNotNull { o.optJSONArray(it) }.firstOrNull() ?: JSONArray()
        }
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map(::toBean).sortedByDescending { it.updated }
    }

    // Labels for the fields Coffee is known to keep; anything else is shown with a tidied name.
    private val ORDER = listOf(
        "roaster" to "roaster", "origin" to "origin", "region" to "region", "producer" to "producer",
        "altitude" to "altitude", "process" to "process", "variety" to "variety", "roast" to "roast",
        "notes" to "tasting notes", "method" to "method", "brewer" to "brewer", "temp" to "water",
        "grind" to "grind", "dose" to "coffee", "water" to "water amount", "yield" to "yield", "ratio" to "ratio",
        "time" to "time", "rating" to "rating", "myNotes" to "my notes", "bagGrams" to "bag", "price" to "price",
        "opened" to "opened", "finishedOn" to "finished on", "url" to "link"
    )
    private val SKIP = setOf("id", "name", "added", "updated", "finished")

    private fun pretty(key: String) = key.replace(Regex("([a-z])([A-Z])"), "$1 $2").replace('_', ' ').lowercase()

    private fun valueText(key: String, v: Any?): String? {
        if (v == null || v == JSONObject.NULL) return null
        val dateLike = Regex("(?i)date|opened|finishedon|day|at$").containsMatchIn(key)
        return when (v) {
            is Number -> if (dateLike && v.toLong() > 1_000_000_000_000L)
                Instant.ofEpochMilli(v.toLong()).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())).lowercase()
            else if (key == "rating") { val n = v.toInt(); if (n <= 0) null else "★".repeat(n) + "☆".repeat((5 - n).coerceAtLeast(0)) }
            else if (v.toDouble() == 0.0) null else v.toString()
            is Boolean -> null
            is JSONObject -> v.keys().asSequence().mapNotNull { k -> valueText(k, v.opt(k))?.let { "${pretty(k)} $it" } }.joinToString(", ").ifBlank { null }
            is JSONArray -> (0 until v.length()).mapNotNull { valueText(key, v.opt(it)) }.joinToString(", ").ifBlank { null }
            else -> v.toString().trim().ifBlank { null }
        }
    }

    private fun toBean(o: JSONObject): KohiBean {
        val keys = o.keys().asSequence().toList()
        val known = ORDER.mapNotNull { (k, label) -> valueText(k, o.opt(k))?.let { label to it } }
        val rest = keys.filter { it !in SKIP && ORDER.none { (k, _) -> k == it } }
            .mapNotNull { k -> valueText(k, o.opt(k))?.let { pretty(k) to it } }
        val recipe = listOf("method", "temp", "grind", "dose", "water").mapNotNull { k ->
            o.opt(k)?.takeIf { it != JSONObject.NULL }?.toString()?.takeIf { it.isNotBlank() && it != "0" }?.let { k to it }
        }.toMap()
        return KohiBean(
            id = o.optString("id"),
            name = o.optString("name").ifBlank { "unnamed beans" },
            roaster = o.optString("roaster"),
            finished = o.optBoolean("finished", false),
            lines = known + rest,
            updated = o.optLong("updated", o.optLong("added", 0L)),
            recipe = recipe
        )
    }

    // ---- daily brews ------------------------------------------------------------

    private fun findChild(ctx: Context, tree: Uri, name: String): Uri? {
        val treeId = DocumentsContract.getTreeDocumentId(tree)
        ctx.contentResolver.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, treeId),
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { c -> while (c.moveToNext()) if (c.getString(1) == name) return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0)) }
        return null
    }

    /** Every brew logged so far, newest first. Call off the main thread. */
    fun brews(ctx: Context): List<DailyBrew> {
        val tree = Uri.parse(folder(ctx) ?: return emptyList())
        val uri = findChild(ctx, tree, BREWS) ?: return emptyList()
        val text = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return emptyList()
        val arr = try { JSONObject(text).optJSONArray("brews") } catch (e: Exception) { null } ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { b ->
            DailyBrew(
                b.optLong("id"), b.optString("beanId"), b.optString("beanName"), b.optString("date"), b.optLong("time"),
                b.optString("method"), b.optString("temp"), b.optString("grind"), b.optString("dose"), b.optString("water"),
                b.optInt("rating"), b.optString("note")
            )
        }.sortedByDescending { it.time }
    }

    /** Adds a brew (or, with [removeId], takes one out) and writes the file. Call off the main thread. */
    fun saveBrews(ctx: Context, add: DailyBrew? = null, removeId: Long? = null) {
        val tree = Uri.parse(folder(ctx) ?: throw IllegalStateException("No folder chosen."))
        val all = (brews(ctx).filter { it.id != removeId } + listOfNotNull(add)).sortedBy { it.time }
        val arr = JSONArray()
        all.forEach { b ->
            arr.put(
                JSONObject().put("id", b.id).put("beanId", b.beanId).put("beanName", b.beanName).put("date", b.date).put("time", b.time)
                    .put("method", b.method).put("temp", b.temp).put("grind", b.grind).put("dose", b.dose).put("water", b.water)
                    .put("rating", b.rating).put("note", b.note)
            )
        }
        val text = JSONObject().put("version", 1).put("brews", arr).toString(2)
        val uri = findChild(ctx, tree, BREWS) ?: DocumentsContract.createDocument(
            ctx.contentResolver, DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)),
            "application/json", BREWS
        ) ?: throw IllegalStateException("The brews file couldn't be made. Choose the folder again (coffee > stop using…).")
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw IllegalStateException("The brews file couldn't be written.")
    }

    /** Opens the Coffee app (to add or change beans). */
    fun openApp(ctx: Context): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(PACKAGE) ?: return false
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}
