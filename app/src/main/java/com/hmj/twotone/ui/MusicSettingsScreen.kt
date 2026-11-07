package com.hmj.twotone.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.hmj.twotone.music.Music
import com.hmj.twotone.music.MusicSettings
import com.hmj.twotone.music.Repeat

private class MusicSetting(val label: String, val trailing: String, val action: () -> Unit)

/** Click sound, vibration, where the music is, repeat, sleep timer. */
@Composable
fun MusicSettingsScreen(onFolder: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    LaunchedEffect(Unit) { MusicSettings.load(ctx) }
    fun onOff(b: Boolean) = if (b) "on" else "off"
    var vibrationReport by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val sleepSteps = listOf(0, 15, 30, 45, 60, 90)
    val sleepNow = if (Music.sleepAt > System.currentTimeMillis())
        "${((Music.sleepAt - System.currentTimeMillis()) / 60_000L) + 1} min" else "off"

    val rows = listOf(
        MusicSetting("click sound", onOff(MusicSettings.clickSound)) {
            MusicSettings.setClickSound(ctx, !MusicSettings.clickSound); MusicSettings.tick(view)
        },
        MusicSetting("vibrate on click", onOff(MusicSettings.haptics)) {
            MusicSettings.setHaptics(ctx, !MusicSettings.haptics); MusicSettings.tick(view)
        },
        MusicSetting("test vibration", "") { vibrationReport = MusicSettings.testVibration(ctx) },
        MusicSetting("music folder", MusicSettings.folder?.trimEnd('/')?.substringAfterLast('/')?.lowercase() ?: "all", onFolder),
        MusicSetting("shuffle", onOff(MusicSettings.shuffle)) { Music.setShuffle(ctx, !MusicSettings.shuffle) },
        MusicSetting("repeat", MusicSettings.repeat.label) {
            val all = Repeat.entries
            MusicSettings.setRepeat(ctx, all[(all.indexOf(MusicSettings.repeat) + 1) % all.size])
        },
        MusicSetting("sleep timer", sleepNow) {
            // Step through off → 15 → 30 → 45 → 60 → 90 → off
            val current = if (Music.sleepAt > System.currentTimeMillis())
                sleepSteps.firstOrNull { it * 60_000L >= Music.sleepAt - System.currentTimeMillis() } ?: 90 else 0
            val next = sleepSteps[(sleepSteps.indexOf(current) + 1) % sleepSteps.size]
            Music.setSleepTimer(next)
        }
    )
    PageColumn {
        PageHeader("music settings", onBack)
        vibrationReport?.let { Hint(it) }
        PagedList(rows, Modifier.weight(1f)) { r -> ListRow(r.label, trailing = bracket(r.trailing), onPress = r.action) }
        FooterLine("tap a row to change it")
    }
}

/** Choose the folder music comes from: every folder that holds songs, with counts. */
@Composable
fun MusicFolderScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    LaunchedEffect(Unit) { MusicSettings.load(ctx); Music.load(ctx) }
    // Each folder, plus every folder above it, so "Music/" covers "Music/Geese/".
    val counts = Music.tracks.flatMap { t ->
        val parts = t.folder.trimEnd('/').split('/').filter { it.isNotBlank() }
        (1..parts.size).map { n -> parts.take(n).joinToString("/") + "/" }
    }.groupingBy { it }.eachCount().toSortedMap(String.CASE_INSENSITIVE_ORDER)
    val choices: List<String?> = listOf<String?>(null) + counts.keys

    PageColumn {
        PageHeader("music folder", onBack)
        PagedList(choices, Modifier.weight(1f)) { f ->
            if (f == null) {
                ListRow("all music", trailing = if (MusicSettings.folder == null) "• ${Music.tracks.size}" else "${Music.tracks.size}") {
                    MusicSettings.setFolder(ctx, null); onBack()
                }
            } else {
                val depth = f.count { it == '/' } - 1
                ListRow(
                    "  ".repeat(depth) + f.trimEnd('/').substringAfterLast('/').lowercase(),
                    trailing = (if (MusicSettings.folder == f) "• " else "") + "${counts[f]}"
                ) { MusicSettings.setFolder(ctx, f); onBack() }
            }
        }
        FooterLine("only songs in the chosen folder are shown")
    }
}
