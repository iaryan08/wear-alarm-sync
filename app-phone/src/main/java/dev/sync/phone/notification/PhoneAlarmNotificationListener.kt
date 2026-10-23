package dev.sync.phone.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import dev.sync.core.model.RemoteAlarmEvent
import dev.sync.core.model.RemoteAlarmEventMessage
import dev.sync.core.model.SyncAlarmItem
import dev.sync.phone.service.WearableTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Observes alarm notifications posted by OEM clock applications and extracts the native
 * Dismiss and Snooze [android.app.PendingIntent] instances.
 *
 * Responsibilities:
 * 1. Outbound dispatch: On active alarm notification publication, dispatches
 *    [RemoteAlarmEvent.RINGING_STARTED] to the paired smartwatch node.
 * 2. Inbound execution: On receipt of [RemoteAlarmEvent.DISMISS] or [RemoteAlarmEvent.SNOOZE]
 *    from the smartwatch node, executes the captured [android.app.PendingIntent].
 *
 * This mechanism relies on standard Android [Notification.Action] intents attached to the
 * ringing notification, avoiding private reflection or vendor-specific internal APIs.
 */
class PhoneAlarmNotificationListener : NotificationListenerService() {

    /**
     * Holds active alarm PendingIntents keyed by a stable alarm identifier.
     * Cleared when [onNotificationRemoved] fires for the alarm notification.
     */
    private val activeAlarmIntents = ConcurrentHashMap<String, AlarmActionIntents>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification ?: return

        // We only care about CATEGORY_ALARM notifications
        if (notification.category != Notification.CATEGORY_ALARM) return

        Log.d(TAG, "Alarm notification posted from: ${sbn.packageName}")

        val actions = notification.actions ?: return
        val dismissAction = actions.find { it.title?.toString()?.contains("dismiss", ignoreCase = true) == true }
        val snoozeAction  = actions.find { it.title?.toString()?.contains("snooze",  ignoreCase = true) == true }

        if (dismissAction == null) {
            Log.w(TAG, "No dismiss action found in alarm notification — skipping")
            return
        }

        // Derive a stable key: use notification tag+id combo
        val alarmKey = "${sbn.packageName}:${sbn.tag}:${sbn.id}"

        val intents = AlarmActionIntents(
            dismissIntent = dismissAction.actionIntent,
            snoozeIntent  = snoozeAction?.actionIntent
        )
        activeAlarmIntents[alarmKey] = intents

        // Extract time from notification extras if available
        val extras = notification.extras
        val hour   = extras?.getInt("android.alarmHour",   -1)?.takeIf { it >= 0 } ?: 0
        val minute = extras?.getInt("android.alarmMinute", -1)?.takeIf { it >= 0 } ?: 0
        val title  = extras?.getString(Notification.EXTRA_TITLE) ?: extras?.getString("android.title") ?: ""
        val text   = extras?.getString(Notification.EXTRA_TEXT) ?: extras?.getString("android.text") ?: ""
        val label  = if (text.isNotBlank() && title.contains("alarm", ignoreCase = true)) text else title
        if (label.isNotBlank()) {
            putCachedLabel(hour, minute, label)
        }

        val alarmId = SyncAlarmItem.buildId(hour, minute, emptyList())

        scope.launch {
            WearableTransport.sendRemoteAlarmEvent(
                context = this@PhoneAlarmNotificationListener,
                message = RemoteAlarmEventMessage(
                    event = RemoteAlarmEvent.RINGING_STARTED,
                    alarmId = alarmId,
                    hour = hour,
                    minute = minute,
                    label = label
                )
            )
            Log.i(TAG, "RINGING_STARTED sent to Watch for alarm at $hour:$minute")
        }

        // Store the alarmKey in a way WearDataListenerService can look up
        lastRingingAlarmKey = alarmKey
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
                    context = this@PhoneAlarmNotificationListener,
                    message = RemoteAlarmEventMessage(event = RemoteAlarmEvent.RINGING_STOPPED)
                )
                Log.i(TAG, "RINGING_STOPPED sent to Watch")
            }
        }
    }

    companion object {
        private const val TAG = "PhoneAlarmNLS"

        /** Shared state: the alarm key currently ringing (null if none). */
        @Volatile
        var lastRingingAlarmKey: String? = null

        /** Live reference to the running service instance for cross-service calls. */
        @Volatile
        var instance: PhoneAlarmNotificationListener? = null

        /**
         * Executes the native dismiss [PendingIntent] for the currently ringing alarm.
         * Called by [dev.sync.phone.service.PhoneWearDataListenerService] when Watch
         * sends [RemoteAlarmEvent.DISMISS].
         */
        fun executeDismiss(): Boolean {
            val key = lastRingingAlarmKey ?: return false
            val intents = instance?.activeAlarmIntents?.get(key) ?: return false
            return try {
                intents.dismissIntent.send()
                Log.i(TAG, "Dismiss PendingIntent executed for $key")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send dismiss intent", e)
                false
            }
        }

        /**
         * Executes the native snooze [PendingIntent] for the currently ringing alarm.
         * Falls back to dismiss if no snooze intent is available.
         */
        fun executeSnooze(): Boolean {
            val key = lastRingingAlarmKey ?: return false
            val intents = instance?.activeAlarmIntents?.get(key) ?: return false
            val intent = intents.snoozeIntent ?: intents.dismissIntent
            return try {
                intent.send()
                Log.i(TAG, "Snooze PendingIntent executed for $key")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send snooze intent", e)
                false
            }
        }

        private val labelCache = ConcurrentHashMap<String, String>()

        fun getCachedLabel(hour: Int, minute: Int): String {
            return labelCache["$hour:$minute"] ?: ""
        }

        fun putCachedLabel(hour: Int, minute: Int, label: String) {
            if (label.isNotBlank()) {
                labelCache["$hour:$minute"] = label
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}

/** Holds the extracted Dismiss and Snooze PendingIntents from an alarm notification. */
data class AlarmActionIntents(
    val dismissIntent: android.app.PendingIntent,
    val snoozeIntent:  android.app.PendingIntent?
)
