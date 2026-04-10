# Health Connect Spec — Phase A (Read-Only)

## Overview

PowerME integrates with Android Health Connect to surface biometric and recovery data in the Settings screen. Phase A is read-only with manual sync trigger. Phase B (writes — pushing completed workouts as `ExerciseSessionRecord`) is planned but out of scope here.

---

## 1. Permissions

### Declared in `AndroidManifest.xml`

| Permission | Data Type |
|---|---|
| `READ_WEIGHT` | Body weight (kg) |
| `READ_BODY_FAT` | Body fat percentage |
| `READ_HEIGHT` | Height (cm) |
| `READ_SLEEP` | Sleep session duration (minutes) |
| `READ_HEART_RATE_VARIABILITY` | HRV RMSSD (ms) |
| `READ_RESTING_HEART_RATE` | Resting heart rate (bpm) |
| `READ_STEPS` | Step count (today) |

`READ_EXERCISE` is intentionally omitted from Phase A — no `ExerciseSessionRecord` code exists yet. Add it when Phase B lands.

### `health_permissions.xml`

Required by the Health Connect SDK to register this app's permission set. Referenced via `meta-data` on `PermissionsRationaleActivity`.

---

## 2. Data Types Read

| Record Class | Method | Returns | Time Window |
|---|---|---|---|
| `WeightRecord` | `getLatestWeight()` | `Double?` (kg) | Last 30 days |
| `HeightRecord` | `getLatestHeight()` | `Float?` (cm) | Last 365 days |
| `BodyFatRecord` | `getLatestBodyFat()` | `Double?` (%) | Last 30 days |
| `SleepSessionRecord` | `getSleepDurationMinutes()` | `Int?` (minutes) | Last 30 days |
| `HeartRateVariabilityRmssdRecord` | `getHeartRateVariability()` | `Double?` (ms) | Last 30 days |
| `RestingHeartRateRecord` | `getRestingHeartRate()` | `Int?` (bpm) | Last 30 days |
| `StepsRecord` | `getSteps()` | `Int?` (count) | Today (midnight to now) |

All reads: most recent record in the window, graceful `null` if not granted or no data.

---

## 3. Sync Trigger

Phase A: **manual only**. User taps "Sync Now" in the Settings Health Connect card.

- No background sync, no `WorkManager`, no periodic jobs.
- On sync: calls `HealthConnectManager.syncAndRead()` → writes `HealthConnectSync` row to Room → returns `HealthConnectReadResult` to ViewModel.
- On init: `checkHealthConnectStatus()` loads last sync data silently (no new HC query on app open, just reads the last Room row).

---

## 4. Data Flow

```
SettingsScreen
  [Connect button] → PermissionController.createRequestPermissionResultContract()
  [Sync Now button] → SettingsViewModel.syncHealthConnect()
    → HealthConnectManager.syncAndRead()
        syncHealthData()     → writes HealthConnectSync row to Room (sleep/HRV/RHR/steps + flags)
        readAllData()        → reads all 7 HC data types, returns HealthConnectReadResult
    → SettingsUiState updated with healthConnectData
```

### Persistence

- `HealthConnectSync` entity (Room, `health_connect_sync` table) — keyed by `LocalDate`, one row per day.
- Stores: `sleepDurationMinutes`, `hrv`, `rhr`, `steps`, `highFatigueFlag`, `anomalousRecoveryFlag`, `syncTimestamp`.
- Body metrics (weight, height, body fat) are NOT persisted to Room in Phase A — they are held in `SettingsUiState.healthConnectData` only.

---

## 5. UI Contract

### Settings Card States

**State 1 — Unavailable** (HC app not installed on device)
- Info text: "Health Connect is not available on this device. Install it from the Play Store to sync health data."
- No buttons.

**State 2 — Not Connected** (available but permissions not granted)
- Description text explaining what data is read and why.
- "Connect" button → launches `PermissionController.createRequestPermissionResultContract()` with `ALL_PERMISSIONS`.

**State 3 — Connected** (permissions granted)
- Status line: green "Connected" indicator + "Last sync: X min ago" (or "No sync yet" if null).
- Metrics grid (compact, 2-column):
  - Row 1: Sleep | HRV
  - Row 2: RHR | Steps
  - Row 3: Weight | Body Fat | Height
  - Shows "--" for null values.
- "Sync Now" button (or `CircularProgressIndicator` while `healthConnectSyncing = true`).
- Error text (red) if `healthConnectError != null`.

### Permission Rationale Activity

`PermissionsRationaleActivity` — launched by Health Connect when user taps "More info" in the system permission dialog. Simple Compose screen with one paragraph per data type explaining why it's needed. "Got it" button calls `finish()`.

---

## 6. Error States

| Error | User-visible text |
|---|---|
| HC not available | "Health Connect is not available on this device." |
| Permissions denied | "Connect" button re-shown. |
| Read failure (exception) | Red error text under Sync Now button. |
| No data for a metric | "--" shown in grid cell. |

---

## 7. Derived Flags (existing `syncHealthData` logic)

Stored in `HealthConnectSync` entity:
- `highFatigueFlag` — `sleepDurationMinutes < 420` (< 7 hours)
- `anomalousRecoveryFlag` — HRV dropped > 10% vs previous sync row

These flags are computed on every sync. Used by Coach Carter / Boaz analytics (future integration).

---

## 8. Phase B — Writes (Placeholder)

Phase B will add:
- `WRITE_EXERCISE` permission declaration
- Write completed workouts as `ExerciseSessionRecord` to HC in `WorkoutViewModel.finishWorkout()`
- Import external exercise sessions from HC into History tab

No implementation in Phase A. See `WORKOUT_SPEC.md` for the `finishWorkout()` flow that Phase B will extend.
