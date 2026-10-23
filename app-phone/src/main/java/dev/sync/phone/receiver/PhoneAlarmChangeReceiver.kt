package dev.sync.phone.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.sync.core.engine.AlarmDiffCalculator
import dev.sync.core.engine.SyncEchoSuppressor
import dev.sync.core.model.AlarmSyncMessage
import dev.sync.core.model.SyncAction
import dev.sync.phone.alarm.PhoneAlarmReader
import dev.sync.phone.service.WearableTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Reacts to [android.app.action.NEXT_ALARM_CLOCK_CHANGED] broadcast.
 *
 * ## Lifecycle
 * - [BroadcastReceiver.onReceive] is called on the main thread with a short execution window.
 * - We use [goAsync] to extend the window and launch a coroutine on [Dispatchers.IO].
 * - The receiver is NOT exported; only the system can fire this broadcast.
 *
 * ## Echo suppression
 * If the alarm change was triggered by us applying a remote alarm (from Watch),
 * [SyncEchoSuppressor] will classify it as an echo and we skip sending.
 */
class PhoneAlarmChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED) return

        Log.d(TAG, "NEXT_ALARM_CLOCK_CHANGED received")
        val pendingResult = goAsync()

        receiverScope.launch {
            try {
                handleAlarmChange(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling alarm change", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAlarmChange(context: Context) {
        val reader = PhoneAlarmReader(context)
        val currentAlarms = reader.readNextAlarms()

        if (currentAlarms.isEmpty()) {
            Log.d(TAG, "No active alarms found — nothing to sync")
            return
        }

        // Filter out alarms that originated from the Watch (echo suppression)
        val alarmsToSync = currentAlarms.filter { alarm ->
            val suppressed = echoes.isEcho(alarm)
            if (suppressed) Log.d(TAG, "Echo suppressed: ${alarm.id}")
            !suppressed
        }

        if (alarmsToSync.isEmpty()) {
            Log.d(TAG, "All changes were remote echoes — no outbound sync needed")
            return
        }

        val message = AlarmSyncMessage(
            action = SyncAction.UPSERT,
            alarms = alarmsToSync
        )

        WearableTransport.sendAlarmSync(context, message)
        Log.i(TAG, "Synced ${alarmsToSync.size} alarm(s) to Watch")
    }

    companion object {
        private const val TAG = "PhoneAlarmChangeReceiver"

        // Shared singleton instances — receivers are re-instantiated on each broadcast
        // so we hold state in companion objects.
        val echoes = SyncEchoSuppressor()

        private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
