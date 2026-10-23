package dev.sync.phone.alarm

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import dev.sync.core.model.SyncAlarmItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the next upcoming alarm from the phone's [AlarmManager].
 *
 * ## Strategy — two-tier read
 * 1. **Primary:** [AlarmManager.getNextAlarmClock] — available since API 21, returns the
 *    next alarm's trigger time regardless of OEM.  We derive hour/minute from it.
 * 2. **Fallback (DUMP permission):** If `android.permission.DUMP` has been granted via ADB,
 *    `dumpsys alarm` gives us the full repeating alarm table including day-of-week schedules.
 *    Parsing is kept conservative — if it fails we log a warning and return primary data.
 *
 * Note: [AlarmManager.getNextAlarmClock] returns `null` if no alarm is scheduled.
 */
class PhoneAlarmReader(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /**
     * Returns a list of upcoming alarms.
     *
     * Currently returns at most 1 item (the next alarm clock). The [DUMP]-based path
     * (full table) is implemented in [readAlarmsDump] and called when DUMP is granted.
     */
    suspend fun readNextAlarms(): List<SyncAlarmItem> = withContext(Dispatchers.IO) {
        val nextAlarmInfo = alarmManager.nextAlarmClock
        if (nextAlarmInfo == null) {
            Log.d(TAG, "No next alarm clock set")
            return@withContext emptyList()
        }

        // Convert trigger epoch millis → local hour/minute
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = nextAlarmInfo.triggerTime
        }
        val hour   = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)

        Log.d(TAG, "Next alarm: $hour:$minute (from AlarmManager.getNextAlarmClock)")

        val id = SyncAlarmItem.buildId(hour, minute, emptyList())
        listOf(
            SyncAlarmItem(
                id = id,
                hour = hour,
                minute = minute,
                daysOfWeek = emptyList(), // AlarmManager primary doesn't expose repeat days
                label = "",
                isEnabled = true
            )
        )
    }

    /**
     * Attempts to read the full alarm table via `dumpsys alarm`.
     * Requires `android.permission.DUMP` (granted once via ADB; persists across reboots).
     *
     * Returns `null` if DUMP is not granted or parsing fails.
     */
    suspend fun readAlarmsDump(): List<SyncAlarmItem>? = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("dumpsys", "alarm"))
            val output  = process.inputStream.bufferedReader().readText()
            process.waitFor()

            // Parse registered clock package alarm table entries (AOSP and OEM deskclock variants).
            // Extracts relative trigger offsets from the alarm queue definition.
            val results = mutableListOf<SyncAlarmItem>()
            val alarmLineRegex = Regex("""when=\+(\d+)h(\d+)m""")

            output.lines().forEach { line ->
                if (line.contains("deskclock", ignoreCase = true) ||
                    line.contains("walarm*:alarm", ignoreCase = true)) {

                    val match = alarmLineRegex.find(line) ?: return@forEach
                    val relativeHours   = match.groupValues[1].toIntOrNull() ?: return@forEach
                    val relativeMinutes = match.groupValues[2].toIntOrNull() ?: return@forEach

                    val now = java.util.Calendar.getInstance()
                    now.add(java.util.Calendar.HOUR_OF_DAY, relativeHours)
                    now.add(java.util.Calendar.MINUTE, relativeMinutes)

                    val h = now.get(java.util.Calendar.HOUR_OF_DAY)
                    val m = now.get(java.util.Calendar.MINUTE)
                    val id = SyncAlarmItem.buildId(h, m, emptyList())

                    if (results.none { it.id == id }) {
                        results += SyncAlarmItem(id = id, hour = h, minute = m)
                    }
                }
            }

            if (results.isNotEmpty()) {
                Log.d(TAG, "DUMP parsed ${results.size} alarm(s)")
                results
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "DUMP read failed (DUMP permission may not be granted): ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "PhoneAlarmReader"
    }
}
