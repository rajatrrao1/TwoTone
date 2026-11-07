package com.hmj.twotone.trains

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

data class Station(val code: String, val name: String, val short: String)

data class Departure(
    val planned: OffsetDateTime,
    val actual: OffsetDateTime,
    val direction: String,
    val kind: String,          // "IC", "SPR", …
    val track: String,
    val trackChanged: Boolean,
    val cancelled: Boolean,
    val via: String,
    val note: String
) {
    val delayMinutes: Long get() = java.time.Duration.between(planned, actual).toMinutes()
}

/**
 * Live departures from NS (the Dutch railways), through their public API. It needs
 * your own free key from apiportal.ns.nl (subscribe to the "Ns-App" product).
 * Must be called off the main thread.
 */
object Ns {
    private const val BASE = "https://gateway.apiportal.ns.nl/reisinformatie-api/api/v2"
    private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")

    var key by mutableStateOf<String?>(null)
        private set
    /** Your stations, in your order. */
    var favourites by mutableStateOf<List<Station>>(emptyList())
        private set
    private var loaded = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("twotone_trains", Context.MODE_PRIVATE)

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val sp = prefs(ctx)
        key = sp.getString("key", null)
        favourites = sp.getString("favourites", "").orEmpty().split('\n').filter { it.isNotBlank() }.mapNotNull {
            val p = it.split('\t'); if (p.size >= 3) Station(p[0], p[1], p[2]) else null
        }
    }

    fun setKey(ctx: Context, k: String?) {
        key = k?.trim()?.ifBlank { null }
        prefs(ctx).edit().putString("key", key).apply()
    }

    fun addFavourite(ctx: Context, s: Station) { if (favourites.none { it.code == s.code }) saveFavs(ctx, favourites + s) }
    fun removeFavourite(ctx: Context, code: String) = saveFavs(ctx, favourites.filter { it.code != code })
    private fun saveFavs(ctx: Context, l: List<Station>) {
        favourites = l
        prefs(ctx).edit().putString("favourites", l.joinToString("\n") { "${it.code}\t${it.name}\t${it.short}" }).apply()
    }

    private fun get(path: String): JSONObject {
        val k = key ?: throw IOException("Add your NS key first.")
        val c = URL("$BASE$path").openConnection() as HttpURLConnection
        c.setRequestProperty("Ocp-Apim-Subscription-Key", k)
        c.setRequestProperty("Accept", "application/json")
        c.connectTimeout = 12_000
        c.readTimeout = 12_000
        try {
            when (c.responseCode) {
                in 200..299 -> return JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                401, 403 -> throw IOException("NS didn't accept the key. Check it was copied whole, and that it's subscribed to Ns-App.")
                429 -> throw IOException("Too many requests for now; try again in a minute.")
                else -> throw IOException("NS answered ${c.responseCode}.")
            }
        } finally { c.disconnect() }
    }

    /** All stations (kept on the phone for a week, so search works quickly). */
    fun stations(ctx: Context): List<Station> {
        val cache = File(ctx.cacheDir, "ns-stations.json")
        val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < 7 * 24 * 3_600_000L
        val o = if (fresh) JSONObject(cache.readText()) else get("/stations").also { cache.writeText(it.toString()) }
        val arr = o.optJSONArray("payload") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.filter { it.optString("land") == "NL" }.map {
            val n = it.optJSONObject("namen")
            Station(it.getString("code"), n?.optString("lang").orEmpty().ifBlank { it.getString("code") }, n?.optString("kort").orEmpty())
        }.sortedBy { it.name }
    }

    fun departures(stationCode: String): List<Departure> {
        val o = get("/departures?maxJourneys=25&station=" + URLEncoder.encode(stationCode, "UTF-8"))
        val arr = o.optJSONObject("payload")?.optJSONArray("departures") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val d = arr.optJSONObject(i) ?: return@mapNotNull null
            try {
                val planned = OffsetDateTime.parse(d.getString("plannedDateTime"), TIME)
                val actual = d.optString("actualDateTime").takeIf { it.isNotBlank() }?.let { OffsetDateTime.parse(it, TIME) } ?: planned
                val plannedTrack = d.optString("plannedTrack")
                val actualTrack = d.optString("actualTrack").ifBlank { plannedTrack }
                val route = d.optJSONArray("routeStations")
                val via = (0 until (route?.length() ?: 0)).mapNotNull { route?.optJSONObject(it)?.optString("mediumName") }.take(3).joinToString(", ")
                val msgs = d.optJSONArray("messages")
                val note = (0 until (msgs?.length() ?: 0)).mapNotNull { msgs?.optJSONObject(it)?.optString("message") }.firstOrNull().orEmpty()
                Departure(
                    planned, actual,
                    d.optString("direction"),
                    d.optJSONObject("product")?.optString("shortCategoryName").orEmpty().ifBlank { d.optString("trainCategory") },
                    actualTrack, actualTrack != plannedTrack && plannedTrack.isNotBlank(),
                    d.optBoolean("cancelled", false), via, note
                )
            } catch (e: Exception) { null }
        }
    }
}
