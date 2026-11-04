package com.hmj.twotone

import android.content.Context
import android.content.Intent

/** One launchable app. [key] identifies it across restarts. */
data class AppEntry(val label: String, val pkg: String, val cls: String) {
    val key: String = "$pkg/$cls"
}

/** Every app that has an icon in a normal launcher, sorted by name. */
@Suppress("DEPRECATION")
fun loadApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .map {
            AppEntry(
                label = it.loadLabel(pm).toString(),
                pkg = it.activityInfo.packageName,
                cls = it.activityInfo.name
            )
        }
        .distinctBy { it.key }
        .sortedBy { it.label.lowercase() }
}
