package com.hmj.twotone

/**
 * The built-in parts of the launcher. Each one can be replaced by any
 * installed app in Settings > sections; the row keeps its name either way.
 */
enum class Section(val id: String, val label: String) {
    PHONE("phone", "phone"),
    MUSIC("music", "music"),
    NOTES("notes", "notes"),
    CALENDAR("calendar", "calendar"),
    WEATHER("weather", "weather"),
    COFFEE("coffee", "coffee"),
    /** Opened by tapping the home clock; not added to the home list by itself. */
    CLOCK("clock", "clock"),
    BOOKS("books", "books"),
    NEWS("news", "news"),
    TRAINS("trains", "trains");

    /** How this section is stored in the home list. */
    val key: String get() = "sec:$id"

    companion object {
        fun fromKey(key: String): Section? = entries.firstOrNull { it.key == key }
    }
}
