package dev.sync.wear.alarm

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import dev.sync.core.model.SyncAlarmItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Reads the next upcoming alarm from the Wear OS [AlarmManager].
 * Logic mirrors [dev.sync.phone.alarm.PhoneAlarmReader] — both sides use
 * the same two-tier strategy (getNextAlarmClock + optional DUMP).
 */
class WearAlarmReader(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    suspend fun readNextAlarms(): List<SyncAlarmItem> = withContext(Dispatchers.IO) {
        val nextAlarmInfo = alarmManager.nextAlarmClock
        if (nextAlarmInfo == null) {
            Log.d(TAG, "No next alarm clock set on watch")
            return@withContext emptyList()
        }

        val calendar = Calendar.getInstance().apply {
            timeInMillis = nextAlarmInfo.triggerTime
        }
        val hour   = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        Log.d(TAG, "Watch next alarm: $hour:$minute")

        val id = SyncAlarmItem.buildId(hour, minute, emptyList())
        listOf(SyncAlarmItem(id = id, hour = hour, minute = minute))
    }

    companion object {
        private const val TAG = "WearAlarmReader"
    }
}
