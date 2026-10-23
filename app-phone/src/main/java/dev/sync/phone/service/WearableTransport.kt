package dev.sync.phone.service

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import dev.sync.core.model.AlarmSyncMessage
import dev.sync.core.model.RemoteAlarmEventMessage
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Stateless transport helper — wraps the Wearable MessageClient for sending
 * alarm sync messages and remote alarm events to connected Watch nodes.
 *
 * ## Message Paths
 * | Path                  | Purpose                                         |
 * |-----------------------|-------------------------------------------------|
 * | `/alarm/sync`         | Alarm UPSERT / DELETE / FULL_SYNC payloads       |
 * | `/alarm/remote-event` | RINGING_STARTED / RINGING_STOPPED / DISMISS / SNOOZE |
 *
 * All calls are suspending; callers must invoke from a coroutine.
 */
object WearableTransport {

    private const val TAG = "WearableTransport"
    const val PATH_ALARM_SYNC         = "/alarm/sync"
    const val PATH_ALARM_REMOTE_EVENT = "/alarm/remote-event"

    private val json = Json { encodeDefaults = true }

    /**
     * Sends an [AlarmSyncMessage] to all connected Wear OS nodes.
     * Silently logs if no nodes are paired.
     */
    suspend fun sendAlarmSync(context: Context, message: AlarmSyncMessage) {
        val payload = json.encodeToString(message).toByteArray(Charsets.UTF_8)
        sendToAllNodes(context, PATH_ALARM_SYNC, payload)
    }

    /**
     * Sends a [RemoteAlarmEventMessage] to all connected Wear OS nodes.
     */
    suspend fun sendRemoteAlarmEvent(context: Context, message: RemoteAlarmEventMessage) {
        val payload = json.encodeToString(message).toByteArray(Charsets.UTF_8)
        sendToAllNodes(context, PATH_ALARM_REMOTE_EVENT, payload)
    }

    private suspend fun sendToAllNodes(context: Context, path: String, payload: ByteArray) {
        try {
            val nodeClient    = Wearable.getNodeClient(context)
            val connectedNodes = nodeClient.connectedNodes.await()

            if (connectedNodes.isEmpty()) {
                Log.w(TAG, "No connected Wear nodes — message not sent ($path)")
                return
            }

            val messageClient = Wearable.getMessageClient(context)
            connectedNodes.forEach { node ->
                messageClient.sendMessage(node.id, path, payload).await()
                Log.d(TAG, "Sent ${payload.size}B to ${node.displayName} on $path")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Wearable message on $path", e)
        }
    }
}
