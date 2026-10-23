package dev.sync.wear.service

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import dev.sync.core.model.AlarmSyncMessage
import dev.sync.core.model.RemoteAlarmEventMessage
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Watch-side Wearable MessageClient transport — mirrors [dev.sync.phone.service.WearableTransport].
 */
object WearableTransport {

    private const val TAG = "WearTransport"
    const val PATH_ALARM_SYNC         = "/alarm/sync"
    const val PATH_ALARM_REMOTE_EVENT = "/alarm/remote-event"

    private val json = Json { encodeDefaults = true }

    suspend fun sendAlarmSync(context: Context, message: AlarmSyncMessage) {
        val payload = json.encodeToString(message).toByteArray(Charsets.UTF_8)
        sendToAllNodes(context, PATH_ALARM_SYNC, payload)
    }

    suspend fun sendRemoteAlarmEvent(context: Context, message: RemoteAlarmEventMessage) {
        val payload = json.encodeToString(message).toByteArray(Charsets.UTF_8)
        sendToAllNodes(context, PATH_ALARM_REMOTE_EVENT, payload)
    }

    private suspend fun sendToAllNodes(context: Context, path: String, payload: ByteArray) {
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            if (nodes.isEmpty()) {
                Log.w(TAG, "No connected phone nodes — message not sent ($path)")
                return
            }
            val client = Wearable.getMessageClient(context)
            nodes.forEach { node ->
                client.sendMessage(node.id, path, payload).await()
                Log.d(TAG, "Sent ${payload.size}B to ${node.displayName} on $path")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send Wearable message on $path", e)
        }
    }
}
