package com.hmj.twotone.notify

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** One notification, reduced to words. */
data class Note(
    val key: String,
    val app: String,
    val title: String,
    val text: String,
    val time: Long,
    val clearable: Boolean,
    val open: PendingIntent?,
    val autoCancel: Boolean
)

/** The phone's current notifications, kept up to date by [NotificationReader]. */
object Notifications {
    var list by mutableStateOf<List<Note>>(emptyList())
        internal set
    internal var service: NotificationReader? = null

    /** Whether you've switched on Notification access for this app in Android settings. */
    fun hasAccess(ctx: Context): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners").orEmpty()
        val me = ComponentName(ctx, NotificationReader::class.java).flattenToString()
        return enabled.split(':').any { it == me }
    }

    /** Opens the app the notification came from. */
    fun open(n: Note) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val opts = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                n.open?.send(null, 0, null, null, null, null, opts.toBundle())
            } else n.open?.send()
        } catch (e: Exception) { }
        if (n.autoCancel) dismiss(n)
    }

    fun dismiss(n: Note) {
        try { service?.cancelNotification(n.key) } catch (e: Exception) { }
    }

    fun clearAll() {
        try { service?.cancelAllNotifications() } catch (e: Exception) { }
    }
}

/**
 * Android hands notifications to this once Notification access is on.
 * It leaves out things that aren't really messages: ongoing ones (music players,
 * downloads, navigation), silent group summaries, and this launcher's own.
 */
class NotificationReader : NotificationListenerService() {
    override fun onListenerConnected() { Notifications.service = this; reload() }
    override fun onListenerDisconnected() { Notifications.service = null; Notifications.list = emptyList() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) = reload()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = reload()

    private fun reload() {
        val all = try { activeNotifications?.toList().orEmpty() } catch (e: Exception) { emptyList() }
        val pm = packageManager
        Notifications.list = all
            .filter { it.packageName != packageName && !it.isOngoing }
            .filter { (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 || all.count { o -> o.groupKey == it.groupKey } == 1 }
            .map { sbn ->
                val ex = sbn.notification.extras
                val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
                val app = try { pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString() } catch (e: Exception) { sbn.packageName }
                Note(
                    key = sbn.key,
                    app = app,
                    title = title.ifBlank { app },
                    text = text.replace(Regex("\\s+"), " ").trim(),
                    time = sbn.postTime,
                    clearable = sbn.isClearable,
                    open = sbn.notification.contentIntent,
                    autoCancel = (sbn.notification.flags and Notification.FLAG_AUTO_CANCEL) != 0
                )
            }
            .filter { it.title.isNotBlank() || it.text.isNotBlank() }
            .sortedByDescending { it.time }
    }
}
