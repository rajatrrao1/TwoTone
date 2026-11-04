package com.hmj.twotone.news

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.io.IOException
import java.net.URI
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private const val UA = "Mozilla/5.0 (Linux; Android 12) TwoTone/0.15 (e-ink launcher; news reader)"

data class Feed(val url: String, val title: String)

data class Story(
    val id: String,
    val feedUrl: String,
    val source: String,
    val title: String,
    val link: String,
    val time: Long,
    /** What the feed itself says about the story; used if the page can't be read. */
    val summary: String
)

/** A story turned into plain text: headings ("# …"), list items ("• …") and paragraphs. */
data class Article(
    val id: String,
    val title: String,
    val site: String,
    val link: String,
    val time: Long,
    val blocks: List<String>
)

fun idFor(link: String): String = Integer.toHexString(link.trim().hashCode())

/**
 * Your news: the sources you add, their latest stories, what you've read,
 * and the stories you saved (kept on the phone as text).
 */
object News {
    var feeds by mutableStateOf<List<Feed>>(emptyList())
        private set
    var stories by mutableStateOf<List<Story>>(emptyList())
        private set
    var read by mutableStateOf<Set<String>>(emptySet())
        private set
    var saved by mutableStateOf<List<Article>>(emptyList())
        private set
    var updatedAt by mutableStateOf(0L)
        private set
    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        try {
            val o = JSONObject(File(ctx.filesDir, "news.json").readText())
            feeds = o.optJSONArray("feeds").objects().map { Feed(it.getString("url"), it.optString("title")) }
            stories = o.optJSONArray("stories").objects().map {
                Story(it.getString("id"), it.getString("feed"), it.optString("source"), it.getString("title"),
                    it.getString("link"), it.optLong("time"), it.optString("summary"))
            }
            read = o.optJSONArray("read").strings().toSet()
            updatedAt = o.optLong("updatedAt")
        } catch (e: Exception) { }
        saved = savedDir(ctx).listFiles()?.mapNotNull { f -> try { articleFrom(JSONObject(f.readText())) } catch (e: Exception) { null } }
            ?.sortedByDescending { it.time } ?: emptyList()
    }

    // ---- sources --------------------------------------------------------------

    /**
     * Adds a source. A feed address is used as it is; for a website, its feed is
     * looked up. Call off the main thread. Returns the feed, or throws with a reason.
     */
    fun addSource(ctx: Context, input: String): Feed {
        val url = normalise(input)
        val feedUrl = discoverFeed(url) ?: throw IOException("No news feed was found at that address.")
        feeds.firstOrNull { it.url == feedUrl }?.let { return it }
        val (title, items) = fetchFeed(feedUrl)
        val f = Feed(feedUrl, title.ifBlank { host(url) })
        feeds = feeds + f
        merge(items.map { it.copy(source = f.title) })
        persist(ctx)
        return f
    }

    fun removeFeed(ctx: Context, url: String) {
        feeds = feeds.filter { it.url != url }
        stories = stories.filter { it.feedUrl != url }
        persist(ctx)
    }

    /** Fetches every source again. Call off the main thread. Returns how many stories are new. */
    fun refresh(ctx: Context): Int {
        val before = stories.map { it.id }.toSet()
        val all = feeds.flatMap { f ->
            try { fetchFeed(f.url).second.map { it.copy(source = f.title) } } catch (e: Exception) { emptyList() }
        }
        merge(all)
        updatedAt = System.currentTimeMillis()
        persist(ctx)
        return stories.count { it.id !in before }
    }

    fun markRead(ctx: Context, id: String) {
        if (id in read) return
        read = read + id
        persist(ctx)
    }

    // ---- saved stories ----------------------------------------------------------

    fun isSaved(id: String) = saved.any { it.id == id }

    fun save(ctx: Context, a: Article) {
        File(savedDir(ctx), "${a.id}.json").writeText(articleJson(a).toString())
        saved = (listOf(a) + saved.filter { it.id != a.id })
    }

    fun unsave(ctx: Context, id: String) {
        File(savedDir(ctx), "$id.json").delete()
        saved = saved.filter { it.id != id }
    }

    fun savedArticle(id: String) = saved.firstOrNull { it.id == id }

    // ---- reading a page -----------------------------------------------------------

    /**
     * Downloads a page and keeps only its article text. If too little text is
     * found, the feed's own summary is used instead. Call off the main thread.
     */
    fun readArticle(link: String, fallback: Story? = null): Article {
        val doc = try {
            Jsoup.connect(link).userAgent(UA).timeout(15_000).followRedirects(true).get()
        } catch (e: Exception) {
            if (fallback != null) return fromSummary(fallback)
            throw e
        }
        val title = doc.selectFirst("meta[property=og:title]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: doc.title().ifBlank { fallback?.title ?: link }
        val site = doc.selectFirst("meta[property=og:site_name]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: fallback?.source ?: host(link)
        val blocks = extract(doc)
        if (blocks.sumOf { it.length } < 400 && fallback != null && fallback.summary.length > blocks.sumOf { it.length }) {
            return fromSummary(fallback)
        }
        return Article(idFor(link), title.trim(), site.trim(), link, fallback?.time ?: System.currentTimeMillis(), blocks)
    }

    private fun fromSummary(s: Story): Article {
        val doc = Jsoup.parse(s.summary)
        val blocks = extract(doc).ifEmpty { listOf(doc.text()) }.filter { it.isNotBlank() }
        return Article(s.id, s.title, s.source, s.link, s.time, blocks)
    }

    /**
     * Finds the part of the page holding the story: every paragraph's text counts
     * towards its parent (fully) and grandparent (half); the best-scoring block wins.
     */
    private fun extract(doc: Document): List<String> {
        doc.select(
            "script,style,noscript,svg,iframe,form,nav,footer,aside,button,select,figure,picture,video,audio," +
                "[role=navigation],[role=banner],[role=complementary],[aria-hidden=true],.ad,.ads,.advert,.share,.social," +
                ".newsletter,.related,.comments,#comments,.cookie,.subscribe"
        ).remove()
        val scores = HashMap<Element, Double>()
        doc.select("p").forEach { p ->
            val n = p.text().length
            if (n < 40) return@forEach
            p.parent()?.let { scores[it] = (scores[it] ?: 0.0) + n }
            p.parent()?.parent()?.let { scores[it] = (scores[it] ?: 0.0) + n / 2.0 }
        }
        val best = doc.selectFirst("article")?.takeIf { a -> a.select("p").sumOf { it.text().length } > 500 }
            ?: scores.maxByOrNull { it.value }?.key
            ?: doc.body() ?: return emptyList()

        val wanted = setOf("h1", "h2", "h3", "h4", "p", "li", "blockquote", "pre")
        val out = mutableListOf<String>()
        best.allElements.forEach { el ->
            val tag = el.tagName().lowercase()
            if (tag !in wanted) return@forEach
            // Skip anything inside another kept block (a <p> inside a <li>, say).
            if (el.parents().takeWhile { it != best }.any { it.tagName().lowercase() in wanted }) return@forEach
            val t = el.text().trim()
            if (t.isEmpty()) return@forEach
            when (tag) {
                "h1", "h2", "h3", "h4" -> out += "# $t"
                "li" -> if (t.length > 2) out += "• $t"
                "blockquote" -> out += "“$t”"
                else -> if (t.length >= 25 || out.isNotEmpty()) out += t
            }
        }
        return out.distinct()
    }

    // ---- feeds ----------------------------------------------------------------------

    private fun normalise(input: String): String {
        val t = input.trim()
        return if (t.startsWith("http://") || t.startsWith("https://")) t else "https://$t"
    }

    private fun host(url: String) = try { URI(url).host.orEmpty().removePrefix("www.") } catch (e: Exception) { url }

    private fun get(url: String): Pair<String, String> {
        val r = Jsoup.connect(url).userAgent(UA).timeout(15_000).followRedirects(true)
            .ignoreContentType(true).maxBodySize(5_000_000).execute()
        return r.body() to (r.contentType() ?: "")
    }

    private fun looksLikeFeed(body: String, type: String) =
        "xml" in type || "rss" in type || "atom" in type ||
            body.trimStart().take(400).let { "<rss" in it || "<feed" in it || "<rdf:RDF" in it }

    /** The feed address for [url]: itself, a feed the page links to, or a usual place for one. */
    private fun discoverFeed(url: String): String? {
        val (body, type) = try { get(url) } catch (e: Exception) { throw IOException("That address couldn't be opened.") }
        if (looksLikeFeed(body, type)) return url
        val page = Jsoup.parse(body, url)
        page.select("link[rel=alternate][type*=rss], link[rel=alternate][type*=atom], link[type*=rss+xml], link[type*=atom+xml]")
            .firstOrNull()?.absUrl("href")?.takeIf { it.isNotBlank() }?.let { return it }
        val root = url.trimEnd('/')
        val origin = try { URI(url).let { "${it.scheme}://${it.host}" } } catch (e: Exception) { root }
        for (guess in listOf("$root/feed", "$root/rss", "$root/rss.xml", "$root/feed.xml", "$root/atom.xml", "$origin/feed", "$origin/rss")) {
            try { val (b, t) = get(guess); if (looksLikeFeed(b, t)) return guess } catch (e: Exception) { }
        }
        return null
    }

    /** RSS 2.0, RSS 1.0 and Atom. */
    private fun fetchFeed(url: String): Pair<String, List<Story>> {
        val (body, _) = get(url)
        val x = Jsoup.parse(body, url, Parser.xmlParser())
        val title = (x.selectFirst("channel > title") ?: x.selectFirst("feed > title"))?.text().orEmpty()
        val items = x.select("item").map { it ->
            val link = it.selectFirst("link")?.text()?.trim().orEmpty().ifBlank { it.selectFirst("guid")?.text().orEmpty() }
            val summary = (it.getElementsByTag("content:encoded").firstOrNull() ?: it.selectFirst("description"))?.text().orEmpty()
            Story(idFor(link), url, title, it.selectFirst("title")?.text().orEmpty(), link,
                parseTime(it.selectFirst("pubDate")?.text() ?: it.getElementsByTag("dc:date").firstOrNull()?.text()), summary)
        } + x.select("entry").map { it ->
            val link = (it.select("link[rel=alternate]").firstOrNull() ?: it.selectFirst("link"))?.attr("href").orEmpty()
            val summary = (it.selectFirst("content") ?: it.selectFirst("summary"))?.text().orEmpty()
            Story(idFor(link), url, title, it.selectFirst("title")?.text().orEmpty(), link,
                parseTime(it.selectFirst("published")?.text() ?: it.selectFirst("updated")?.text()), summary)
        }
        return title to items.filter { it.link.isNotBlank() && it.title.isNotBlank() }
    }

    private fun parseTime(s: String?): Long {
        val t = s?.trim() ?: return System.currentTimeMillis()
        return try { ZonedDateTime.parse(t, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        catch (e: Exception) {
            try { OffsetDateTime.parse(t).toInstant().toEpochMilli() } catch (e2: Exception) { System.currentTimeMillis() }
        }
    }

    /** New stories join the list; it keeps the 300 newest. */
    private fun merge(fresh: List<Story>) {
        val byId = LinkedHashMap<String, Story>()
        (stories + fresh).forEach { byId[it.id] = it }
        stories = byId.values.sortedByDescending { it.time }.take(300)
        read = read.filter { id -> stories.any { it.id == id } }.toSet()
    }

    // ---- storage ------------------------------------------------------------------------

    private fun savedDir(ctx: Context) = File(ctx.filesDir, "news_saved").apply { mkdirs() }

    private fun persist(ctx: Context) {
        val o = JSONObject()
            .put("feeds", JSONArray().apply { feeds.forEach { put(JSONObject().put("url", it.url).put("title", it.title)) } })
            .put("stories", JSONArray().apply {
                stories.forEach {
                    put(JSONObject().put("id", it.id).put("feed", it.feedUrl).put("source", it.source).put("title", it.title)
                        .put("link", it.link).put("time", it.time).put("summary", it.summary.take(4000)))
                }
            })
            .put("read", JSONArray(read.toList()))
            .put("updatedAt", updatedAt)
        try { File(ctx.filesDir, "news.json").writeText(o.toString()) } catch (e: Exception) { }
    }

    private fun articleJson(a: Article) = JSONObject().put("id", a.id).put("title", a.title).put("site", a.site)
        .put("link", a.link).put("time", a.time).put("blocks", JSONArray(a.blocks))

    private fun articleFrom(o: JSONObject) = Article(
        o.getString("id"), o.getString("title"), o.optString("site"), o.optString("link"), o.optLong("time"),
        o.optJSONArray("blocks").strings()
    )

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }
    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { getString(it) }
}
