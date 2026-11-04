package com.hmj.twotone.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hmj.twotone.Prefs

private class Setting(val label: String, val trailing: String, val action: () -> Unit)

@Composable
fun SettingsScreen(
    prefs: Prefs,
    onEditHomeList: () -> Unit,
    onSections: () -> Unit,
    onStatusBar: () -> Unit,
    onEink: () -> Unit,
    onMusicSettings: () -> Unit,
    onFonts: (FontSlot) -> Unit,
    onWeatherPlace: () -> Unit,

    onChooseHomeApp: () -> Unit,
    onAndroidSettings: () -> Unit,
    onBack: () -> Unit
) {
    val sizes = TEXT_SIZES.map { it.first }
    val clock = ClockStyle.of(prefs.clock)
    fun onOff(b: Boolean) = if (b) "on" else "off"

    val rows = listOf(
        Setting("home list", "▸", onEditHomeList),
        Setting("sections", "▸", onSections),
        Setting("music", "▸", onMusicSettings),
        Setting("status bar", if (prefs.ownStatusBar) "launcher's" else "android's", onStatusBar),
        Setting("e-ink", "▸", onEink),
        Setting("font", fontLabel(prefs.font)) { onFonts(FontSlot.LARGE) },
        Setting("your font file is a pixel font", onOff(prefs.pixelSnap)) { prefs.setPixelSnap(!prefs.pixelSnap) },
        Setting("text size", sizes[prefs.textSize.coerceIn(0, sizes.lastIndex)]) { prefs.setTextSize((prefs.textSize + 1) % sizes.size) },
        Setting("clock", clock.label) {
            val all = ClockStyle.entries
            prefs.setClock(all[(all.indexOf(clock) + 1) % all.size].id)
        },
        Setting("charging clock", listOf("off", "at night", "always")[prefs.chargingClock.coerceIn(0, 2)]) {
            prefs.setChargingClock((prefs.chargingClock + 1) % 3)
        },
        Setting("colours", if (prefs.inverted) "white on black" else "black on white") { prefs.setInverted(!prefs.inverted) },
        Setting("weather on home", onOff(prefs.showWeather)) { prefs.setShowWeather(!prefs.showWeather) },
        Setting("calendar on home", onOff(prefs.showCalendar)) { prefs.setShowCalendar(!prefs.showCalendar) },
        Setting("weather place", prefs.place?.name?.substringBefore(",") ?: "none", onWeatherPlace),
        Setting("volume keys turn pages", onOff(prefs.volumePaging)) { prefs.setVolumePaging(!prefs.volumePaging) },
        Setting("choose home screen app", "▸", onChooseHomeApp),
        Setting("android settings", "▸", onAndroidSettings)
    )

    PageColumn {
        PageHeader("settings", onBack)
        PagedList(rows, Modifier.weight(1f)) { s -> ListRow(s.label, trailing = bracket(s.trailing), onPress = s.action) }
    }
}
