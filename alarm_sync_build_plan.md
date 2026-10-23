# Alarm Sync — Final Master Architecture & Build Plan (Zero-Shizuku, Pure Native)

**Target Platforms:** Android Phone (Android 9.0+, API 28+) & Wear OS Smartwatch (Wear OS 3.0+, API 30+)  
**Reference Model:** [`dnd-bedtime-sync`](https://github.com/Silleellie/dnd-bedtime-sync) (Low-power, event-driven Wearable Data Layer architecture) 
---

## 1. Executive Summary & Native Framework Guarantee

By eliminating third-party background daemons and privileged managers, the synchronization and remote-control system executes exclusively on standard **native Android OS frameworks**:

| Capability | Handheld Phone | Wear OS Smartwatch |
|---|---|---|
| **Alarm Creation** | `AlarmClock.ACTION_SET_ALARM` (`EXTRA_SKIP_UI=true`) | `AlarmClock.ACTION_SET_ALARM` (`EXTRA_SKIP_UI=true`) |
| **Repeat Schedule** | `AlarmClock.EXTRA_DAYS` (`ArrayList<Int>` Mon–Sun) | `AlarmClock.EXTRA_DAYS` (`ArrayList<Int>` Mon–Sun) |
| **Ringing Dismiss / Snooze** | `NotificationListenerService` (`actionIntent.send()`) | `NotificationListenerService` (`actionIntent.send()`) |
| **Upcoming Dismissal** | `AlarmClock.ACTION_DISMISS_ALARM` | `AlarmClock.ACTION_DISMISS_ALARM` |
| **Active Alarm Detection** | `AlarmManager.getNextAlarmClock()` + `dumpsys alarm` | `AlarmManager.getNextAlarmClock()` + `dumpsys alarm` |
| **Change Event Trigger** | `android.app.action.NEXT_ALARM_CLOCK_CHANGED` | Passive Wearable Listener (`WearableListenerService`) |
| **Elevated Tool Required?** | ❌ **NO (Zero dependency)** | ❌ **NO (Zero dependency)** |
| **Developer Mode Needed at Runtime?** | ❌ **NO (Disable immediately after install)** | ❌ **NO (Disable immediately after install)** |

---

## 2. Institutional Privacy & Enterprise MDM Compliance

Enterprise networks, mobile device management (MDM) profiles, and sensitive banking applications restrict access when **Developer Options or USB Debugging** remain active.

### How this architecture complies:
1. **One-Time Install:** Developer Options and USB Debugging are enabled only for 2 minutes to install the APKs and grant standard permissions.
2. **Permanent Persistence:** Android stores app permissions (`DUMP`, notification listener access, battery whitelist) in the protected system partition (`/data/system/packages.xml`).
3. **Turn Developer Options OFF:** Once setup is complete, **Developer Options can be switched OFF permanently**.
4. **Zero Impact on Runtime:** The app does not rely on ADB, Wireless Debugging, shell scripts, or Shizuku to run. All operations use standard Android framework APIs.

---

## 3. Power & Battery Strategy (The `dnd-bedtime-sync` Model)

Smartwatches have tiny batteries (300–425 mAh). Background polling loops will drain the battery in hours.

```
                       [ EVENT OCCURS ]
   (User creates/edits alarm OR alarm rings on Phone/Watch)
                              │
            ┌─────────────────┴─────────────────┐
            ▼                                   ▼
    Phone-Side Event                     Watch-Side Event
(BroadcastReceiver fires)          (Notification/Wear listener wakes)
            │                                   │
   Wakes for ~50ms                     Wakes for ~50ms
            │                                   │
            ▼                                   ▼
    [ Google Play Services Wearable.MessageClient (Bluetooth Low Energy) ]
            │                                   │
            ▼                                   ▼
Passive WearableListenerService        Passive WearableListenerService
    (Wakes receiver device)             (Wakes receiver device)
            │                                   │
   Applies alarm delta                 Applies alarm delta
            │                                   │
            ▼                                   ▼
   GOES RIGHT BACK TO DEEP SLEEP       GOES RIGHT BACK TO DEEP SLEEP
```

### Key Power Guarantees:
1. **Zero Watch Polling:** The Watch companion app has **no background loops, no timers, and no persistent foreground service**.
2. **Hardware Deep Sleep (Doze):** The Watch CPU stays asleep. Google Play Services handles BLE packet reception at the Bluetooth radio level and only wakes `WearableListenerService` when a packet arrives.
3. **Micro-Payloads:** Sync packets are tiny (<200 bytes) binary/JSON payloads.
4. **Echo Suppression:** A 10-second memory cache prevents infinite ping-pong sync loops between devices.

---

## 4. Real-Time Remote Dismiss & Snooze Architecture

When an alarm rings on Device A (e.g. Phone), the user can silence it from Device B (e.g. Watch) within 80ms:

```
[ Device A (Alarm Rings) ]                     [ Device B (Remote Controller) ]
           │                                                  │
(Notification Posted: CATEGORY_ALARM)                         │
           │                                                  │
AlarmNotificationListener                                     │
Captures native Dismiss/Snooze PendingIntents                 │
           │                                                  │
           ├─────── Wearable MessageClient (BLE <80ms) ──────►│
           │        RemoteAlarmEvent.RINGING_STARTED          │
           │                                                  │
           │                                          Wakes Wear Compose Alert UI:
           │                                          "⏰ Alarm: 07:30 AM"
           │                                          [ Snooze (10m) ]  [ Dismiss ]
           │                                                  │
           │                                             (User taps [ Dismiss ])
           │                                                  │
           │◄────── Wearable MessageClient (BLE <80ms) ───────┤
           │        RemoteAlarmEvent.DISMISS                  │
           │                                                  │
AlarmNotificationListener                                     │
Executes native dismissPendingIntent.send()                   │
           │                                                  │
[ OEM Clock silences instantly! ]                             │
```

### Why PendingIntent Delegation is 100% Reliable:
1. **Zero Private Hacks:** Both OnePlus (`com.oneplus.deskclock`) and Samsung (`com.samsung.android.watch.alarm`) attach explicit Android `PendingIntent`s to their alarm notifications for both "Dismiss" and "Snooze".
2. **Native Execution:** Calling `action.actionIntent.send()` triggers the exact OEM code path as tapping the physical button on that device's screen.
3. **Two-Way Support:** Operates identically whether the OnePlus phone is ringing (dismissed from Galaxy Watch) or the Galaxy Watch is ringing (dismissed from OnePlus phone).

---

## 5. Data Contract & Sync Protocol

### Alarm Data Model
```kotlin
@Serializable
data class SyncAlarmItem(
    val id: String,                  // Unique hash of (hour, minute, repeatDays)
    val hour: Int,                   // 0-23
    val minute: Int,                 // 0-59
    val daysOfWeek: List<Int> = emptyList(), // Calendar.MONDAY..Calendar.SUNDAY
    val label: String = "",          // E.g. "Work", "Exam"
    val isEnabled: Boolean = true,   // Active vs Disabled
    val lastModified: Long = System.currentTimeMillis()
)

@Serializable
data class AlarmSyncMessage(
    val action: SyncAction,          // UPSERT, DELETE, FULL_SYNC
    val alarms: List<SyncAlarmItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

enum class SyncAction { UPSERT, DELETE, FULL_SYNC }

@Serializable
data class RemoteAlarmEventMessage(
    val event: RemoteAlarmEvent,     // RINGING_STARTED, RINGING_STOPPED, DISMISS, SNOOZE
    val alarmId: String? = null,
    val hour: Int = 0,
    val minute: Int = 0,
    val label: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

enum class RemoteAlarmEvent {
    RINGING_STARTED,
    RINGING_STOPPED,
    DISMISS,
    SNOOZE
}
```

### Echo Suppression Logic
```kotlin
class SyncEchoSuppressor {
    private val remoteSyncCache = ConcurrentHashMap<String, Long>()

    fun isEcho(alarm: SyncAlarmItem): Boolean {
        val appliedAt = remoteSyncCache[alarm.id] ?: return false
        return (System.currentTimeMillis() - appliedAt) < 10_000 // 10s cooldown
    }

    fun recordRemoteSync(alarm: SyncAlarmItem) {
        remoteSyncCache[alarm.id] = System.currentTimeMillis()
    }
}
```

---

## 6. API & Permission Matrix

| Device | Mechanism | Permission | Grant Method |
|---|---|---|---|
| **Phone** | Alarm Creation | `com.android.alarm.permission.SET_ALARM` | Auto-granted normal permission |
| **Phone** | Alarm Notification Listener | `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` | Standard system Settings toggle |
| **Phone** | Change Detection | `android.app.action.NEXT_ALARM_CLOCK_CHANGED` | Normal Android public broadcast |
| **Phone** | Wear Transport | Google Play Services Wearable API | Standard GMS library |
| **Phone** | Battery Exemption | Direct whitelist | One-time ADB or System Settings |
| **Phone** | Scheduled DUMP (Fallback) | `android.permission.DUMP` | One-time ADB grant (persists forever) |
| **Watch** | Alarm Creation | `com.android.alarm.permission.SET_ALARM` | Auto-granted normal permission (`ACTION_SET_ALARM`) |
| **Watch** | Alarm Notification Listener | `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` | One-time grant via Watch ADB command |
| **Watch** | Listener Service | `com.google.android.gms.wearable.BIND_LISTENER` | Declared in Wear Manifest |
| **Watch** | Battery Exemption | Direct whitelist | One-time ADB grant (persists forever) |

---

## 7. Recommended Tech Stack: Native Kotlin (Multi-Module)

Following `dnd-bedtime-sync`'s proven multi-module architecture:

* **Language:** Kotlin (100% native)
* **Phone UI:** Jetpack Compose (Material 3) — clean 1-screen status dashboard showing connection state, next active alarm, and manual sync trigger.
* **Watch UI:** Wear Compose (Material Wear) — minimal status screen, Quick Glance Tile, and full-screen reactive Ringing Alert dialog.
* **Communication:** `com.google.android.gms:play-services-wearable` (Low-power BLE MessageClient & ChannelClient).
* **Serialization:** `kotlinx.serialization.json`
* **Zero 3rd-party background daemons:** No Shizuku, no root, no termux.

---

## 8. Project Directory Structure

```
alarm-sync/
│
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
│
├── core/                                 <-- Shared pure-Kotlin logic
│   └── src/main/java/dev/sync/core/
│       ├── model/
│       │   ├── SyncAlarmItem.kt
│       │   ├── AlarmSyncMessage.kt
│       │   └── RemoteAlarmEventMessage.kt
│       └── engine/
│           ├── SyncEchoSuppressor.kt
│           └── AlarmDiffCalculator.kt
│
├── app-phone/                            <-- OnePlus Phone Application
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/dev/sync/phone/
│           ├── receiver/
│           │   └── PhoneAlarmChangeReceiver.kt       (Listens to NEXT_ALARM_CLOCK_CHANGED)
│           ├── notification/
│           │   └── PhoneAlarmNotificationListener.kt (Captures ringing alarm + Dismiss/Snooze intents)
│           ├── alarm/
│           │   ├── PhoneAlarmReader.kt               (Reads next alarm from AlarmManager / dumpsys)
│           │   └── PhoneAlarmInjector.kt             (Creates alarm via ACTION_SET_ALARM)
│           ├── service/
│           │   └── PhoneWearDataListenerService.kt   (Receives watch changes & remote dismiss actions)
│           └── ui/
│               └── MainActivity.kt                   (Clean connection, sync & ringing control screen)
│
└── app-wear/                             <-- Samsung Galaxy Watch 7 Application
    └── src/main/
        ├── AndroidManifest.xml
        └── java/dev/sync/wear/
            ├── service/
            │   └── WearAlarmListenerService.kt       (Passive WearableListenerService for sync & ring events)
            ├── notification/
            │   └── WearAlarmNotificationListener.kt  (Captures watch ringing alarms + Dismiss/Snooze)
            ├── alarm/
            │   ├── WearAlarmReader.kt                (Reads next alarm from Wear OS AlarmManager)
            │   └── WearAlarmInjector.kt              (Injects alarm via ACTION_SET_ALARM with EXTRA_SKIP_UI)
            └── ui/
                ├── WearMainActivity.kt               (Minimal Wear Compose UI)
                ├── WearRingingAlertActivity.kt       (Reactive Ringing Alert dialog: Dismiss / Snooze)
                └── SyncStatusTile.kt                 (Quick Glance Tile on Watch)
```

---

## 9. One-Time Setup Commands (Then Turn Developer Mode OFF)

### On Handheld Phone:
```powershell
# 1. Install app
adb -d install -r app-phone/build/outputs/apk/debug/app-phone-debug.apk

# 2. Whitelist from background execution limits
adb -d shell dumpsys deviceidle whitelist +dev.sync.alarm

# 3. Grant Notification Listener access
adb -d shell cmd notification allow_listener dev.sync.alarm/dev.sync.phone.notification.PhoneAlarmNotificationListener

# 4. Grant DUMP permission (for system alarm table state verification)
adb -d shell pm grant dev.sync.alarm android.permission.DUMP
```

### On Wear OS Smartwatch (over Wi-Fi ADB):
```powershell
# 1. Install app on smartwatch
adb -s <watch-ip>:<port> install -r app-wear/build/outputs/apk/debug/app-wear-debug.apk

# 2. Whitelist from ambient sleep restrictions
adb -s <watch-ip>:<port> shell dumpsys deviceidle whitelist +dev.sync.alarm

# 3. Grant Notification Listener access
adb -s <watch-ip>:<port> shell cmd notification allow_listener dev.sync.alarm/dev.sync.wear.notification.WearAlarmNotificationListener

# 4. Grant DUMP permission
adb -s <watch-ip>:<port> shell pm grant dev.sync.alarm android.permission.DUMP
```

---

## 10. Implementation Milestones

1. **Milestone 1 — Core & Protocol:** Shared pure-Kotlin models (`SyncAlarmItem`, `AlarmSyncMessage`, `RemoteAlarmEventMessage`), serialization, and echo suppression tests.
2. **Milestone 2 — Phone Module:** `PhoneAlarmChangeReceiver`, `PhoneAlarmNotificationListener` (Dismiss/Snooze delegation), `PhoneAlarmInjector`, and Wearable Data Layer transport.
3. **Milestone 3 — Wear Module:** Passive `WearableListenerService`, `WearAlarmInjector` (`ACTION_SET_ALARM` with `EXTRA_SKIP_UI = true`), and `WearRingingAlertActivity` (touch-friendly Dismiss/Snooze dialog).
4. **Milestone 4 — Two-Way End-to-End Test:**
   - Create alarm on Phone -> verifies silent instant creation on Watch.
   - Create alarm on Watch -> verifies silent instant creation on Phone.
   - Ring alarm on Phone -> verify Watch immediately shows Dismiss/Snooze alert; tapping Dismiss silences Phone.
   - Ring alarm on Watch -> verify Phone can dismiss Watch.
5. **Milestone 5 — Polish & Clean UI:** Simple Jetpack Compose phone dashboard & Wear Compose watch status card and Quick Glance Tile.
