package dev.sync.wear.service

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dev.sync.core.model.AlarmSyncMessage
import dev.sync.core.model.RemoteAlarmEvent
import dev.sync.core.model.RemoteAlarmEventMessage
import dev.sync.core.model.SyncAction
import dev.sync.wear.alarm.WearAlarmChangeReceiver
import dev.sync.wear.alarm.WearAlarmNotifier
import dev.sync.wear.alarm.WearAlarmScheduler
import dev.sync.wear.alarm.WearAlarmStorage
import dev.sync.wear.notification.WearAlarmNotificationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Passive [WearableListenerService] for the Galaxy Watch 7.
 *
 * Woken by Google Play Services when BLE packets arrive from the paired phone.
 * Zero battery cost when no messages are in-flight.
 *
 * ## Handled paths
 * | Path                  | Action                                          |
 * |-----------------------|-------------------------------------------------|
 * | `/alarm/sync`         | Apply alarm UPSERT/DELETE/FULL_SYNC from Phone  |
 * | `/alarm/remote-event` | Show Ringing Alert UI or execute dismiss/snooze |
 */
class WearAlarmListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json  = Json { ignoreUnknownKeys = true }

    override fun onMessageReceived(event: MessageEvent) {
        Log.d(TAG, "Message received on path: ${event.path} (${event.data.size} bytes)")

        when {
            event.path.startsWith(WearableTransport.PATH_ALARM_SYNC) ->
                scope.launch { handleSyncMessage(event.data) }

            event.path.startsWith(WearableTransport.PATH_ALARM_REMOTE_EVENT) ->
                scope.launch { handleRemoteEvent(event.data) }

            else -> Log.w(TAG, "Unknown path: ${event.path}")
        }
    }

    private suspend fun handleSyncMessage(data: ByteArray) {
        val message = try {
            json.decodeFromString<AlarmSyncMessage>(String(data, Charsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "Parse error AlarmSyncMessage", e); return
        }

        Log.i(TAG, "Watch sync received: ${message.action} (${message.alarms.size} alarms)")

        val scheduler = WearAlarmScheduler(applicationContext)
        message.alarms.forEach { WearAlarmChangeReceiver.echoes.recordRemoteSync(it) }

        when (message.action) {
            SyncAction.FULL_SYNC -> scheduler.reconcileSnapshot(message.alarms)
            SyncAction.UPSERT -> {
                message.alarms.forEach { scheduler.schedule(it) }
                val current = WearAlarmStorage.getAlarms(applicationContext).toMutableList()
                message.alarms.forEach { a ->
                    current.removeAll { it.id == a.id }
                    current.add(a)
                }
                WearAlarmStorage.saveAlarms(applicationContext, current)
            }
            SyncAction.DELETE -> {
                message.alarms.forEach { scheduler.cancel(it.id) }
                val current = WearAlarmStorage.getAlarms(applicationContext).toMutableList()
                val delIds = message.alarms.map { it.id }.toSet()
                current.removeAll { it.id in delIds }
                WearAlarmStorage.saveAlarms(applicationContext, current)
            }
        }
    }

    private fun handleRemoteEvent(data: ByteArray) {
        val message = try {
            json.decodeFromString<RemoteAlarmEventMessage>(String(data, Charsets.UTF_8))
        } catch (e: Exception) {
            Log.e(TAG, "Parse error RemoteAlarmEventMessage", e); return
        }

        Log.i(TAG, "Watch remote event: ${message.event}")

        when (message.event) {
            RemoteAlarmEvent.RINGING_STARTED -> {
                WearAlarmNotifier.showRingingNotification(
                    context = applicationContext,
                    hour = message.hour,
                    minute = message.minute,
                    label = message.label,
                    alarmId = message.alarmId ?: ""
                )
            }
            RemoteAlarmEvent.RINGING_STOPPED -> {
                WearAlarmNotifier.cancelRingingNotification(applicationContext)
            }
            RemoteAlarmEvent.DISMISS -> {
                val ok = WearAlarmNotificationListener.executeDismiss()
                Log.i(TAG, "Watch dismiss executed: $ok")
            }
            RemoteAlarmEvent.SNOOZE -> {
                val ok = WearAlarmNotificationListener.executeSnooze()
                Log.i(TAG, "Watch snooze executed: $ok")
            }
        }
    }

    companion object {
        private const val TAG = "WearAlarmListenerSvc"
    }
}
