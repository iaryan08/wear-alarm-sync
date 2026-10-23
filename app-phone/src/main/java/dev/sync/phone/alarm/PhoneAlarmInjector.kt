package dev.sync.phone.alarm

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import dev.sync.core.model.SyncAlarmItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Creates and enables alarms on the phone using the **AOSP `AlarmClock.ACTION_SET_ALARM` API**.
 *
 * ## Why this is Zero-Shizuku
 * `ACTION_SET_ALARM` with `EXTRA_SKIP_UI = true` is a standard Android system intent
 * (API 19+) that directly instructs the system alarm app to create an alarm silently,
 * without any user interface. No root, no Shizuku, no reflection.
 *
 * ## Repeat Alarms
 * Days of week are provided via `AlarmClock.EXTRA_DAYS` as an `ArrayList<Int>` using
 * [Calendar] day constants (Sunday = 1, Monday = 2, … Saturday = 7).
 *
 * ## Upcoming-Alarm Dismissal
 * [dismissUpcoming] fires `AlarmClock.ACTION_DISMISS_ALARM` to pre-cancel a future alarm
 * before it rings (requires AlarmClock to resolve the match by time and repeat days).
 */
class PhoneAlarmInjector(private val context: Context) {

    /**
     * Creates or updates an alarm silently based on [alarm].
     * If an alarm already exists at the same time and repeat days, most OEM clock apps
     * will deduplicate it; they will not create a duplicate.
     */
    suspend fun setAlarm(alarm: SyncAlarmItem) = withContext(Dispatchers.Main) {
        // ACTION_SET_ALARM must be sent from a component with a window (context), not a
        // background service context. We flag FLAG_ACTIVITY_NEW_TASK for service callers.
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR,    alarm.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, alarm.minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)

            if (alarm.label.isNotBlank()) {
                putExtra(AlarmClock.EXTRA_MESSAGE, alarm.label)
            }

            if (alarm.daysOfWeek.isNotEmpty()) {
                // EXTRA_DAYS expects ArrayList<Int> with Calendar day constants
                putExtra(AlarmClock.EXTRA_DAYS, ArrayList(alarm.daysOfWeek))
            }

            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
            Log.i(TAG, "ACTION_SET_ALARM fired: ${alarm.hour}:${alarm.minute} " +
                    "days=${alarm.daysOfWeek} label='${alarm.label}'")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fire ACTION_SET_ALARM for alarm ${alarm.id}", e)
            throw e
        }
    }

    /**
     * Pre-dismisses (cancels before ringing) an alarm using [AlarmClock.ACTION_DISMISS_ALARM].
     * Matches by HOUR, MINUTE, and optionally DAYS.
     *
     * Note: Not all OEM clock apps handle ACTION_DISMISS_ALARM for repeating alarms;
     * on unsupported OEMs this is a no-op with no error thrown.
     */
    suspend fun dismissUpcoming(alarm: SyncAlarmItem) = withContext(Dispatchers.Main) {
        val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR,    alarm.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, alarm.minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (alarm.daysOfWeek.isNotEmpty()) {
                putExtra(AlarmClock.EXTRA_DAYS, ArrayList(alarm.daysOfWeek))
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
            Log.i(TAG, "ACTION_DISMISS_ALARM fired: ${alarm.hour}:${alarm.minute}")
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_DISMISS_ALARM failed (OEM may not support it): ${e.message}")
        }
    }

    companion object {
        private const val TAG = "PhoneAlarmInjector"
    }
}
