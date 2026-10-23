package dev.sync.core.model

import kotlinx.serialization.Serializable

/**
 * Wire-protocol envelope for alarm sync operations sent over the Wearable MessageClient.
 *
 * Wearable Message path: `/alarm/sync`
 * Max payload size enforced by GMS: 100 KB (we target < 200 bytes for typical payloads).
 */
@Serializable
data class AlarmSyncMessage(
    /** Describes what the receiver should do with [alarms]. */
    val action: SyncAction,
    /**
     * Alarm items relevant to [action]:
     * - [SyncAction.UPSERT]: one or more alarms to create or update.
     * - [SyncAction.DELETE]: alarms to remove (only [SyncAlarmItem.id] is inspected).
     * - [SyncAction.FULL_SYNC]: the complete current set; receiver reconciles against it.
     */
    val alarms: List<SyncAlarmItem> = emptyList(),
    /** Sender's wall-clock time at message creation. Used for drift telemetry only. */
    val timestamp: Long = System.currentTimeMillis()
)

/** Actions that can be requested of the receiving device's alarm engine. */
enum class SyncAction {
    /** Create or update one or more alarms (last-modified-wins semantics). */
    UPSERT,
    /** Delete specific alarms by ID. */
    DELETE,
    /** Full reconcile: receiver replaces its alarm set with the provided list. */
    FULL_SYNC
}
