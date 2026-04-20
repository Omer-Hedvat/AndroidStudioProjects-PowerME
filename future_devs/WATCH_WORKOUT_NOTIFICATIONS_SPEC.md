# Watch & Phone Workout Notifications

| Field | Value |
|---|---|
| **Phase** | P1 |
| **Status** | `in-progress` |
| **Effort** | L |
| **Depends on** | — |
| **Blocks** | — |
| **Touches** | `util/WorkoutTimerService.kt`, `ui/workout/WorkoutViewModel.kt`, `notification/WorkoutNotificationManager.kt` *(new)*, `notification/NotificationActionReceiver.kt` *(new)*, `data/AppSettingsDataStore.kt`, `ui/settings/SettingsViewModel.kt`, `ui/settings/SettingsScreen.kt`, `AndroidManifest.xml`, `PowerMeApplication.kt` |

---

## Overview

Push workout context to the user's wrist so they can track sets and rest without looking at their phone. Each set completion and rest timer event surfaces as a notification on connected WearOS/Galaxy Watch via Android's automatic notification bridging. When the app is backgrounded, a persistent foreground service keeps the timer alive and exposes action buttons on the lock screen.

---

## Architecture Decision: Pure Android Notifications

**WearOS companion app rejected for V1.** Reasons:
- WearOS companion requires a separate APK, Wearable Data Layer API, significant extra infrastructure.
- Android notifications auto-bridge to WearOS and Galaxy Watch with zero extra code.
- V1 goal is "workout context on the wrist", not "full watch interaction".
- V2 can add a WearOS companion if users want richer watch UI.

---

## Implementation Architecture

### Components

| Component | Role |
|---|---|
| `notification/WorkoutNotificationManager.kt` | `@Singleton` — channel creation, all notification builders and posting |
| `notification/NotificationActionReceiver.kt` | `BroadcastReceiver` — handles "Skip Rest" and "Finish Workout" button taps, forwards to service |
| `util/WorkoutTimerService.kt` | Promoted to **foreground service** — `onStartCommand()` calls `startForeground()`. Holds callbacks `onSkipRestRequested` and `onFinishWorkoutRequested` set by ViewModel after binding |
| `ui/workout/WorkoutViewModel.kt` | Calls `startForegroundService()` on workout start; posts notification updates at each event |

### Notification Channels

| Channel ID | Name | Importance | Sound | Vibration | Purpose |
|---|---|---|---|---|---|
| `powerme_workout_active` | Active Workout | LOW | None | None | Persistent foreground notification |
| `powerme_rest_timer` | Rest Timer Alerts | HIGH | None | None | Heads-up when rest ends |

Sound/vibration are disabled on both channels — `RestTimerNotifier` already handles audio (ToneGenerator) and haptics (Vibrator) correctly per `TimerSound` setting.

### Permissions Added

- `FOREGROUND_SERVICE` — required for `startForeground()`
- `FOREGROUND_SERVICE_SPECIAL_USE` — service type for workout timer
- `POST_NOTIFICATIONS` — already declared prior to this feature

---

## Event → Notification Mapping

| Event | Channel | Content |
|---|---|---|
| Workout started (`startWorkout`, `startWorkoutFromRoutine`, `startWorkoutFromPlan`) | `active` (persistent) | Title: workout name, Chronometer (elapsed), Action: "Finish Workout" |
| Rest timer started (`startRestTimer`) | `active` (update persistent) | Text: "Rest 1:28 — Set 3 of Bench Press", progress bar, Action: "Skip Rest" + "Finish Workout" |
| Rest timer ended (`onTimerFinish`) | `rest_timer` (heads-up) | Title: "Rest Complete", Text: "Bench Press — Set 3", auto-cancel 10s |
| Rest timer skipped/stopped (`stopRestTimer`) | — | Rest notification cancelled, persistent reverts to elapsed mode |
| Workout finished (`finishWorkout`) | `active` (replaces persistent) | "Workout Complete — Push Day, 45m, 12 sets", auto-cancel |
| Workout cancelled (`cancelWorkout`) | — | All notifications cancelled, foreground service stopped |

### Notification Actions

- **"Skip Rest"** → `NotificationActionReceiver` → `WorkoutTimerService.onSkipRestRequested` → `WorkoutViewModel.skipRestTimer()`
- **"Finish Workout"** → `NotificationActionReceiver` → `WorkoutTimerService.onFinishWorkoutRequested` → `WorkoutViewModel.finishWorkout()`

---

## Settings

- `AppSettingsDataStore.notificationsEnabled: Flow<Boolean>` (key: `notifications_enabled`, default: `true`)
- Toggle visible in Settings → Rest Timer card, below Haptics:
  ```
  Notifications     [Switch]
  Watch & lock screen alerts
  ```
- When disabled: persistent foreground notification still shows (Android requires it for foreground services), but rest-done heads-up and workout summary notifications are suppressed.

---

## Files Created / Modified

### New
- `app/src/main/java/com/powerme/app/notification/WorkoutNotificationManager.kt`
- `app/src/main/java/com/powerme/app/notification/NotificationActionReceiver.kt`
- `app/src/main/res/drawable/ic_notification.xml` (monochrome dumbbell icon)
- `app/src/test/java/com/powerme/app/notification/WorkoutNotificationManagerTest.kt`

### Modified
- `app/src/main/java/com/powerme/app/util/WorkoutTimerService.kt` — foreground service, callbacks
- `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` — start/stop service, post notifications
- `app/src/main/java/com/powerme/app/data/AppSettingsDataStore.kt` — `notifications_enabled` key
- `app/src/main/java/com/powerme/app/ui/settings/SettingsViewModel.kt` — toggle state + function
- `app/src/main/java/com/powerme/app/ui/settings/SettingsScreen.kt` — toggle row in Rest Timer card
- `app/src/main/AndroidManifest.xml` — permissions, service type, receiver registration
- `app/src/main/java/com/powerme/app/PowerMeApplication.kt` — channel creation on startup

---

## How to QA

1. Start a workout → notification appears in tray with workout name + elapsed timer
2. Lock phone → notification still visible with "Finish Workout" action
3. Complete a set → rest timer notification updates with countdown + "Skip Rest" + "Finish Workout" buttons
4. Rest timer ends → heads-up notification "Rest Complete — [exercise] Set N" (+ existing audio/haptic)
5. Tap "Skip Rest" on notification → timer stops, notification reverts to elapsed mode
6. Finish workout → summary notification "Workout Complete — 45m, 12 sets"
7. Cancel workout → all notifications removed
8. Settings → Rest Timer → turn off Notifications → rest-done and summary notifications suppressed
9. WearOS or Galaxy Watch (if available) → all notifications bridge automatically
10. Background app during rest timer → timer continues; notification stays alive
