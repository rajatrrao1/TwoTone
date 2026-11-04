package com.hmj.twotone

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.hmj.twotone.weather.Place

/**
 * Settings stored on the phone. Each value is Compose state,
 * so the screen redraws as soon as a setting changes.
 */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("twotone", Context.MODE_PRIVATE)

    private val _pinned = mutableStateOf(
        sp.getString(KEY_PINNED, "").orEmpty().split("|").filter { it.isNotBlank() }
    )
    private val _inverted = mutableStateOf(sp.getBoolean(KEY_INVERTED, false))
    private val _textSize = mutableStateOf(sp.getInt(KEY_TEXT, 1))
    private val _cleanRefresh = mutableStateOf(sp.getBoolean(KEY_REFRESH, false))
    private val _font = mutableStateOf(sp.getString(KEY_FONT, "mono") ?: "mono")
    private val _fontSmall = mutableStateOf(sp.getString(KEY_FONT_SMALL, "mono") ?: "mono")
    private val _pixelSnap = mutableStateOf(sp.getBoolean(KEY_PIXEL_SNAP, false))
    private val _fontStamp = mutableStateOf(sp.getLong(KEY_FONT_STAMP, 0L))
    private val _clock = mutableStateOf(sp.getString(KEY_CLOCK, "pixel") ?: "pixel")
    private val _showWeather = mutableStateOf(sp.getBoolean(KEY_SHOW_WEATHER, true))
    private val _showCalendar = mutableStateOf(sp.getBoolean(KEY_SHOW_CALENDAR, true))
    private val _volumePaging = mutableStateOf(sp.getBoolean(KEY_VOLUME_PAGING, true))
    private val _sectionApps = mutableStateOf(
        Section.entries.mapNotNull { s -> sp.getString(sectionKey(s), null)?.let { s to it } }.toMap()
    )
    private val _place = mutableStateOf(loadPlace())
    private val _clockApp = mutableStateOf(sp.getString(KEY_CLOCK_APP, null))
    private val _chargingClock = mutableStateOf(sp.getInt(KEY_CHARGING_CLOCK, 1))
    private val _hiddenApps = mutableStateOf(sp.getStringSet("hiddenApps", emptySet())!!.toSet())
    private val _appNames = mutableStateOf(
        try {
            val o = org.json.JSONObject(sp.getString("appNames", "{}") ?: "{}")
            o.keys().asSequence().associateWith { o.getString(it) }
        } catch (e: Exception) { emptyMap() }
    )
    private val _ownStatusBar = mutableStateOf(sp.getBoolean(KEY_OWN_STATUS_BAR, true))
    private val _statusItems = mutableStateOf(
        (sp.getStringSet(KEY_STATUS_ITEMS, null)?.toSet() ?: setOf("battery", "bluetooth", "wifi", "mobile", "sound", "alarm"))
            .let {
                if (sp.getBoolean("statusNotesAdded2", false)) it
                else {
                    val withBell = it + "notifications" + "popup"
                    sp.edit().putBoolean("statusNotesAdded2", true).putStringSet(KEY_STATUS_ITEMS, withBell).apply()
                    withBell
                }
            }
    )
    private val _refreshMode = mutableStateOf(sp.getInt(KEY_REFRESH_MODE, if (sp.getBoolean(KEY_REFRESH, false)) 1 else 2))

    // ---- home list ---------------------------------------------------------

    /** Keys of the rows on the home list, in order: app keys and section keys. */
    val pinned: List<String> get() = _pinned.value
    val hasSavedHomeList: Boolean get() = sp.contains(KEY_PINNED)
    val sectionsAdded: Boolean get() = sp.getBoolean(KEY_SECTIONS_ADDED, false)

    fun setPinned(list: List<String>) {
        _pinned.value = list
        sp.edit().putString(KEY_PINNED, list.joinToString("|")).apply()
    }

    fun togglePinned(key: String) = setPinned(if (key in pinned) pinned - key else pinned + key)

    /** Ids of sections that have been offered on the home list before, so each is added only once. */
    fun seenSections(): Set<String> {
        sp.getStringSet(KEY_SEEN_SECTIONS, null)?.let { return it.toSet() }
        // v0.2 added the first five sections in one go and only kept a flag.
        return if (sectionsAdded) setOf("phone", "music", "notes", "calendar", "weather") else emptySet()
    }

    fun markSectionsSeen(ids: Set<String>) = sp.edit().putStringSet(KEY_SEEN_SECTIONS, ids).apply()

    // ---- sections ----------------------------------------------------------

    /** The app key that replaces [s], or null when the built-in version is used. */
    fun sectionApp(s: Section): String? = _sectionApps.value[s]

    fun setSectionApp(s: Section, appKey: String?) {
        _sectionApps.value = if (appKey == null) _sectionApps.value - s else _sectionApps.value + (s to appKey)
        sp.edit().apply {
            if (appKey == null) remove(sectionKey(s)) else putString(sectionKey(s), appKey)
        }.apply()
    }

    /** The app the home clock opens, or null to find the phone's clock app automatically. */
    val clockApp: String? get() = _clockApp.value

    fun setClockApp(key: String?) {
        _clockApp.value = key
        sp.edit().apply { if (key == null) remove(KEY_CLOCK_APP) else putString(KEY_CLOCK_APP, key) }.apply()
    }

    /** Apps left out of the apps list (they still open from search or the home list). */
    val hiddenApps: Set<String> get() = _hiddenApps.value
    fun setHidden(key: String, hide: Boolean) {
        _hiddenApps.value = if (hide) hiddenApps + key else hiddenApps - key
        sp.edit().putStringSet("hiddenApps", _hiddenApps.value).apply()
    }

    /** Your own names for apps, by app key. */
    val appNames: Map<String, String> get() = _appNames.value
    fun rename(key: String, name: String?) {
        _appNames.value = if (name.isNullOrBlank()) appNames - key else appNames + (key to name.trim())
        sp.edit().putString("appNames", org.json.JSONObject(_appNames.value).toString()).apply()
    }
    fun nameOf(app: AppEntry): String = appNames[app.key] ?: app.label

    /** Charging page: 0 = off, 1 = at night (21:00–07:00), 2 = whenever charging. */
    val chargingClock: Int get() = _chargingClock.value
    fun setChargingClock(v: Int) { _chargingClock.value = v; sp.edit().putInt(KEY_CHARGING_CLOCK, v).apply() }

    /** True: the launcher draws its own status bar and hides Android's while it is open. */
    val ownStatusBar: Boolean get() = _ownStatusBar.value
    fun setOwnStatusBar(v: Boolean) { _ownStatusBar.value = v; sp.edit().putBoolean(KEY_OWN_STATUS_BAR, v).apply() }

    /** Which icons the launcher's status bar shows (see StatusBar.kt for the names). */
    val statusItems: Set<String> get() = _statusItems.value
    fun toggleStatusItem(id: String) {
        _statusItems.value = if (id in statusItems) statusItems - id else statusItems + id
        sp.edit().putStringSet(KEY_STATUS_ITEMS, _statusItems.value).apply()
    }

    /** Clean refresh: 0 off, 1 after other apps, 2 + every 10 page turns, 3 + every 5 page turns. */
    val refreshMode: Int get() = _refreshMode.value
    fun setRefreshMode(v: Int) { _refreshMode.value = v; sp.edit().putInt(KEY_REFRESH_MODE, v).apply() }

    // ---- look --------------------------------------------------------------

    val inverted: Boolean get() = _inverted.value
    /** 0 = small, 1 = medium, 2 = large */
    val textSize: Int get() = _textSize.value
    /** "mono", "sans", "serif", "hand" or "custom" */
    val font: String get() = _font.value
    /** Changes whenever a new custom font file is loaded, so it gets re-read. */
    val fontStamp: Long get() = _fontStamp.value
    /** Font for small text: same choices as [font]. */
    val fontSmall: String get() = _fontSmall.value
    val pixelSnap: Boolean get() = _pixelSnap.value
    /** "pixel", "type" or "cards" */
    val clock: String get() = _clock.value

    fun setInverted(v: Boolean) { _inverted.value = v; sp.edit().putBoolean(KEY_INVERTED, v).apply() }
    fun setTextSize(v: Int) { _textSize.value = v; sp.edit().putInt(KEY_TEXT, v).apply() }
    fun setFont(v: String) { _font.value = v; sp.edit().putString(KEY_FONT, v).apply() }
    fun setFontSmall(v: String) { _fontSmall.value = v; sp.edit().putString(KEY_FONT_SMALL, v).apply() }
    fun setPixelSnap(v: Boolean) { _pixelSnap.value = v; sp.edit().putBoolean(KEY_PIXEL_SNAP, v).apply() }
    fun bumpFontStamp() {
        val v = System.currentTimeMillis()
        _fontStamp.value = v
        sp.edit().putLong(KEY_FONT_STAMP, v).apply()
    }
    fun setClock(v: String) { _clock.value = v; sp.edit().putString(KEY_CLOCK, v).apply() }

    // ---- behaviour ---------------------------------------------------------

    val cleanRefresh: Boolean get() = _cleanRefresh.value
    val showWeather: Boolean get() = _showWeather.value
    val showCalendar: Boolean get() = _showCalendar.value
    val volumePaging: Boolean get() = _volumePaging.value

    fun setCleanRefresh(v: Boolean) { _cleanRefresh.value = v; sp.edit().putBoolean(KEY_REFRESH, v).apply() }
    fun setShowWeather(v: Boolean) { _showWeather.value = v; sp.edit().putBoolean(KEY_SHOW_WEATHER, v).apply() }
    fun setShowCalendar(v: Boolean) { _showCalendar.value = v; sp.edit().putBoolean(KEY_SHOW_CALENDAR, v).apply() }
    fun setVolumePaging(v: Boolean) { _volumePaging.value = v; sp.edit().putBoolean(KEY_VOLUME_PAGING, v).apply() }

    // ---- weather -----------------------------------------------------------

    val place: Place? get() = _place.value

    fun setPlace(p: Place) {
        _place.value = p
        sp.edit()
            .putString(KEY_PLACE_NAME, p.name)
            .putString(KEY_PLACE_LAT, p.lat.toString())
            .putString(KEY_PLACE_LON, p.lon.toString())
            .remove(KEY_WEATHER_RAW)
            .putLong(KEY_WEATHER_AT, 0L)
            .apply()
    }

    val weatherRaw: String? get() = sp.getString(KEY_WEATHER_RAW, null)
    val weatherFetchedAt: Long get() = sp.getLong(KEY_WEATHER_AT, 0L)

    fun saveWeather(raw: String, at: Long) =
        sp.edit().putString(KEY_WEATHER_RAW, raw).putLong(KEY_WEATHER_AT, at).apply()

    private fun loadPlace(): Place? {
        val name = sp.getString(KEY_PLACE_NAME, null) ?: return null
        val lat = sp.getString(KEY_PLACE_LAT, null)?.toDoubleOrNull() ?: return null
        val lon = sp.getString(KEY_PLACE_LON, null)?.toDoubleOrNull() ?: return null
        return Place(name, lat, lon)
    }

    private fun sectionKey(s: Section) = "app_${s.id}"

    private companion object {
        const val KEY_PINNED = "pinned"
        const val KEY_SECTIONS_ADDED = "sectionsAdded"
        const val KEY_INVERTED = "inverted"
        const val KEY_TEXT = "textSize"
        const val KEY_REFRESH = "cleanRefresh"
        const val KEY_FONT = "font"
        const val KEY_CLOCK_APP = "clockApp"
        const val KEY_CHARGING_CLOCK = "chargingClock"
        const val KEY_OWN_STATUS_BAR = "ownStatusBar"
        const val KEY_STATUS_ITEMS = "statusItems"
        const val KEY_REFRESH_MODE = "refreshMode"
        const val KEY_FONT_SMALL = "fontSmall"
        const val KEY_PIXEL_SNAP = "pixelSnap"
        const val KEY_SEEN_SECTIONS = "seenSections"
        const val KEY_FONT_STAMP = "fontStamp"
        const val KEY_CLOCK = "clock"
        const val KEY_SHOW_WEATHER = "showWeather"
        const val KEY_SHOW_CALENDAR = "showCalendar"
        const val KEY_VOLUME_PAGING = "volumePaging"
        const val KEY_PLACE_NAME = "placeName"
        const val KEY_PLACE_LAT = "placeLat"
        const val KEY_PLACE_LON = "placeLon"
        const val KEY_WEATHER_RAW = "weatherRaw"
        const val KEY_WEATHER_AT = "weatherAt"
    }
}
