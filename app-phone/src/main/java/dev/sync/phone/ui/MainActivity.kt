package dev.sync.phone.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.Wearable
import dev.sync.core.model.AlarmSyncMessage
import dev.sync.core.model.SyncAction
import dev.sync.phone.alarm.PhoneAlarmReader
import dev.sync.phone.notification.PhoneAlarmNotificationListener
import dev.sync.phone.service.WearableTransport
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Single-screen status dashboard for the wear-alarm-sync phone companion.
 *
 * Shows:
 * - Watch connection state
 * - Next alarm from AlarmManager
 * - Notification Listener permission state
 * - Manual full-sync trigger button
 */
class MainActivity : ComponentActivity() {

    private val notificationPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* handled by recompose */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request POST_NOTIFICATIONS on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            AlarmSyncTheme {
                AlarmSyncDashboard()
            }
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────────────
// Theme
// ──────────────────────────────────────────────────────────────────────────────────────

private val DarkBackground  = Color(0xFF0A0E1A)
private val SurfaceCard     = Color(0xFF131827)
private val AccentBlue      = Color(0xFF4F8EF7)
private val AccentCyan      = Color(0xFF26D0CE)
private val AccentGreen     = Color(0xFF2ECC71)
private val AccentOrange    = Color(0xFFFF9800)
private val AccentRed       = Color(0xFFFF5252)
private val TextPrimary     = Color(0xFFE8EDF7)
private val TextSecondary   = Color(0xFF8A95B0)

@Composable
private fun AlarmSyncTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background   = DarkBackground,
            surface      = SurfaceCard,
            primary      = AccentBlue,
            onPrimary    = Color.White,
            onBackground = TextPrimary,
            onSurface    = TextPrimary
        ),
        content = content
    )
}

// ──────────────────────────────────────────────────────────────────────────────────────
// Main Dashboard
// ──────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun AlarmSyncDashboard() {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    // State
    var watchConnected     by remember { mutableStateOf(false) }
    var watchName          by remember { mutableStateOf("") }
    var nextAlarmLabel     by remember { mutableStateOf("Loading…") }
    var nlsGranted         by remember { mutableStateOf(false) }
    var syncInProgress     by remember { mutableStateOf(false) }
    var lastSyncStatus     by remember { mutableStateOf("") }

    // Poll connection + alarm state on resume
    LaunchedEffect(Unit) {
        while (true) {
            // Watch connection
            try {
                val nodes = Wearable.getNodeClient(context).connectedNodes.await()
                watchConnected = nodes.isNotEmpty()
                watchName      = nodes.firstOrNull()?.displayName ?: ""
            } catch (_: Exception) {
                watchConnected = false
            }

            // Next alarm
            val reader = PhoneAlarmReader(context)
            val alarms = reader.readNextAlarms()
            nextAlarmLabel = if (alarms.isEmpty()) "No alarm set"
                             else alarms.first().let { a ->
                                 val h = a.hour.toString().padStart(2, '0')
                                 val m = a.minute.toString().padStart(2, '0')
                                 "$h:$m${if (a.label.isNotBlank()) " — ${a.label}" else ""}"
                             }

            // Notification listener
            nlsGranted = isNotificationListenerGranted(context)

            delay(4_000)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── App Title ──
            Text(
                text       = "Alarm Sync",
                fontSize   = 26.sp,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary,
                letterSpacing = 0.5.sp
            )

                Spacer(modifier = Modifier.height(4.dp))

                // ── Watch Connection Card ──
                StatusCard(
                    title    = "Watch Connection",
                    value    = if (watchConnected) "Connected: $watchName" else "Not Connected",
                    icon     = if (watchConnected) "🟢" else "🔴",
                    accent   = if (watchConnected) AccentGreen else AccentRed
                )

                // ── Next Alarm Card ──
                StatusCard(
                    title  = "Next Alarm",
                    value  = nextAlarmLabel,
                    icon   = "🕐",
                    accent = AccentCyan
                )

                // ── Notification Listener Card ──
                NlsStatusCard(
                    granted = nlsGranted,
                    context = context
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ── Manual Sync Button ──
                GradientButton(
                    text       = if (syncInProgress) "Syncing…" else "Force Full Sync",
                    enabled    = watchConnected && !syncInProgress,
                    modifier   = Modifier.fillMaxWidth()
                ) {
                    scope.launch {
                        syncInProgress = true
                        lastSyncStatus = ""
                        try {
                            val reader = PhoneAlarmReader(context)
                            val alarms = reader.readNextAlarms()
                            val msg    = AlarmSyncMessage(
                                action = SyncAction.FULL_SYNC,
                                alarms = alarms
                            )
                            WearableTransport.sendAlarmSync(context, msg)
                            lastSyncStatus = "✅ Sent ${alarms.size} alarm(s) to Watch"
                        } catch (e: Exception) {
                            lastSyncStatus = "❌ Sync failed: ${e.message}"
                        } finally {
                            syncInProgress = false
                        }
                    }
                }

                if (lastSyncStatus.isNotEmpty()) {
                    Text(
                        text      = lastSyncStatus,
                        fontSize  = 13.sp,
                        color     = if (lastSyncStatus.startsWith("✅")) AccentGreen else AccentRed,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

// ──────────────────────────────────────────────────────────────────────────────────────
// Reusable Composables
// ──────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatusCard(title: String, value: String, icon: String, accent: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(18.dp),
        colors   = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Accent indicator dot
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 20.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = value, fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun NlsStatusCard(granted: Boolean, context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(18.dp),
        colors   = CardDefaults.cardColors(containerColor = SurfaceCard),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background((if (granted) AccentGreen else AccentOrange).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = if (granted) "🔔" else "⚠️", fontSize = 20.sp)
                }
                Column {
                    Text(text = "Notification Listener", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                    Text(
                        text = if (granted) "Granted — Dismiss/Snooze active" else "Not Granted — Remote dismiss disabled",
                        fontSize = 14.sp,
                        color = if (granted) AccentGreen else AccentOrange,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (!granted) {
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            android.content.Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = AccentOrange),
                    border   = androidx.compose.foundation.BorderStroke(1.dp, AccentOrange.copy(alpha = 0.5f))
                ) {
                    Text("Open Settings", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun GradientButton(
    text: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick    = onClick,
        enabled    = enabled,
        modifier   = modifier.height(52.dp),
        shape      = RoundedCornerShape(16.dp),
        colors     = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = SurfaceCard
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    if (enabled)
                        Brush.horizontalGradient(listOf(AccentBlue, AccentCyan))
                    else
                        Brush.horizontalGradient(listOf(SurfaceCard, SurfaceCard)),
                    RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = text,
                fontSize   = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color      = if (enabled) Color.White else TextSecondary
            )
        }
    }
}

// ──────────────────────────────────────────────────────────────────────────────────────
// Helper
// ──────────────────────────────────────────────────────────────────────────────────────

private fun isNotificationListenerGranted(context: Context): Boolean {
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    if (flat.isNullOrEmpty()) return false
    val cn = ComponentName(context, PhoneAlarmNotificationListener::class.java)
    return flat.split(":").any { it.trim() == cn.flattenToString() }
}
