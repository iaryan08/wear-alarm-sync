package dev.sync.phone.alarm

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import dev.sync.core.model.SyncAlarmItem
import dev.sync.phone.notification.PhoneAlarmNotificationListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * Reads all active alarms from the phone using a robust multi-source approach:
 * 1. High-fidelity parsing of `dumpsys alarm` queue for OEM clock packages (OnePlus, Samsung, AOSP).
 * 2. System [AlarmManager.getNextAlarmClock] for the authoritative immediate next alarm.
 * 3. Enriches each alarm with its user-defined label from [PhoneAlarmNotificationListener].
 */
class PhoneAlarmReader(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /**
     * Reads all active scheduled alarms.
     */
    suspend fun readAllAlarms(): List<SyncAlarmItem> = withContext(Dispatchers.IO) {
        val alarmsMap = mutableMapOf<String, SyncAlarmItem>()

        // 1. Try dumpsys alarm table (captures ALL alarms, not just the next one)
        val dumpAlarms = readAlarmsDump()
        dumpAlarms?.forEach { alarm ->
            alarmsMap[alarm.id] = alarm
        }

        // 2. Also query AlarmManager.nextAlarmClock (ensures next alarm is always captured)
        val nextAlarmInfo = alarmManager.nextAlarmClock
        if (nextAlarmInfo != null) {
            val cal = Calendar.getInstance().apply { timeInMillis = nextAlarmInfo.triggerTime }
            val h = cal.get(Calendar.HOUR_OF_DAY)
            val m = cal.get(Calendar.MINUTE)
            val id = SyncAlarmItem.buildId(h, m, emptyList())
            val label = PhoneAlarmNotificationListener.getCachedLabel(h, m)

            alarmsMap[id] = SyncAlarmItem(
                id = id,
                hour = h,
                minute = m,
                label = label,
                isEnabled = true
            )
        }

        val results = alarmsMap.values.sortedWith(compareBy({ it.hour }, { it.minute }))
        Log.d(TAG, "readAllAlarms found ${results.size} active alarm(s): ${results.map { "${it.hour}:${it.minute}" }}")
        results
    }

    /**
     * Backwards-compatible alias for UI and receivers.
     */
    suspend fun readNextAlarms(): List<SyncAlarmItem> = readAllAlarms()

    /**
     * Parses all scheduled alarms from `dumpsys alarm` output.
     */
    private fun readAlarmsDump(): List<SyncAlarmItem>? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("dumpsys", "alarm"))
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()

            val results = mutableMapOf<String, SyncAlarmItem>()

            // Regex 1: Matches origWhen formatted as "2026-10-23 07:50:00.000" in clock sections
            val origWhenDateRegex = Regex("""origWhen=(\d{4}-\d{2}-\d{2}\s+(\d{2}):(\d{2}):\d{2})""")

            // Regex 2: Matches epoch timestamps like "origWhen 1792716780000"
            val origWhenEpochRegex = Regex("""origWhen\s+(\d{12,14})""")

            // Regex 3: Matches relative offsets like "when=+1h5m"
            val relativeRegex = Regex("""when=\+(\d+)h(\d+)m""")

            val lines = output.lines()
            for (i in lines.indices) {
                val line = lines[i]

                // Look for lines belonging to deskclock / clock apps
                val isClockLine = line.contains("deskclock", ignoreCase = true) ||
                        line.contains("clockpackage", ignoreCase = true) ||
                        line.contains("walarm*:change_state", ignoreCase = true) ||
                        line.contains("walarm*:indicator", ignoreCase = true)

                if (!isClockLine) continue

                // Check epoch on same line (e.g. Alarm{... origWhen 1792716780000 ... com.oneplus.deskclock})
                origWhenEpochRegex.find(line)?.let { match ->
                    val epoch = match.groupValues[1].toLongOrNull()
                    if (epoch != null && epoch > System.currentTimeMillis() - 60_000L) {
                        val cal = Calendar.getInstance().apply { timeInMillis = epoch }
                        val h = cal.get(Calendar.HOUR_OF_DAY)
                        val m = cal.get(Calendar.MINUTE)
                        val id = SyncAlarmItem.buildId(h, m, emptyList())
                        val label = PhoneAlarmNotificationListener.getCachedLabel(h, m)
                        results[id] = SyncAlarmItem(id = id, hour = h, minute = m, label = label, isEnabled = true)
                    }
                }

                // Check surrounding context lines (up to 4 lines after)
                val maxLookahead = minOf(lines.size, i + 5)
                for (j in i..maxLookahead - 1) {
                    val subLine = lines[j]

                    origWhenDateRegex.find(subLine)?.let { match ->
                        val h = match.groupValues[2].toIntOrNull()
                        val m = match.groupValues[3].toIntOrNull()
                        if (h != null && m != null) {
                            val id = SyncAlarmItem.buildId(h, m, emptyList())
                            val label = PhoneAlarmNotificationListener.getCachedLabel(h, m)
                            results[id] = SyncAlarmItem(id = id, hour = h, minute = m, label = label, isEnabled = true)
                        }
                    }

                    relativeRegex.find(subLine)?.let { match ->
                        val relH = match.groupValues[1].toIntOrNull() ?: 0
                        val relM = match.groupValues[2].toIntOrNull() ?: 0
                        val cal = Calendar.getInstance().apply {
                            add(Calendar.HOUR_OF_DAY, relH)
                            add(Calendar.MINUTE, relM)
                        }
                        val h = cal.get(Calendar.HOUR_OF_DAY)
                        val m = cal.get(Calendar.MINUTE)
                        val id = SyncAlarmItem.buildId(h, m, emptyList())
                        val label = PhoneAlarmNotificationListener.getCachedLabel(h, m)
                        results[id] = SyncAlarmItem(id = id, hour = h, minute = m, label = label, isEnabled = true)
                    }
                }
            }

            if (results.isNotEmpty()) {
                Log.d(TAG, "DUMP parsed ${results.size} distinct alarm(s)")
                results.values.toList()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "DUMP read error: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "PhoneAlarmReader"
    }
}
