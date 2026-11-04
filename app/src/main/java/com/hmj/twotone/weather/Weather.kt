package com.hmj.twotone.weather

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

data class Place(val name: String, val lat: Double, val lon: Double)

data class HourForecast(val epochMs: Long, val temp: Double, val symbol: String)
data class DayForecast(val date: LocalDate, val min: Double, val max: Double, val symbol: String)

data class Forecast(
    val place: String,
    val fetchedAt: Long,
    val nowTemp: Double,
    val nowSymbol: String,
    val hours: List<HourForecast>,
    val days: List<DayForecast>
)

/**
 * Forecasts and town search both come from Open-Meteo (open-meteo.com): free,
 * no account or key, CC BY 4.0. These are the only network requests the launcher makes.
 */
object WeatherApi {
    private const val USER_AGENT = "TwoTone/0.7 (personal launcher for Mudita Kompakt)"

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", USER_AGENT)
        c.connectTimeout = 15_000
        c.readTimeout = 15_000
        try {
            val code = c.responseCode
            if (code !in 200..299) throw IOException("the weather service answered $code")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    /** A short, plain explanation of why a request failed. */
    fun explain(e: Exception): String = when (e) {
        is java.net.UnknownHostException -> "The phone couldn't reach the internet (no connection, or the name lookup failed)."
        is java.net.SocketTimeoutException -> "The weather service took too long to answer."
        is javax.net.ssl.SSLException -> "A secure connection couldn't be made. Check the phone's date and time."
        is SecurityException -> "This app isn't allowed to use the internet. Check its permissions in Android settings."
        else -> "Couldn't update the weather: ${e.message ?: e.javaClass.simpleName}."
    }

    /** Must be called off the main thread. */
    fun searchPlaces(query: String): List<Place> {
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=8&format=json&name=" +
            URLEncoder.encode(query, "UTF-8")
        val arr = JSONObject(get(url)).optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val name = listOf("name", "admin1", "country")
                .map { o.optString(it) }
                .filter { it.isNotBlank() && it != "null" }
                .distinct()
                .joinToString(", ")
            Place(name, o.getDouble("latitude"), o.getDouble("longitude"))
        }
    }

    /** Must be called off the main thread. Returns the raw JSON, which is cached as-is. */
    fun fetchForecast(p: Place): String {
        val lat = String.format(Locale.US, "%.4f", p.lat)
        val lon = String.format(Locale.US, "%.4f", p.lon)
        return get(
            "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code,is_day" +
                "&hourly=temperature_2m,weather_code,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                "&timezone=auto&forecast_days=7"
        )
    }

    fun parse(raw: String, placeName: String, fetchedAt: Long): Forecast {
        val o = JSONObject(raw)
        val offset = java.time.ZoneOffset.ofTotalSeconds(o.optInt("utc_offset_seconds", 0))
        fun epoch(t: String) = java.time.LocalDateTime.parse(t).toInstant(offset).toEpochMilli()

        val cur = o.getJSONObject("current")
        val nowSymbol = symbolFor(cur.getInt("weather_code"), cur.optInt("is_day", 1) == 1)

        val h = o.getJSONObject("hourly")
        val ht = h.getJSONArray("time"); val htemp = h.getJSONArray("temperature_2m")
        val hcode = h.getJSONArray("weather_code"); val hday = h.optJSONArray("is_day")
        val now = System.currentTimeMillis()
        val hours = (0 until ht.length())
            .map { i -> HourForecast(epoch(ht.getString(i)), htemp.getDouble(i), symbolFor(hcode.getInt(i), (hday?.optInt(i, 1) ?: 1) == 1)) }
            .filter { it.epochMs > now }
            .take(12)

        val d = o.getJSONObject("daily")
        val dt = d.getJSONArray("time"); val dcode = d.getJSONArray("weather_code")
        val dmax = d.getJSONArray("temperature_2m_max"); val dmin = d.getJSONArray("temperature_2m_min")
        val days = (0 until dt.length()).map { i ->
            DayForecast(LocalDate.parse(dt.getString(i)), dmin.getDouble(i), dmax.getDouble(i), symbolFor(dcode.getInt(i), true))
        }

        return Forecast(placeName, fetchedAt, cur.getDouble("temperature_2m"), nowSymbol, hours, days)
    }

    /** WMO weather codes → the symbol names the icons and words use. */
    private fun symbolFor(code: Int, day: Boolean): String {
        val dn = if (day) "_day" else "_night"
        return when (code) {
            0 -> "clearsky$dn"
            1 -> "fair$dn"
            2 -> "partlycloudy$dn"
            3 -> "cloudy"
            45, 48 -> "fog"
            51, 53, 55, 56, 57, 61 -> "lightrain"
            63 -> "rain"
            65 -> "heavyrain"
            66, 67 -> "sleet"
            71 -> "lightsnow"
            73, 77 -> "snow"
            75 -> "heavysnow"
            80 -> "lightrainshowers$dn"
            81 -> "rainshowers$dn"
            82 -> "heavyrainshowers$dn"
            85, 86 -> "snowshowers$dn"
            95, 96, 99 -> "rainandthunder"
            else -> "cloudy"
        }
    }

    /** The phone's last known position, or null if it has none yet. */
    @SuppressLint("MissingPermission")
    fun lastKnownLocation(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.getProviders(true)
            .mapNotNull { p -> try { lm.getLastKnownLocation(p) } catch (e: SecurityException) { null } }
            .maxByOrNull { it.time }
    }
}

/** Plain words for MET Norway's symbol codes, e.g. "lightrainshowers_day" → "light showers". */
fun describe(symbol: String): String {
    val base = symbol.substringBefore('_')
    return when (base) {
        "clearsky" -> "clear"
        "fair" -> "fair"
        "partlycloudy" -> "partly cloudy"
        "cloudy" -> "cloudy"
        "fog" -> "fog"
        "lightrain" -> "light rain"
        "rain" -> "rain"
        "heavyrain" -> "heavy rain"
        "lightrainshowers" -> "light showers"
        "rainshowers" -> "showers"
        "heavyrainshowers" -> "heavy showers"
        "lightsnow", "lightsnowshowers" -> "light snow"
        "snow", "snowshowers" -> "snow"
        "heavysnow", "heavysnowshowers" -> "heavy snow"
        "lightsleet", "sleet", "heavysleet", "lightsleetshowers", "sleetshowers", "heavysleetshowers" -> "sleet"
        else -> if ("thunder" in base) "thunder" else base
    }
}
