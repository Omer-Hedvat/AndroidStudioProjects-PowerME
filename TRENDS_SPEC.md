# TRENDS_SPEC.md — PowerME Trends Tab

**Status:** 🚧 In Progress (v1.0 — April 2026)
**Domain:** Trends Tab · Performance Analytics · Health Connect Integration · Charting
**Route:** `Screen.Trends` → `MetricsScreen` (composable to be restructured)

> **Living document.** Update this file whenever a chart, algorithm, or data source changes in the Trends tab.
> Cross-referenced by `CLAUDE.md`, `HEALTH_CONNECT_SPEC.md`, `HISTORY_ANALYTICS_SPEC.md`.

---

## Table of Contents

1. [Philosophy & Goals](#1-philosophy--goals)
2. [Screen Layout](#2-screen-layout)
3. [Feature Cards](#3-feature-cards)
   - 3.1 [BodyVitalsCard (Existing)](#31-bodyvitalscard-existing)
   - 3.2 [Readiness Gauge](#32-readiness-gauge)
   - 3.3 [e1RM Progression Chart](#33-e1rm-progression-chart)
   - 3.4 [Volume Load Trend Chart](#34-volume-load-trend-chart)
   - 3.5 [Muscle Group Volume Breakdown](#35-muscle-group-volume-breakdown)
   - 3.6 [Effective Sets Chart](#36-effective-sets-chart)
   - 3.7 [Body Composition Overlay](#37-body-composition-overlay)
   - 3.8 [NEAT Guardrail (Steps Trend)](#38-neat-guardrail-steps-trend)
   - 3.9 [Chronotype & Sleep Analysis](#39-chronotype--sleep-analysis)
   - 3.10 [Boaz's Insights (Existing)](#310-boazs-insights-existing)
4. [Architecture & Infrastructure](#4-architecture--infrastructure)
5. [New DAO Queries](#5-new-dao-queries)
6. [Readiness Score Algorithm](#6-readiness-score-algorithm)
7. [Vico Chart Integration Guide](#7-vico-chart-integration-guide)
8. [Existing Infrastructure to Reuse](#8-existing-infrastructure-to-reuse)
9. [Known Gaps & Deferred Work](#9-known-gaps--deferred-work)
10. [Body Heatmap (Future Phase)](#10-body-heatmap-future-phase)
11. [Testing Strategy](#11-testing-strategy)
12. [Implementation Order](#12-implementation-order)

---

## 1. Philosophy & Goals

### 1.1 Core Philosophy: "Active Performance Optimization"

PowerME Trends is not a passive log viewer. It is a **data-hardened optimization engine** for advanced, hypertrophy-focused lifters. By fusing internal workout metrics with physiological sensor data from Health Connect, the tab shifts the app from "what did I do?" to "what should I do next?"

The guiding question at each cognitive layer:

| Layer | Question | Primary Signal |
|---|---|---|
| **Now** | Am I ready to train hard today? | HRV, RHR, Sleep |
| **Long Game** | Am I getting stronger and leaner? | e1RM, Volume, Body Composition |
| **Deep Dive** | Am I training the right muscles enough? | Per-muscle volume, effective sets, NEAT |

### 1.2 Design Principles

- **Progressive disclosure** — Most important insight first. Scroll down for more detail.
- **Data honesty** — Never show a metric that has no data. Empty states are informative, not blank.
- **Personal baselines** — Every score is relative to the user's own history, not population norms.
- **Passive, not intrusive** — The Trends tab observes and reports. It does not inject warnings into other screens.
- **Pro Tracker v6.0 aesthetic** — Dark palette, BarlowCondensed headers, data-ink maximized.

---

## 2. Screen Layout

The Trends tab is a single `LazyColumn`. Cards are rendered in this fixed scroll order:

| Position | Card | Status |
|---|---|---|
| 1 | `BodyVitalsCard` | ✅ Existing |
| 2 | `ReadinessGaugeCard` | 🚧 New |
| 3 | `E1RMProgressionCard` | 🚧 New |
| 4 | `VolumeTrendCard` | 🚧 New |
| 5 | `MuscleGroupVolumeCard` | 🚧 New |
| 6 | `EffectiveSetsCard` | 🚧 New |
| 7 | `BodyCompositionCard` | 🚧 New |
| 8 | `StepsTrendCard` | 🚧 New |
| 9 | `ChronotypeCard` | 🚧 New |
| 10 | Boaz's Insights section | ✅ Existing, pushed to bottom |

Each card follows this structural contract:

```kotlin
// Every trend card is a Card with:
// - ProSurface background
// - BarlowCondensed SemiBold header (labelMedium or titleSmall)
// - ProSubGrey subtitle/timeframe label
// - Content area (chart or metric grid)
// - Optional "See more" / time-range selector row at bottom
```

### 2.1 Time Range Selector

Cards that display time-series data should support a shared time range state (driven by `TrendsViewModel`). Options: **1M / 3M / 6M / 1Y**. Default: **3M**.

The selected range is a single `StateFlow<TrendsTimeRange>` in `TrendsViewModel` shared across all chart cards. Changing the range re-queries all charts.

```kotlin
enum class TrendsTimeRange(val label: String, val days: Int) {
    ONE_MONTH("1M", 30),
    THREE_MONTHS("3M", 90),
    SIX_MONTHS("6M", 180),
    ONE_YEAR("1Y", 365)
}
```

### 2.2 Conditional Card Rendering (hide-when-empty)

Each data-driven chart card is conditionally rendered based on a corresponding `Boolean StateFlow` in `TrendsViewModel`. Cards with no data are omitted entirely — no empty states shown.

| Card | ViewModel flag | Hidden when |
|---|---|---|
| `VolumeTrendCard` | `hasVolumeData` | 0 volume data points for selected time range |
| `E1RMProgressionCard` | `hasE1rmData` | 0 sessions for selected exercise |
| `MuscleGroupVolumeCard` | `hasMuscleGroupData` | 0 muscle-group volume rows |
| `EffectiveSetsCard` | `hasEffectiveSetsData` | 0 effective-set rows |
| `BodyCompositionCard` | `hasBodyCompositionData` | 0 weight AND 0 body fat records |
| `ChronotypeCard` | `hasChronotypeData` | Both sleep AND workout-time points are empty |

**InfoCard:** When `hiddenCardCount >= 3` (out of the 6 data-driven cards), a bottom-of-list `Card` shows static copy: *"Some charts are hidden — they appear once you have enough data logged."* Disappears once fewer than 3 cards are hidden.

**BodyVitalsCard** and **ReadinessGauge** are always shown (they tolerate sparse data via their own loading states).

---

## 3. Feature Cards

---

### 3.1 BodyVitalsCard (Existing)

**File:** `ui/metrics/BodyVitalsCard.kt`

No changes required. Stays at the top of the screen as the snapshot of today's biometrics. Displays: Age, Weight (+7d delta), BMI, Body Fat (+7d delta), Height, Steps, Sleep, HRV, RHR.

**Data sources:** `HealthConnectSyncDao.getLatestSync()`, `MetricLogRepository.getByType(WEIGHT/BODY_FAT)`, `UserSessionManager.getCurrentUser()`

---

### 3.2 Readiness Gauge

**Composable:** `ReadinessGaugeCard`
**ViewModel state:** `StateFlow<ReadinessState>` in `TrendsViewModel`
**Data source:** `HealthConnectSyncDao.getRecentSyncs()` — 30-day Flow (currently unused, ready-made)

#### Purpose

A hero gauge that answers "Am I recovered today?" using actual CNS/recovery biomarkers, not heuristic lifestyle factors. Replaces age/chronotype-based guesswork.

#### UI Design

- **Card height:** ~220dp
- **Chart type:** Custom Canvas arc (240-degree sweep). Vico does not have gauge widgets.
- Arc colors: gradient from `ProError` (left, 0%) → amber `ReadinessAmber` (center, 50%) → `TimerGreen` (right, 100%)
- Needle/indicator at the computed score position
- Score displayed numerically inside the arc (e.g., "74")
- Label below score: "RECOVERED" / "MODERATE" / "FATIGUED"
- Below gauge: three sub-metrics row — HRV delta, RHR delta, Sleep vs target (with directional arrows using `TimerGreen`/`TimerRed`)

#### Color Tier Mapping

| Score | Color | Label | Hex |
|---|---|---|---|
| 70–100 | `TimerGreen` | RECOVERED | `#4CC990` |
| 40–69 | `ReadinessAmber` | MODERATE | `#FFB74D` (new token) |
| 0–39 | `ProError` | FATIGUED | `#E05555` |

`ReadinessAmber` must be added to `Color.kt` and documented in `THEME_SPEC.md §2`.

#### Empty / Calibrating State

- Fewer than 5 days of HC sync history → show "Calibrating… sync for 5 days to unlock Readiness"
- Any metric null for today → compute with available metrics only (re-normalize weights)
- HC not connected → show "Connect Health Connect in Settings to unlock Readiness"

#### Algorithm

See **§6 Readiness Score Algorithm** for full detail.

#### Data Flow

```
HealthConnectSyncDao.getRecentSyncs(): Flow<List<HealthConnectSync>>
  → TrendsRepository.getReadinessState(): Flow<ReadinessState>
    → ReadinessEngine.compute(history: List<HealthConnectSync>): ReadinessScore
      → TrendsViewModel.readinessState: StateFlow<ReadinessState>
        → ReadinessGaugeCard composable
```

#### Acceptance Criteria

- [ ] Gauge renders with correct arc gradient and needle position
- [ ] Score updates on every HC sync
- [ ] All three empty states (calibrating, null metrics, no HC) display correctly
- [ ] Sub-metrics row shows delta arrows with correct colors
- [ ] `ReadinessAmber` token added and used only for this card

---

### 3.3 e1RM Progression Chart

**Composable:** `E1RMProgressionCard`
**ViewModel state:** `StateFlow<E1RMChartState>` in `TrendsViewModel`

#### Purpose

Track estimated 1-Rep Max over time for the user's key compound lifts. Removes high-rep set noise by computing per-session peak e1RM via the Epley formula, then optionally smoothing with Bayesian shrinkage.

#### UI Design

- **Chart type:** Vico `CartesianChart` with `LineCartesianLayer` (multi-line)
- X-axis: date labels ("Jan 5", "Feb 12", etc.)
- Y-axis: kg (left)
- Up to 4 exercise lines, each a distinct color from: `ProViolet`, `TimerGreen`, `NeonPurple`, `ProMagenta`
- Legend row below chart: exercise name chips matching line colors
- Exercise selector: `FilterChip` row at top of card. Default selection: Bench Press, Squat, Deadlift (any exercise whose `name` contains these substrings)
- Empty state: "Log at least 2 sessions for an exercise to see progression"

#### Algorithm

For each selected exercise and each workout session:
```
sessionE1RM = MAX over all completed non-warmup sets in session:
    StatisticalEngine.calculate1RM(weight = ws.weight, reps = ws.reps)
```

Then optionally apply Bayesian smoothing (controlled by a toggle in the card header):
```
priorMean = mean of all historical sessionE1RM values for the exercise
bayesianE1RM = StatisticalEngine.calculateBayesian1RM(
    sampleMean = sessionE1RM,
    sampleSize = setsInSession,
    priorMean = priorMean
)
```

Display raw Epley values as default. "Smoothed" toggle shows Bayesian values.

#### New DAO Query

```kotlin
// New method in TrendsDao:
@Query("""
    SELECT w.timestamp AS dateMs,
           MAX(ws.weight * (1 + ws.reps / 30.0)) AS bestE1RM,
           COUNT(ws.id) AS setCount
    FROM workout_sets ws
    JOIN workouts w ON ws.workoutId = w.id
    WHERE ws.exerciseId = :exerciseId
      AND w.isCompleted = 1
      AND w.isArchived = 0
      AND w.timestamp >= :sinceMs
      AND ws.isCompleted = 1
      AND ws.setType != 'WARMUP'
      AND ws.weight > 0
      AND ws.reps > 0
    GROUP BY w.id
    ORDER BY w.timestamp ASC
""")
suspend fun getE1RMHistory(exerciseId: Long, sinceMs: Long): List<E1RMDataPoint>

data class E1RMDataPoint(val dateMs: Long, val bestE1RM: Double, val setCount: Int)
```

#### Data Flow

```
TrendsDao.getE1RMHistory(exerciseId, sinceMs)
  → TrendsRepository.getE1RMProgressionData(exerciseIds, windowDays)
    → TrendsViewModel.e1RMState: StateFlow<E1RMChartState>
      → E1RMProgressionCard composable
```

#### Acceptance Criteria

- [ ] Multi-line chart renders with correct exercise colors
- [ ] Exercise selector chips update visible lines reactively
- [ ] Bayesian smoothing toggle works
- [ ] Time range selector (1M/3M/6M/1Y) re-queries data
- [ ] Empty state shown when < 2 sessions

---

### 3.4 Volume Load Trend Chart

**Composable:** `VolumeTrendCard`
**ViewModel state:** `StateFlow<VolumeTrendState>` in `TrendsViewModel`

#### Purpose

Show weekly total training volume (kg × reps summed across all sets) over time. Optionally overlay body weight to visualize if volume increase accompanies the right body composition trend.

#### UI Design

- **Chart type:** Vico `CartesianChart` with `ColumnCartesianLayer` (volume bars, `ProViolet`) + optional `LineCartesianLayer` (body weight, `TimerGreen`, right Y-axis)
- X-axis: ISO week labels ("W1", "W4", etc.)
- Y-axis left: total volume (kg)
- Y-axis right (when overlay enabled): body weight (kg)
- "Show weight overlay" toggle in card header
- Bar tooltip on tap: exact volume + workout count for that week

#### Algorithm

Weekly volume aggregation uses the pre-computed `Workout.totalVolume` field (no need to re-sum sets):
```
weekBucket = workout.timestamp / 604_800_000  // 7 days in ms
weeklyVolume = SUM(totalVolume) GROUP BY weekBucket
```

Body weight from `MetricLogRepository.getByType(MetricType.WEIGHT)` — align to same week buckets (use the entry closest to the end of each week).

4-week moving average computed in `TrendsRepository`:
```kotlin
fun List<WeeklyVolumeRow>.fourWeekMovingAverage(): List<Double> {
    return mapIndexed { i, _ ->
        subList(maxOf(0, i - 3), i + 1).map { it.totalVolume }.average()
    }
}
```

#### New DAO Query

```kotlin
@Query("""
    SELECT (timestamp / 604800000) AS weekBucket,
           MIN(timestamp) AS weekStartMs,
           SUM(totalVolume) AS totalVolume,
           COUNT(id) AS workoutCount
    FROM workouts
    WHERE isCompleted = 1
      AND isArchived = 0
      AND timestamp >= :sinceMs
    GROUP BY weekBucket
    ORDER BY weekBucket ASC
""")
suspend fun getWeeklyVolume(sinceMs: Long): List<WeeklyVolumeRow>

data class WeeklyVolumeRow(
    val weekBucket: Long,
    val weekStartMs: Long,
    val totalVolume: Double,
    val workoutCount: Int
)
```

#### Data Flow

```
TrendsDao.getWeeklyVolume(sinceMs)
MetricLogRepository.getByType(WEIGHT)
  → TrendsRepository.getVolumeTrendData(windowDays)
    → TrendsViewModel.volumeTrendState: StateFlow<VolumeTrendState>
      → VolumeTrendCard composable
```

#### Acceptance Criteria

- [ ] Bar chart shows correct weekly totals
- [ ] Body weight overlay toggles on/off cleanly
- [ ] Dual Y-axis scales independently
- [ ] Time range selector works
- [ ] Empty state: "Log at least 2 weeks of workouts to see volume trends"

---

### 3.5 Muscle Group Volume Breakdown

**Composable:** `MuscleGroupVolumeCard`
**ViewModel state:** `StateFlow<MuscleGroupChartState>` in `TrendsViewModel`

#### Purpose

Show per-muscle-group weekly volume as a stacked bar chart. Ensures the user maintains balanced training stimulus across muscle groups and avoids chronic under-stimulation of any muscle.

#### UI Design

- **Chart type:** Vico `CartesianChart` with stacked `ColumnCartesianLayer`
- Each major muscle group (`Legs`, `Back`, `Chest`, `Shoulders`, `Arms`, `Core`, `Full Body`, `Cardio`) gets a distinct color from a fixed palette (see §7)
- Legend row below chart listing each group with its color
- X-axis: ISO week labels
- Y-axis: total volume (kg)
- Secondary display: current week's muscle group distribution as a `LinearProgressIndicator` row below the chart (% of total per group)

#### Volume Attribution Rule

Secondary muscles (where `exercise_muscle_groups.isPrimary = 0`) receive **50% credit** to avoid double-counting:
```sql
SUM(
    CASE WHEN emg.isPrimary = 1 THEN ws.weight * ws.reps
         ELSE ws.weight * ws.reps * 0.5
    END
) AS volume
```

#### New DAO Query

```kotlin
@Query("""
    SELECT (w.timestamp / 604800000) AS weekBucket,
           MIN(w.timestamp) AS weekStartMs,
           emg.majorGroup,
           SUM(
               CASE WHEN emg.isPrimary = 1 THEN ws.weight * ws.reps
                    ELSE ws.weight * ws.reps * 0.5
               END
           ) AS volume
    FROM workout_sets ws
    JOIN workouts w ON ws.workoutId = w.id
    JOIN exercise_muscle_groups emg ON ws.exerciseId = emg.exerciseId
    WHERE w.isCompleted = 1
      AND w.isArchived = 0
      AND ws.isCompleted = 1
      AND ws.setType != 'WARMUP'
      AND ws.weight > 0
      AND ws.reps > 0
      AND w.timestamp >= :sinceMs
    GROUP BY weekBucket, emg.majorGroup
    ORDER BY weekBucket ASC, volume DESC
""")
suspend fun getWeeklyMuscleGroupVolume(sinceMs: Long): List<MuscleGroupVolumeRow>

data class MuscleGroupVolumeRow(
    val weekBucket: Long,
    val weekStartMs: Long,
    val majorGroup: String,
    val volume: Double
)
```

#### Muscle Group Color Palette (fixed, not from theme — must be visually distinct)

| Group | Color Hex | Note |
|---|---|---|
| Legs | `#7B68EE` | Medium slate blue |
| Back | `#4CC990` | TimerGreen |
| Chest | `#E05555` | ProError |
| Shoulders | `#FFB74D` | ReadinessAmber |
| Arms | `#9B7DDB` | ProViolet |
| Core | `#9E6B8A` | ProMagenta |
| Full Body | `#A0A0A0` | ProSubGrey |
| Cardio | `#80CBC4` | Teal |

These colors are chart-only and must NOT be added to the semantic token system.

#### Data Flow

```
TrendsDao.getWeeklyMuscleGroupVolume(sinceMs)
  → TrendsRepository.getMuscleGroupBreakdownData(windowDays)
    → TrendsViewModel.muscleGroupState: StateFlow<MuscleGroupChartState>
      → MuscleGroupVolumeCard composable
```

#### Acceptance Criteria

- [ ] Stacked bars show correct per-group volume each week
- [ ] 50% secondary muscle credit applied correctly
- [ ] WARMUP sets excluded from calculation
- [ ] Distribution row below chart sums to 100%
- [ ] Empty state: "Log at least 1 week of workouts to see muscle breakdown"

---

### 3.6 Effective Sets Chart

**Composable:** `EffectiveSetsCard`
**ViewModel state:** `StateFlow<EffectiveSetsState>` in `TrendsViewModel`

#### Purpose

Advanced hypertrophy tracking: count only sets performed near failure (high RPE), not junk volume. A hard set close to failure is the primary driver of hypertrophic stimulus.

#### RPE Storage Convention

`WorkoutSet.rpe` is stored as `Int?` scaled by 10 (e.g., RPE 7.5 = stored as `75`). This is consistent with the existing `RpePickerSheet` which uses `FilterChip` values from 60 to 100 in increments of 5.

**RPE collection is already fully implemented** — picker UI, `WorkoutSetDao.updateRpe()`, ViewModel, and ghost label display all exist. No new RPE UI work is needed for this card.

#### Effective Set Definition

A set qualifies as "effective" if:
- `ws.rpe >= 70` (RPE ≥ 7.0, equivalent to RIR ≤ 3)
- `ws.isCompleted = 1`
- `ws.setType != 'WARMUP'` and `ws.setType != 'DROP'`
- `ws.weight > 0` and `ws.reps > 0`

#### UI Design

- **Chart type:** Vico stacked `ColumnCartesianLayer` (same colors as §3.5)
- X-axis: ISO week labels
- Y-axis: effective set count
- Secondary display: "% of all sets that were effective" text below chart
- RPE data coverage indicator: "Based on X% of sets with RPE logged" — important for data quality transparency

#### Empty/Sparse State

- No RPE data logged at all → show "Log RPE on sets to track effective training"
- RPE data exists but < 30% coverage → show chart with coverage warning banner

#### New DAO Query

```kotlin
@Query("""
    SELECT (w.timestamp / 604800000) AS weekBucket,
           MIN(w.timestamp) AS weekStartMs,
           emg.majorGroup,
           COUNT(ws.id) AS effectiveSets
    FROM workout_sets ws
    JOIN workouts w ON ws.workoutId = w.id
    JOIN exercise_muscle_groups emg ON ws.exerciseId = emg.exerciseId
    WHERE w.isCompleted = 1
      AND w.isArchived = 0
      AND ws.isCompleted = 1
      AND ws.rpe >= 70
      AND ws.setType NOT IN ('WARMUP', 'DROP')
      AND ws.weight > 0
      AND ws.reps > 0
      AND emg.isPrimary = 1
      AND w.timestamp >= :sinceMs
    GROUP BY weekBucket, emg.majorGroup
    ORDER BY weekBucket ASC
""")
suspend fun getWeeklyEffectiveSets(sinceMs: Long): List<EffectiveSetsRow>

data class EffectiveSetsRow(
    val weekBucket: Long,
    val weekStartMs: Long,
    val majorGroup: String,
    val effectiveSets: Int
)
```

#### Data Flow

```
TrendsDao.getWeeklyEffectiveSets(sinceMs)          // sinceMs = TrendsTimeRange.sinceMs()
  → TrendsRepository.getWeeklyEffectiveSets(range)
    → TrendsViewModel._effectiveSets: StateFlow<List<EffectiveSetsChartPoint>>
      → EffectiveSetsCard composable
```

#### Chart / Scroll / Zoom Implementation Notes

- **Producer:** `TrendsViewModel.effectiveSetsModelProducer` — shared, ViewModel-scoped.
- **Scroll:** `rememberVicoScrollState(initialScroll = Scroll.Absolute.End, autoScroll = Scroll.Absolute.End, autoScrollCondition = { _, _ -> true })` — always scrolls to the most-recent bar on any model update (including when switching to a shorter range where the model shrinks, which `OnModelSizeIncreased` would miss).
- **Zoom:** `rememberVicoZoomState(initialZoom = Zoom.Content)` — fits all bars in the viewport. Wrapped in `key(timeRange)` to force re-creation on range change; this resets `initialZoom` so the zoom is recalculated for the incoming data, making the bar-count difference between 1M and 1Y visually obvious.
- **Race fix:** `effectiveSetsPushJob?.cancel()` before each `viewModelScope.launch { runTransaction { … } }` prevents a stale larger-range push from overwriting the producer after a shorter-range push completes.
- **SQL grouping:** `weekStartMs = (w.timestamp / 604800000 * 604800000)` — epoch-aligned, not `MIN(w.timestamp)`, so all muscle groups trained in the same ISO week share the same X-axis bucket.

#### Acceptance Criteria

- [x] Only RPE ≥ 7.0 sets counted
- [x] WARMUP and DROP sets excluded
- [x] Primary muscle group only (no 50% secondary credit for set counts)
- [x] RPE coverage percentage displayed in card
- [x] Time-range chips (1M / 3M / 6M / 1Y) correctly scope data; chart visually differs across ranges
- [ ] Empty state and sparse-data warning render correctly

---

### 3.7 Body Composition Overlay

**Composable:** `BodyCompositionCard`
**ViewModel state:** `StateFlow<BodyCompState>` in `TrendsViewModel`

#### Purpose

Visualize whether progressive overload (increasing volume) is translating to the right body composition outcome — muscle gain without excessive fat accumulation. This is the "are you actually growing?" validation chart.

#### UI Design

- **Chart type:** Vico `CartesianChart` with 3 `LineCartesianLayer` instances
  - 4-week moving average volume (left Y-axis, `ProViolet`, dashed line)
  - Body weight in kg (right Y-axis, `TimerGreen`, solid line)
  - Body fat % (right Y-axis, `ProMagenta`, dotted line)
- X-axis: weekly dates
- Toggle chips at top: "Volume MA" / "Weight" / "Body Fat" — each toggles its respective line

#### Algorithm

4-week moving average of weekly volume (from `TrendsDao.getWeeklyVolume()`):
```kotlin
fun computeMovingAverage(rows: List<WeeklyVolumeRow>, windowSize: Int = 4): List<Double>
```

Body weight and body fat: from `MetricLogRepository.getByType()`, aligned to weekly buckets (nearest entry per week).

#### Data Flow

```
TrendsDao.getWeeklyVolume(sinceMs)
MetricLogRepository.getByType(WEIGHT)
MetricLogRepository.getByType(BODY_FAT)
  → TrendsRepository.getBodyCompositionData(windowDays)
    → TrendsViewModel.bodyCompState: StateFlow<BodyCompState>
      → BodyCompositionCard composable
```

#### Empty State

"Log body weight and body fat regularly in Settings → Health to see this chart."

#### Acceptance Criteria

- [ ] Three-line chart renders with correct dual Y-axis
- [ ] Moving average is computed correctly (4-week rolling window)
- [ ] Line toggles work independently
- [ ] Empty state shown if < 4 weeks of any data source

---

### 3.8 NEAT Guardrail (Steps Trend)

**Composable:** `StepsTrendCard`
**ViewModel state:** `StateFlow<StepsTrendState>` in `TrendsViewModel`

#### Purpose

NEAT (Non-Exercise Activity Thermogenesis) — the calories burned through daily movement outside structured exercise — is a major determinant of body composition. Lifters often unconsciously reduce daily steps when fatigued, unknowingly suppressing their caloric deficit. This card makes that pattern visible.

#### UI Design

- **Chart type:** Vico `CartesianChart` with `ColumnCartesianLayer` (daily steps, `ProViolet`) + horizontal reference `LineCartesianLayer` (step target, `TimerGreen`)
- X-axis: date labels (last 30 days)
- Y-axis: step count
- Step target line: user-configurable (default 8,000 steps)
- Bars colored with alpha: below target = `ProError.copy(alpha=0.7)`, at/above = `TimerGreen.copy(alpha=0.8)`
- "Daily target: X steps" text with pencil edit icon (inline edit dialog)

#### Step Target Storage

Use `AppSettingsDataStore` (DataStore-based, `data/AppSettingsDataStore.kt`) rather than Room to avoid a DB migration. Add:
```kotlin
val dailyStepTarget: Flow<Int> = dataStore.data.map { it[STEP_TARGET_KEY] ?: 8000 }
suspend fun setDailyStepTarget(target: Int)
```

Note: This is a **DataStore addition only**, not a Room schema migration.

#### Data Source

`HealthConnectSyncDao.getRecentSyncs()` — the `steps` field on each `HealthConnectSync` row (already stored per sync date).

Only 30 days of data shown regardless of the global time range selector (step data is sparse and most meaningful as a recent trend).

#### Data Flow

```
HealthConnectSyncDao.getRecentSyncs(): Flow<List<HealthConnectSync>>
AppSettingsDataStore.dailyStepTarget: Flow<Int>
  → TrendsRepository.getStepsTrendData()
    → TrendsViewModel.stepsTrendState: StateFlow<StepsTrendState>
      → StepsTrendCard composable
```

#### Acceptance Criteria

- [ ] 30-day bar chart with correct step counts
- [ ] Target line at configurable value
- [ ] Bars colored red/green relative to target
- [ ] Inline edit dialog for target value (validated: 1,000–50,000 range)
- [ ] Empty state: "Sync Health Connect to see your daily steps"

---

### 3.9 Chronotype & Sleep Analysis

**Composable:** `ChronotypeCard`
**ViewModel state:** `StateFlow<ChronotypeState>` in `TrendsViewModel`

#### Purpose

Two insights in one card:
1. **Sleep trend** — track sleep duration over time to correlate with readiness and performance
2. **Optimal training window** — scatter plot of workout start time vs performance (volume) to reveal the user's peak training hours empirically

#### UI Design — Two Tabs or Two Sub-cards

**Sub-card A: Sleep Trend**
- **Chart type:** Vico `ColumnCartesianLayer` — daily sleep duration in hours
- Reference line at 7h (420 minutes = minimum threshold for `highFatigueFlag`)
- Color coding: green ≥ 7h, red < 7h
- X-axis: last 30 days

**Sub-card B: Training Window Scatter**
- **Chart type:** NOT natively supported by Vico 2.0.0-beta.2. Use Canvas drawing over a Vico chart frame, or a custom Canvas composable.
- X-axis: hour of day (0–23)
- Y-axis: total workout volume (kg)
- Each dot = one completed workout, plotted at (startHour, totalVolume)
- Highlight the hour bucket with the highest median volume in `ProViolet`
- Text summary: "Your best sessions tend to start around Xam/pm"

**Important limitation:** Health Connect only provides total sleep duration (`sleepDurationMinutes`), not sleep stages (Deep / REM). Sleep stage breakdown is **not available in Phase 1**. Do not display sleep stage data. If a future HC sync adds `SleepSessionRecord.stages`, document the extension here.

#### New DAO Query

```kotlin
@Query("""
    SELECT id,
           startTimeMs,
           totalVolume,
           (startTimeMs / 3600000 % 24) AS startHour
    FROM workouts
    WHERE isCompleted = 1
      AND isArchived = 0
      AND startTimeMs > 0
      AND timestamp >= :sinceMs
    ORDER BY timestamp ASC
""")
suspend fun getWorkoutsByTimeOfDay(sinceMs: Long): List<WorkoutTimeRow>

data class WorkoutTimeRow(
    val id: String,
    val startTimeMs: Long,
    val totalVolume: Double,
    val startHour: Int
)
```

#### Minimum Data Requirements

- Sleep trend: needs ≥ 7 HC sync records with non-null `sleepDurationMinutes`
- Training window: needs ≥ 10 completed workouts with `startTimeMs > 0`

Both sub-cards show skeleton/empty states independently if requirements not met.

#### Data Flow

```
HealthConnectSyncDao.getRecentSyncs(): Flow<List<HealthConnectSync>>
TrendsDao.getWorkoutsByTimeOfDay(sinceMs)
  → TrendsRepository.getChronotypeData(windowDays)
    → TrendsViewModel.chronotypeState: StateFlow<ChronotypeState>
      → ChronotypeCard composable
```

#### Acceptance Criteria

- [x] Sleep trend bar chart with 7h reference line and red/green coloring
- [x] Training window scatter plot renders (even if using Canvas)
- [x] "Best training hour" summary text computed from median volume by hour bucket
- [x] Both sub-cards handle empty state independently
- [x] No sleep stage data displayed (limitation acknowledged in UI if relevant)

---

### 3.10 Boaz's Insights (Existing)

**File:** `ui/metrics/MetricsScreen.kt` (existing section, keep as-is)

Moved to the **bottom** of the scroll to make room for the chart cards above it. Content unchanged:
- Weekly volume anomaly detection (z-score)
- Progression anomaly detection (Bayesian 1RM)
- Health-performance Pearson correlation
- Committee recommendations

No functional changes to this section.

---

## 4. Architecture & Infrastructure

### 4.1 New Files

| File | Package | Purpose |
|---|---|---|
| `TrendsDao.kt` | `data/database/` | All aggregate trend queries (see §5) |
| `TrendsRepository.kt` | `data/repository/` | Orchestrates trend data, computes moving averages, aligns time series |
| `ReadinessEngine.kt` | `analytics/` | Readiness score algorithm (see §6) |
| `TrendsViewModel.kt` | `ui/metrics/` | Exposes all trend StateFlows; holds shared time range state |
| `ReadinessGaugeCard.kt` | `ui/metrics/` | Canvas arc gauge |
| `VicoChartHelpers.kt` | `ui/metrics/charts/` | Reusable Vico chart factory functions |
| `E1RMProgressionCard.kt` | `ui/metrics/charts/` | Multi-line e1RM chart |
| `VolumeTrendCard.kt` | `ui/metrics/charts/` | Volume bar + weight overlay |
| `MuscleGroupVolumeCard.kt` | `ui/metrics/charts/` | Stacked muscle group bars |
| `EffectiveSetsCard.kt` | `ui/metrics/charts/` | RPE-filtered effective sets |
| `BodyCompositionCard.kt` | `ui/metrics/charts/` | Three-line body comp overlay |
| `StepsTrendCard.kt` | `ui/metrics/charts/` | Steps with target line |
| `ChronotypeCard.kt` | `ui/metrics/charts/` | Sleep trend + training window scatter |

### 4.2 Existing Files to Modify

| File | Change |
|---|---|
| `data/database/PowerMeDatabase.kt` | Add `TrendsDao` abstract method; bump version to **34** |
| `di/DatabaseModule.kt` | Provide `TrendsDao`; add `MIGRATION_33_34` |
| `ui/metrics/MetricsScreen.kt` | Add new chart cards to `LazyColumn`; move Boaz's Insights section to bottom |
| `data/AppSettingsDataStore.kt` | Add `dailyStepTarget` DataStore preference |
| `ui/theme/Color.kt` | Add `ReadinessAmber = Color(0xFFFFB74D)` |
| `THEME_SPEC.md` | Document `ReadinessAmber` in §2 Semantic Color Contexts |
| `HEALTH_CONNECT_SPEC.md` | Note that Trends tab consumes `getRecentSyncs()` Flow |

### 4.3 Database Migration

**No migration needed.** DB is already at v34 (MIGRATION_33_34 adds `dateOfBirth` to users). TrendsDao uses only aggregate SELECT queries over existing tables. `dailyStepTarget` is stored in DataStore, not Room. No new tables, no new columns.

### 4.4 Dependency Injection

```kotlin
// In DatabaseModule.kt
@Provides
@Singleton
fun provideTrendsDao(db: PowerMeDatabase): TrendsDao = db.trendsDao()

@Provides
@Singleton
fun provideTrendsRepository(
    trendsDao: TrendsDao,
    metricLogRepository: MetricLogRepository,
    healthConnectSyncDao: HealthConnectSyncDao,
    readinessEngine: ReadinessEngine
): TrendsRepository = TrendsRepository(trendsDao, metricLogRepository, healthConnectSyncDao, readinessEngine)
```

---

## 5. New DAO Queries

All queries defined in `TrendsDao`. Summary table:

| Method | Returns | Used by |
|---|---|---|
| `getE1RMHistory(exerciseId, sinceMs)` | `List<E1RMDataPoint>` | e1RM Progression Card |
| `getWeeklyVolume(sinceMs)` | `List<WeeklyVolumeRow>` | Volume Trend + Body Comp Cards |
| `getWeeklyMuscleGroupVolume(sinceMs)` | `List<MuscleGroupVolumeRow>` | Muscle Group Card |
| `getWeeklyEffectiveSets(sinceMs)` | `List<EffectiveSetsRow>` | Effective Sets Card |
| `getWorkoutsByTimeOfDay(sinceMs)` | `List<WorkoutTimeRow>` | Chronotype Card |
| `getExercisesWithHistory(sinceMs)` | `List<ExerciseWithHistory>` | Exercise selector in e1RM card |

Full SQL for each query is documented in §3 under the respective card.

### 5.1 `getExercisesWithHistory` — Exercise Selector Feed

```kotlin
@Query("""
    SELECT DISTINCT e.id, e.name
    FROM exercises e
    JOIN workout_sets ws ON ws.exerciseId = e.id
    JOIN workouts w ON ws.workoutId = w.id
    WHERE w.isCompleted = 1
      AND w.isArchived = 0
      AND ws.isCompleted = 1
      AND ws.setType != 'WARMUP'
      AND ws.weight > 0
      AND ws.reps > 0
      AND w.timestamp >= :sinceMs
    ORDER BY e.name ASC
""")
suspend fun getExercisesWithHistory(sinceMs: Long): List<ExerciseWithHistory>

data class ExerciseWithHistory(val id: Long, val name: String)
```

---

## 6. Readiness Score Algorithm

**Class:** `ReadinessEngine` in `analytics/ReadinessEngine.kt`

### 6.1 Inputs

From the 30-day `HealthConnectSync` history:
- `hrv: Double?` — RMSSD in milliseconds (higher = better recovery)
- `rhr: Int?` — Resting heart rate in bpm (lower = better recovery)
- `sleepDurationMinutes: Int?` — Total sleep (higher = better)

### 6.2 Baseline Computation

Requires ≥ 5 days of non-null values per metric. If fewer than 5 days: return `ReadinessScore.Calibrating`.

```kotlin
val hrvHistory = history.mapNotNull { it.hrv }
val rhrHistory = history.mapNotNull { it.rhr?.toDouble() }
val sleepHistory = history.mapNotNull { it.sleepDurationMinutes?.toDouble() }

val hrvBaseline = StatisticalEngine.mean(hrvHistory)
val hrvStdDev   = StatisticalEngine.standardDeviation(hrvHistory)
val rhrBaseline = StatisticalEngine.mean(rhrHistory)
val rhrStdDev   = StatisticalEngine.standardDeviation(rhrHistory)
val sleepBaseline = StatisticalEngine.mean(sleepHistory)
val sleepStdDev   = StatisticalEngine.standardDeviation(sleepHistory)
```

### 6.3 Per-Metric Z-Scores

```kotlin
val today = history.maxByOrNull { it.date }

// HRV: positive Z = above baseline = better
val hrvZ = today.hrv?.let { StatisticalEngine.zScore(it, hrvBaseline, hrvStdDev) } ?: 0.0

// RHR: NEGATED because lower is better
val rhrZ = today.rhr?.let { -StatisticalEngine.zScore(it.toDouble(), rhrBaseline, rhrStdDev) } ?: 0.0

// Sleep: positive Z = more sleep than usual = better
val sleepZ = today.sleepDurationMinutes?.let {
    StatisticalEngine.zScore(it.toDouble(), sleepBaseline, sleepStdDev)
} ?: 0.0
```

### 6.4 Null Metric Handling

If one or more metrics are null for today, re-normalize the weights across available metrics:

```kotlin
val availableMetrics = buildMap {
    if (today.hrv != null) put("hrv", hrv_Z to HRV_WEIGHT)
    if (today.rhr != null) put("rhr", rhrZ to RHR_WEIGHT)
    if (today.sleepDurationMinutes != null) put("sleep", sleepZ to SLEEP_WEIGHT)
}
val totalWeight = availableMetrics.values.sumOf { it.second }
val rawScore = if (totalWeight == 0.0) 0.0 else {
    availableMetrics.values.sumOf { (z, w) -> z * (w / totalWeight) }
}
```

### 6.5 Weights

```kotlin
const val HRV_WEIGHT   = 0.45  // Autonomic nervous system recovery — most predictive
const val SLEEP_WEIGHT = 0.35  // Sleep duration — second most impactful
const val RHR_WEIGHT   = 0.20  // Resting HR — least day-to-day variable
```

Rationale: HRV is the gold-standard CNS fatigue marker. Sleep directly gates protein synthesis and growth hormone. RHR is a lagging indicator — often stays elevated for 1-2 days post-illness and doesn't spike as rapidly post-workout.

### 6.6 Score Mapping

Map raw composite Z-score (typically in range [-2.5, +2.5]) to [0, 100] using linear scaling with clamping:

```kotlin
// Z = 0 (at baseline) → score = 50
// Z = +2.0 (well above baseline) → score = 100
// Z = -2.0 (well below baseline) → score = 0
val normalizedScore = ((rawScore + 2.0) / 4.0).coerceIn(0.0, 1.0) * 100.0
val score = normalizedScore.roundToInt()
```

### 6.7 Output

```kotlin
sealed class ReadinessScore {
    object Calibrating : ReadinessScore()
    object NoData : ReadinessScore()
    data class Computed(
        val score: Int,          // 0–100
        val tier: Tier,
        val hrvDelta: Double?,   // today.hrv - hrvBaseline
        val rhrDelta: Int?,      // today.rhr - rhrBaseline (negative = better)
        val sleepMinutes: Int?,
        val sleepTargetMinutes: Int = 420  // 7 hours
    ) : ReadinessScore()

    enum class Tier { RECOVERED, MODERATE, FATIGUED }
}
```

### 6.8 Invariants

- Score is always in [0, 100]
- `Calibrating` returned when < 5 days of history for any required metric
- `NoData` returned when HC is unavailable or not synced for today
- Weights always re-normalized when a metric is null
- Algorithm is deterministic — same inputs produce same output

---

## 7. Vico Chart Integration Guide

**Library:** `com.patrykandpatrick.vico:compose-m3:2.0.0-beta.2`

Establish a canonical pattern in `VicoChartHelpers.kt` before implementing individual chart composables.

### 7.1 Theme Integration

Vico charts must match the Pro Tracker v6.0 dark palette. Never use Vico's default colors.

```kotlin
// VicoChartHelpers.kt — canonical color binding
val trendsLineColors = listOf(
    ProVioletColor,   // #9B7DDB — primary line / compound lift 1
    TimerGreenColor,  // #4CC990 — secondary line / compound lift 2
    NeonPurpleColor,  // #BB86FC — tertiary line / compound lift 3
    ProMagentaColor   // #9E6B8A — quaternary line / compound lift 4
)
```

### 7.2 Standard Chart Configuration

```kotlin
// X-axis: date labels with week/date formatting
// Y-axis: left axis for primary metric, right axis for secondary (when dual-axis)
// Grid lines: ProOutline (#383838) at 50% alpha
// Axis labels: ProSubGrey (#A0A0A0), 10sp, BarlowCondensed
// Chart background: ProSurface (#1C1C1C) — same as Card background
```

### 7.3 Chart Types Used

| Card | Vico Layer(s) |
|---|---|
| e1RM Progression | `LineCartesianLayer` (multi-series) |
| Volume Trend | `ColumnCartesianLayer` + `LineCartesianLayer` (dual-axis) |
| Muscle Group Volume | `ColumnCartesianLayer` (stacked) |
| Effective Sets | `ColumnCartesianLayer` (stacked) |
| Body Composition | `LineCartesianLayer` × 3 (multi-axis) |
| Steps Trend | `ColumnCartesianLayer` + horizontal `LineCartesianLayer` |
| Sleep Trend (sub-card) | `ColumnCartesianLayer` |
| Training Window scatter | **Custom Canvas** (Vico does not support scatter) |

### 7.4 Scatter Workaround (Training Window)

Since Vico 2.0.0-beta.2 does not have a scatter layer, use a `Canvas` composable:

```kotlin
Canvas(modifier = Modifier.fillMaxWidth().height(200.dp)) {
    dataPoints.forEach { (hour, volume) ->
        val x = hour / 23f * size.width
        val y = size.height - (volume / maxVolume * size.height)
        drawCircle(color = ProViolet, radius = 8.dp.toPx(), center = Offset(x, y))
    }
    // Highlight peak hour bucket
}
```

### 7.5 Model Producer Pattern

```kotlin
// Use CartesianChartModelProducer for reactive data
// Inside each chart composable:
val modelProducer = remember { CartesianChartModelProducer() }
LaunchedEffect(chartData) {
    modelProducer.runTransaction {
        // populate series from chartData
    }
}
CartesianChartHost(
    chart = rememberCartesianChart(
        rememberLineCartesianLayer(/* config */),
        startAxis = VerticalAxis.rememberStart(/* config */),
        bottomAxis = HorizontalAxis.rememberBottom(/* config */)
    ),
    modelProducer = modelProducer
)
```

---

## 8. Existing Infrastructure to Reuse

| Infrastructure | Location | Used by |
|---|---|---|
| `StatisticalEngine.mean()` | `analytics/StatisticalEngine.kt` | ReadinessEngine |
| `StatisticalEngine.standardDeviation()` | same | ReadinessEngine |
| `StatisticalEngine.zScore()` | same | ReadinessEngine |
| `StatisticalEngine.calculate1RM()` | same | e1RM card |
| `StatisticalEngine.calculateBayesian1RM()` | same | e1RM card (smoothed mode) |
| `StatisticalEngine.pearsonCorrelation()` | same | Boaz's Insights (existing) |
| `HealthConnectSyncDao.getRecentSyncs()` | `data/database/HealthConnectSyncDao.kt` | Readiness, Steps, Sleep |
| `HealthConnectSyncDao.getLatestSync()` | same | BodyVitalsCard (existing) |
| `MetricLogRepository.getByType(WEIGHT)` | `data/repository/MetricLogRepository.kt` | Volume overlay, Body Comp |
| `MetricLogRepository.getByType(BODY_FAT)` | same | Body Comp |
| `Workout.totalVolume` | `data/database/Workout.kt` | Volume Trend card |
| `WorkoutSet.rpe` | `data/database/WorkoutSet.kt` | Effective Sets card |
| `exercise_muscle_groups` table | `data/database/ExerciseMuscleGroup.kt` | Muscle Group, Effective Sets cards |
| `WeeklyInsightsAnalyzer` | `analytics/WeeklyInsightsAnalyzer.kt` | Boaz's Insights (unchanged) |

**Do not re-implement any of the above.** Always import and reuse.

---

## 9. Known Gaps & Deferred Work

| Gap | Impact | Mitigation |
|---|---|---|
| **Sleep stages (Deep/REM) not read from HC** | `ChronotypeCard` cannot show sleep quality breakdown | Total duration shown instead; note limitation in UI; future: read `SleepSessionRecord.stages` in `HealthConnectManager` |
| **Vico scatter chart not supported** | `Training Window` sub-card needs custom Canvas | Custom Canvas workaround documented in §7.4 |
| **No aggregate queries today** | All trend queries are new (`TrendsDao`) | Queries defined in this spec; implement before chart composables |
| **`BoazPerformanceAnalyzer` is a stub** | Planned-vs-actual comparison non-functional | Out of scope for Trends; tracked separately |
| **Steps data is daily, not historical beyond 30 days** | `StepsTrendCard` limited to 30-day window | By design; `HealthConnectSyncDao.getRecentSyncs()` returns 30 days |
| **No workout frequency streak query** | Consistency streak not shown | Deferred to future card |
| **e1RM for bodyweight exercises** | `weight = 0` → Epley returns 0 | Filter out `weight == 0` in `getE1RMHistory` query (already in SQL spec) |
| **RPE null coverage** | Many sets have `rpe = null` (field is optional) | Effective Sets card shows "Based on X% of sets with RPE logged" coverage indicator; sets without RPE excluded from effective set count |
| **Vico 2.0 beta API instability** | `2.0.0-beta.2` API may change before stable release | `VicoChartHelpers.kt` isolates Vico configuration; if API changes, only this file + card composables need updating |
| **Week bucketing timezone caveat** | `(timestamp / 604800000)` arithmetic uses UTC epoch, not local timezone | For weekly aggregation this is acceptable — at most 1 day offset at week boundaries; no user-facing week labels rely on exact day alignment |

---

## 10. Body Heatmap

**Status:** ✅ Completed (full redesign — April 2026)

### What Is Implemented

#### Data Model

1. **`ExerciseStressVector` entity** ✅ — DB table `exercise_stress_vectors(exerciseId, bodyRegion, stressCoefficient)`.
2. **`StressAccumulationEngine`** ✅ — 21-day lookback, half-life 4 days decay formula `stress = Σ(volume × coeff × e^(-λ × daysSince))`. Exposes:
   - `computeRegionStress()` — legacy method, returns `List<RegionStress>`
   - `computeRegionDetails(sets, vectors, exerciseNames, nowMs)` — full detail output (see below)
   - `classifyIntensity(stress, maxStress)` — 4-tier classification
3. **`RegionDetail`** ✅ — Rich per-region output including `topExercises: List<ExerciseContribution>` (top 3 by contribution) and `recoveryStatus: RecoveryStatus`
4. **`IntensityTier`** ✅ — `LOW / MODERATE / HIGH / VERY_HIGH` enum (quartile-based, relative to user's max stress)
5. **`RecoveryStatus`** ✅ — `READY / RECOVERING / FATIGUED` (FATIGUED when >40% of stress is from last 48h)
6. **`BodyStressMapData`** ✅ — `data class BodyStressMapData(regionDetails, maxStress)` with computed `regionStresses` property for backward compat
7. **`TrendsRepository.getBodyStressMap()`** ✅ — Returns `List<RegionDetail>` including exercise name lookup via `getExerciseNamesByIds()`

#### Color System

4-tier opacity-based color system (tokens in `ui/theme/Color.kt`):

| Tier | Token | Hex | Ratio Range | Alpha Range |
|---|---|---|---|---|
| No stress | `surfaceVariant` | — | < 0.01 | — |
| LOW | `HeatmapLow` | `#34D399` (emerald green) | [0.01, 0.25) | 0.30 → 0.80 |
| MODERATE | `HeatmapModerate` | `#F59E0B` (amber) | [0.25, 0.50) | 0.40 → 0.90 |
| HIGH | `HeatmapHigh` | `#EF4444` (red) | [0.50, 0.75) | 0.50 → 1.00 |
| VERY_HIGH | `HeatmapVeryHigh` | `#7C3AED` (deep purple) | [0.75, 1.00] | 0.60 → 1.00 |

Ratio = `regionStress / maxStress`. Alpha interpolated linearly within each tier. Implemented in `StressColorMapper.mapRatioToTierColor()`.

#### Canvas Body Outline

- **`BodyRegionPaths.kt`** ✅ — Redesigned with continuous bezier silhouette paths (`cubicTo()` traced clockwise per body side). Each region uses organic muscle-shaped paths (bilateral PECS, spindle QUADS/HAMSTRINGS/CALVES as ovals, wing-shaped LATS, etc.). Normalized [0,1] coordinate system.
- **`BodyOutlineCanvas.kt`** ✅ — `aspectRatio(0.65f)` (height ≈ 1.54× width, more compact). Stroke 2.5dp. Radial gradient depth effect per region (`Color.White.copy(alpha = 0.12f)` highlight from top-center). FRONT/BACK labels.

#### Card Layout

`BodyStressHeatmapCard.kt` — fits on one screen without internal scrolling:
```
Card {
  Column(16dp padding) {
    "BODY STRESS MAP" header + "21-day cumulative load · tap a region for detail" subtitle
    BodyOutlineCanvas (aspectRatio 0.65)
    [Inline RegionDetailPanel — shown when region tapped, hidden on re-tap]
    Top-3 region chips row (RegionChip: name + tier label + tier-colored border)
    4-dot legend row (Low · Moderate · High · Very High)
  }
}
```

`RegionDetailPanel` (inline, toggled on tap):
- Region name + intensity tier badge (tier.label in colored box)
- Recovery status dot (READY=green, RECOVERING=amber, FATIGUED=red)
- Top contributing exercises (up to 3)

#### 24h Recompute Gate

- `AppSettingsDataStore` stores `lastStressComputedAt: Long` (epoch ms) under key `last_stress_computed_at`
- `TrendsViewModel.refreshBodyStressMap()` — called on ON_RESUME — skips if `now - lastStressComputedAt < 24h`
- `TrendsViewModel.loadAll()` in `init` always calls `loadBodyStressMap()` unconditionally (startup path)
- Timestamp stamped in `loadBodyStressMap()` after successful DB fetch

### Intensity Classification

```kotlin
fun classifyIntensity(stress: Double, maxStress: Double): IntensityTier {
    if (maxStress <= 0) return IntensityTier.LOW
    return when (stress / maxStress) {
        in 0.0..<0.25 -> LOW
        in 0.25..<0.50 -> MODERATE
        in 0.50..<0.75 -> HIGH
        else -> VERY_HIGH
    }
}
```

### Recovery Status Logic

```kotlin
// Inside computeRegionDetails():
val recent48h = // stress from sets within last 48h (daysSince < 2.0)
val recoveryStatus = when {
    totalStress <= 0.0 -> RecoveryStatus.READY
    recent48h / totalStress > FATIGUE_THRESHOLD -> RecoveryStatus.FATIGUED  // THRESHOLD = 0.40
    else -> RecoveryStatus.RECOVERING
}
```

### Stress Vector Data Strategy

**Phase 1 — Manual science-sourced seed (~30 most common exercises):**
Seed data in `ExerciseStressVectorSeedData.kt` covering: squat, deadlift, bench press, OHP, barbell row, pull-up, chin-up, Romanian deadlift, leg press, lunge, incline press, dip, curl, tricep pushdown, lateral raise, face pull, hip thrust, cable row, leg curl, leg extension, plank, push-up, Arnold press, front squat, sumo deadlift, good morning, Bulgarian split squat, Nordic curl, shrug, farmers carry.

**Phase 2 — Gemini-generated expansion (remaining 120+ exercises):**
Use Gemini API to generate stress vectors for all remaining exercises. See `StressVectorSeeder.kt` for the prompt template. Review Gemini output before committing — flag any coefficient > 0.8 for manual verification.

**Phase 3 — Ongoing correction:**
As users log workouts, surface a "Does this feel right?" feedback mechanism. Aggregate corrections to refine coefficients over time.

### Remaining Work

- Stress vectors only cover top 30 exercises — Gemini expansion needed for full coverage
- Algorithm validation: flag coefficients > 0.8 for biomechanics review before shipping

---

## 11. Testing Strategy

### 11.1 Unit Tests

| Class | Tests |
|---|---|
| `ReadinessEngine` | Calibrating state (< 5 days), NoData state, full computation with all metrics, null HRV re-normalization, null RHR re-normalization, null sleep re-normalization, score clamping at 0 and 100, RECOVERED/MODERATE/FATIGUED tier boundaries |
| `TrendsRepository` | Moving average calculation (4-week window), time series alignment (empty weeks), muscle group volume attribution (50% secondary credit) |
| `TrendsViewModel` | Time range change re-queries all streams, readiness state propagates correctly |

### 11.2 Test File

New test file: `src/test/java/com/powerme/app/analytics/ReadinessEngineTest.kt`
Follows the same pattern as `StatisticalEngineTest.kt` — pure unit tests, no Mockito needed (ReadinessEngine uses `StatisticalEngine` directly, no DI).

### 11.3 Minimum Test Coverage for ReadinessEngine

```kotlin
// Calibrating path
@Test fun `returns Calibrating when fewer than 5 days of HRV history`()
@Test fun `returns Calibrating when fewer than 5 days of RHR history`()

// NoData path
@Test fun `returns NoData when today has no HC sync record`()

// Happy path
@Test fun `score is 50 when all metrics exactly at baseline`()
@Test fun `score is 100 when all metrics 2 stdDevs above baseline`()
@Test fun `score is 0 when all metrics 2 stdDevs below baseline`()
@Test fun `RHR contribution is negated (lower RHR = higher score)`()

// Null metric handling
@Test fun `weights re-normalized when HRV is null for today`()
@Test fun `score computable with only sleep and RHR data`()

// Tier boundaries
@Test fun `score 70 maps to RECOVERED tier`()
@Test fun `score 69 maps to MODERATE tier`()
@Test fun `score 40 maps to MODERATE tier`()
@Test fun `score 39 maps to FATIGUED tier`()
```

### 11.4 QA Protocol

For each new chart card:
1. Build passes (`assembleDebug`)
2. Unit tests pass (`testDebugUnitTest`)
3. Screenshot on emulator confirming: card renders, empty state renders, no crash
4. Manual test: vary time range selector and confirm chart data updates

---

## 12. Implementation Order

Build sequence ordered by: infrastructure first → highest-impact standalone card → simplest chart → progressively more complex/niche cards.

| Step | What | Depends On | Key Files |
|---|---|---|---|
| **0** | **Infrastructure Layer** | — | `TrendsDao.kt`, `ReadinessEngine.kt`, `TrendsRepository.kt`, `TrendsViewModel.kt`, `TrendsModels.kt`, `VicoChartHelpers.kt`, DI wiring |
| **1** | **ReadinessGaugeCard** | Step 0 | `ReadinessGaugeCard.kt` — Custom Canvas arc (no Vico), hero element |
| **2** | **VolumeTrendCard** | Step 0 | `VolumeTrendCard.kt` — Simplest Vico chart (weekly bars), uses `Workout.totalVolume` |
| **3** | **E1RMProgressionCard** | Step 0 | `E1RMProgressionCard.kt` — Vico multi-line + exercise picker dropdown |
| **4** | **MuscleGroupVolumeCard** | Step 0 | `MuscleGroupVolumeCard.kt` — Stacked bars, `exercise_muscle_groups` join |
| **5** | **EffectiveSetsCard** | Step 0 | `EffectiveSetsCard.kt` — RPE-filtered stacked bars, coverage indicator |
| **6** | **BodyCompositionCard** | Step 0 | `BodyCompositionCard.kt` — 3-line chart, dual Y-axis, `metric_log` data |
| **7** | **StepsTrendCard** | Step 0 | `StepsTrendCard.kt` — Simple bars from HC sync + configurable target line |
| **8** | **ChronotypeCard** | Step 0 | `ChronotypeCard.kt` — Sleep bars + custom Canvas scatter (Training Window) |
| **Future** | **Body Heatmap** | See §10 | Not built now — requires SVG paths, stress mapping, new DB table |

### Step 0 Internal Build Order

Phase 1 (no dependencies): `Color.kt` + `AppSettingsDataStore.kt` + `ReadinessEngine.kt` + `TrendsModels.kt`
Phase 2 (data layer): `TrendsDao.kt` → `PowerMeDatabase.kt` → `DatabaseModule.kt`
Phase 3 (orchestration): `TrendsRepository.kt`
Phase 4 (presentation): `TrendsViewModel.kt` + `VicoChartHelpers.kt`

### Per-Step QA Gate

Each step must pass before advancing:
1. `assembleDebug` succeeds
2. `testDebugUnitTest` passes (all existing + new tests)
3. Screenshot on emulator: card renders, empty state renders, no crash
4. `### WHAT CHANGED` + `### HOW TO QA IT` output for user approval

---

*Last updated: April 2026 — implementation order added, gaps updated, DB migration corrected.*
