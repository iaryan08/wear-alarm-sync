package dev.sync.wear.alarm

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.util.Log
import dev.sync.core.model.SyncAlarmItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Injects alarm records into the native Wear OS clock using [AlarmClock.ACTION_SET_ALARM]
 * with [AlarmClock.EXTRA_SKIP_UI] set to `true`.
 *
 * Supported Wear OS clock providers process `EXTRA_SKIP_UI` and `EXTRA_DAYS` per the AOSP
 * [AlarmClock] API specifications without launching interactive interfaces.
 */
class WearAlarmInjector(private val context: Context) {

    suspend fun setAlarm(alarm: SyncAlarmItem) = withContext(Dispatchers.Main) {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR,    alarm.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, alarm.minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (alarm.label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, alarm.label)
            if (alarm.daysOfWeek.isNotEmpty()) putExtra(AlarmClock.EXTRA_DAYS, ArrayList(alarm.daysOfWeek))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            Log.i(TAG, "Watch ACTION_SET_ALARM: ${alarm.hour}:${alarm.minute}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set alarm on watch", e)
            throw e
        }
    }

    suspend fun dismissUpcoming(alarm: SyncAlarmItem) = withContext(Dispatchers.Main) {
        val intent = Intent(AlarmClock.ACTION_DISMISS_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR,    alarm.hour)
            putExtra(AlarmClock.EXTRA_MINUTES, alarm.minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (alarm.daysOfWeek.isNotEmpty()) putExtra(AlarmClock.EXTRA_DAYS, ArrayList(alarm.daysOfWeek))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
            Log.i(TAG, "Watch ACTION_DISMISS_ALARM: ${alarm.hour}:${alarm.minute}")
        } catch (e: Exception) {
            Log.w(TAG, "Dismiss failed on watch: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "WearAlarmInjector"
    }
}
