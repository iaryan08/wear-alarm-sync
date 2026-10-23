package dev.sync.wear.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.sync.core.model.RemoteAlarmEvent
import dev.sync.core.model.RemoteAlarmEventMessage
import dev.sync.wear.service.WearableTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Headless receiver that handles native notification action buttons ("Dismiss" and "Snooze")
 * on the Galaxy Watch.
 *
 * Sends the corresponding [RemoteAlarmEvent] to the Phone over BLE.
 */
class WearAlarmActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "Notification action clicked: $action")

        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: ""
        val hour = intent.getIntExtra(EXTRA_HOUR, 0)
        val minute = intent.getIntExtra(EXTRA_MINUTE, 0)
        val label = intent.getStringExtra(EXTRA_LABEL) ?: ""

        // Cancel watch notification immediately
        WearAlarmNotifier.cancelRingingNotification(context)

        val event = when (action) {
            ACTION_DISMISS -> RemoteAlarmEvent.DISMISS
            ACTION_SNOOZE -> RemoteAlarmEvent.SNOOZE
            else -> return
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                WearableTransport.sendRemoteAlarmEvent(
                    context,
                    RemoteAlarmEventMessage(
                        event = event,
                        alarmId = alarmId,
                        hour = hour,
                        minute = minute,
                        label = label
                    )
                )
                Log.i(TAG, "Sent $event to phone for alarm $hour:$minute")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send $event to phone", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "WearAlarmActionReceiver"
        const val ACTION_DISMISS = "dev.sync.wear.alarm.ACTION_DISMISS"
        const val ACTION_SNOOZE = "dev.sync.wear.alarm.ACTION_SNOOZE"

        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_HOUR = "extra_hour"
        const val EXTRA_MINUTE = "extra_minute"
        const val EXTRA_LABEL = "extra_label"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
