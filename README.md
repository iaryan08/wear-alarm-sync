# wear-alarm-sync

**Production-ready, open-source Native Kotlin multi-module Android project for seamless two-way alarm synchronization and real-time remote dismiss/snooze between Android phones and Wear OS smartwatches.**

Target Platforms: **Android 9.0+ (API level 28+)** ↔ **Wear OS 3.0+ (API level 30+)**

---

## 📱 Compatibility & Tested Devices

This application has been verified and tested on:
* **Handheld Phone:** **OxygenOS 16** (Android 16)
* **Smartwatch:** **Samsung Galaxy Watch 7** running **Wear OS 6**

*General Hardware Requirements:*
* Handheld: Android 9.0 (Pie / API 28) or newer.
* Smartwatch: Wear OS 3.0 (API 30) or newer.
* Connection: Standard Bluetooth / Wi-Fi PAN pairing established via the device's companion manager (e.g., Galaxy Wearable).

---

## Architecture Overview

| Feature | Android API | Elevated Privileges Required |
|---|---|---|
| Alarm Injection | `AlarmClock.ACTION_SET_ALARM` (`EXTRA_SKIP_UI=true`) | None (Standard AOSP Intent) |
| Recurrence Rules | `AlarmClock.EXTRA_DAYS` | None (Standard AOSP Intent) |
| Active Alarm Dismissal | `NotificationListenerService` (`actionIntent.send()`) | Notification Access Permission |
| Cross-Device Transport | Google Play Services Wearable `MessageClient` | Standard Bluetooth / Wi-Fi PAN |
| Background Execution | Native BroadcastReceivers + Whitelisted Listeners | None (Zero Background Daemons) |

---

## Module Structure

```
:core           — Shared domain models, serialization, state reconciliation, and echo suppression
:app-phone      — Handheld application (Jetpack Compose UI, NotificationListenerService, BroadcastReceiver)
:app-wear       — Wear OS application (Wear Compose UI, WearableListenerService, TileService)
```

**Alarm Synchronization Workflow:**
```
Handheld alarm state mutation
  → BroadcastReceiver (ACTION_NEXT_ALARM_CLOCK_CHANGED)
  → Echo suppression validation
  → Wearable MessageClient transport
  → WearableListenerService on smartwatch
  → ACTION_SET_ALARM (EXTRA_SKIP_UI=true) → Native smartwatch clock updated
```

**Remote Alarm Dismissal Workflow:**
```
Handheld alarm begins ringing
  → PhoneAlarmNotificationListener resolves notification Action Intent
  → Ringing state dispatched to smartwatch via MessageClient
  → WearRingingAlertActivity presents remote dismissal interface
  → Dismissal confirmation transmitted back to handheld
  → actionIntent.send() executed → Ringing alarm silenced natively
```

---

## 📦 Modern Tech Stack (Modern Toolchain Baseline)

- **Build System:** AGP 9.4.1 + Gradle 8.9+ + Version Catalogs (`libs.versions.toml`)
- **Language & Plugins:** Kotlin 2.4.10 + KSP 2.3.10 + Kotlin Compose Compiler
- **Phone UI:** Jetpack Compose (BOM `2026.09.00`) + Material 3 + Activity Compose `1.13.0`
- **Watch UI:** Wear Compose `1.5.0` (BOM `1.5.0`) + Horologist `0.7.15`
- **AndroidX & Lifecycle:** Core-KTX `1.19.0`, Lifecycle `2.11.0`, Work `2.11.2`, Splashscreen `1.2.0`
- **Transport:** `com.google.android.gms:play-services-wearable:20.0.1` (MessageClient)
- **Serialization & Coroutines:** `kotlinx.serialization` `1.11.0` + `kotlinx.coroutines` `1.11.0`

---

## 🔐 Release Signing (via local.properties)

Add your signing credentials to `local.properties` (which is git-ignored and never committed):

```properties
KEYSTORE_FILE=path/to/your-release-key.jks
KEYSTORE_PASSWORD=your_keystore_password
KEY_ALIAS=your_key_alias
KEY_PASSWORD=your_key_password
```

Or provide them via environment variables when running CI/CD:
```powershell
$env:KEY_ALIAS="<your_key_alias>"
$env:KEYSTORE_PASSWORD="<your_keystore_password>"
$env:KEY_PASSWORD="<your_key_password>"

# Assemble Release APKs
./gradlew :app-phone:assembleRelease
./gradlew :app-wear:assembleRelease
```

---

## ⚙️ Architectural Decisions & Technical Rationale

### 1. Pure Native Framework vs. Daemon-Based Architectures
* **Memory & Process Lifecycle:** External daemon architectures (e.g., local root socket servers) impose permanent RAM overhead and require foreground services to avoid OEM process termination. `wear-alarm-sync` relies entirely on passive, event-driven OS entry points (`BroadcastReceiver` and `WearableListenerService`), achieving zero CPU and memory consumption when alarms are idle.
* **Security & Attestation Compliance:** Enterprise Mobile Device Management (MDM), corporate Intune policies, and banking applications verify hardware-backed attestation and enforce compliance checks that disallow persistent ADB debugging or elevated root daemons. Implementing standard AOSP contracts (`AlarmClock.ACTION_SET_ALARM`, `NotificationListenerService`) allows full functionality while maintaining device compliance.
* **Permission Persistence:** Android writes package permission grants (`DUMP`, notification listener bindings, and battery whitelist entries) directly to the system partition under `/data/system/packages.xml`. Consequently, all granted permissions survive device reboots and remain active after Developer Options are permanently turned off.

---

## 🚀 Deployment & Setup Guide

### 1. Compile Application Artifacts
```powershell
# Assemble debug packages
./gradlew :app-phone:assembleDebug
./gradlew :app-wear:assembleDebug
```

### 2. Deploy to Handheld Device
```powershell
# Install application
adb -d install -r app-phone/build/outputs/apk/debug/app-phone-debug.apk

# Exempt from Doze battery restrictions (ensures BLE transport while in deep sleep)
adb -d shell dumpsys deviceidle whitelist +dev.sync.alarm

# Grant NotificationListenerService binding authority
adb -d shell cmd notification allow_listener dev.sync.alarm/dev.sync.phone.notification.PhoneAlarmNotificationListener

# Grant DUMP privilege (allows inspection of system alarm table)
adb -d shell pm grant dev.sync.alarm android.permission.DUMP
```

### 3. Deploy to Wear OS Smartwatch
```powershell
# Connect via Wi-Fi ADB
adb connect <watch-ip>:<port>

# Install application
adb -s <watch-ip>:<port> install -r app-wear/build/outputs/apk/debug/app-wear-debug.apk

# Exempt from Wear OS ambient power restrictions
adb -s <watch-ip>:<port> shell dumpsys deviceidle whitelist +dev.sync.alarm

# Grant smartwatch NotificationListenerService binding authority
adb -s <watch-ip>:<port> shell cmd notification allow_listener dev.sync.alarm/dev.sync.wear.notification.WearAlarmNotificationListener

# Grant DUMP privilege
adb -s <watch-ip>:<port> shell pm grant dev.sync.alarm android.permission.DUMP
```

> [!WARNING]
> **Essential Post-Installation Step — Disable Wireless Debugging:**
> Toggle **Wireless Debugging** and **Developer Options** to **OFF** on the smartwatch immediately after running the commands above.
> *Technical Cause:* Wear OS prevents the Wi-Fi modem from entering low-power sleep modes while ADB daemon sockets are listening. Leaving wireless debugging active prevents hardware deep sleep, resulting in accelerated battery drain (15–25% per hour). Because granted permissions persist in system configuration tables, debugging is not required during normal operation.

---

### 4. Functional Verification Workflow

1. **Verify Alarm State Synchronization:**
   * Create or toggle an alarm in the native handheld clock application.
   * Open the native smartwatch clock application; confirm that the alarm entry is reflected with identical trigger time and recurrence days.
2. **Verify Remote Control & Dismissal:**
   * Schedule a test alarm on the handheld for 1 minute in the future.
   * When the alarm fires, the smartwatch automatically presents the full-screen `WearRingingAlertActivity` dialog over Bluetooth LE.
   * Tap **Dismiss** or **Snooze** on the smartwatch; verify the handheld alarm immediately executes the native dismissal action.

---

## 🧪 Run Core Tests
```powershell
./gradlew :core:test
```

---

## 📋 Milestones

- [x] **Milestone 1 — Core:** Data models, serialization, echo suppressor, diff engine, unit tests
- [x] **Milestone 2 — Phone:** Alarm reader/injector, NLS, broadcast receiver, Wearable transport, Compose UI
- [x] **Milestone 3 — Watch:** Alarm reader/injector, NLS, WearableListenerService, Wear Compose UI, Ringing Alert, Quick Glance Tile
- [ ] **Milestone 4 — E2E Test**
- [ ] **Milestone 5 — Polish**
