package dev.sync.wear.service

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dev.sync.core.model.AlarmSyncMessage
import dev.sync.core.model.RemoteAlarmEvent
import dev.sync.core.model.RemoteAlarmEventMessage
import dev.sync.core.model.SyncAction
import dev.sync.wear.alarm.WearAlarmInjector
import dev.sync.wear.notification.WearAlarmNotificationListener
import dev.sync.wear.ui.WearRingingAlertActivity
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

        val injector = WearAlarmInjector(applicationContext)
        when (message.action) {
            SyncAction.UPSERT, SyncAction.FULL_SYNC -> message.alarms.forEach { injector.setAlarm(it) }
            SyncAction.DELETE                       -> message.alarms.forEach { injector.dismissUpcoming(it) }
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
                // Launch the full-screen ringing alert UI
                val intent = Intent(applicationContext, WearRingingAlertActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(WearRingingAlertActivity.EXTRA_HOUR,    message.hour)
                    putExtra(WearRingingAlertActivity.EXTRA_MINUTE,  message.minute)
                    putExtra(WearRingingAlertActivity.EXTRA_LABEL,   message.label)
                    putExtra(WearRingingAlertActivity.EXTRA_ALARM_ID, message.alarmId ?: "")
                }
                applicationContext.startActivity(intent)
            }
            RemoteAlarmEvent.RINGING_STOPPED -> {
                // Dismiss the alert activity if it's open
                WearRingingAlertActivity.instance?.finish()
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
