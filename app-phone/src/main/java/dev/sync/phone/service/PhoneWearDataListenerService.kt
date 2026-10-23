package dev.sync.phone.service

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dev.sync.core.model.AlarmSyncMessage
import dev.sync.core.model.RemoteAlarmEvent
import dev.sync.core.model.RemoteAlarmEventMessage
import dev.sync.core.model.SyncAction
import dev.sync.phone.alarm.PhoneAlarmInjector
import dev.sync.phone.notification.PhoneAlarmNotificationListener
import dev.sync.phone.receiver.PhoneAlarmChangeReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Passive [WearableListenerService] that Google Play Services wakes when a Wearable
 * message arrives from the Galaxy Watch 7.
 *
 * **CPU Impact:** Zero when idle.  Google Play Services Bluetooth stack handles the BLE
 * radio; this service is woken only when a packet arrives on a registered path prefix.
 *
 * ## Handled message paths
 * | Path                  | Action                                                  |
 * |-----------------------|---------------------------------------------------------|
 * | `/alarm/sync`         | Apply alarm UPSERT/DELETE/FULL_SYNC from Watch          |
 * | `/alarm/remote-event` | Execute DISMISS or SNOOZE for phone's ringing alarm     |
 */
class PhoneWearDataListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json  = Json { ignoreUnknownKeys = true }

    override fun onMessageReceived(event: MessageEvent) {
        Log.d(TAG, "Message received on path: ${event.path} (${event.data.size} bytes)")

        when {
            event.path.startsWith(WearableTransport.PATH_ALARM_SYNC) -> {
                scope.launch { handleSyncMessage(event.data) }
            }
            event.path.startsWith(WearableTransport.PATH_ALARM_REMOTE_EVENT) -> {
                scope.launch { handleRemoteEvent(event.data) }
            }
            else -> Log.w(TAG, "Unknown message path: ${event.path}")
        }
    }

    // ──────────────────────────────────────────────────────────────────────────────────
    // Sync message handler
    // ──────────────────────────────────────────────────────────────────────────────────

    private suspend fun handleSyncMessage(data: ByteArray) {
        val message = try {
            json.decodeFromString<AlarmSyncMessage>(String(data, Charsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse AlarmSyncMessage", e)
            return
        }

        Log.i(TAG, "AlarmSyncMessage received: ${message.action} (${message.alarms.size} alarms)")

        val injector = PhoneAlarmInjector(applicationContext)

        when (message.action) {
            SyncAction.UPSERT, SyncAction.FULL_SYNC -> {
                message.alarms.forEach { alarm ->
                    // Record echo suppression BEFORE calling setAlarm because setAlarm
                    // triggers NEXT_ALARM_CLOCK_CHANGED which fires the receiver.
                    PhoneAlarmChangeReceiver.echoes.recordRemoteSync(alarm)
                    injector.setAlarm(alarm)
                }
            }
            SyncAction.DELETE -> {
                message.alarms.forEach { alarm ->
                    PhoneAlarmChangeReceiver.echoes.recordRemoteSync(alarm)
                    injector.dismissUpcoming(alarm)
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────────────
    // Remote event handler (dismiss/snooze from Watch)
    // ──────────────────────────────────────────────────────────────────────────────────

    private fun handleRemoteEvent(data: ByteArray) {
        val message = try {
            json.decodeFromString<RemoteAlarmEventMessage>(String(data, Charsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse RemoteAlarmEventMessage", e)
            return
        }

        Log.i(TAG, "RemoteAlarmEvent received: ${message.event}")

        when (message.event) {
            RemoteAlarmEvent.DISMISS -> {
                val ok = PhoneAlarmNotificationListener.executeDismiss()
                Log.i(TAG, "Dismiss executed: $ok")
            }
            RemoteAlarmEvent.SNOOZE -> {
                val ok = PhoneAlarmNotificationListener.executeSnooze()
                Log.i(TAG, "Snooze executed: $ok")
            }
            else -> Log.d(TAG, "Ignoring event ${message.event} on phone side")
        }
    }

    companion object {
        private const val TAG = "PhoneWearDataListener"
    }
}
