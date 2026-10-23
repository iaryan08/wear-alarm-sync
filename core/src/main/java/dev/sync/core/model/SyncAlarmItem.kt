package dev.sync.core.model

import kotlinx.serialization.Serializable

/**
 * Canonical representation of a single alarm shared between phone and watch.
 *
 * The [id] is derived as a stable hash of (hour, minute, daysOfWeek) so both sides
 * can independently compute the same key — no UUID exchange needed.
 */
@Serializable
data class SyncAlarmItem(
    /** Stable identity: SHA-1 hash of "$hour:$minute:${daysOfWeek.sorted()}". */
    val id: String,
    /** Hour in 24-hour format (0–23). */
    val hour: Int,
    /** Minute (0–59). */
    val minute: Int,
    /**
     * Days of week using [java.util.Calendar] constants:
     * Calendar.MONDAY (2) … Calendar.SUNDAY (1).
     * Empty list means one-shot (non-repeating) alarm.
     */
    val daysOfWeek: List<Int> = emptyList(),
    /** User-visible label, e.g. "Work", "Exam". */
    val label: String = "",
    /** Whether the alarm is currently enabled. */
    val isEnabled: Boolean = true,
    /**
     * Wall-clock epoch millis of the most recent change.
     * Used to resolve conflicts: the higher timestamp wins.
     */
    val lastModified: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Derives a deterministic, collision-resistant ID string.
         * Stable across devices; does not depend on system alarm IDs.
         */
        fun buildId(hour: Int, minute: Int, daysOfWeek: List<Int>): String {
            val key = "$hour:$minute:${daysOfWeek.sorted().joinToString(",")}"
            return key.hashCode().toUInt().toString(16).padStart(8, '0')
        }
    }
}
