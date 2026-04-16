# Health Connect — Extended Read Records Spec

| | |
|---|---|
| **Phase** | P4 |
| **Status** | `not-started` |
| **Effort** | M |
| **Depends on** | — |
| **Roadmap** | `ROADMAP.md §P4` |

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
| `RespiratoryRateRecord` | Breathing rate during sleep — early illness / overtraining signal | Medium |
| `SleepSessionRecord` stages | Deep / REM / light / awake breakdown — already read, stages not yet extracted | High |

> **Note on sleep stages:** `SleepSessionRecord` is already read in Phase A (live), but only total duration is extracted. Extracting stages requires no new permission — it is an enhancement to the existing read, implemented in this phase.

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

Add to `AndroidManifest.xml` `<uses-permission>` block:

```xml
<uses-permission android:name="android.permission.health.READ_RESPIRATORY_RATE" />
```

Add the corresponding string entry to `health_permissions.xml`.

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
    READ_DISTANCE, READ_OXYGEN_SATURATION, READ_RESPIRATORY_RATE
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

### 3.6 RespiratoryRateRecord

| Field | Details |
|---|---|
| **What it contains** | Average breathing rate (breaths per minute) over a time interval — typically overnight sleep. Written by Garmin, Samsung Galaxy Watch, Oura Ring via Health Connect. |
| **Read method** | `getSleepRespiratoryRate(): Double?` — average of all `RespiratoryRateRecord` samples falling within the latest sleep session's time window. |
| **Query window** | Same time range as the latest `SleepSessionRecord` (overnight). Falls back to last 24h if no sleep session found. |
| **Return type** | `Double?` (breaths/min). Null if not granted or no data. |
| **Normal range** | 12–20 bpm at rest. Elevated rate (>20) during sleep is an early signal of illness, overtraining, or autonomic disruption. |
| **Stored in** | `HealthConnectSync.sleepRespiratoryRate: Double?` |
| **Anomaly flag** | Add `elevatedRespiratoryRateFlag: Boolean` — set `true` when `sleepRespiratoryRate != null && sleepRespiratoryRate > 20`. Used by ReadinessEngine. |
| **UI position** | Not shown in Body & Vitals card directly (too clinical). Feeds ReadinessEngine and future AI trainer. |

---

### 3.7 Sleep Stage Extraction (Enhancement to Existing Read)

`SleepSessionRecord` is already read in Phase A. Currently only `duration` (endTime - startTime) is extracted. This phase extracts `stages` from the same record — no new permission required.

| Stage type | HC constant | Meaning |
|---|---|---|
| AWAKE | `STAGE_TYPE_AWAKE` | Periods of wakefulness during sleep |
| LIGHT | `STAGE_TYPE_SLEEPING_LIGHT` | Light NREM sleep |
| DEEP | `STAGE_TYPE_SLEEPING_DEEP` | Deep NREM / slow-wave sleep — primary physical recovery |
| REM | `STAGE_TYPE_SLEEPING_REM` | REM sleep — cognitive recovery, memory consolidation |
| OUT_OF_BED | `STAGE_TYPE_OUT_OF_BED` | Out of bed during the session |

**Read logic:** Iterate `SleepSessionRecord.stages`, sum minutes by type. If `stages` is empty (wearable doesn't provide staging), all stage fields remain null — duration-only behaviour is preserved.

**New computed values stored in `HealthConnectSync`:**

| Column | Type | Formula / Notes |
|---|---|---|
| `deepSleepMinutes` | `Int?` | Sum of SLEEPING_DEEP stage durations |
| `remSleepMinutes` | `Int?` | Sum of SLEEPING_REM stage durations |
| `lightSleepMinutes` | `Int?` | Sum of SLEEPING_LIGHT stage durations |
| `awakeMinutes` | `Int?` | Sum of AWAKE stage durations |
| `sleepEfficiency` | `Float?` | `(totalMinutes - awakeMinutes) / totalMinutes` — null if awakeMinutes null |
| `sleepScore` | `Int?` | Computed 0–100: `0.35 * durationScore + 0.30 * deepPctScore + 0.20 * remPctScore + 0.15 * efficiencyScore`. Each sub-score is 0–100 based on target ranges. Null if any component is null. |

**Sleep score target ranges (for sub-score normalisation):**

| Component | Target / Optimal | Scoring |
|---|---|---|
| Duration | 7–9h = 100, <6h or >10h = 0, linear interpolation | |
| Deep % | 15–25% of total = 100, <10% = 0, linear | |
| REM % | 20–25% of total = 100, <15% = 0, linear | |
| Efficiency | ≥95% = 100, <80% = 0, linear | |

**Wearable coverage:** Garmin, Samsung Galaxy Watch, Oura, Google Pixel Watch, Fitbit all provide sleep stages via Health Connect. Devices without staging (most budget trackers, phones) produce empty `stages` — the existing duration path covers them.

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
// Sleep stages (§3.7)
val deepSleepMinutes: Int? = null,
val remSleepMinutes: Int? = null,
val lightSleepMinutes: Int? = null,
val awakeMinutes: Int? = null,
val sleepEfficiency: Float? = null,
val sleepScore: Int? = null,
// Respiratory rate (§3.6)
val sleepRespiratoryRate: Double? = null,
val elevatedRespiratoryRateFlag: Boolean = false,
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

**Current DB is v38 (April 2026) — assign the next available version at implementation time. Coordinate with any other pending P2/P3 migrations that may have bumped the version. Verify `PowerMEDatabase.kt` before implementing.**  
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
ALTER TABLE health_connect_sync ADD COLUMN deepSleepMinutes INTEGER DEFAULT NULL;
ALTER TABLE health_connect_sync ADD COLUMN remSleepMinutes INTEGER DEFAULT NULL;
ALTER TABLE health_connect_sync ADD COLUMN lightSleepMinutes INTEGER DEFAULT NULL;
ALTER TABLE health_connect_sync ADD COLUMN awakeMinutes INTEGER DEFAULT NULL;
ALTER TABLE health_connect_sync ADD COLUMN sleepEfficiency REAL DEFAULT NULL;
ALTER TABLE health_connect_sync ADD COLUMN sleepScore INTEGER DEFAULT NULL;
ALTER TABLE health_connect_sync ADD COLUMN sleepRespiratoryRate REAL DEFAULT NULL;
ALTER TABLE health_connect_sync ADD COLUMN elevatedRespiratoryRateFlag INTEGER NOT NULL DEFAULT 0;
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

`ReadinessEngine` currently weights: HRV (0.45), Sleep duration (0.35), RHR (0.20 negated).

**Phase A upgrade — incorporate sleep stages and respiratory rate into the base formula:**

When sleep stage data is available, replace the raw sleep duration component with the richer `sleepScore` (§3.7). When `sleepScore` is null (no staging available), fall back to the existing duration z-score. This preserves behaviour for users without staging-capable wearables.

| Signal | Weight | Notes |
|---|---|---|
| HRV RMSSD (z-score) | 0.40 | Reduced from 0.45 to make room for new signals |
| Sleep (sleepScore or duration z-score) | 0.30 | Reduced from 0.35; sleepScore preferred over raw duration |
| RHR (z-score, negated) | 0.15 | Reduced from 0.20 |
| Deep sleep minutes (z-score) | 0.10 | New — only when `deepSleepMinutes != null` |
| Respiratory rate (inverted z-score) | 0.05 | New — only when `sleepRespiratoryRate != null` |

When any new optional signal is null, its weight is redistributed proportionally among the remaining signals (same sum = 1.0). This means the engine degrades gracefully — users without wearables get the original 3-signal readiness, and no existing test breaks.

**Phase B additions — applied after base score as caps/bonuses:**

| Signal | Effect |
|---|---|
| `spo2Percent < 92` | Cap readiness at 40 (severe recovery signal) |
| `elevatedRespiratoryRateFlag == true` | −10 point penalty (illness / overtraining indicator) |
| `vo2MaxMlKgMin` trending up (+2 over 30d) | +3 point bonus (aerobic improvement) |
| `vo2MaxMlKgMin` trending down (−3 over 30d) | −2 point penalty (detraining signal) |

All adjustments clamped to [0, 100].

**Test strategy:** Existing `ReadinessEngineTest` tests pass nulls for the new fields — behaviour is identical to current. New tests cover: sleep score substitution, deep sleep contribution, respiratory rate penalty, SpO₂ cap.

---

## 7. Architecture Changes

### Modified Files

| File | Change |
|---|---|
| `health/HealthConnectManager.kt` | Add 7 new read methods (sleep stages, respiratory rate + 5 original); extend `readAllData()` concurrently; update `HealthConnectReadResult` |
| `data/database/HealthConnectSync.kt` | Add 22 new nullable columns (12 original + 8 sleep stages + 2 respiratory rate) |
| `data/database/PowerMEDatabase.kt` | Bump version, add migration |
| `data/repository/HealthConnectSyncDao.kt` | No query changes — all new fields auto-selected via `SELECT *` |
| `ui/metrics/BodyVitalsCard.kt` | Render Rows 5–6 conditionally |
| `ui/metrics/MetricsViewModel.kt` | Map new `HealthConnectReadResult` fields → `BodyVitalsState` |
| `analytics/ReadinessEngine.kt` | Updated weight formula (sleep stages + respiratory rate); SpO₂ cap + VO₂ Max delta adjustments |
| `analytics/ReadinessEngineTest.kt` | New tests for sleep score substitution, deep sleep contribution, respiratory rate penalty, SpO₂ cap |
| `data/AppSettingsDataStore.kt` | No changes |

### New Files

| File | Purpose |
|---|---|
| `health/HrZoneCalculator.kt` | Stateless object — `maxHr(age)`, `zoneForBpm(bpm, maxHr)`, `aggregateZones(samples, maxHr): HrZoneDistribution` |
| `health/SleepStageCalculator.kt` | Stateless object — `extractStages(record: SleepSessionRecord): SleepStageBreakdown`, `computeSleepScore(breakdown): Int?` |

---

## 8. Implementation Order (by priority)

1. **Sleep stage extraction** — High priority. No new permission needed. Biggest free quality improvement to the ReadinessEngine and most impactful for the AI trainer. Implement first.
2. **RespiratoryRateRecord** — Medium-high priority. One new permission, simple overnight average read. Pairs with sleep stages to complete the recovery picture. Implement second.
3. **HeartRateRecord + ActiveCaloriesBurnedRecord** — Medium priority. Implement together; both use today-window queries. Most visible user-facing value (calories, HR zones).
4. **Vo2MaxRecord** — Medium priority. Simple latest-record read. High signal for aerobic fitness trending.
5. **DistanceRecord** — Low priority. Trivial read; pairs with Steps cell.
6. **OxygenSaturationRecord** — Low priority. Wearable coverage limited. Implement last.

---

## 9. Out of Scope

- HR overlay on `WorkoutDetailScreen` — deferred to Phase B (ExerciseSessionRecord pairing).
- HR Zone pie/bar chart in Trends tab — deferred to `TRENDS_CHARTS_SPEC.md`.
- Writing any of these record types back to Health Connect.
- Calorie goal / daily calorie budget UI — nutrition is out of scope for PowerME v1.
- Clinical SpO₂ alerts or push notifications — surface data only, no medical advice.
