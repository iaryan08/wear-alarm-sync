package dev.sync.wear.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.sync.core.model.SyncAlarmItem
import dev.sync.wear.ui.WearMainActivity
import java.util.Calendar

/**
 * Headless, silent alarm scheduling engine for Wear OS.
 *
 * Uses Android's native [AlarmManager.setAlarmClock] directly on the watch.
 *
 * ## Key Guarantees
 * - **100% Silent:** Zero UI popups; never launches external clock apps.
 * - **Exact CRUD:** Reconciles incoming snapshot arrays against local storage.
 * - **Zero Duplicates:** Deterministic requestCodes based on alarm ID hash.
 * - **System Indicators:** Native status bar alarm icon, ambient mode, and AOD update automatically.
 */
class WearAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /**
     * Schedules a single alarm using native [AlarmManager.setAlarmClock].
     */
    fun schedule(alarm: SyncAlarmItem) {
        val triggerEpoch = computeNextTriggerTime(alarm.hour, alarm.minute)

        val ringIntent = Intent(context, WearAlarmReceiver::class.java).apply {
            action = WearAlarmReceiver.ACTION_ALARM_TRIGGER
            putExtra(WearAlarmReceiver.EXTRA_HOUR, alarm.hour)
            putExtra(WearAlarmReceiver.EXTRA_MINUTE, alarm.minute)
            putExtra(WearAlarmReceiver.EXTRA_LABEL, alarm.label)
            putExtra(WearAlarmReceiver.EXTRA_ALARM_ID, alarm.id)
        }

        val ringPendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id.hashCode(),
            ringIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, WearMainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            alarm.id.hashCode(),
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerEpoch, showPendingIntent)
        alarmManager.setAlarmClock(alarmClockInfo, ringPendingIntent)

        Log.i(TAG, "Scheduled alarm silently on watch: ${alarm.hour}:${alarm.minute} (id=${alarm.id}, label='${alarm.label}')")
    }

    /**
     * Cancels an alarm silently by its deterministic ID.
     */
    fun cancel(alarmId: String) {
        val ringIntent = Intent(context, WearAlarmReceiver::class.java).apply {
            action = WearAlarmReceiver.ACTION_ALARM_TRIGGER
        }

        val ringPendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.hashCode(),
            ringIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (ringPendingIntent != null) {
            alarmManager.cancel(ringPendingIntent)
            ringPendingIntent.cancel()
            Log.i(TAG, "Canceled alarm silently on watch: id=$alarmId")
        }
    }

    /**
     * Reconciles the incoming authoritative snapshot against currently scheduled alarms.
     * Cancels stale/disabled alarms and schedules/updates active ones.
     */
    fun reconcileSnapshot(incomingAlarms: List<SyncAlarmItem>) {
        val currentAlarms = WearAlarmStorage.getAlarms(context)
        val currentMap = currentAlarms.associateBy { it.id }
        val incomingMap = incomingAlarms.filter { it.isEnabled }.associateBy { it.id }

        // 1. Cancel removed or disabled alarms
        val staleIds = currentMap.keys - incomingMap.keys
        for (staleId in staleIds) {
            cancel(staleId)
        }

        // 2. Schedule or update incoming alarms
        for (alarm in incomingMap.values) {
            schedule(alarm)
        }

        // 3. Save new state
        WearAlarmStorage.saveAlarms(context, incomingMap.values.toList())
        Log.i(TAG, "Reconciled snapshot: ${incomingMap.size} active, ${staleIds.size} canceled")
    }

    private fun computeNextTriggerTime(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (target.before(now)) {
            target.add(Calendar.DAY_OF_YEAR, 1)
        }
        return target.timeInMillis
    }

    companion object {
        private const val TAG = "WearAlarmScheduler"
    }
}
