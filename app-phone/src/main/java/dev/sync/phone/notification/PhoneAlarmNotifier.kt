package dev.sync.phone.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.util.Log
import dev.sync.phone.receiver.PhoneAlarmActionReceiver

/**
 * Native notification manager for the phone companion.
 *
 * Displays high-priority notifications with Dismiss and Snooze action buttons
 * when the Galaxy Watch alarm is ringing.
 */
object PhoneAlarmNotifier {

    private const val TAG = "PhoneAlarmNotifier"
    const val CHANNEL_ID = "phone_watch_alarm_channel"
    const val NOTIFICATION_ID = 5001

    fun createNotificationChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Galaxy Watch Alarms",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for alarms ringing on your Galaxy Watch"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 500, 300, 500)
            setBypassDnd(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        manager.createNotificationChannel(channel)
    }

    fun showWatchRingingNotification(
        context: Context,
        hour: Int,
        minute: Int,
        label: String,
        alarmId: String
    ) {
        createNotificationChannel(context)

        val timeStr = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
        val titleText = if (label.isNotBlank()) "Watch Alarm: $timeStr ($label)" else "Watch Alarm: $timeStr"

        // Dismiss action
        val dismissIntent = Intent(context, PhoneAlarmActionReceiver::class.java).apply {
            action = PhoneAlarmActionReceiver.ACTION_DISMISS
            putExtra(PhoneAlarmActionReceiver.EXTRA_ALARM_ID, alarmId)
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            201,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze action
        val snoozeIntent = Intent(context, PhoneAlarmActionReceiver::class.java).apply {
            action = PhoneAlarmActionReceiver.ACTION_SNOOZE
            putExtra(PhoneAlarmActionReceiver.EXTRA_ALARM_ID, alarmId)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            202,
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
            .setContentText("⌚ Galaxy Watch 7 Alarm Ringing")
            .setCategory(Notification.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setOngoing(true)
            .addAction(dismissAction)
            .addAction(snoozeAction)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
        Log.i(TAG, "Watch ringing notification posted on phone for $timeStr")
    }

    fun cancelWatchRingingNotification(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.cancel(NOTIFICATION_ID)
        Log.i(TAG, "Watch ringing notification canceled on phone")
    }
}
