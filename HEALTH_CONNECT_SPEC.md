# Health Connect Spec — Phase A (Read-Only)

## Overview

PowerME integrates with Android Health Connect to surface biometric and recovery data in the Settings screen. Phase A is read-only with manual sync trigger. Phase B (writes — pushing completed workouts as `ExerciseSessionRecord`) is planned but out of scope here.

---

## 1. Permissions

### Declared in `AndroidManifest.xml`

| Permission | Data Type | Required? |
|---|---|---|
| `READ_WEIGHT` | Body weight (kg) | Core |
| `READ_BODY_FAT` | Body fat percentage | Core |
| `READ_HEIGHT` | Height (cm) | Core |
| `READ_SLEEP` | Sleep session duration (minutes) | Core |
| `READ_HEART_RATE_VARIABILITY` | HRV RMSSD (ms) | Core |
| `READ_RESTING_HEART_RATE` | Resting heart rate (bpm) | Core |
| `READ_STEPS` | Step count (today) | Core |
| `READ_BASAL_METABOLIC_RATE` | BMR (kcal/day) — written by Renpho/smart scales | Optional |
| `READ_BONE_MASS` | Bone mass (kg) — written by Renpho/smart scales | Optional |
| `READ_LEAN_BODY_MASS` | Lean body mass / fat-free mass (kg) — written by Renpho/smart scales | Optional |

**Core permissions** (7): All must be granted for the Body Vitals card to enter `AVAILABLE_GRANTED` state. `checkPermissionsGranted()` checks only `CORE_PERMISSIONS`.

**Optional permissions** (3): Requested at permission-grant time via `ALL_PERMISSIONS`. If denied, the corresponding tile shows "--" (graceful degradation). No state change.

`READ_EXERCISE` is intentionally omitted from Phase A — no `ExerciseSessionRecord` code exists yet. Add it when Phase B lands.

### `health_permissions.xml`

Required by the Health Connect SDK to register this app's permission set. Referenced via `meta-data` on `PermissionsRationaleActivity`.

### Manifest Declarations (both required for cross-version support)

**`PermissionsRationaleActivity`** — two intent-filter registrations:

1. `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE` (direct on the Activity)  
   → Android 13 / standalone HC APK (`com.google.android.apps.healthdata`) path.

2. `android.intent.action.VIEW_PERMISSION_USAGE` + category `android.intent.category.HEALTH_PERMISSIONS` (via `<activity-alias>` named `.health.ViewPermissionUsageActivity`)  
   → Android 14+ / framework HC (`com.google.android.healthconnect.controller`) path. **This alias is mandatory on API 34+.** Without it the framework HC controller's pre-flight package-manager query fails and `launcher.launch()` returns an empty granted set in ~93ms with no dialog shown.  
   → Protected by `android:permission="android.permission.START_VIEW_PERMISSION_USAGE"` so only the system can invoke it.

Both registrations must coexist. Removing either breaks the corresponding platform version.

### `<queries>` stanza

```xml
<package android:name="com.google.android.healthconnect.controller" />  <!-- API 34+ built-in -->
<package android:name="com.google.android.apps.healthdata" />            <!-- API 28–33 standalone -->
<intent>
    <action android:name="android.health.connect.action.REQUEST_HEALTH_PERMISSIONS" />
</intent>
```

### ADB diagnostics (Android 14+)

`android.permission.health.*` permissions are dual-gated: standard pm runtime registry **and** the HC service's own per-app registry. `pm grant` / `pm revoke` only write to pm's registry; `PermissionController.getGrantedPermissions()` reads the HC service's registry. For reset/debug use `cmd health_connect`:

```bash
adb shell cmd health_connect get-permissions com.powerme.app
adb shell cmd health_connect revoke-all-permissions com.powerme.app
```

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
| `BasalMetabolicRateRecord` | `getLatestBmr()` | `Double?` (kcal/day) | Last 30 days |
| `BoneMassRecord` | `getLatestBoneMass()` | `Double?` (kg) | Last 30 days |
| `LeanBodyMassRecord` | `getLatestLeanBodyMass()` | `Double?` (kg) | Last 30 days |

All reads: most recent record in the window, graceful `null` if not granted or no data. The last 3 rows require the optional permissions and return `null` when denied.

---

## 3. Sync Trigger

Phase A: **manual only**. User taps "Sync Now" in the Settings Health Connect card.

- No background sync, no `WorkManager`, no periodic jobs.
- On sync: calls `HealthConnectManager.syncAndRead()` → writes `HealthConnectSync` row to Room → returns `HealthConnectReadResult` to ViewModel.
- On init: `checkHealthConnectStatus()` loads last sync data silently (no new HC query on app open, just reads the last Room row).

---

## 4. Data Flow

```
SettingsScreen / MetricsScreen (Trends tab)
  [Connect button] → PermissionController.createRequestPermissionResultContract()
  [Sync Now button] → SettingsViewModel.syncHealthConnect() OR MetricsViewModel.syncHealthConnect()
    → HealthConnectManager.syncAndRead()
        readAllData()        → reads all 10 HC data types concurrently, returns HealthConnectReadResult
        writes HealthConnectSync row to Room (sleep/HRV/RHR/steps + derived flags)
        MetricLogRepository.upsertTodayIfChanged() → WEIGHT / BODY_FAT / HEIGHT / BMR / BONE_MASS / LEAN_BODY_MASS persisted to metric_log
        UserSessionManager.updateBodyMetricsFromHc() → User entity patched (weightKg/bodyFatPercent/heightCm)
    → caller UiState updated
```

### Persistence

- `HealthConnectSync` entity (Room, `health_connect_sync` table) — keyed by `LocalDate`, one row per day.
  - Stores: `sleepDurationMinutes`, `hrv`, `rhr`, `steps`, `highFatigueFlag`, `anomalousRecoveryFlag`, `syncTimestamp`.
- Body metrics (weight, body fat, height) **are now persisted** on every sync:
  - `metric_log` table via `MetricLogRepository.upsertTodayIfChanged()` — deduped per day (replaces today's row if value changed; appends new row if prior row is from a different day).
  - `users` table via `UserSessionManager.updateBodyMetricsFromHc()` — patches `weightKg`, `bodyFatPercent`, `heightCm` fields on the current user.

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

---

## 5b. Trends Tab — Body & Vitals Card

A `BodyVitalsCard` composable renders at the top of `MetricsScreen` (Trends tab), above the Boaz Insights section. It has four render states driven by `HcAvailability`:

| State | Trigger | UI |
|---|---|---|
| `CHECKING` | On init before availability check resolves | Spinner + "Checking Health Connect…" |
| `UNAVAILABLE` | `HealthConnectManager.isAvailable() == false` | Label + "Install Health Connect…" text, no buttons |
| `AVAILABLE_NOT_GRANTED` | HC available but permissions not granted | Label + description + "Connect" button → navigates to Settings |
| `AVAILABLE_GRANTED` | HC available and all permissions granted | Full metrics grid (see below) |

### Connected state metrics grid (4 rows × 3 columns)

| Row 1 | Age | Weight | BMI |
| Row 2 | Body Fat % | Height | Steps Today |
| Row 3 | Sleep | HRV | RHR |
| Row 4 | Lean Mass | Bone Mass | BMR |

- Each cell: label (10sp, muted), value (15sp semibold), optional sub-line (10sp muted).
- Weight, Body Fat, and Lean Mass cells show 7-day trend arrows when delta ≥ 0.05 (upward = TimerRed, downward = TimerGreen).
- BMI shows a qualitative label: underweight / healthy / overweight / obese.
- Sleep formatted as Xh Ym.
- `"--"` shown for null values.
- Row 4 tiles show "--" when optional body-composition permissions are denied or no smart-scale data has been synced.
- Header row: "BODY & VITALS" title + "Last sync: X min ago" (or "No sync yet") + sync icon button (or spinner while `isSyncing`).
- Error text (MaterialTheme.colorScheme.error) shown below header if `syncError != null`.

### State data class

```kotlin
data class BodyVitalsState(
    val hcAvailability: HcAvailability = HcAvailability.CHECKING,
    val age: Int? = null,
    val weightKg: Double? = null,
    val bodyFatPct: Double? = null,
    val heightCm: Double? = null,
    val bmi: Double? = null,
    val weightDelta7d: Double? = null,
    val bodyFatDelta7d: Double? = null,
    val sleepMinutes: Int? = null,
    val hrvMs: Double? = null,
    val rhrBpm: Int? = null,
    val stepsToday: Int? = null,
    val lastSyncTimestamp: Long? = null,
    val isSyncing: Boolean = false,
    val syncError: String? = null,
    val bmrKcal: Double? = null,            // kcal/day (optional, from smart scale)
    val boneMassKg: Double? = null,         // kg (optional, from smart scale)
    val leanBodyMassKg: Double? = null,     // kg (optional, from smart scale)
    val leanBodyMassDelta7d: Double? = null // 7d trend for lean mass
)
```

### Data sources

- **Age**: `User.age` field (manually entered at profile setup).
- **Weight, Body Fat, Height**: latest entry in `metric_log` (post-HC sync they are auto-persisted there); falls back to `User.weightKg` / `User.bodyFatPercent` / `User.heightCm`.
- **Sleep, HRV, RHR, Steps**: `HealthConnectSync` latest row from `health_connect_sync` table.
- **BMI**: computed as `weightKg / (heightCm/100)^2`.
- **7d deltas**: find the closest `metric_log` entry in the 7–10 day window; delta = latest − reference.

### Sync trigger

Inline sync icon button in the card header calls `MetricsViewModel.syncHealthConnect()`, which delegates to `HealthConnectManager.syncAndRead()` (same as Settings card). The "Connect" CTA navigates to the Settings screen (permission flow stays in Settings).

---

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
