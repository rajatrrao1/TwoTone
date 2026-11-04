package com.hmj.twotone.books

import android.text.Html
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Looks words up in Wiktionary (en.wiktionary.org), which explains words from many
 * languages in English. The book's language is used to pick the right entry.
 * Needs the internet; must be called off the main thread.
 */
object Dictionary {
    class Sense(val partOfSpeech: String, val language: String, val definitions: List<String>)

    fun lookUp(word: String, bookLanguage: String): List<Sense> {
        val tries = listOf(word, word.lowercase()).distinct()
        for (w in tries) {
            val json = try { get(w) } catch (e: java.io.FileNotFoundException) { null } ?: continue
            val o = JSONObject(json)
            val keys = o.keys().asSequence().toList()
            // The book's language first, then English, then whatever else there is.
            val order = (listOf(bookLanguage, "en") + keys).filter { it.isNotBlank() && it in keys }.distinct()
            val senses = order.flatMap { k ->
                val arr = o.getJSONArray(k)
                (0 until arr.length()).map { i ->
                    val e = arr.getJSONObject(i)
                    val defs = e.optJSONArray("definitions")
                    val list = (0 until (defs?.length() ?: 0)).mapNotNull { j ->
                        plain(defs!!.getJSONObject(j).optString("definition")).takeIf { it.isNotBlank() }
                    }
                    Sense(e.optString("partOfSpeech").lowercase(), e.optString("language"), list.take(4))
                }
            }.filter { it.definitions.isNotEmpty() }
            if (senses.isNotEmpty()) return senses.take(6)
        }
        return emptyList()
    }

    private fun get(word: String): String {
        val url = "https://en.wiktionary.org/api/rest_v1/page/definition/" + URLEncoder.encode(word, "UTF-8").replace("+", "%20")
        val c = URL(url).openConnection() as HttpURLConnection
        // Wikimedia asks for a name that identifies the app.
        c.setRequestProperty("User-Agent", "TwoTone/0.14 (personal e-ink launcher; reader dictionary)")
        c.connectTimeout = 10_000
        c.readTimeout = 10_000
        try {
            if (c.responseCode == 404) throw java.io.FileNotFoundException(word)
            if (c.responseCode !in 200..299) throw IOException("the dictionary answered ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun plain(html: String): String = Html.fromHtml(html).toString().replace(Regex("\\s+"), " ").trim()

    /** The word at [offset] in [text], or null if there isn't one. */
    fun wordAt(text: String, offset: Int): String? {
        if (text.isEmpty()) return null
        var i = offset.coerceIn(0, text.length - 1)
        fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '’' || c == '-'
        if (!isWordChar(text[i]) && i > 0 && isWordChar(text[i - 1])) i--
        if (!isWordChar(text[i])) return null
        var a = i; var b = i
        while (a > 0 && isWordChar(text[a - 1])) a--
        while (b < text.length - 1 && isWordChar(text[b + 1])) b++
        return text.substring(a, b + 1).trim('\'', '’', '-').takeIf { it.isNotBlank() }
    }
}
