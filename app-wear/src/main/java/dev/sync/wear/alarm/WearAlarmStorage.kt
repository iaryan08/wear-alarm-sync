package dev.sync.wear.alarm

import android.content.Context
import dev.sync.core.model.SyncAlarmItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistent local storage for alarms synchronized to the watch.
 * Backed by SharedPreferences so active schedules survive watch reboots.
 */
object WearAlarmStorage {

    private const val PREFS_NAME = "wear_alarm_sync_prefs"
    private const val KEY_ALARMS = "synced_alarms_json"

    private val json = Json { ignoreUnknownKeys = true }

    fun getAlarms(context: Context): List<SyncAlarmItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ALARMS, null) ?: return emptyList()
        return try {
            json.decodeFromString<List<SyncAlarmItem>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveAlarms(context: Context, alarms: List<SyncAlarmItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serialized = json.encodeToString(alarms)
        prefs.edit().putString(KEY_ALARMS, serialized).apply()
    }

    fun clear(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_ALARMS).apply()
    }
}
