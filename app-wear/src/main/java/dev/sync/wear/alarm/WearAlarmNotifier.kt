package dev.sync.wear.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

/**
 * Headless, native notification engine for Wear OS.
 *
 * Replaces all custom overlay/activity screens with standard native Wear OS notifications.
 * Renders high-priority alarm alerts with native Dismiss and Snooze action buttons.
 */
object WearAlarmNotifier {

    private const val TAG = "WearAlarmNotifier"
    const val CHANNEL_ID = "wear_alarm_sync_channel"
    const val NOTIFICATION_ID = 4001

    fun createNotificationChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Alarm Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Native alarm notifications with remote dismiss/snooze actions"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 400, 600, 400)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        manager.createNotificationChannel(channel)
    }

    fun showRingingNotification(
        context: Context,
        hour: Int,
        minute: Int,
        label: String,
        alarmId: String
    ) {
        createNotificationChannel(context)

        val timeStr = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
        val titleText = if (label.isNotBlank()) "$timeStr — $label" else "Alarm $timeStr"

        // Dismiss action PendingIntent
        val dismissIntent = Intent(context, WearAlarmActionReceiver::class.java).apply {
            action = WearAlarmActionReceiver.ACTION_DISMISS
            putExtra(WearAlarmActionReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(WearAlarmActionReceiver.EXTRA_HOUR, hour)
            putExtra(WearAlarmActionReceiver.EXTRA_MINUTE, minute)
            putExtra(WearAlarmActionReceiver.EXTRA_LABEL, label)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            101,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze action PendingIntent
        val snoozeIntent = Intent(context, WearAlarmActionReceiver::class.java).apply {
            action = WearAlarmActionReceiver.ACTION_SNOOZE
            putExtra(WearAlarmActionReceiver.EXTRA_ALARM_ID, alarmId)
            putExtra(WearAlarmActionReceiver.EXTRA_HOUR, hour)
            putExtra(WearAlarmActionReceiver.EXTRA_MINUTE, minute)
            putExtra(WearAlarmActionReceiver.EXTRA_LABEL, label)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            102,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissAction = Notification.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel,
            "Dismiss",
            dismissPendingIntent
        ).build()

        val snoozeAction = Notification.Action.Builder(
            android.R.drawable.ic_popup_reminder,
            "Snooze",
            snoozePendingIntent
        ).build()

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(titleText)
            .setContentText("📱 Phone Alarm Ringing")
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setOngoing(true)
            .addAction(dismissAction)
            .addAction(snoozeAction)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
        Log.i(TAG, "Native ringing notification posted on watch for $timeStr")
    }

    fun cancelRingingNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.cancel(NOTIFICATION_ID)
        Log.i(TAG, "Native ringing notification canceled")
    }
}
