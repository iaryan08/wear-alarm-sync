package dev.sync.wear.notification

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dev.sync.core.model.RemoteAlarmEvent
import dev.sync.core.model.RemoteAlarmEventMessage
import dev.sync.core.model.SyncAlarmItem
import dev.sync.wear.service.WearableTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Smartwatch notification listener service providing parity with the handheld listener.
 *
 * Observes alarm notifications published by the Wear OS system clock package and:
 * 1. Dispatches [RemoteAlarmEvent.RINGING_STARTED] to the connected handheld node.
 * 2. Exposes [executeDismiss] and [executeSnooze] for remote dismissal execution initiated
 *    from the handheld companion.
 */
class WearAlarmNotificationListener : NotificationListenerService() {

    private val activeAlarmIntents = ConcurrentHashMap<String, WearAlarmActionIntents>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        if (notification.category != Notification.CATEGORY_ALARM) return

        Log.d(TAG, "Watch alarm notification posted: ${sbn.packageName}")

        val actions = notification.actions ?: return
        val dismissAction = actions.find { it.title?.toString()?.contains("dismiss", ignoreCase = true) == true }
        val snoozeAction  = actions.find { it.title?.toString()?.contains("snooze",  ignoreCase = true) == true }
        if (dismissAction == null) return

        val alarmKey = "${sbn.packageName}:${sbn.tag}:${sbn.id}"
        activeAlarmIntents[alarmKey] = WearAlarmActionIntents(
            dismissIntent = dismissAction.actionIntent,
            snoozeIntent  = snoozeAction?.actionIntent
        )
        lastRingingAlarmKey = alarmKey

        val extras = notification.extras
        val hour   = extras?.getInt("android.alarmHour",   -1)?.takeIf { it >= 0 } ?: 0
        val minute = extras?.getInt("android.alarmMinute", -1)?.takeIf { it >= 0 } ?: 0
        val label  = extras?.getString("android.title") ?: ""
        val alarmId = SyncAlarmItem.buildId(hour, minute, emptyList())

        scope.launch {
            WearableTransport.sendRemoteAlarmEvent(
                context = this@WearAlarmNotificationListener,
                message = RemoteAlarmEventMessage(
                    event   = RemoteAlarmEvent.RINGING_STARTED,
                    alarmId = alarmId,
                    hour    = hour,
                    minute  = minute,
                    label   = label
                )
            )
            Log.i(TAG, "RINGING_STARTED sent to Phone: $hour:$minute")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return
        if (notification.category != Notification.CATEGORY_ALARM) return

        val alarmKey = "${sbn.packageName}:${sbn.tag}:${sbn.id}"
        activeAlarmIntents.remove(alarmKey)

        if (lastRingingAlarmKey == alarmKey) {
            lastRingingAlarmKey = null
            scope.launch {
                WearableTransport.sendRemoteAlarmEvent(
                    context = this@WearAlarmNotificationListener,
                    message = RemoteAlarmEventMessage(event = RemoteAlarmEvent.RINGING_STOPPED)
                )
            }
        }
    }

    companion object {
        private const val TAG = "WearAlarmNLS"

        @Volatile var lastRingingAlarmKey: String? = null
        @Volatile var instance: WearAlarmNotificationListener? = null

        fun executeDismiss(): Boolean {
            val key     = lastRingingAlarmKey ?: return false
            val intents = instance?.activeAlarmIntents?.get(key) ?: return false
            return try {
                intents.dismissIntent.send()
                Log.i(TAG, "Watch dismiss executed for $key")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Watch dismiss failed", e)
                false
            }
        }

        fun executeSnooze(): Boolean {
            val key     = lastRingingAlarmKey ?: return false
            val intents = instance?.activeAlarmIntents?.get(key) ?: return false
            val intent  = intents.snoozeIntent ?: intents.dismissIntent
            return try {
                intent.send()
                Log.i(TAG, "Watch snooze executed for $key")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Watch snooze failed", e)
                false
            }
        }
    }
}

data class WearAlarmActionIntents(
    val dismissIntent: android.app.PendingIntent,
    val snoozeIntent:  android.app.PendingIntent?
)
