package dev.sync.core.engine

import dev.sync.core.model.SyncAlarmItem

/**
 * Computes the minimal diff between a local alarm set and a remote alarm set,
 * producing the smallest possible set of [SyncAlarmItem]s to upsert or delete.
 *
 * Used on both phone and watch to avoid redundant `ACTION_SET_ALARM` calls.
 *
 * ## Conflict Resolution (Last-Modified-Wins)
 * When both sides have an alarm with the same [SyncAlarmItem.id] but different state,
 * the alarm with the higher [SyncAlarmItem.lastModified] timestamp wins.
 */
object AlarmDiffCalculator {

    /**
     * Result of diffing local vs remote alarm sets.
     *
     * @property toUpsert Alarms that must be created or updated on the local device.
     * @property toDelete Alarm IDs that must be deleted from the local device.
     */
    data class DiffResult(
        val toUpsert: List<SyncAlarmItem>,
        val toDelete: List<String>
    ) {
        val isEmpty: Boolean get() = toUpsert.isEmpty() && toDelete.isEmpty()
    }

    /**
     * Computes what changes the local device needs to apply to converge with [remote].
     *
     * @param local  The current set of alarms on the local device.
     * @param remote The authoritative set of alarms from the peer device (FULL_SYNC payload).
     * @return A [DiffResult] describing the minimal changes.
     */
    fun diff(local: List<SyncAlarmItem>, remote: List<SyncAlarmItem>): DiffResult {
        val localById = local.associateBy { it.id }
        val remoteById = remote.associateBy { it.id }

        val toUpsert = mutableListOf<SyncAlarmItem>()
        val toDelete = mutableListOf<String>()

        // Check every remote alarm against what we have locally
        for ((id, remoteAlarm) in remoteById) {
            val localAlarm = localById[id]
            when {
                // New alarm from remote — must create it locally
                localAlarm == null -> toUpsert.add(remoteAlarm)

                // Both have it; remote is newer — update locally
                remoteAlarm.lastModified > localAlarm.lastModified -> toUpsert.add(remoteAlarm)

                // Local is same age or newer — no action needed
                else -> { /* no-op */ }
            }
        }

        // Alarms present locally but absent from remote → remote deleted them
        for (id in localById.keys) {
            if (id !in remoteById) {
                toDelete.add(id)
            }
        }

        return DiffResult(toUpsert = toUpsert, toDelete = toDelete)
    }

    /**
     * Merges a single incoming [remote] alarm into the [local] set.
     * Used for UPSERT actions (not full syncs).
     *
     * @return `true` if the alarm should be applied (remote newer or new);
     *         `false` if local is already at the same version or newer (skip).
     */
    fun shouldApplyUpsert(local: List<SyncAlarmItem>, remote: SyncAlarmItem): Boolean {
        val existing = local.find { it.id == remote.id } ?: return true
        return remote.lastModified > existing.lastModified
    }
}
