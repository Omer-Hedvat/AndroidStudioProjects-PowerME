# PowerME — Session Handoff Document
**Date:** 2026-03-29
**Status:** Phase 2 in progress — Steps A–D complete, Steps E–F remaining

---

## Project Overview

**PowerME** is an Android fitness tracking app (Kotlin + Jetpack Compose + Material3 + Room + Hilt).
**Package:** `com.powerme.app`
**Room DB version:** v30
**Build:** min SDK 26, target SDK 35, `gradle-9.1.0`

The canonical project reference is `CLAUDE.md` (always read first). Spec files for each domain live at the project root (`WORKOUT_SPEC.md`, `EXERCISES_SPEC.md`, `THEME_SPEC.md`, `NAVIGATION_SPEC.md`, `HISTORY_ANALYTICS_SPEC.md`).

---

## How to Build & Test

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

# Unit tests
/Users/omerhedvat/.gradle/wrapper/dists/gradle-9.1.0-bin/9agqghryom9wkf8r80qlhnts3/gradle-9.1.0/bin/gradle \
  -p /Users/omerhedvat/git/AndroidStudioProjects-PowerME :app:testDebugUnitTest

# Debug APK
/Users/omerhedvat/.gradle/wrapper/dists/gradle-9.1.0-bin/9agqghryom9wkf8r80qlhnts3/gradle-9.1.0/bin/gradle \
  -p /Users/omerhedvat/git/AndroidStudioProjects-PowerME :app:assembleDebug
```

**Current test count:** 225 unit tests, all passing.
**Note:** `gradle-wrapper.jar` is missing — must use the hardcoded cached binary above.
**Note:** `./gradlew` does NOT work — use the full path above.

---

## What Was Done This Session (Phase 2)

All changes are in uncommitted working tree modifications. See `plans.json` last entry for full summary.

### Completed Steps

| Step | Description | Files Changed |
|---|---|---|
| A2 | Header button swap in ActiveWorkoutScreen: chevron top-left always, X/Timer top-right conditional on edit mode | `ActiveWorkoutScreen.kt` |
| A3 | MoreVert (⋮) visible in both active and edit modes | `ActiveWorkoutScreen.kt` |
| SQ1 | Phantom START WORKOUT screen deleted (was appearing during workout transitions) | `ActiveWorkoutScreen.kt` |
| SQ2 | Edit mode freeze fixed: `startEditMode()` DB queries wrapped in `withContext(Dispatchers.IO)` | `WorkoutViewModel.kt` |
| SQ3 | Ghost workout on app open fixed: `rehydrateIfNeeded()` purges workouts with zero completed sets | `WorkoutViewModel.kt` |
| SQ4 | Stub ⏱ Rest button deleted from ExerciseCard (had no implementation) | `ActiveWorkoutScreen.kt` |
| A1 | Dark mode surface colors updated: `StremioSurface=#252525`, `StremioSurfaceVar=#303030`, `StremioInputPill=#2A2A2A` | `Color.kt`, `THEME_SPEC.md` |
| C | Replace Exercise wired: `showReplaceDialog` → `MagicAddDialog` → `viewModel.replaceExercise()` | `ActiveWorkoutScreen.kt` |
| D | Per-set-type rest timer: `computeRestDuration()` + `startRestTimer(overrideSeconds)` + `completeSet()` update | `WorkoutViewModel.kt`, `WorkoutViewModelTest.kt` |

### Step D — Rest Timer Logic (most complex change)

`computeRestDuration(completed: SetType, next: SetType?, defaultSeconds: Int)` in `WorkoutViewModel.kt`:

| Completed | Next | Duration |
|---|---|---|
| DROP | any | 0s |
| WARMUP | WARMUP | 30s |
| WARMUP | other | `defaultSeconds` |
| NORMAL | DROP | 0s |
| NORMAL | other | `defaultSeconds` |
| FAILURE | any | `defaultSeconds` |

---

## Remaining Work (Steps E & F)

### Step E — Exercise Reorder (Tap-to-Collapse + Drag-and-Drop)

**Objective:** Allow reordering exercises during an active workout via drag handles.

**ViewModel changes (`WorkoutViewModel.kt`):**
- Add `collapsedExerciseIds: Set<Long>` to `ActiveWorkoutState`
- Add `collapseAllExcept(exerciseId: Long)` — collapses all exercises except the tapped one
- Add `toggleCollapsed(exerciseId: Long)` — toggles collapse of a single exercise
- Add `reorderExercises(fromIndex: Int, toIndex: Int)` — reorders the `exercises` list in state

**UI changes (`ActiveWorkoutScreen.kt`):**
- Replace `LazyColumn` with `ReorderableLazyColumn` from `sh.calvin.reorderable` (already in deps as `sh.calvin.reorderable:reorderable-compose:2.4.3`)
- Add drag handle icon (`DragHandle`) in ExerciseCard header — visible only in active mode (not edit mode)
- When an exercise header is tapped → call `collapseAllExcept()` so only the tapped exercise shows its sets
- VM-driven collapse state: `ExerciseCard` hides the sets section when `exerciseId in collapsedExerciseIds`

**Tests:** 2 new tests in `WorkoutViewModelTest.kt`:
- `collapseAllExcept collapses all other exercises and expands the target`
- `reorderExercises moves exercise from one index to another`

**Dependency already present** in `app/build.gradle.kts`:
```kotlin
implementation("sh.calvin.reorderable:reorderable-compose:2.4.3")
```

---

### Step F — Post-Workout Questionnaire / Routine Sync Fix

**Objective:** Fix the `RoutineSyncType` diff engine so it correctly classifies workout-vs-routine differences.

**Problem:** After finishing a workout, the sync type is sometimes classified incorrectly (e.g., always suggesting STRUCTURE or BOTH when only weight changed).

**Files:** `WorkoutViewModel.kt` (`finishWorkout()`), `WorkoutRepository.kt`

**Expected logic:**
- `null` → ad-hoc workout (no routine attached)
- `RoutineSyncType.VALUES` → only weight/reps changed (sets count, exercise list, order all match)
- `RoutineSyncType.STRUCTURE` → exercise list or order changed (but weight/reps match)
- `RoutineSyncType.BOTH` → both exercise structure AND weight/reps differ

**Tests:** Unit tests covering the 4 diff engine branches.

---

## Key Architecture Notes

### WorkoutViewModel Test Pattern

```kotlin
private val testDispatcher = StandardTestDispatcher()

@Before fun setup() {
    Dispatchers.setMain(testDispatcher)
    // mocks, runBlocking stubs, create viewModel
}
@After fun tearDown() { Dispatchers.resetMain() }

// Inside each test (runTest(testDispatcher)):
viewModel.startWorkout(0L)
runCurrent()          // drains t=0 tasks
advanceTimeBy(N)      // if testing timers (N*1000 + 1 to tick N times)
viewModel.cancelWorkout()  // ALWAYS cancel before asserting on timer state
runCurrent()
assertEquals(...)
```

**Critical:** For `startEditMode()` which uses `withContext(Dispatchers.IO)`:
```kotlin
viewModel.startEditMode(99L)
runCurrent()
Thread.sleep(100)   // let IO thread schedule the continuation back on testDispatcher
runCurrent()        // drain the re-queued continuation
```

### SetType Enum

`NORMAL`, `WARMUP`, `DROP`, `FAILURE` — defined in `data/database/SetType.kt`.

### Color Tokens

Dark palette in `ui/theme/Color.kt`. All composables must use `MaterialTheme.colorScheme.*` — no raw hex. See `THEME_SPEC.md` for full rules.

---

## QA Protocol

After each step: **build ✅ + unit tests ✅ + screenshot on emulator**.

The emulator runs API 36. Firebase Auth will fail (no network) — expected. QA is scoped to: app launches without crash, welcome screen renders, any screen reachable without auth.

Install APK:
```bash
# APK is at: app/build/outputs/apk/debug/app-debug.apk
# Use mcp__mobile__install_app tool or adb install
```

---

## Memory Files

Project memory is at:
`/Users/omerhedvat/.claude/projects/-Users-omerhedvat-git-AndroidStudioProjects-PowerME/memory/`

---

## Files Modified This Session (uncommitted)

```
M app/src/main/java/com/powerme/app/ui/theme/Color.kt
M app/src/main/java/com/powerme/app/ui/workout/ActiveWorkoutScreen.kt
M app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt
M app/src/test/java/com/powerme/app/ui/workout/WorkoutViewModelTest.kt
M THEME_SPEC.md
M plans.json
```
