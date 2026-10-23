package dev.sync.wear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.*
import com.google.android.gms.wearable.Wearable
import dev.sync.core.model.AlarmSyncMessage
import dev.sync.core.model.SyncAction
import dev.sync.wear.alarm.WearAlarmReader
import dev.sync.wear.service.WearableTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Minimal Wear Compose status screen.
 *
 * Shows:
 * - Phone connection state
 * - Next alarm on the watch
 * - Manual sync trigger
 */
class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearAppTheme { WearStatusScreen() }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────────────
// Theme
// ──────────────────────────────────────────────────────────────────────────────────────

private val WearBackground = Color.Black
private val WearSurface    = Color(0xFF141414)
private val WearAccent     = Color(0xFF4F8EF7)
private val WearCyan       = Color(0xFF26D0CE)
private val WearGreen      = Color(0xFF2ECC71)
private val WearRed        = Color(0xFFFF5252)
private val WearText       = Color(0xFFE8EDF7)
private val WearMuted      = Color(0xFF8A95B0)

@Composable
private fun WearAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = MaterialTheme.colors.copy(
            primary    = WearAccent,
            background = WearBackground,
            surface    = WearSurface,
            onPrimary  = Color.White,
            onSurface  = WearText
        ),
        content = content
    )
}

// ──────────────────────────────────────────────────────────────────────────────────────
// Screen
// ──────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun WearStatusScreen() {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var phoneConnected  by remember { mutableStateOf(false) }
    var nextAlarm       by remember { mutableStateOf("—") }
    var syncing         by remember { mutableStateOf(false) }
    var syncResult      by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                val nodes = Wearable.getNodeClient(context).connectedNodes.await()
                phoneConnected = nodes.isNotEmpty()
            } catch (_: Exception) { phoneConnected = false }

            val reader = WearAlarmReader(context)
            val alarms = reader.readNextAlarms()
            nextAlarm  = if (alarms.isEmpty()) "None"
                         else alarms.first().let {
                             "${it.hour.toString().padStart(2,'0')}:${it.minute.toString().padStart(2,'0')}"
                         }
            delay(5_000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WearBackground)
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 24.dp, bottom = 16.dp, start = 8.dp, end = 8.dp)
        ) {
            item {
                Text(
                    text       = "Alarm Sync",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color      = WearText,
                    textAlign  = TextAlign.Center
                )
            }
            item { Spacer(Modifier.height(8.dp)) }

            // Connection chip
            item {
                Chip(
                    label = {
                        Text(
                            text     = if (phoneConnected) "Phone Connected" else "No Phone",
                            fontSize = 12.sp,
                            color    = if (phoneConnected) WearGreen else WearRed
                        )
                    },
                    onClick = {},
                    colors = ChipDefaults.chipColors(backgroundColor = WearSurface)
                )
            }

            // Next alarm chip
            item {
                Chip(
                    label = {
                        Column {
                            Text("Next Alarm", fontSize = 10.sp, color = WearMuted)
                            Text(nextAlarm, fontSize = 14.sp, color = WearCyan, fontWeight = FontWeight.Bold)
                        }
                    },
                    onClick = {},
                    colors = ChipDefaults.chipColors(backgroundColor = WearSurface)
                )
            }

            item { Spacer(Modifier.height(4.dp)) }

            // Sync button
            item {
                Button(
                    onClick = {
                        scope.launch {
                            syncing = true
                            syncResult = ""
                            try {
                                val alarms = WearAlarmReader(context).readNextAlarms()
                                WearableTransport.sendAlarmSync(
                                    context,
                                    AlarmSyncMessage(SyncAction.FULL_SYNC, alarms)
                                )
                                syncResult = "✅ Sent"
                            } catch (e: Exception) {
                                syncResult = "❌ Failed"
                            } finally {
                                syncing = false
                            }
                        }
                    },
                    enabled = phoneConnected && !syncing,
                    colors  = ButtonDefaults.buttonColors(backgroundColor = WearAccent)
                ) {
                    Text(if (syncing) "…" else "Sync Now", fontSize = 13.sp, color = Color.White)
                }
            }

            if (syncResult.isNotEmpty()) {
                item {
                    Text(
                        text     = syncResult,
                        fontSize = 11.sp,
                        color    = if (syncResult.startsWith("✅")) WearGreen else WearRed
                    )
                }
            }
        }
    }
}
