# Trends Tab — Chart Cards (Steps 2–8)

| | |
|---|---|
| **Phase** | P4 (Steps 2–5) · P5 (Steps 6–8) |
| **Status** | Steps 2–4 `wrapped` · Steps 5–6 `completed` · Step 7 `not-started` · Step 8 `completed` |
| **Effort** | M per step (Steps 2–5) · L (Steps 6, 8) · S (Step 7) |
| **Depends on** | Step 7 depends on HC Extended Reads (calories). Step B deep-link depends on Step 3. |
| **Roadmap** | `ROADMAP.md §P4` and `§P5` |

**Prerequisite:** Steps 0 + 1 complete ✅ — full data layer, TrendsViewModel, ReadinessGaugeCard all exist  
**Authoritative spec:** `TRENDS_SPEC.md` (full detail on every card)  
**This file:** Developer-ready implementation guide. Read this first, then reference TRENDS_SPEC.md for exhaustive acceptance criteria and algorithm detail.

---

## What Already Exists (Do Not Rebuild)

| File | What it provides |
|---|---|
| `analytics/ReadinessEngine.kt` | Z-score readiness computation |
| `data/database/TrendsDao.kt` | 6 aggregate Room queries (e1RM, weekly volume, muscle group volume, effective sets, chronotype, exercise picker) |
| `data/repository/TrendsRepository.kt` | All 8 data methods including gap-fill, moving average, body composition, chronotype |
| `ui/metrics/TrendsViewModel.kt` | 11 StateFlows, `setTimeRange()`, `selectExercise()`, `refreshReadiness()` |
| `ui/metrics/charts/VicoChartHelpers.kt` | Chart palette constants + `muscleGroupColors` map |
| `ui/metrics/TrendsModels.kt` | `TrendsTimeRange` enum, all domain data classes |
| `ui/metrics/ReadinessGaugeCard.kt` | Step 1 complete — arc gauge at top of MetricsScreen |

**TrendsViewModel StateFlows already wired up (just consume them):**

```kotlin
val volumeTrendData: StateFlow<VolumeTrendData?>
val e1RMData: StateFlow<E1RMProgressionData?>
val muscleGroupVolume: StateFlow<List<MuscleGroupWeekData>>
val effectiveSets: StateFlow<List<EffectiveSetsWeekData>>
val bodyComposition: StateFlow<BodyCompositionData?>
val chronotypeData: StateFlow<ChronotypeData?>
val exercisePickerItems: StateFlow<List<ExercisePickerItem>>
val timeRange: StateFlow<TrendsTimeRange>
val isLoading: StateFlow<Boolean>
```

---

## Build Order

Steps are ordered simplest → most complex. Each step is independently shippable.

| Step | Card | Vico Chart Type | Complexity |
|---|---|---|---|
| 2 | `VolumeTrendCard` | `ColumnCartesianLayer` (bars) | Low — single data series, no join |
| 3 | `E1RMProgressionCard` | `LineCartesianLayer` (multi-line) | Medium — exercise picker, multi-series |
| 4 | `MuscleGroupVolumeCard` | Stacked `ColumnCartesianLayer` | Medium — fixed color palette, legend |
| 5 | `EffectiveSetsCard` | Stacked `ColumnCartesianLayer` | Medium — RPE coverage indicator |
| 6 | `BodyCompositionCard` | 3× `LineCartesianLayer`, dual Y-axis | High — dual Y-axis, toggle chips |
| 7 | `StepsTrendCard` | `ColumnCartesianLayer` + target line | Low — simple bars, DataStore target |
| 8 | `ChronotypeCard` | Bars + Canvas scatter | High — Canvas scatter workaround |

All new composable files go in: `app/src/main/java/com/powerme/app/ui/metrics/charts/`

Wire each card into `MetricsScreen.kt` `LazyColumn` in order, above Boaz's Insights section.

---

## Step 2 — VolumeTrendCard

**File:** `ui/metrics/charts/VolumeTrendCard.kt`  
**ViewModel data:** `TrendsViewModel.volumeTrendData: StateFlow<VolumeTrendData?>`

### What to Build

Single bar chart: weekly total training volume (kg × reps). Optional body weight overlay on a second Y-axis.

### UI Spec

- Card header: "VOLUME TREND" title + time range selector (1M/3M/6M/1Y chips) + "Weight overlay" toggle switch
- **Chart:** Vico `CartesianChart` with:
  - `ColumnCartesianLayer` — weekly volume bars, color `ProViolet`
  - `LineCartesianLayer` (optional, when overlay on) — body weight, color `TimerGreen`, right Y-axis
  - 4-week moving average dashed line (`VicoChartHelpers.trendLineSpec()`)
- X-axis: ISO week labels ("W1", "W4", …)
- Y-axis left: volume in kg (or lbs if `unitSystem == IMPERIAL` — convert via `UnitConverter`)
- Y-axis right (overlay only): body weight
- Tap a bar → tooltip: "Week of Jan 5 · 4,280 kg · 3 workouts"
- **Empty state:** "Log at least 2 weeks of workouts to see volume trends"

### Data Shape (from TrendsRepository)

```kotlin
data class VolumeTrendData(
    val weeklyRows: List<WeeklyVolumePoint>,   // from TrendsDao
    val movingAverage: List<Double>,            // 4-week rolling, same length
    val weightPoints: List<TimestampedValue>    // aligned to same week buckets
)
```

### Key Notes

- `WeeklyVolumePoint.weekBucket` is epoch ms (start of week). Convert to readable label: `Instant.ofEpochMilli(weekBucket).atZone(ZoneId.systemDefault()).toLocalDate()`
- Moving average already computed in `TrendsRepository` — just pass through
- `unitSystem` is collected from `MetricsViewModel.unitSystem` (already in MetricsScreen scope) — pass as parameter

### Acceptance Criteria

- [ ] Bars reflect correct weekly totals
- [ ] MA dashed line overlays correctly
- [ ] Weight overlay toggles on/off without recomposition flicker
- [ ] Time range chip changes re-trigger `TrendsViewModel.setTimeRange()`
- [ ] Unit-aware: kg vs lbs label
- [ ] Empty state shown when no workout data in window

---

## Step 3 — E1RMProgressionCard

**File:** `ui/metrics/charts/E1RMProgressionCard.kt`  
**ViewModel data:** `TrendsViewModel.e1RMData: StateFlow<E1RMProgressionData?>`  
**ViewModel interactions:** `TrendsViewModel.selectExercise(id)`, `TrendsViewModel.exercisePickerItems`

### What to Build

Multi-line chart of estimated 1-Rep Max over time per exercise. Up to 4 exercises simultaneously. Exercise selection via `FilterChip` row.

### UI Spec

- Card header: "STRENGTH PROGRESSION"
- Exercise picker: `FilterChip` row (horizontally scrollable `LazyRow`) above chart
  - Default selection: first exercises matching "Bench Press", "Squat", "Deadlift" (substring match, case-insensitive)
  - Max 4 chips selected simultaneously — enforce in ViewModel
  - Chip shows exercise name (truncated to ~20 chars)
- **Chart:** Vico `CartesianChart` with up to 4 `LineCartesianLayer` instances
  - Colors in order: `ProViolet`, `TimerGreen`, `NeonPurple`, `ProMagenta`
  - Raw Epley values by default; "Smoothed" toggle in header shows Bayesian 1RM
  - Moving average line (dashed, `VicoChartHelpers.trendLineSpec()`) per exercise
- X-axis: date labels ("Jan 5", "Feb 12")
- Y-axis: kg (or lbs)
- Legend row below chart: color dot + exercise name per selected exercise
- **Empty state:** "Log at least 2 sessions for an exercise to see progression"

### Data Shape

```kotlin
data class E1RMProgressionData(
    val series: List<E1RMSeries>  // one per selected exercise
)
data class E1RMSeries(
    val exerciseId: Long,
    val exerciseName: String,
    val points: List<E1RMPoint>,        // raw Epley
    val smoothedPoints: List<E1RMPoint> // Bayesian
)
```

### Key Notes

- `TrendsViewModel.selectExercise()` triggers reload of e1RM data only (not all charts)
- `exercisePickerItems` provides the dropdown feed — already filtered to exercises with ≥ 2 sessions in the selected time window
- Use `StatisticalEngine.calculate1RM()` for Epley, `calculateBayesian1RM()` for smoothed — both already implemented

### Deep-Link Contract (History Summary Integration)

The History workout summary redesign (`HISTORY_SUMMARY_REDESIGN_SPEC.md`) adds a **[View Trend →]** button on each exercise card. Tapping it navigates here with a pre-selected exercise. Implementation requirements when building this card:

1. `Routes.TRENDS` must accept an optional `?exerciseId=<String>` query parameter
2. `MetricsScreen` reads the `exerciseId` nav arg on entry and calls `trendsViewModel.selectExercise(id)` if present
3. The `FilterChip` `LazyRow` must scroll to and highlight the pre-selected exercise chip via `LazyListState.animateScrollToItem()`
4. If the exercise has < 2 sessions in the selected time window, show the empty state with the exercise name pre-filled

---

## Step 4 — MuscleGroupVolumeCard

**File:** `ui/metrics/charts/MuscleGroupVolumeCard.kt`  
**ViewModel data:** `TrendsViewModel.muscleGroupVolume: StateFlow<List<MuscleGroupWeekData>>`

### What to Build

Stacked bar chart — each bar is a week, each segment is a muscle group. Shows whether training is balanced or skewed.

### UI Spec

- Card header: "MUSCLE BALANCE"
- **Chart:** Vico stacked `ColumnCartesianLayer`
  - One column per week bucket, stacked segments per muscle group
- Color palette (fixed — do NOT use theme tokens, these are chart-only):

```kotlin
val muscleGroupColors = mapOf(
    "Legs"       to Color(0xFF7B68EE),
    "Back"       to Color(0xFF4CC990),
    "Chest"      to Color(0xFFE05555),
    "Shoulders"  to Color(0xFFFFB74D),
    "Arms"       to Color(0xFF9B7DDB),
    "Core"       to Color(0xFF9E6B8A),
    "Full Body"  to Color(0xFFA0A0A0),
    "Cardio"     to Color(0xFF80CBC4)
)
```

This map already exists in `VicoChartHelpers.muscleGroupColors`.

- Legend: color-dot + label row below chart
- Distribution row below legend: `LinearProgressIndicator` per group for current week showing % of total
- **Empty state:** "Log at least 1 week of workouts to see muscle breakdown"

### Key Notes

- Secondary muscles get 50% credit — already applied in `TrendsDao.getWeeklyMuscleGroupVolume()` SQL
- WARMUP sets already excluded in DAO query
- Sort segments by volume descending so largest groups anchor the bottom

### Implementation Notes (as built)

- `VicoChartHelpers.muscleGroupOrder` (a fixed 8-element list) is the single source of truth for series order — both `TrendsViewModel.pushMuscleGroupToProducer()` and the composable's `ColumnProvider.series(...)` use this list to stay in sync
- `TrendsViewModel` always pushes exactly 8 series (one per group), using 0.0 for absent groups — prevents Vico layer/producer mismatch when time range changes
- `@OptIn(ExperimentalLayoutApi::class)` required for `FlowRow` legend row
- Distribution row ("THIS WEEK") only shows groups with nonzero volume in the most recent week

### How to QA

1. Navigate to the Trends tab — the MUSCLE BALANCE card should appear below the STRENGTH PROGRESSION card
2. **Stacked bars:** Verify bars appear for each week, stacked by muscle group with the correct colors
3. **Legend:** Color dots with group names render below the chart (only groups with volume in the range)
4. **Distribution row:** "THIS WEEK" section shows bars + percentages for the current week, summing to 100%
5. **Empty state:** On a new account with no workouts, the overlay shows "Log at least 1 week of workouts to see muscle breakdown"
6. **Time range chips:** Switching 1M/3M/6M/1Y reloads the chart data
7. **Navigation:** Switching away and back to the Trends tab preserves chart state without crash

---

## Step 5 — EffectiveSetsCard

**File:** `ui/metrics/charts/EffectiveSetsCard.kt`  
**ViewModel data:** `TrendsViewModel.effectiveSets: StateFlow<List<EffectiveSetsWeekData>>`

### What to Build

Like MuscleGroupVolumeCard but counts *effective sets* (RPE ≥ 7.0) rather than volume. Includes a data-quality coverage indicator since RPE logging is optional.

### UI Spec

- Card header: "EFFECTIVE SETS"
- **Chart:** Same stacked `ColumnCartesianLayer` layout as Step 4, same color palette
- Coverage banner: "Based on X% of sets with RPE logged" — shown as a muted subtitle below the header; amber color if coverage < 50%
- "% effective" text: below chart — "X% of all logged sets qualified as effective this week"
- **Empty state — no RPE data at all:** "Log RPE on your sets to track effective training"
- **Sparse state (< 30% coverage):** Show chart + warning banner "Low RPE coverage — results may not be representative"

### RPE Convention Reminder

`WorkoutSet.rpe` is stored ×10 as `Int?`. `rpe >= 70` = RPE ≥ 7.0. This is already the filter in `TrendsDao.getWeeklyEffectiveSets()`.

### Key Notes

- Primary muscle group only for set counting (no 50% secondary credit) — already applied in DAO query
- WARMUP and DROP sets excluded — already in DAO query
- The RPE coverage % needs a companion DAO query (or compute in Repository from total set count vs sets with non-null RPE). Add `getTotalSetsCount(sinceMs)` to TrendsDao if not already there

### Implementation Notes (as built)

- `TrendsDao` — added `getTotalSetsCount(sinceMs)` and `getRpeCoveredSetsCount(sinceMs)` for coverage %
- `TrendsRepository.getEffectiveSetsCoverage(range)` computes `covered / total * 100f` (returns 0f when no sets)
- `TrendsViewModel` — added `effectiveSetsModelProducer`, `_effectiveSetsCoverage` StateFlow, updated `loadEffectiveSets()` to also fetch coverage and call `pushEffectiveSetsToProducer()`
- `pushEffectiveSetsToProducer()` is a non-suspend fun using `viewModelScope.launch{}` — same anti-race pattern as `pushMuscleGroupToProducer()`; always pushes exactly 8 series in `VicoChartHelpers.muscleGroupOrder` order
- Empty state trigger: `weeks.isEmpty()` (no effective sets data in range)
- Sparse warning trigger: `coveragePct > 0f && coveragePct < 30f` — shows amber text below chart
- Coverage subtitle shows amber color when `coveragePct < 50f`
- "THIS WEEK" distribution row shows effective set COUNT per group (not %) — bar fraction = group count / total effective sets this week
- `WorkoutViewModelTest` required a `timerSound` stub (`flowOf(TimerSound.BEEP)`) due to pre-existing `WorkoutViewModel.kt` changes that added `timerSoundState`

### How to QA

1. Navigate to Trends tab — the EFFECTIVE SETS card should appear below the MUSCLE BALANCE card
2. **Coverage banner:** Verify "Based on X% of sets with RPE logged" appears below the header; amber color if < 50%
3. **Stacked bars:** Verify bars appear for each week, stacked by muscle group with the correct colors
4. **Legend:** Color dots with group names render below the chart (only groups with effective sets in range)
5. **Distribution row:** "THIS WEEK" section shows bars + set counts for the current week
6. **Empty state:** On a fresh account or with no RPE logged, overlay shows "Log RPE on your sets to track effective training"
7. **Sparse warning:** With < 30% RPE coverage, amber text "Low RPE coverage — results may not be representative" appears below the chart
8. **Time range chips:** Switching 1M/3M/6M/1Y reloads the chart data
9. **Navigation:** Switching away and back to the Trends tab preserves chart state without crash

---

## Step 6 — BodyCompositionCard ✅ completed

**File:** `ui/metrics/charts/BodyCompositionCard.kt`  
**ViewModel data:** `TrendsViewModel.bodyComposition: StateFlow<BodyCompositionData?>`

> **Implementation note:** Volume MA overlay was dropped in favour of a simpler 2-line design
> (Weight + Body Fat %). Volume MA is already visible in VolumeTrendCard and the dual-axis
> approach in Vico 2.0.0-beta.2 is fragile with mismatched scales. Toggle chips control
> per-series visibility by toggling alpha — series count in the producer stays constant (2)
> to prevent Vico crashes. `bodyCompTimestamps` StateFlow drives the x-axis formatter.

### What to Build

Three-line chart correlating training volume trend with body weight and body fat %. The "are you building muscle or just gaining fat?" chart.

### UI Spec

- Card header: "BODY COMPOSITION"
- Toggle chips at top of card: `FilterChip` row — "Volume MA" / "Weight" / "Body Fat" — each toggles its line on/off independently
- **Chart:** Vico `CartesianChart` with up to 3 `LineCartesianLayer` instances:
  - Volume MA: `ProViolet`, dashed (`VicoChartHelpers.trendLineSpec()`), left Y-axis
  - Body weight: `TimerGreen`, solid, right Y-axis
  - Body fat %: `ProMagenta`, dotted, right Y-axis
- Dual Y-axis: volume on left (kg), weight + body fat on right (different scales — Vico supports independent axis scaling)
- **Empty state:** "Log body weight and body fat regularly to see this chart"
- **Sparse state:** Show available lines; hide missing lines with a note "Not enough weight/body fat data"

### Data Shape

```kotlin
data class BodyCompositionData(
    val weightPoints: List<TimestampedValue>,
    val bodyFatPoints: List<TimestampedValue>,
    val bmiPoints: List<TimestampedValue>        // computed from height
)
```

Volume MA comes from `volumeTrendData.movingAverage` (already in TrendsViewModel) — align by week bucket.

### Key Notes

- Dual Y-axis in Vico 2.0: use separate `startAxis` and `endAxis` on `CartesianChart`
- The 4-week volume MA is already computed in `TrendsRepository.getVolumeTrendData()`
- Body weight points are aligned to weekly buckets in `TrendsRepository.getBodyCompositionData()` — nearest entry per week

---

## Step 7 — StepsTrendCard

**File:** `ui/metrics/charts/StepsTrendCard.kt`  
**ViewModel data:** `TrendsViewModel.chronotypeData` (steps come from `HealthConnectSyncDao.getRecentSyncs()`)  
**Settings:** `AppSettingsDataStore.dailyStepTarget: Flow<Int>` (already added, default 8,000)

### What to Build

30-day bar chart of daily steps vs a configurable target line. Surfacing NEAT (non-exercise activity) suppression.

### UI Spec

- Card header: "DAILY STEPS"
- **Chart:** Vico `CartesianChart` with:
  - `ColumnCartesianLayer` — daily step counts
    - Bar color: `TimerGreen.copy(alpha=0.8f)` when steps ≥ target, `ProError.copy(alpha=0.7f)` when below
  - `LineCartesianLayer` — flat horizontal target line at `dailyStepTarget` value, color `ProSubGrey`, dashed
- Fixed 30-day window regardless of global time range selector (step data is most useful as a recent rolling window)
- Header sub-line: "Daily target: 8,000 steps" with pencil `IconButton` → opens inline `AlertDialog` to edit
- Step target edit dialog: `OutlinedTextField` (integer, validated 1,000–50,000), "Save" / "Cancel" buttons
- **Empty state:** "Sync Health Connect to see your daily steps"

### Key Notes

- `AppSettingsDataStore.setDailyStepTarget()` is already implemented
- Data source: `HealthConnectSyncDao.getRecentSyncs()` returns a `Flow<List<HealthConnectSync>>`; the `steps` field gives daily count
- This card ignores the global `TrendsTimeRange` — always 30 days

---

## Step 8 — ChronotypeCard

**File:** `ui/metrics/charts/ChronotypeCard.kt`  
**ViewModel data:** `TrendsViewModel.chronotypeData: StateFlow<ChronotypeData?>`

### What to Build

Two sub-cards in one scrollable card:
1. **Sleep Trend** — bar chart of nightly sleep duration
2. **Training Window** — scatter plot of workout start hour vs volume (custom Canvas)

### Sub-card A: Sleep Trend

- Vico `ColumnCartesianLayer`
- X-axis: last 30 days
- Y-axis: hours (convert from `sleepDurationMinutes / 60.0`)
- Reference line at 7h (`highFatigueFlag` threshold)
- Bar color: `TimerGreen` ≥ 7h, `ProError` < 7h
- **Empty state:** needs ≥ 7 HC sync records with non-null `sleepDurationMinutes`

### Sub-card B: Training Window Scatter

Vico 2.0 does **not** support native scatter plots. Use custom Canvas drawing:

```kotlin
Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
    // Draw X-axis: hours 0–23
    // For each workout: drawCircle at (startHour * xStep, maxY - volume * yScale)
    // Highlight the peak hour bucket in ProViolet
}
```

- X-axis: 0–23 hours of day
- Y-axis: total workout volume
- Each dot = one completed workout at `(startHour, totalVolume)`
- Highlight: hour bucket with highest **median** volume → `ProViolet` larger dot
- Text summary below: "Your best sessions tend to start around 6pm" (computed from peak bucket)
- **Empty state:** needs ≥ 10 completed workouts with `startTimeMs > 0`

### Data Shape

```kotlin
data class ChronotypeData(
    val sleepPoints: List<Pair<LocalDate, Int>>,   // (date, sleepMinutes)
    val workoutTimePoints: List<WorkoutTimeRow>     // (id, startTimeMs, totalVolume, startHour)
)
```

### Key Notes

- Both sub-cards show independently — one can be in empty state while the other renders data
- Sleep stage breakdown (Deep/REM) is NOT available from HC Phase A — do not display. If `SleepSessionRecord.stages` becomes available, document the extension in `HEALTH_CONNECT_SPEC.md`
- `WorkoutTimeRow.startHour` is computed in SQL: `(startTimeMs / 3600000 % 24)` — already in `TrendsDao.getWorkoutsByTimeOfDay()`

---

## Wiring into MetricsScreen

After implementing each card, add it to `MetricsScreen.kt`'s `LazyColumn` in this order:

```kotlin
item { BodyVitalsCard(...) }
item { ReadinessGaugeCard(...) }     // Step 1 ✅
item { VolumeTrendCard(...) }         // Step 2
item { E1RMProgressionCard(...) }    // Step 3
item { MuscleGroupVolumeCard(...) }  // Step 4
item { EffectiveSetsCard(...) }      // Step 5
item { BodyCompositionCard(...) }    // Step 6
item { StepsTrendCard(...) }         // Step 7
item { ChronotypeCard(...) }         // Step 8
item { BoazInsightsSection(...) }    // Existing — stays at bottom
```

`TrendsViewModel` is already injected into `MetricsScreen` (done in Step 1). Each new card needs to collect its own StateFlow:

```kotlin
val volumeTrendData by trendsViewModel.volumeTrendData.collectAsStateWithLifecycle()
```

---

## Vico 2.0 Integration Notes

**Version in use:** `com.patrykandpatrick.vico:compose-m3:2.0.0-beta.2`

**Critical pattern — model producer:**

```kotlin
// In ViewModel
val modelProducer = CartesianChartModelProducer()

// On data change
modelProducer.runTransaction {
    columnSeries { series(listOf(1f, 2f, 3f)) }
    // or lineSeries { series(...) }
}

// In Composable
CartesianChartHost(
    chart = rememberCartesianChart(
        ColumnCartesianLayer(/* config */),
        startAxis = VerticalAxis.rememberStart(),
        bottomAxis = HorizontalAxis.rememberBottom()
    ),
    modelProducer = modelProducer
)
```

**Known issues with Vico 2.0.0-beta.2:**
- API is unstable — `rememberCartesianChart` parameter names may differ from 1.x docs. Always refer to the actual Vico 2.0 source/samples, not blog posts.
- Stacked bars require `ColumnCartesianLayer.MergeMode.Stack` (exact class path may vary — check via autocomplete)
- Dual Y-axis: use `endAxis = VerticalAxis.rememberEnd()` on `CartesianChart`
- Scatter is not supported natively — use Canvas (see Step 8)

**Reusable helpers in `VicoChartHelpers`:**

```kotlin
VicoChartHelpers.defaultLineSpec()       // solid line
VicoChartHelpers.trendLineSpec()         // dashed moving average line
VicoChartHelpers.defaultBarSpec()        // standard volume bar
VicoChartHelpers.defaultAxisStyle()      // axis text style matching theme
VicoChartHelpers.muscleGroupColors       // Map<String, Color> for muscle groups
```

---

## QA Gate (per step)

Each step must pass before advancing:

1. `./gradlew :app:assembleDebug` — zero errors
2. `./gradlew :app:testDebugUnitTest` — all existing tests pass
3. Card renders on device/emulator: data state + empty state both tested
4. Time range selector (1M/3M/6M/1Y) updates the chart
5. Output `### WHAT CHANGED` + `### HOW TO QA IT` for user approval before pushing

---

## Future Phase — Body Heatmap

**Not in scope for Steps 2–8.** Blocked by:
- No SVG body-outline asset exists in the project
- Requires a new DB table mapping exercises → body region polygons
- Stress/recovery scoring per muscle region is a separate algorithm

When this becomes a priority, see `TRENDS_SPEC.md §10` for the full concept.

---

*Written April 2026. Prerequisite: TRENDS_SPEC.md (authoritative) + Steps 0–1 complete.*

---

## How to QA — Steps 2–3 (Shipped April 2026)

### VolumeTrendCard (Step 2)

1. Build and install the debug APK; sign in and open the **Trends** tab.
2. Confirm **VolumeTrendCard** appears below the Readiness gauge.
3. **With ≥ 2 weeks of logged workouts:** purple bars render per week; a green moving-average line overlays the bars.
4. **With < 2 weeks:** centered message "Log at least 2 weeks of workouts to see volume trends".
5. Tap **1M → 3M → 6M → 1Y** chips — chart data updates each time; selected chip turns primary-colored.
6. Switch unit system (Settings → Imperial) — Y-axis re-labels to `lb`; large values show `K lb` suffix.
7. **Header stat:** "Avg X.X/wk" appears top-right when data is present.

### E1RMProgressionCard (Step 3)

1. Confirm the card renders directly below VolumeTrendCard.
2. **No weighted exercises logged:** "Complete workouts with weighted sets to track strength" (no chip row).
3. **Exercises present, selected exercise < 2 sessions:** "Log at least 2 sessions of [name] to see strength progression".
4. **≥ 2 sessions:** purple raw e1RM line renders; if ≥ 3 sessions exist, green 3-session MA line overlays it. Legend dots appear below.
5. Tap a different exercise chip — chart switches to that exercise's data.
6. **Percent-change badge** (top-right): green with `+X.X%` if progression, red with `-X.X%` if regressing.
7. Switch unit system — Y-axis re-labels to `lb`.
8. Verify both light and dark themes render all text and lines readably.
