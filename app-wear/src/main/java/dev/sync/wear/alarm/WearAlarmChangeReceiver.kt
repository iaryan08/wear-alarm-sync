package dev.sync.wear.alarm

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.sync.core.engine.SyncEchoSuppressor
import dev.sync.core.model.AlarmSyncMessage
import dev.sync.core.model.SyncAction
import dev.sync.wear.service.WearableTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Headless receiver that monitors alarm changes on the Galaxy Watch (e.g. from Samsung Clock).
 *
 * Automatically syncs the watch's active alarm state back to the OnePlus phone over BLE.
 * Includes echo suppression to prevent infinite ping-pong sync loops.
 */
class WearAlarmChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED) return

        Log.d(TAG, "Watch NEXT_ALARM_CLOCK_CHANGED received")
        val pendingResult = goAsync()

        receiverScope.launch {
            try {
                handleAlarmChange(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling watch alarm change", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAlarmChange(context: Context) {
        val reader = WearAlarmReader(context)
        val currentAlarms = reader.readNextAlarms()

        if (currentAlarms.isEmpty()) {
            Log.d(TAG, "No active alarms on watch — sending empty FULL_SYNC to Phone")
            val message = AlarmSyncMessage(
                action = SyncAction.FULL_SYNC,
                alarms = emptyList()
            )
            WearableTransport.sendAlarmSync(context, message)
            return
        }

        // Echo suppression: ignore alarms that originated from the phone
        val alarmsToSync = currentAlarms.filter { alarm ->
            val suppressed = echoes.isEcho(alarm)
            if (suppressed) Log.d(TAG, "Echo suppressed on watch: ${alarm.id}")
            !suppressed
        }

        if (alarmsToSync.isEmpty()) {
            Log.d(TAG, "All watch changes were remote echoes — no outbound sync needed")
            return
        }

        val message = AlarmSyncMessage(
            action = SyncAction.FULL_SYNC,
            alarms = alarmsToSync
        )

        WearableTransport.sendAlarmSync(context, message)
        Log.i(TAG, "FULL_SYNC ${alarmsToSync.size} alarm(s) from Watch to Phone")
    }

    companion object {
        private const val TAG = "WearAlarmChangeRcvr"
        val echoes = SyncEchoSuppressor()
        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
