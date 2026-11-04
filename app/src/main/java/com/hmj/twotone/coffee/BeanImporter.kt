package com.hmj.twotone.coffee

import android.text.Html
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Reads a coffee shop's product page and fills in what it can find.
 * Shop pages are all different, so this is a best guess: the result
 * always opens in the edit page for you to check.
 *
 * Shopify shops (many roasters, including Wakuli at the time of writing)
 * publish a product's details at the page address + ".json"; that is tried first.
 */
object BeanImporter {
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 12) TwoTone/0.3 coffee log"
    private const val MAX_BYTES = 3_000_000

    /** Must be called off the main thread. */
    fun fetch(link: String, id: Long): Bean {
        val url = link.trim().let { if (it.startsWith("http")) it else "https://$it" }
        val host = try { URI(url).host.orEmpty().removePrefix("www.") } catch (e: Exception) { "" }

        val product = try {
            JSONObject(get(url.substringBefore('?').substringBefore('#').trimEnd('/') + ".json")).optJSONObject("product")
        } catch (e: Exception) {
            null
        }

        val html = if (product == null) get(url) else ""
        val name = product?.optString("title")?.takeIf { it.isNotBlank() }
            ?: meta(html, "og:title") ?: titleTag(html) ?: ""
        val roaster = product?.optString("vendor")?.takeIf { it.isNotBlank() }
            ?: meta(html, "og:site_name") ?: host

        val body = if (product != null) {
            product.optString("body_html") + " | " + product.optString("tags")
        } else {
            html.replace(Regex("(?is)<(script|style|noscript|svg)[^>]*>.*?</\\1>"), " ")
        }
        val text = plainText(body)

        return Bean(
            id = id,
            name = clean(name),
            roaster = clean(roaster),
            origin = find(text, ORIGIN),
            process = find(text, PROCESS),
            altitude = findAltitude(text),
            roast = find(text, ROAST),
            notes = find(text, NOTES, maxLen = 80),
            link = url
        )
    }

    // ---- page reading -------------------------------------------------------

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", USER_AGENT)
        c.setRequestProperty("Accept-Language", "en,nl;q=0.8")
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        try {
            if (c.responseCode !in 200..299) throw IOException("The shop answered ${c.responseCode}.")
            c.inputStream.use { input ->
                val bytes = input.readNBytesCompat(MAX_BYTES)
                return String(bytes, Charsets.UTF_8)
            }
        } finally {
            c.disconnect()
        }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16_384)
        while (out.size() < limit) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun meta(html: String, property: String): String? {
        val a = Regex("(?is)<meta[^>]+(?:property|name)=[\"']$property[\"'][^>]*content=[\"']([^\"']+)[\"']").find(html)
        val b = Regex("(?is)<meta[^>]+content=[\"']([^\"']+)[\"'][^>]*(?:property|name)=[\"']$property[\"']").find(html)
        return (a ?: b)?.groupValues?.get(1)?.let { decode(it) }?.takeIf { it.isNotBlank() }
    }

    private fun titleTag(html: String): String? =
        Regex("(?is)<title[^>]*>(.*?)</title>").find(html)?.groupValues?.get(1)
            ?.let { decode(it).substringBefore(" | ").substringBefore(" – ").trim() }
            ?.takeIf { it.isNotBlank() }

    @Suppress("DEPRECATION")
    private fun decode(s: String): String =
        if (android.os.Build.VERSION.SDK_INT >= 24) Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString()
        else Html.fromHtml(s).toString()

    /** Turns HTML into one line of text, with " | " wherever a block of the page ended. */
    private fun plainText(html: String): String {
        val marked = html.replace(Regex("(?i)</?(p|div|li|ul|ol|tr|td|th|dt|dd|h[1-6]|br|section|table|strong|b)[^>]*>"), " | ")
        return decode(marked).replace(Regex("\\s+"), " ")
    }

    // ---- finding the details -------------------------------------------------

    private val LABELS = listOf(
        "origin", "country", "herkomst", "land", "region", "regio", "process", "processing", "proces",
        "verwerking", "altitude", "elevation", "hoogte", "teelthoogte", "roast", "branding", "variety",
        "varietal", "variëteit", "tasting notes", "flavour notes", "flavor notes", "smaak", "notes", "farm",
        "producer", "boer", "harvest", "oogst"
    )
    private val ORIGIN = Regex("(?i)\\b(?:origin|country|herkomst|land|region|regio)\\b\\s*[:\\-–]?\\s*\\|?\\s*([^|]{2,60})")
    private val PROCESS = Regex("(?i)\\b(?:process(?:ing)?|proces|verwerking)\\b\\s*[:\\-–]?\\s*\\|?\\s*([^|]{2,40})")
    private val ROAST = Regex("(?i)\\b(?:roast(?: level)?|branding(?:sgraad)?)\\b\\s*[:\\-–]?\\s*\\|?\\s*([^|]{2,40})")
    private val NOTES = Regex("(?i)\\b(?:tasting notes|flavou?r notes|smaaknotities|smaakprofiel|smaak|notes)\\b\\s*[:\\-–]?\\s*\\|?\\s*([^|]{3,120})")
    private val ALTITUDE = Regex(
        "(?i)\\b(?:altitude|elevation|hoogte|teelthoogte|grown at)\\b[^0-9|]{0,20}([0-9][0-9.,]{2,5}(?:\\s*(?:-|–|to|tot)\\s*[0-9][0-9.,]{2,5})?)\\s*(?:m\\b|masl|m\\.a\\.s\\.l|meter|mtr)?"
    )
    private val ALTITUDE_UNIT = Regex(
        "(?i)([0-9][0-9.,]{2,5}(?:\\s*(?:-|–|to|tot)\\s*[0-9][0-9.,]{2,5})?)\\s*(?:masl|m\\.a\\.s\\.l|meters? above sea|m boven zeeniveau)"
    )

    private fun find(text: String, pattern: Regex, maxLen: Int = 40): String {
        val raw = pattern.find(text)?.groupValues?.get(1) ?: return ""
        return clean(cutAtNextLabel(raw)).take(maxLen)
    }

    private fun findAltitude(text: String): String {
        val m = ALTITUDE.find(text) ?: ALTITUDE_UNIT.find(text) ?: return ""
        return m.groupValues[1].replace(" ", "").replace("tot", "–").replace("to", "–") + " m"
    }

    /** "Ethiopia Process Washed" → "Ethiopia": stop where the next label starts. */
    private fun cutAtNextLabel(s: String): String {
        val lower = s.lowercase()
        val cut = LABELS.mapNotNull { l ->
            Regex("\\b${Regex.escape(l)}\\b").find(lower)?.range?.first?.takeIf { it > 0 }
        }.minOrNull()
        return if (cut != null) s.substring(0, cut) else s
    }

    private fun clean(s: String) = s.trim().trim(':', '-', '–', '|', ',', '.', ' ').replace(Regex("\\s+"), " ")
}
