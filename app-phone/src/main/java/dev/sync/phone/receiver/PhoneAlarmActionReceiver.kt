package dev.sync.phone.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.sync.core.model.RemoteAlarmEvent
import dev.sync.core.model.RemoteAlarmEventMessage
import dev.sync.phone.notification.PhoneAlarmNotifier
import dev.sync.phone.service.WearableTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Headless receiver that handles notification action clicks ("Dismiss" and "Snooze")
 * on the phone when the Galaxy Watch alarm is ringing.
 *
 * Transmits the dismiss/snooze command to the Galaxy Watch over BLE.
 */
class PhoneAlarmActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "Notification action clicked on phone: $action")

        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: ""

        PhoneAlarmNotifier.cancelWatchRingingNotification(context)

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
                    RemoteAlarmEventMessage(event = event, alarmId = alarmId)
                )
                Log.i(TAG, "Sent $event command to Watch for alarm $alarmId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send $event to Watch", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "PhoneAlarmActionRcvr"
        const val ACTION_DISMISS = "dev.sync.phone.action.DISMISS"
        const val ACTION_SNOOZE = "dev.sync.phone.action.SNOOZE"
        const val EXTRA_ALARM_ID = "extra_alarm_id"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
