package com.hmj.twotone.books

import android.text.Html
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.util.zip.ZipFile

/**
 * Reads an EPUB: a zip with a list of chapters (the "spine") and their text as
 * web pages. Text keeps paragraphs, headings, bold and italic; pictures are left out.
 */
class Epub(file: File) : Closeable {
    private val zip = ZipFile(file)

    val title: String
    val author: String
    /** The book's language code from its contents list ("en", "nl", …), or "". */
    val language: String
    /** Chapter files, in reading order, as paths inside the zip. */
    val spine: List<String>
    /** Path of the cover picture inside the zip, if the book names one. */
    val coverPath: String?

    init {
        val container = text("META-INF/container.xml") ?: throw IOException("not an EPUB file")
        val opfPath = Regex("full-path\\s*=\\s*[\"']([^\"']+)[\"']").find(container)?.groupValues?.get(1)
            ?: throw IOException("this EPUB has no contents list")
        val base = opfPath.substringBeforeLast('/', "")
        val opf = text(opfPath) ?: throw IOException("this EPUB's contents list is missing")

        title = tag(opf, "dc:title") ?: file.nameWithoutExtension
        author = tag(opf, "dc:creator").orEmpty()
        language = tag(opf, "dc:language").orEmpty().lowercase().take(2)

        class Item(val id: String, val href: String, val type: String, val props: String)
        val items = Regex("(?is)<item\\b[^>]*>").findAll(opf).mapNotNull { m ->
            val t = m.value
            val id = attr(t, "id") ?: return@mapNotNull null
            val href = attr(t, "href") ?: return@mapNotNull null
            Item(id, resolve(base, href), attr(t, "media-type").orEmpty(), attr(t, "properties").orEmpty())
        }.toList()
        val byId = items.associateBy { it.id }

        spine = Regex("(?is)<itemref\\b[^>]*>").findAll(opf)
            .mapNotNull { attr(it.value, "idref") }
            .mapNotNull { byId[it]?.href }
            .filter { zip.getEntry(it) != null }
            .toList()
            .ifEmpty { items.filter { "html" in it.type }.map { it.href } }

        val coverId = Regex("(?is)<meta\\b[^>]*name\\s*=\\s*[\"']cover[\"'][^>]*>").find(opf)?.value?.let { attr(it, "content") }
        coverPath = (items.firstOrNull { "cover-image" in it.props } ?: coverId?.let { byId[it] }
            ?: items.firstOrNull { it.type.startsWith("image") && "cover" in it.id.lowercase() })?.href
    }

    fun coverBytes(): ByteArray? = coverPath?.let { bytes(it) }

    /** One chapter as styled text. */
    fun chapter(i: Int): AnnotatedString {
        var html = spine.getOrNull(i)?.let { text(it) } ?: return AnnotatedString("")
        html = html.replace(Regex("(?is)<head\\b.*?</head>"), "")
            .replace(Regex("(?is)<(script|style|svg)\\b[^>]*>.*?</\\1>"), "")
            .replace(Regex("(?is)<img\\b[^>]*>"), "")
        @Suppress("DEPRECATION")
        val sp: Spanned = if (android.os.Build.VERSION.SDK_INT >= 24) Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY) else Html.fromHtml(html)
        val raw = sp.toString()

        // Copy the text across, dropping picture placeholders and runs of blank lines,
        // and remember where every original character ended up so styles still line up.
        val b = AnnotatedString.Builder()
        val map = IntArray(raw.length + 1)
        var out = 0
        var newlines = 0
        var started = false
        for (k in raw.indices) {
            map[k] = out
            val c = raw[k]
            if (c == '\uFFFC') continue
            if (c == '\n') {
                newlines++
                if (!started || newlines > 2) continue
            } else {
                if (!started && c.isWhitespace()) continue
                newlines = 0
                started = true
            }
            b.append(c)
            out++
        }
        map[raw.length] = out
        sp.getSpans(0, sp.length, StyleSpan::class.java).forEach { s ->
            val st = map[sp.getSpanStart(s)]
            val en = map[sp.getSpanEnd(s)]
            if (en <= st) return@forEach
            val style = when (s.style) {
                android.graphics.Typeface.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                android.graphics.Typeface.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                android.graphics.Typeface.BOLD_ITALIC -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                else -> null
            }
            if (style != null) b.addStyle(style, st, en)
        }
        return b.toAnnotatedString()
    }

    /** A short name for chapter [i]: its first line of text. */
    fun chapterName(i: Int): String =
        chapter(i).text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }?.take(48) ?: "part ${i + 1}"

    override fun close() = zip.close()

    // ---- helpers -------------------------------------------------------------

    private fun text(path: String): String? = bytes(path)?.toString(Charsets.UTF_8)

    private fun bytes(path: String): ByteArray? {
        val e = zip.getEntry(path) ?: return null
        return zip.getInputStream(e).use { it.readBytes() }
    }

    private fun tag(xml: String, name: String): String? =
        Regex("(?is)<$name\\b[^>]*>(.*?)</$name>").find(xml)?.groupValues?.get(1)
            ?.let { @Suppress("DEPRECATION") Html.fromHtml(it).toString().trim() }
            ?.takeIf { it.isNotBlank() }

    private fun attr(tag: String, name: String): String? =
        Regex("(?is)\\b${Regex.escape(name)}\\s*=\\s*[\"']([^\"']*)[\"']").find(tag)?.groupValues?.get(1)

    /** "OEBPS" + "../Text/ch1.xhtml#x" → "Text/ch1.xhtml" */
    private fun resolve(base: String, href: String): String {
        val clean = try { URLDecoder.decode(href.substringBefore('#'), "UTF-8") } catch (e: Exception) { href.substringBefore('#') }
        val parts = (if (base.isEmpty()) emptyList() else base.split('/')).toMutableList()
        clean.split('/').forEach { seg ->
            when (seg) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts += seg
            }
        }
        return parts.joinToString("/")
    }
}
