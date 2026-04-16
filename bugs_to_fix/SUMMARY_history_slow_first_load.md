# Fix Summary: History tab takes ~3 seconds to show records on first open

## Root Cause

Five compounding issues (fixed across two sessions):

1. **O(N²) correlated SQL subquery for `hasPR`** — `getAllCompletedWorkoutsWithExerciseNames()` computed `hasPR` by running a deeply-nested correlated subquery for every row in the result. For each workout row, SQLite scanned all historical sets and ran a `MAX(e1RM)` aggregate across all prior workouts for each exercise — effectively O(N²) in the number of (workout × exercise) combinations.

2. **Triple query subscription** — `HistoryViewModel` subscribed to the same expensive Flow three independent times (for `groups`, `insightCards`, and `workouts`). Room creates a separate query execution per cold subscriber, so the query ran 3× on every tab open.

3. **`WhileSubscribed(5000)` forced cold restarts** — every time the user navigated away from History for >5 seconds, all three flows went cold and re-executed the full O(N²) query from scratch. Initial value `emptyList()` caused a visible blank-state flash.

4. **Remaining correlated subquery for `setCount`** — even after the `hasPR` fix, each result row ran `SELECT COUNT(*) FROM workout_sets WHERE workoutId = ?` as a correlated subquery (O(W×E) evaluations, once per workout × exercise result row). Replaced with a pre-aggregated CTE that computes all setCount values in a single `GROUP BY workoutId` pass.

5. **Missing composite indexes** — the listing query and PR detection query lacked covering indexes, forcing SQLite to do full-table scans for key filter/join operations.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/data/database/WorkoutDao.kt` | Removed `hasPR` correlated subquery (`0 AS hasPR`). Added `PRDetectionRow` + `getAllCompletedSetsForPRDetection()`. Replaced `setCount` correlated subquery with a CTE. |
| `app/src/main/java/com/powerme/app/data/repository/WorkoutRepository.kt` | Added `getAllCompletedSetsForPRDetection()` pass-through. |
| `app/src/main/java/com/powerme/app/ui/history/HistoryViewModel.kt` | Single shared `baseWorkouts` StateFlow (`SharingStarted.Eagerly`). Separate `prWorkoutIds` StateFlow using O(N) Kotlin scan (`computePRWorkoutIds()`). All flows `Eagerly` started. |
| `app/src/main/java/com/powerme/app/data/database/WorkoutSet.kt` | Added `Index(workoutId, exerciseId)` and `Index(workoutId, isCompleted, setType)` to `@Entity.indices`. |
| `app/src/main/java/com/powerme/app/data/database/Workout.kt` | Added `Index(isCompleted, isArchived, timestamp)` to `@Entity.indices`. |
| `app/src/main/java/com/powerme/app/data/database/PowerMeDatabase.kt` | Version bumped 41 → 42. |
| `app/src/main/java/com/powerme/app/di/DatabaseModule.kt` | Added `MIGRATION_41_42` with 3 `CREATE INDEX` statements. |
| `app/src/test/java/com/powerme/app/ui/history/HistoryViewModelTest.kt` | Tests mock `getAllCompletedSetsForPRDetection()`. Direct unit tests of `computePRWorkoutIds()`. |

## Surfaces Fixed

- **History tab first open**: workouts now appear near-instantly (O(1) listing query instead of O(N²))
- **History tab re-entry**: no re-query on navigation — flows stay warm with `Eagerly`
- **PR badges**: still correct — computed via O(N) Kotlin scan instead of O(N²) SQL

## How to QA

1. Complete at least 5–10 workouts (or import via CSV).
2. Navigate away from History tab to Workouts tab.
3. Tap History tab — workout cards should appear **immediately** with no blank-state flash.
4. Navigate away, wait >5 seconds, return to History — still instant (no re-query).
5. Verify that workouts where you set a personal record show the 🏆 PR chip.
6. Verify that workouts without PRs do **not** show the 🏆 PR chip.
