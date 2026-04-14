# Health Connect — Extended Read Records Spec

## Overview

This spec covers the five additional Health Connect record types not yet read in Phase A. They extend `HealthConnectManager`, `HealthConnectSync`, and the Body & Vitals card with richer fitness and recovery signals.

**Relationship to existing specs:**
- `HEALTH_CONNECT_SPEC.md` — Phase A (currently live). All permissions, data-flow patterns, and UI contracts defined there apply here.
- `HEALTH_CONNECT_SPEC.md §8` — Phase B is reserved for **writes** (`ExerciseSessionRecord`, import external sessions). The records here are additional **reads** and do not conflict with Phase B writes.

---

## 1. New Records at a Glance

| Record Class | Signal | Priority |
|---|---|---|
| `HeartRateRecord` | Active HR time-series during workouts — intensity zones | Medium |
| `ActiveCaloriesBurnedRecord` | Calories burned — pairs with steps for NEAT picture | Medium |
| `Vo2MaxRecord` | Aerobic fitness level — written by wearables | Medium |
| `DistanceRecord` | Daily distance walked / run | Low |
| `OxygenSaturationRecord` | Peripheral O₂ saturation (SpO₂) — recovery signal | Low |

---

## 2. Permissions

### New manifest declarations

Add to `AndroidManifest.xml` `<uses-permission>` block:

```xml
<uses-permission android:name="android.permission.health.READ_HEART_RATE" />
<uses-permission android:name="android.permission.health.READ_ACTIVE_CALORIES_BURNED" />
<uses-permission android:name="android.permission.health.READ_VO2_MAX" />
<uses-permission android:name="android.permission.health.READ_DISTANCE" />
<uses-permission android:name="android.permission.health.READ_OXYGEN_SATURATION" />
```

Add the corresponding string entries to `health_permissions.xml` so Health Connect's permission rationale dialog displays them correctly.

### Permission tier

All five are **optional** — identical to how `READ_BASAL_METABOLIC_RATE` / `READ_BONE_MASS` / `READ_LEAN_BODY_MASS` are handled in Phase A:

- Added to `ALL_PERMISSIONS` set (requested at grant time alongside core permissions).
- **Not** added to `CORE_PERMISSIONS` — their absence does not prevent `AVAILABLE_GRANTED` state.
- Corresponding tiles show `"--"` when denied or no data available. No state machine change.

### Updated permission sets in `HealthConnectManager`

```kotlin
val CORE_PERMISSIONS: Set<String> = setOf(
    READ_WEIGHT, READ_BODY_FAT, READ_HEIGHT,
    READ_SLEEP, READ_HEART_RATE_VARIABILITY,
    READ_RESTING_HEART_RATE, READ_STEPS
)   // unchanged

val ALL_PERMISSIONS: Set<String> = CORE_PERMISSIONS + setOf(
    // existing optional
    READ_BASAL_METABOLIC_RATE, READ_BONE_MASS, READ_LEAN_BODY_MASS,
    // new optional
    READ_HEART_RATE, READ_ACTIVE_CALORIES_BURNED, READ_VO2_MAX,
    READ_DISTANCE, READ_OXYGEN_SATURATION
)
```

---

## 3. Data Types — Read Contracts

### 3.1 HeartRateRecord

| Field | Details |
|---|---|
| **What it contains** | Time-series of bpm samples with nanosecond timestamps. Written by wearables and smartwatches during active exercise sessions. |
| **Primary use case** | HR zone distribution during a logged workout — categorise each sample into Zone 1–5 using the user's max HR (`220 − age`). |
| **Read method** | `getWorkoutHeartRate(startTimeMs: Long, endTimeMs: Long): List<HeartRateSample>?` |
| **Query window** | Per-workout: `[workout.startTimeMs, workout.endTimeMs]`. For the Trends daily summary: today midnight to now. |
| **Return type** | `List<HeartRateSample>?` — null if not granted or no data. `HeartRateSample(timestampMs: Long, bpm: Int)`. |
| **Aggregation for sync row** | Average bpm + peak bpm + time-in-zone breakdown (Z1%…Z5%) stored in `HealthConnectSync`. |
| **Dependency** | Requires `User.ageYears` for max HR calculation. Falls back to age = 30 if null. |

#### HR Zone thresholds (% of max HR)

| Zone | Range | Label |
|---|---|---|
| Z1 | < 50% | Recovery |
| Z2 | 50–60% | Fat Burn |
| Z3 | 60–70% | Aerobic |
| Z4 | 70–85% | Threshold |
| Z5 | > 85% | Anaerobic |

#### ExerciseSessionRecord pairing (Phase B)

When Phase B lands, `HeartRateRecord` queries can be scoped to a specific `ExerciseSessionRecord` time range to overlay HR data on a completed workout in `WorkoutDetailScreen`. This spec does not implement that overlay — it is a placeholder for Phase B.

---

### 3.2 ActiveCaloriesBurnedRecord

| Field | Details |
|---|---|
| **What it contains** | Energy expended from physical activity only (excludes BMR). Written by wearables, Google Fit, Fitbit. |
| **Read method** | `getActiveCaloriesToday(): Double?` — sum of all `ActiveCaloriesBurnedRecord` samples from midnight to now. |
| **Query window** | Today (midnight to now), matching `StepsRecord` pattern. |
| **Return type** | `Double?` (kcal). Null if not granted or no data. |
| **Stored in** | `HealthConnectSync.activeCaloriesKcal: Double?` |
| **UI position** | Body & Vitals card — replaces the empty space in Row 4 or extends to Row 5 alongside Steps. Pairs with steps to present a NEAT (Non-Exercise Activity Thermogenesis) picture. |
| **NEAT composite** | `neat = activeCalories + (steps * 0.04)` — displayed as "~N kcal active" in a sub-label. Coefficient 0.04 kcal/step is a common approximation; surface as an estimate, not a precise measurement. |

---

### 3.3 Vo2MaxRecord

| Field | Details |
|---|---|
| **What it contains** | Maximal aerobic capacity (mL/kg/min). Written by Garmin, Polar, Apple Health (via HC bridge), Fitbit. Updated infrequently (days/weeks between readings). |
| **Read method** | `getLatestVo2Max(): Double?` — most recent record in the last 90 days. |
| **Query window** | Last 90 days (longer window than other vitals because wearables update it rarely). |
| **Return type** | `Double?` (mL/kg/min). Null if not granted or no data. |
| **Stored in** | `HealthConnectSync.vo2MaxMlKgMin: Double?` |
| **Qualitative tiers** (age/gender-adjusted classification is ideal but complex; use universal tiers as a starting point) | |

#### VO₂ Max universal tiers (mL/kg/min)

| Tier | Range | Label |
|---|---|---|
| Very Poor | < 25 | — |
| Poor | 25–33 | — |
| Fair | 34–42 | — |
| Good | 43–52 | — |
| Excellent | 53–62 | — |
| Superior | > 62 | — |

- Display: value + tier label in the Body & Vitals card cell.
- 30-day trend delta: compare latest vs. the value 28–35 days ago in `metric_log`.

---

### 3.4 DistanceRecord

| Field | Details |
|---|---|
| **What it contains** | Distance walked, run, or cycled. Written by step counters, GPS wearables, treadmill apps. |
| **Read method** | `getDistanceToday(): Double?` — sum from midnight to now, in metres. |
| **Query window** | Today (midnight to now). |
| **Return type** | `Double?` (metres internally; convert to km or miles via `UnitConverter` for display). |
| **Stored in** | `HealthConnectSync.distanceMetres: Double?` |
| **Display** | Unit-aware: km (METRIC) or miles (IMPERIAL) via `UnitConverter.formatDistance()`. |
| **UI position** | Body & Vitals card — paired with Steps in the same row or as a sub-label `"X km walked"` under the Steps cell. |
| **Note** | Steps + Distance can be inconsistent (phone steps vs. GPS distance from watch). Surface both raw; do not synthesise. |

---

### 3.5 OxygenSaturationRecord (SpO₂)

| Field | Details |
|---|---|
| **What it contains** | Peripheral blood oxygen saturation as a percentage. Written by smartwatches (Garmin, Fitbit, Pixel Watch). |
| **Read method** | `getLatestSpO2(): Double?` — most recent record in the last 30 days. |
| **Query window** | Last 30 days. |
| **Return type** | `Double?` (percentage 0–100). Null if not granted or no data. |
| **Stored in** | `HealthConnectSync.spo2Percent: Double?` |
| **Clinical note** | Normal resting SpO₂ is 95–100%. Values below 90% are clinically concerning. The app displays the raw value and a simple indicator; no medical advice is offered. |
| **Anomaly flag** | Add `lowSpO2Flag: Boolean` to `HealthConnectSync` — set `true` when `spo2Percent != null && spo2Percent < 92`. Used by Boaz analytics for recovery scoring (future). |
| **UI position** | Body & Vitals card Row 5 (or wherever space allows). Low priority — show "--" prominently if no wearable provides it. |

---

## 4. Database Changes

### 4.1 `HealthConnectSync` entity

Add new nullable columns (no new migration required if added as `DEFAULT NULL` — but a formal migration is preferred for clarity):

```kotlin
// New fields on HealthConnectSync
val avgHeartRateBpm: Int? = null,
val peakHeartRateBpm: Int? = null,
val hrZone1Pct: Float? = null,      // fraction 0.0–1.0
val hrZone2Pct: Float? = null,
val hrZone3Pct: Float? = null,
val hrZone4Pct: Float? = null,
val hrZone5Pct: Float? = null,
val activeCaloriesKcal: Double? = null,
val vo2MaxMlKgMin: Double? = null,
val distanceMetres: Double? = null,
val spo2Percent: Double? = null,
val lowSpO2Flag: Boolean = false,
```

### 4.2 `MetricType` enum

Add new variants for `metric_log` persistence (VO₂ Max and SpO₂ are slowly-changing body metrics worth longitudinal tracking):

```kotlin
enum class MetricType {
    WEIGHT, BODY_FAT, CALORIES, HEIGHT,
    VO2_MAX,      // mL/kg/min — store as Double
    SPO2          // percentage — store as Double
}
```

### 4.3 Migration

Bump DB version to the next available (currently 37 is claimed by CSV Import spec — coordinate).  
SQL additions on `health_connect_sync`:

```sql
ALTER TABLE health_connect_sync ADD COLUMN avgHeartRateBpm INTEGER DEFAULT NULL;
ALTER TABLE health_connect_sync ADD COLUMN peakHeartRateBpm INTEGER DEFAULT NULL;
ALTER TABLE health_connect_sync ADD COLUMN hrZone1Pct REAL DEFAULT NULL;
ALTER TABLE health_connect_sync ADD COLUMN hrZone2Pct REAL DEFAULT NULL;
ALTER TABLE health_connect_sync ADD COLUMN hrZone3Pct REAL DEFAULT NULL;
ALTER TABLE health_connect_sync ADD COLUMN hrZone4Pct REAL DEFAULT NULL;
ALTER TABLE health_connect_sync ADD COLUMN hrZone5Pct REAL DEFAULT NULL;
ALTER TABLE health_connect_sync ADD COLUMN activeCaloriesKcal REAL DEFAULT NULL;
ALTER TABLE health_connect_sync ADD COLUMN vo2MaxMlKgMin REAL DEFAULT NULL;
ALTER TABLE health_connect_sync ADD COLUMN distanceMetres REAL DEFAULT NULL;
ALTER TABLE health_connect_sync ADD COLUMN spo2Percent REAL DEFAULT NULL;
ALTER TABLE health_connect_sync ADD COLUMN lowSpO2Flag INTEGER NOT NULL DEFAULT 0;
```

---

## 5. UI — Body & Vitals Card Extensions

The current 4-row grid (§5b of `HEALTH_CONNECT_SPEC.md`) expands:

| Row | Col 1 | Col 2 | Col 3 |
|---|---|---|---|
| 1 | Age | Weight | BMI |
| 2 | Body Fat % | Height | Steps + Distance sub-label |
| 3 | Sleep | HRV | RHR |
| 4 | Lean Mass | Bone Mass | BMR |
| 5 *(new)* | Avg HR | VO₂ Max | SpO₂ |
| 6 *(new, optional)* | Active Calories | — | — |

- Row 5 and Row 6 are only rendered when at least one cell has a non-null value; otherwise suppressed entirely to avoid a row of `"--"`.
- **Active Calories** cell includes a NEAT composite sub-label: `"~N kcal active (NEAT est.)"` in 10sp muted text.
- **VO₂ Max** cell includes a tier label sub-line: e.g., `"Good"` in 10sp muted text.
- **SpO₂** cell shows a coloured dot indicator: green ≥ 95%, amber 92–94%, red < 92%.
- **HR Zones** are not surfaced in the Body & Vitals card (too much detail). They are deferred to a future Trends chart (HR Zone Distribution pie/bar, see `TRENDS_CHARTS_SPEC.md`).

### BodyVitalsState additions

```kotlin
data class BodyVitalsState(
    // ... existing fields ...
    val avgHeartRateBpm: Int? = null,
    val peakHeartRateBpm: Int? = null,
    val hrZones: HrZoneDistribution? = null,   // for future Trends chart
    val activeCaloriesKcal: Double? = null,
    val vo2MaxMlKgMin: Double? = null,
    val vo2MaxDelta30d: Double? = null,
    val distanceMetres: Double? = null,
    val spo2Percent: Double? = null,
    val lowSpO2Flag: Boolean = false,
)

data class HrZoneDistribution(
    val z1Pct: Float, val z2Pct: Float, val z3Pct: Float,
    val z4Pct: Float, val z5Pct: Float
)
```

---

## 6. Readiness Engine Integration

`ReadinessEngine` currently weights: HRV (0.45), Sleep (0.35), RHR (0.20 negated).

When VO₂ Max and SpO₂ are available, two optional boosts are applied **after** the base score is computed — they do not alter existing weights (preserves backward compatibility with tests):

| Signal | Effect |
|---|---|
| `spo2Percent < 92` | Cap readiness score at 40 (severe recovery signal) |
| `vo2MaxMlKgMin` trending up (+2 over 30d) | +3 point bonus (aerobic improvement) |
| `vo2MaxMlKgMin` trending down (−3 over 30d) | −2 point penalty (detraining signal) |

These adjustments are additive and clamped to [0, 100] by the existing engine.

No changes to `ReadinessEngineTest` existing tests — new behaviour is additive and only triggers when the new optional fields are non-null.

---

## 7. Architecture Changes

### Modified Files

| File | Change |
|---|---|
| `health/HealthConnectManager.kt` | Add 5 new read methods; extend `readAllData()` to call them concurrently; update `HealthConnectReadResult` data class |
| `data/database/HealthConnectSync.kt` | Add 12 new nullable columns |
| `data/database/PowerMEDatabase.kt` | Bump version, add migration |
| `data/repository/HealthConnectSyncDao.kt` | No query changes needed — all new fields auto-selected via `SELECT *` |
| `ui/metrics/BodyVitalsCard.kt` | Render Rows 5–6 conditionally |
| `ui/metrics/MetricsViewModel.kt` | Map new `HealthConnectReadResult` fields → `BodyVitalsState` |
| `analytics/ReadinessEngine.kt` | Add optional SpO₂ cap + VO₂ Max delta adjustments |
| `analytics/ReadinessEngineTest.kt` | Add tests for SpO₂ cap and VO₂ Max trend adjustments |
| `data/AppSettingsDataStore.kt` | No changes |

### New Files

| File | Purpose |
|---|---|
| `health/HrZoneCalculator.kt` | Stateless object — `maxHr(age)`, `zoneForBpm(bpm, maxHr)`, `aggregateZones(samples, maxHr): HrZoneDistribution` |

---

## 8. Implementation Order (by priority)

1. **HeartRateRecord + ActiveCaloriesBurnedRecord** — Medium priority. Implement together; both use today-window queries. Adds most visible value (calories is a frequently-requested metric).
2. **Vo2MaxRecord** — Medium priority. Simple latest-record read. High signal value for aerobic fitness tracking in Trends.
3. **DistanceRecord** — Low priority. Trivial read; pairs with existing Steps cell. Implement after VO₂ Max.
4. **OxygenSaturationRecord** — Low priority. Wearable coverage is limited. Implement last; ensure graceful `"--"` degradation dominates the UX.

---

## 9. Out of Scope

- HR overlay on `WorkoutDetailScreen` — deferred to Phase B (ExerciseSessionRecord pairing).
- HR Zone pie/bar chart in Trends tab — deferred to `TRENDS_CHARTS_SPEC.md`.
- Writing any of these record types back to Health Connect.
- Calorie goal / daily calorie budget UI — nutrition is out of scope for PowerME v1.
- Clinical SpO₂ alerts or push notifications — surface data only, no medical advice.
