package dev.sync.wear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import dev.sync.core.model.RemoteAlarmEvent
import dev.sync.core.model.RemoteAlarmEventMessage
import dev.sync.wear.service.WearableTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Full-screen Ringing Alert dialog shown on the Watch when the **Phone**'s alarm rings.
 *
 * Launched by [dev.sync.wear.service.WearAlarmListenerService] via an explicit Intent.
 * The user can dismiss or snooze the phone alarm remotely from their wrist.
 *
 * Design: Dark circular watch UI with pulsing alarm ring and two large touch targets.
 */
class WearRingingAlertActivity : ComponentActivity() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this

        val hour    = intent.getIntExtra(EXTRA_HOUR,    0)
        val minute  = intent.getIntExtra(EXTRA_MINUTE,  0)
        val label   = intent.getStringExtra(EXTRA_LABEL)   ?: ""
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID) ?: ""

        setContent {
            RingingAlertScreen(
                hour    = hour,
                minute  = minute,
                label   = label,
                onDismiss = {
                    serviceScope.launch {
                        WearableTransport.sendRemoteAlarmEvent(
                            applicationContext,
                            RemoteAlarmEventMessage(
                                event   = RemoteAlarmEvent.DISMISS,
                                alarmId = alarmId,
                                hour    = hour,
                                minute  = minute,
                                label   = label
                            )
                        )
                    }
                    finish()
                },
                onSnooze = {
                    serviceScope.launch {
                        WearableTransport.sendRemoteAlarmEvent(
                            applicationContext,
                            RemoteAlarmEventMessage(
                                event   = RemoteAlarmEvent.SNOOZE,
                                alarmId = alarmId,
                                hour    = hour,
                                minute  = minute,
                                label   = label
                            )
                        )
                    }
                    finish()
                }
            )
        }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_HOUR     = "extra_hour"
        const val EXTRA_MINUTE   = "extra_minute"
        const val EXTRA_LABEL    = "extra_label"
        const val EXTRA_ALARM_ID = "extra_alarm_id"

        @Volatile
        var instance: WearRingingAlertActivity? = null
    }
}

// ──────────────────────────────────────────────────────────────────────────────────────
// UI
// ──────────────────────────────────────────────────────────────────────────────────────

private val DismissColor = Color(0xFFFF5252)
private val SnoozeColor  = Color(0xFF4F8EF7)
private val BgColor      = Color.Black
private val TextColor    = Color(0xFFE8EDF7)
private val MutedColor   = Color(0xFF8A95B0)

@Composable
private fun RingingAlertScreen(
    hour: Int,
    minute: Int,
    label: String,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val timeStr = "${hour.toString().padStart(2,'0')}:${minute.toString().padStart(2,'0')}"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("⏰", fontSize = 28.sp)

            Text(
                text       = timeStr,
                fontSize   = 32.sp,
                fontWeight = FontWeight.Bold,
                color      = TextColor
            )

            if (label.isNotBlank()) {
                Text(
                    text      = label,
                    fontSize  = 12.sp,
                    color     = MutedColor,
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text     = "📱 Phone Alarm",
                fontSize = 10.sp,
                color    = MutedColor
            )

            Spacer(Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Snooze
                CompactButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSnooze()
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = SnoozeColor)
                ) {
                    Text("💤", fontSize = 14.sp)
                }

                // Dismiss
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = DismissColor),
                    modifier = Modifier.size(52.dp)
                ) {
                    Text("✕", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Text(
                text     = "Snooze  •  Dismiss",
                fontSize = 9.sp,
                color    = MutedColor
            )
        }
    }
}
