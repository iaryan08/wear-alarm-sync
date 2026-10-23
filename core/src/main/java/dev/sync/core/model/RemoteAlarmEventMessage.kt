package dev.sync.core.model

import kotlinx.serialization.Serializable

/**
 * Wire-protocol envelope for real-time ringing events and remote dismiss/snooze commands.
 *
 * Wearable Message path: `/alarm/remote-event`
 *
 * Lifecycle:
 * 1. Device A's alarm rings → its [NotificationListenerService] sends [RemoteAlarmEvent.RINGING_STARTED].
 * 2. Device B wakes its Ringing Alert UI showing Dismiss / Snooze buttons.
 * 3. User taps Dismiss → Device B sends [RemoteAlarmEvent.DISMISS].
 * 4. Device A's listener receives DISMISS → calls `dismissIntent.send()` → OEM clock silenced.
 *
 * Round-trip target: < 80 ms over BLE.
 */
@Serializable
data class RemoteAlarmEventMessage(
    /** The event being communicated. */
    val event: RemoteAlarmEvent,
    /**
     * Stable alarm ID matching [SyncAlarmItem.id].
     * Nullable for events where no specific alarm is identified (rare edge cases).
     */
    val alarmId: String? = null,
    /** Hour (0–23) — shown on the receiving device's Ringing Alert UI. */
    val hour: Int = 0,
    /** Minute (0–59) — shown on the receiving device's Ringing Alert UI. */
    val minute: Int = 0,
    /** Human-readable label, e.g. "Work". Empty string if none. */
    val label: String = "",
    /** Sender's wall-clock at event emission. */
    val timestamp: Long = System.currentTimeMillis()
)

/** Events exchanged for real-time alarm ringing control. */
enum class RemoteAlarmEvent {
    /** Alarm has started ringing on the sender's device. */
    RINGING_STARTED,
    /** Alarm stopped ringing on its own (timeout / already dismissed locally). */
    RINGING_STOPPED,
    /** Remote controller requests the sender dismiss its ringing alarm. */
    DISMISS,
    /** Remote controller requests the sender snooze its ringing alarm. */
    SNOOZE
}
