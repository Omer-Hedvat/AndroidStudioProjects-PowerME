# BUG: History tab takes ~3 seconds to show records on first open

## Status
[x] Fixed

## Description

Navigating to the History tab for the first time results in a ~3 second blank/empty-state delay before workout records appear. There are three compounding root causes:

### Root Cause 1 — `hasPR` correlated subquery is O(N²)

The query in `WorkoutDao.getAllCompletedWorkoutsWithExerciseNames()` computes `hasPR` via a deeply-nested correlated subquery. For every row in the result set, SQLite must:
1. Scan all completed sets in that workout (`workout_sets ws3`)
2. For each set/exercise, run a `NOT EXISTS` correlated subquery across all historical `workout_sets` joined with `workouts` (full table scan)
3. OR run a `MAX(weight * (1 + reps/30.0))` aggregate subquery across all historical workout_sets (another full table scan)

With even a moderate workout history (e.g. 50 workouts × 5 exercises × 3 sets = 750 rows, with all prior history scanned for each), this easily takes seconds. No index can fully rescue nested correlated subqueries of this shape.

### Root Cause 2 — Triple query execution in `HistoryViewModel`

`HistoryViewModel` subscribes to `getAllCompletedWorkoutsWithExerciseNames()` **three separate times** — once each for `groups`, `insightCards`, and `workouts` StateFlows. Room creates a separate query execution for each cold Flow subscriber. The expensive query therefore runs 3× on every first open.

```kotlin
// HistoryViewModel.kt — three independent subscriptions to the same heavy query
val groups     = workoutRepository.getAllCompletedWorkoutsWithExerciseNames().map { ... }.stateIn(...)
val insightCards = combine(workoutRepository.getAllCompletedWorkoutsWithExerciseNames(), ...) { ... }.stateIn(...)
val workouts   = workoutRepository.getAllCompletedWorkoutsWithExerciseNames().map { ... }.stateIn(...)
```

### Root Cause 3 — `SharingStarted.WhileSubscribed(5000)` forces cold restart on every nav

All three StateFlows use `WhileSubscribed(5000)`, which cancels the upstream (and therefore the DB query) 5 seconds after the last subscriber leaves. Every time the user navigates away from History for >5 seconds and comes back, the flow goes cold and re-executes the full query from scratch. The initial value is `emptyList()`, so the empty-state UI is briefly shown while the query runs.

## Steps to Reproduce

1. Complete at least 5–10 workouts
2. Navigate away from History tab
3. Tap History tab — observe ~3 second delay before workout cards appear
4. (The delay is worst on the very first open after app launch, and recurs on subsequent navs after >5s away)

## Assets
- Related file: `app/src/main/java/com/powerme/app/data/database/WorkoutDao.kt` (lines 53–95)
- Related file: `app/src/main/java/com/powerme/app/ui/history/HistoryViewModel.kt`

## Fix Notes

All three root causes addressed across two sessions:

1. **O(N²) `hasPR` SQL subquery removed** — replaced with `0 AS hasPR` in the main listing query. PR detection moved to a new lightweight `getAllCompletedSetsForPRDetection()` DAO query that returns only (workoutId, exerciseId, weight, reps, timestamp), sorted by timestamp ASC. A single-pass O(N) Kotlin scan (`computePRWorkoutIds()`) computes which workouts contain a PR.

2. **Triple Flow subscription collapsed** — `HistoryViewModel` now creates one `private val baseWorkouts` StateFlow (shared, `SharingStarted.Eagerly`). `groups`, `insightCards`, and `workouts` are all derived from this single flow via `.map`/`.combine` — Room runs the listing query exactly once.

3. **`WhileSubscribed` replaced with `Eagerly`** — both `baseWorkouts` and `prWorkoutIds` use `SharingStarted.Eagerly`, so the DB query stays warm across tab navigation and never re-runs from cold on re-entry.

4. **Remaining correlated subquery for `setCount` replaced with CTE** — `getAllCompletedWorkoutsWithExerciseNames()` now uses a `WITH set_counts AS (SELECT workoutId, COUNT(*) ... GROUP BY workoutId)` CTE instead of a per-row correlated subquery, computing setCount once per workout rather than once per result row.

5. **Three composite indexes added (DB v42)** — `workout_sets(workoutId, exerciseId)`, `workout_sets(workoutId, isCompleted, setType)`, and `workouts(isCompleted, isArchived, timestamp)` cover the hot paths in both the listing query and the PR detection query.
