# HISTORY_ANALYTICS_SPEC.md — PowerME History & Analytics

**Status:** ✅ Complete (v1.0 — March 2026)
**Domain:** History Screen · Statistical Engine · Weekly Insights · WorkoutDetailScreen Data Contract · BoazPerformanceAnalyzer

> **Living document.** Update this file whenever history display, analytics formulas, or the Statistical Engine change.
> Cross-referenced by `CLAUDE.md`. Read this before touching `HistoryScreen.kt`, `HistoryViewModel.kt`, `StatisticalEngine.kt`, `AnalyticsRepository.kt`, or `WorkoutDetailScreen.kt`.

---

## Table of Contents

1. [Scope, Ownership & MVP Constraints](#1-scope-ownership--mvp-constraints)
2. [Data Contracts & Query Layer](#2-data-contracts--query-layer)
3. [Statistical Engine](#3-statistical-engine)
4. [History Screen (Tab)](#4-history-screen-tab)
5. [Weekly Insights Analyzer](#5-weekly-insights-analyzer)
6. [WorkoutDetailScreen — Data Contract & Edit Flow](#6-workoutdetailscreen--data-contract--edit-flow)
7. [BoazPerformanceAnalyzer (V2 — Deferred)](#7-boazperformanceanalyzer-v2--deferred)
8. [Technical Invariants](#8-technical-invariants)

---

## 1. Scope, Ownership & MVP Constraints

### 1.1 Domain Ownership

| Component | Owner |
|---|---|
| `HistoryScreen` layout & UI | This spec (§4) |
| `WeeklyInsightsAnalyzer` logic & UI | This spec (§5) |
| `StatisticalEngine` — all formulas | This spec (§3) |
| `AnalyticsRepository` query contracts | This spec (§2) |
| `WorkoutDetailScreen` composable structure | `WORKOUT_SPEC.md §26` (canonical — do not re-spec here) |
| `WorkoutDetailScreen` data contract & edit atomicity | This spec (§6) |
| `BoazPerformanceAnalyzer` | This spec (§7) — V2 no-op stub in MVP |

**Cross-reference note for `WORKOUT_SPEC.md`:** Invariants §27 #11 (volume exclusions) and §27 #12 (PR formula by exercise type) are authoritative in `WORKOUT_SPEC.md` for the live workout flow. The formulas they describe originate in `StatisticalEngine` and are canonically defined here (§3). Neither spec contradicts the other — they reference a shared engine.

### 1.2 MVP Constraints

- **No AI / LLM dependency.** All analytics, insights, and 1RM calculations are deterministic, locally computed, and run on-device.
- **No stored PR flags.** `workout_sets` must never gain an `isPR` boolean column. PRs are always derived dynamically at query time (see §3.4).
- **BoazPerformanceAnalyzer is a V2 stub.** The call site in `finishWorkout()` (`WORKOUT_SPEC.md §21.1`) must compile and be present but must execute zero analytics logic in MVP (see §7).

---

## 2. Data Contracts & Query Layer

### 2.1 HistoryViewModel Query Map

| Query | DAO method | Powers |
|---|---|---|
| All completed workouts with exercise names | `getAllCompletedWorkoutsWithExerciseNames()` | `HistoryScreen` card list |
| Sets for a single workout | `getWorkoutSetsForWorkout(workoutId)` | `WorkoutDetailScreen` |
| All completed sets for one exercise (history) | `getCompletedSetsForExercise(exerciseId)` | `ExerciseDetailSheet` Tab 2 (EXERCISES_SPEC.md §8) |
| All-time PR per exercise | `getMaxE1RMForExercise(exerciseId)` | `ExerciseDetailSheet` Tab 3 + PR badge |
| Weekly volume aggregation | `getVolumeByWeek(startEpoch, endEpoch)` | `WeeklyInsightsAnalyzer` |
| Bayesian 1RM inputs | `getCompletedSetsGroupedByExercise()` | `AnalyticsRepository.getBayesian1RMs()` |

**Filter applied to every history query:** `WHERE workouts.isCompleted = 1` — never expose in-progress or cancelled sessions.

### 2.2 AnalyticsRepository

`AnalyticsRepository` is the bridge between `WorkoutSetDao` and `StatisticalEngine`. It owns no business logic itself.

- `getBayesian1RMs(): Map<Long, Double>` — returns a map of `exerciseId → bayesian e1RM` for all exercises with ≥ 3 completed sessions. Fed to `WeeklyInsightsAnalyzer`.
- `getSessionVolume(workoutId): Double` — returns total volume for a completed session, applying the exclusion rules from §3.3.
- `hasPRInSession(workoutId): Boolean` — returns true if any exercise in the session set a new all-time high e1RM. Used to compute the `🏆 PR` badge on `HistoryCard`.

### 2.3 Required DB Column — `⚠️ DB Migration Required`

> **Schema gap:** The `Workout` entity currently has no `startTime` or `endTime` fields. The `HistoryCard` duration display and the `WorkoutDetailScreen` session header both require this data.

**Resolution:** A DB migration (v28+) must add:
- `workouts.startTimeMs INTEGER NOT NULL DEFAULT 0` — epoch ms, set when `startWorkoutFromRoutine()` creates the `Workout` row.
- `workouts.endTimeMs INTEGER NOT NULL DEFAULT 0` — epoch ms, set when `finishWorkout()` completes.

Duration displayed = `endTimeMs - startTimeMs`, formatted as `mm:ss` (or `h:mm:ss` for sessions ≥ 1 hour). Until migration ships, duration is hidden on `HistoryCard` (not shown as 0 or `--`).

---

## 3. Statistical Engine

`StatisticalEngine` is a pure-function utility object (no DI, no coroutines, no DB access). All functions are deterministic given the same inputs.

### 3.1 Epley 1RM — STRENGTH Exercises

**Formula:** `e1RM = weight × (1 + reps / 30)`

**Applies to:** NORMAL and FAILURE sets only. Never WARMUP, DROP, TIMED, or CARDIO sets.

**Owner:** `StatisticalEngine.calculate1RM(weight: Double, reps: Int): Double`

**Guard:** `reps == 0` → return `weight` (single-rep max is the weight itself, formula denominator is safe but semantically the weight is the 1RM). `reps < 0` → throw `IllegalArgumentException`.

### 3.2 Bayesian M-Estimate 1RM

**Formula:** `μ_bayesian = (C × μ_prior + n × μ_sample) / (C + n)`, where `C = 5` (default confidence weight).

**Purpose:** Smooths e1RM readings across small sample sets (< 5 sessions). Reduces noise from outlier sessions (injury days, easy sessions) when computing week-over-week trends.

**Applies to:** STRENGTH exercises only. Requires ≥ 3 completed sessions to emit a result — suppressed below threshold (see §8 invariant #6).

**Owner:** `StatisticalEngine.calculateBayesian1RM(priorMean: Double, sampleMean: Double, n: Int, C: Int = 5): Double`

**Used by:** `AnalyticsRepository.getBayesian1RMs()` → `WeeklyInsightsAnalyzer` (§5.3).

### 3.3 Volume Calculation

**Formula:** `Volume = SUM(weight × reps)` across qualifying sets in a session.

**Strict exclusions:**
- All WARMUP sets (regardless of exercise type)
- All sets belonging to TIMED exercises
- All sets belonging to CARDIO exercises
- Sets where `isCompleted = 0`

**Included:** NORMAL, FAILURE, and DROP sets on STRENGTH exercises only.

**Owner:** `StatisticalEngine.calculateVolume(sets: List<WorkoutSet>, exercises: Map<Long, Exercise>): Double`

This is the single source of truth. Any screen displaying volume (History card, post-workout summary sheet, `WorkoutDetailScreen` header) must call this function — never inline the formula.

### 3.4 Dynamic PR Evaluation — No Stored Flags

PRs are **never stored** as a boolean flag on `workout_sets`. They are computed dynamically at query time.

**Query pattern:**
```sql
SELECT MAX(weight * (1 + reps / 30.0))
FROM workout_sets ws
JOIN workouts w ON ws.workoutId = w.id
WHERE ws.exerciseId = :exerciseId
  AND ws.isCompleted = 1
  AND ws.setType NOT IN ('WARMUP', 'DROP')
  AND w.isCompleted = 1
```

**Benefits:**
- Retroactive edits in `WorkoutDetailScreen` automatically recalculate PRs. No cascading update scripts needed.
- Exercise normalization migrations (DB v24 merged duplicates) do not orphan PR flags.

**PR for TIMED exercises:** `score = weight × timeSeconds`. PR = MAX(score). No Epley formula applied. (See `WORKOUT_SPEC.md §27 invariant #12`.)

**PR for CARDIO exercises:** No PR tracking.

### 3.5 Session PR Badge Computation

A session "contains a PR" if: for **any** STRENGTH exercise in that session, the `MAX(e1RM)` achieved in that session is strictly greater than the `MAX(e1RM)` across **all prior sessions** for that exercise (sessions with `endTimeMs < this session's startTimeMs`).

This is computed by `AnalyticsRepository.hasPRInSession(workoutId): Boolean` and surfaced on `HistoryCard` as the `🏆 PR` badge.

> **Performance note:** Computing session PR badges requires a subquery per exercise per session. For large history sets (100+ sessions) this may be slow. If query latency exceeds 200ms, introduce a denormalized `workouts.hasPR INTEGER NOT NULL DEFAULT 0` column populated by `finishWorkout()` and kept in sync by `WorkoutDetailScreen` on retroactive edit save. This optimization is deferred until profiling shows it is needed.

---

## 4. History Screen (Tab)

### 4.1 Architecture

- **Composable:** `HistoryScreen`
- **ViewModel:** `HistoryViewModel`
- **Primary data source:** `Flow<List<WorkoutWithExerciseNames>>` via `getAllCompletedWorkoutsWithExerciseNames()`
- **Grouping:** Client-side in `HistoryViewModel` — group by calendar month/year after collecting from the Flow. Do not group in SQL.
- **Filter:** `isCompleted = 1` enforced at the DAO level.

### 4.2 Screen Layout

```
┌────────────────────────────────────────────────┐
│  [Weekly Insights Carousel]                    │  ← LazyRow, §5. Hidden when < 1 full week of data.
│─────────────────────────────────────────────────│
│  March 2026                                    │  ← Month/Year section header
│  [HistoryCard]                                 │
│  [HistoryCard]                                 │
│  February 2026                                 │
│  [HistoryCard]                                 │
│  ...                                           │
└────────────────────────────────────────────────┘
```

### 4.3 HistoryCard Layout

```
┌────────────────────────────────────────────────┐
│  Workout Name (titleMedium)        [🏆 PR]     │
│  Mon, Mar 10  ·  42:17 (mm:ss)                │
│  Volume: 3,420 kg  ·  24 Working Sets          │
│  Bench Press, OHP, Lat Pulldown +2             │
└────────────────────────────────────────────────┘
```

- **Workout Name:** `titleMedium`, `onSurface`, `maxLines = 1`, ellipsis.
- **PR Badge:** `SuggestionChip`, background `FormCuesGold (#5A4D1A)`, text `"🏆 PR"`. Visible only when `hasPR = true`. Hidden otherwise — no placeholder.
- **Date:** `bodySmall`, `onSurfaceVariant`. Format: `EEE, MMM d` (e.g. `Mon, Mar 10`).
- **Duration:** `bodySmall`, monospace font, `onSurfaceVariant`. Separator `·` between date and duration. Hidden if `endTimeMs == 0` (migration pending, see §2.3).
- **Volume:** `bodyMedium`. Computed via `StatisticalEngine.calculateVolume()`. Formatted with locale-aware thousands separator.
- **Working Sets:** Count of NORMAL + FAILURE + DROP completed sets across all exercises in the session. WARMUP excluded. Always an integer.
- **Exercise preview:** First 3 exercise names, comma-separated. If total > 3: `+N more`. Derived from `WorkoutWithExerciseNames` projection — no second query needed.

### 4.4 Empty State

When `getAllCompletedWorkoutsWithExerciseNames()` emits an empty list:

```
[FitnessCenter icon, 64dp, onSurfaceVariant]
No workouts logged yet.
Start your first session from the Workouts tab.
```

Centered vertically in the `LazyColumn` area. `WeeklyInsightsAnalyzer` carousel is also hidden in this state.

### 4.5 Interactions

- **Tap card** → `navController.navigate("workout_detail/$workoutId")`
- **No swipe-to-delete** on `HistoryCard` in MVP. Session deletion is accessible from `WorkoutDetailScreen` overflow menu (§6.4).

---

## 5. Weekly Insights Analyzer

### 5.1 Scope & Constraints

- **100% local and deterministic.** Zero network calls. Zero AI or Gemini calls. Any future attempt to add an LLM dependency here violates this invariant.
- Runs on `HistoryViewModel` — not in a background service, not in a separate coroutine scope.
- **Week boundary:** Monday 00:00:00 → Sunday 23:59:59 (ISO 8601 week). All epoch comparisons use the device locale's week start only for display — calculations always use Monday as canonical start.

### 5.2 Metrics Computed

| Metric | Formula | Source |
|---|---|---|
| Total Weekly Volume vs Last Week | `Δ = thisWeekVolume - lastWeekVolume` | `getVolumeByWeek()` via §3.3 |
| Total Workouts this week vs Last Week | `Δ = thisWeekCount - lastWeekCount` | Count of `isCompleted = 1` sessions |
| Most trained muscle group this week | `MAX COUNT(sets) GROUP BY muscleGroup` | `workout_sets` JOIN `exercises` |
| Top exercise 1RM trend (Bayesian) | `Δ = thisWeekBayesian1RM - lastWeekBayesian1RM` | `AnalyticsRepository.getBayesian1RMs()` |

### 5.3 Bayesian 1RM Integration

`WeeklyInsightsAnalyzer` receives `bayesian1RMs: Map<Long, Double>` from `AnalyticsRepository.getBayesian1RMs()`. This map contains the smoothed e1RM per exercise for exercises with ≥ 3 completed sessions.

The analyzer uses this to generate week-over-week 1RM trend cards (e.g. *"Your Bench Press 1RM is estimated up 2.5 kg this week"*). If an exercise has < 3 sessions, its card is suppressed entirely — not shown as 0 or N/A.

### 5.4 UI Presentation

- **Component:** Horizontal `LazyRow` of `InsightCard` composables at the top of `HistoryScreen`.
- **Visibility:** Hidden entirely when the user has fewer than 7 days of completed workout data. Not shown as a loading state or empty placeholder.
- **Card layout:**

```
┌──────────────────────────────┐
│  [Icon]  Metric label        │
│  Value   [↑ +12.3 kg]       │
└──────────────────────────────┘
```

- **Card background:** `surfaceVariant`, no elevation, `8dp` corner radius.
- **Delta indicator:** `↑` (primary/green), `↓` (error/red), `→` (onSurfaceVariant) for neutral delta (< 1% change).
- **No empty cards.** If a metric cannot be computed (insufficient data), that card is omitted from the carousel. The carousel is never shown with 0 cards.

---

## 6. WorkoutDetailScreen — Data Contract & Edit Flow

> **UI layout ownership:** `WORKOUT_SPEC.md §26` is canonical for composable structure, column layout, and superset rendering. This section owns only the data contract, query strategy, and edit atomicity rules.

### 6.1 Data Loading

- **Entry:** `WorkoutDetailScreen` receives `workoutId: Long` via nav argument `workout_detail/{workoutId}`.
- **Primary query:** `getWorkoutWithSets(workoutId)` — returns `WorkoutWithSets` (Workout entity + `List<WorkoutSet>` + `List<Exercise>` projections).
- **e1RM per set:** Computed in `WorkoutDetailViewModel` via `StatisticalEngine.calculate1RM(set.weight, set.reps)`. Never stored in DB. The PREV column from `ActiveWorkoutScreen` is replaced by this e1RM value in read mode. See `WORKOUT_SPEC.md §26.1`.
- **State:** `Flow<WorkoutWithSets>` collected as `StateFlow` in the ViewModel. Emits new data automatically after retroactive edits.

### 6.2 Read Mode (Default)

- All `WorkoutInputField` widgets are rendered as non-editable `Text` composables.
- Set type badges (W / D / F) are visible but non-interactive.
- TopAppBar actions: **Edit Session** (pencil icon) · overflow menu (⋮) with **Delete Session**.

### 6.3 Edit Flow — Data Contract `[Retroactive Edit]`

**Trigger:** Tap "Edit Session" in TopAppBar.

**What becomes editable:**
- Weight and reps for any set → `WorkoutInputField` (same as `ActiveWorkoutScreen`, `KeyboardType.Decimal` / `KeyboardType.Number`).
- Set type → `SetTypePickerSheet`.
- Add set → appends a new `WorkoutSet` row with `isCompleted = 1` (retroactive sets are already "done").
- Delete set → removes the `WorkoutSet` row.

**Write contract:**
- All changes (weight, reps, setType, adds, deletes) are accumulated in-memory while in edit mode.
- On `[Save]`: committed atomically via a single Room `withTransaction { }`. Never bare sequential DAO calls.
- On `[Cancel]`: discard all in-memory changes, revert to read mode. No DB writes.
- **No Iron Vault debounce.** There is no auto-save during retroactive editing.
- **No Routine Sync Diff Engine.** Retroactive edits must never trigger `WORKOUT_SPEC.md §7`. The Diff Engine runs only on `finishWorkout()` for live sessions.

**Post-save:** The `Flow<WorkoutWithSets>` emits the updated data. PRs (§3.4) and Volume (§3.3) are recomputed dynamically from the new state — no separate recalculation job is needed.

### 6.4 Session Deletion

- **Entry point:** TopAppBar overflow menu (⋮) → **Delete Session** (error color).
- **Confirmation dialog required:**
  > *"Delete this session? This cannot be undone."*
  > `[Delete]` (error) · `[Cancel]`
- **On confirm:** Delete `Workout` row. All associated `workout_sets` rows are deleted via Room FK cascade.
- **Post-delete:** `navController.popBackStack()` → returns to `HistoryScreen`.

---

## 7. BoazPerformanceAnalyzer (V2 — Deferred)

### 7.1 MVP Status: No-Op Stub

`BoazPerformanceAnalyzer` is **deferred to V2**. In MVP, the call site in `finishWorkout()` (`WORKOUT_SPEC.md §21.1`) must exist as a compiled no-op:

```
// MVP stub — replace with full implementation in V2
fun analyze(workoutId: Long) { /* no-op */ }
```

The stub must not throw, must not block the coroutine, and must not make any DB or network calls.

### 7.2 V2 Scope (Non-Binding)

- Called as a post-write side-effect after `finishWorkout()` completes and history is saved.
- Analyzes multi-session performance trends (volume trajectory, 1RM progression, muscle balance).
- **Strictly deterministic** — no LLM calls in any version. Any V2 implementation that adds an AI dependency requires an explicit spec update and user confirmation before implementation.
- Failure must be silent — if the analyzer throws, it must not affect the workout save result or navigation.

---

## 8. Technical Invariants

1. **No stored PR flags.** Never add `isPR` to `workout_sets` or any other table. PRs are always derived via `MAX()` at query time. Retroactive edits recalculate PRs automatically.

2. **Volume formula is centralised.** Volume aggregation always calls `StatisticalEngine.calculateVolume()`. Never inline `SUM(weight × reps)` directly in a composable, ViewModel, or DAO. The exclusion rules (no WARMUP / TIMED / CARDIO) live only in this function.

3. **`isCompleted = 1` gate on all history queries.** Every DAO query powering the History tab or `WorkoutDetailScreen` must filter `workouts.isCompleted = 1`. In-progress and cancelled sessions must never appear in history.

4. **Retroactive edits never trigger Routine Sync.** The Diff Engine (`WORKOUT_SPEC.md §7`) runs only on `finishWorkout()`. Never call it from `WorkoutDetailScreen` or `HistoryViewModel`.

5. **WeeklyInsightsAnalyzer has no AI dependency.** It must not call Gemini, any `AnalyticsRepository` AI method, or any remote service. 100% local computation, always.

6. **Bayesian 1RM threshold.** Never display or return a Bayesian 1RM value for an exercise with fewer than 3 completed sessions. Return `null` from `getBayesian1RMs()` for that exercise. Never display `0` or an error state — suppress the card entirely.

7. **e1RM column is `WorkoutDetailScreen`-only.** The PREV column in `ActiveWorkoutScreen` must never be replaced by e1RM during a live session. The substitution is valid only in `WorkoutDetailScreen` read mode. See `WORKOUT_SPEC.md §26.1`.

8. **Duration derives from timestamps.** `HistoryCard` duration = `endTimeMs - startTimeMs`. Never hardcode, estimate, or approximate duration. If `endTimeMs == 0` (pre-migration data), hide the duration field silently — do not display `0:00` or `--`.

9. **Atomic retroactive edits.** All changes in a `WorkoutDetailScreen` edit session are committed in a single `withTransaction { }`. Never write partial changes.

10. **BoazPerformanceAnalyzer is a no-op in MVP.** The call site in `finishWorkout()` must exist but execute nothing. Replacing the stub requires an explicit spec update — not a unilateral code change.
