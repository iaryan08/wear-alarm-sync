package dev.sync.core.engine

import dev.sync.core.model.SyncAlarmItem
import java.util.concurrent.ConcurrentHashMap

/**
 * Prevents infinite sync echo loops between phone and watch.
 *
 * ## Problem
 * Without suppression:
 * 1. Phone creates alarm → sends UPSERT to Watch.
 * 2. Watch applies alarm → its `NEXT_ALARM_CLOCK_CHANGED` fires → sends UPSERT back to Phone.
 * 3. Phone sees the "new" alarm → sends UPSERT to Watch again → infinite loop.
 *
 * ## Solution — 10-Second Remote-Origin Cache
 * When a device **receives and applies** a remote alarm, it records the alarm's ID with the
 * current timestamp. Any locally triggered sync event for that same alarm within the next
 * 10 seconds is classified as an echo and suppressed.
 *
 * After 10 seconds the suppression naturally expires: legitimate edits by the user go through.
 *
 * Thread-safe: backed by [ConcurrentHashMap]; safe for use from Broadcast Receivers,
 * NotificationListenerService threads, and Coroutine dispatchers simultaneously.
 */
class SyncEchoSuppressor {

    /**
     * Maps alarm ID → epoch millis when the remote sync was last applied.
     * Entries are lazily expired on [isEcho] check; no background sweeping needed.
     */
    private val remoteSyncCache = ConcurrentHashMap<String, Long>()

    /**
     * Returns `true` if [alarm] was recently applied from a remote source and should
     * therefore be suppressed from triggering an outbound sync.
     *
     * @param alarm The alarm about to be sent to the peer device.
     * @return `true` → suppress (it's an echo); `false` → allow sync.
     */
    fun isEcho(alarm: SyncAlarmItem): Boolean {
        val appliedAt = remoteSyncCache[alarm.id] ?: return false
        val withinCooldown = (System.currentTimeMillis() - appliedAt) < COOLDOWN_MS
        if (!withinCooldown) {
            // Lazy eviction: clean up expired entry to keep map small
            remoteSyncCache.remove(alarm.id)
        }
        return withinCooldown
    }

    /**
     * Records that [alarm] was applied from a remote source.
     * Must be called **after** successfully applying the alarm locally so that
     * the cooldown window starts at the moment of application.
     *
     * @param alarm The alarm received from the peer device and now applied locally.
     */
    fun recordRemoteSync(alarm: SyncAlarmItem) {
        remoteSyncCache[alarm.id] = System.currentTimeMillis()
    }

    /**
     * Clears all suppression entries.
     * Useful in tests or after a deliberate full-sync reset where echoes are expected.
     */
    fun reset() {
        remoteSyncCache.clear()
    }

    companion object {
        /** Echo suppression window. Must be > round-trip Wearable BLE latency (~80 ms)
         *  and < shortest realistic user edit interval (~30 s). 10 s is a safe midpoint. */
        const val COOLDOWN_MS = 10_000L
    }
}
