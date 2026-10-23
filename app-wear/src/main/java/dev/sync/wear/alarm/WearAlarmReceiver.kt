package dev.sync.wear.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

/**
 * BroadcastReceiver triggered by Android's native [android.app.AlarmManager] when a
 * synchronized alarm reaches its scheduled time on the watch.
 *
 * Posts the native Wear OS alarm notification with Dismiss and Snooze actions.
 */
class WearAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val hour = intent.getIntExtra(EXTRA_HOUR, 0)
        val minute = intent.getIntExtra(EXTRA_MINUTE, 0)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: ""
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: ""

        Log.i(TAG, "Watch alarm triggered: $hour:${minute.toString().padStart(2, '0')} ($label)")

        WearAlarmNotifier.showRingingNotification(
            context = context,
            hour = hour,
            minute = minute,
            label = label,
            alarmId = alarmId
        )
    }

    companion object {
        private const val TAG = "WearAlarmReceiver"
        const val ACTION_ALARM_TRIGGER = "dev.sync.alarm.ACTION_ALARM_TRIGGER"
        const val EXTRA_HOUR = "extra_hour"
        const val EXTRA_MINUTE = "extra_minute"
        const val EXTRA_LABEL = "extra_label"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
    }
}
