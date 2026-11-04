package com.hmj.twotone.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Android wakes us here when an alarm or the timer is due. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AlarmStore.EXTRA_ID, -1)
        val label = intent.getStringExtra(AlarmStore.EXTRA_LABEL).orEmpty()
        val ring = intent.getIntExtra(AlarmStore.EXTRA_RING, RING_BOTH)
        AlarmStore.load(context)
        when (id) {
            AlarmStore.TIMER_ID -> AlarmStore.timerFinished(context)
            AlarmStore.SNOOZE_ID -> Unit
            else -> AlarmStore.afterRinging(context, id)
        }
        AlarmRingService.start(context, if (id == AlarmStore.TIMER_ID) "time's up" else label.ifBlank { "alarm" }, ring)
    }
}

/** Alarms are forgotten by Android when the phone restarts; this sets them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.intent.action.MY_PACKAGE_REPLACED" -> AlarmStore.scheduleAll(context)
        }
    }
}
