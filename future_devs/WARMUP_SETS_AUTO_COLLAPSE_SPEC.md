# Warmup Sets — Auto-Collapse After Completion

| Field | Value |
|---|---|
| **Phase** | P1 |
| **Status** | `done` |
| **Effort** | S |
| **Depends on** | — |
| **Blocks** | — |
| **Touches** | `ActiveWorkoutScreen.kt`, `WorkoutViewModel.kt`, `WorkoutViewModelTest.kt` |

---

## Overview

Warmup sets are preparatory — once completed, they don't need to remain visually prominent. After all warmup sets for an exercise are done **and the rest timer that follows the last warmup set has finished**, they auto-collapse into a compact summary row, keeping the screen focused on the upcoming working sets.

---

## Behaviour

### Collapse Trigger (updated — QA rework)

Collapse fires **after the rest timer that follows the last warmup set completes** — not on set confirmation.

Exact sequence (example: 2 warmup sets, warmup rest 10s, work rest 20s):

1. Complete warmup #1 → warmup rest timer starts → **nothing collapses**
2. Warmup rest timer #1 reaches 0 / finishes → collapses normally (standard rest separator collapse)
3. Complete warmup #2 (last warmup) → warmup rest timer #2 starts → **nothing collapses yet**
4. Warmup rest timer #2 reaches 0 / finishes → collapses normally → **immediately after: all warmup rows collapse**

**Skip path:** If the user taps **Skip** on the rest timer that follows the last warmup, the rest separator collapses AND the warmup rows collapse simultaneously (same state update).

**Edge case — no rest timer after last warmup** (`restAfterLastSet = false` or `restTime == 0`): collapse fires immediately on set confirmation (no timer to wait for).

**Edge case — only 1 warmup set:** same rules apply — collapse after that set's rest timer ends (or on confirmation if no rest timer).

### Other Behaviour Rules

- Collapse is per-exercise, not global
- Collapsed state shows: count of warmup sets, a checkmark, and a chevron to re-expand (e.g. "W ×2 ✓")
- Tapping the collapsed row expands warmup sets back to full view
- If a warmup set is partially done (some confirmed, some not), no collapse occurs
- Only warmup sets collapse — drop sets, failure sets, and working sets are unaffected
- Un-completing any warmup set un-collapses the warmup rows
- Deleting a warmup set re-evaluates collapse state

---

## Rest Timer — Skip Behaviour (all set types)

When the user taps **Skip** on a rest timer (`skipRestTimer()`), the active separator row for that set immediately collapses. This applies to all set types (warmup and working sets).

For the **last warmup set's rest timer**: skip collapses both the separator AND the warmup rows in one atomic state update.

Fix in `skipRestTimer()`: captures `exerciseId` and `setOrder` from `restTimer` before clearing it, adds `"${exerciseId}_${setOrder}"` to `hiddenRestSeparators`, and if that timer was the last warmup's rest timer, adds `exerciseId` to `collapsedWarmupExerciseIds` — all in one state update.

---

## UI Changes

- `warmupsCollapsed: Boolean` flag per exercise derived from `ActiveWorkoutState.collapsedWarmupExerciseIds: Set<Long>`
- Collapsed warmup row: single `ListItem` with "W ×N ✓" label, `onSurfaceVariant` text, trailing `ExpandMore`/`ExpandLess` icon
- Animate collapse/expand using `AnimatedVisibility` (shrink + fade out / expand + fade in, 150–200ms)
- Warmup rows (including any active rest separator) are inside the `AnimatedVisibility(!warmupsCollapsed)` block; they collapse as a unit

---

## Implementation Notes

### `WorkoutViewModel.kt`

- **`completeSet(exerciseId, setOrder)`**: does NOT trigger warmup collapse directly. Starts rest timer as normal. Only triggers collapse if the set has no rest timer (restAfterLastSet=false / restTime==0) and this was the last warmup.
- **`onTimerTick(remaining == 0)` / `onTimerFinish()`**: checks if the completing timer's `setType == WARMUP` and all warmups for that exercise are confirmed → adds `exerciseId` to `collapsedWarmupExerciseIds`.
- **`skipRestTimer()`**: same check as above — if skipping the last warmup's rest timer, collapse warmup rows atomically with the separator hide.
- **`deleteSet(exerciseId, setOrder)`**: re-checks warmup collapse state for the exercise after deleting a warmup set.
- **`toggleWarmupsCollapsed(exerciseId)`**: toggled by the collapsed row's tap callback.

### `ActiveWorkoutScreen.kt`

- Warmup rows are wrapped in `AnimatedVisibility(visible = !warmupsCollapsed)`.
- Collapsed summary row is in `AnimatedVisibility(visible = warmupsCollapsed)`.
- Both are rendered only when `hasWarmups = warmupSets.isNotEmpty()`.

---

## Files Touched

- `app/src/main/java/com/powerme/app/ui/workout/ActiveWorkoutScreen.kt` — collapsed warmup row composable + AnimatedVisibility
- `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` — collapse trigger moved from `completeSet()` to `onTimerFinish()`/`onTimerTick(0)` + `skipRestTimer()`
- `app/src/test/java/com/powerme/app/ui/workout/WorkoutViewModelTest.kt` — updated tests

---

## How to QA

1. Start a workout with 2 warmup sets + working sets, warmup rest timer set
2. Complete warmup #1 → rest timer starts → **warmup rows stay visible**
3. Rest timer #1 finishes → separator collapses → **warmup rows still visible**
4. Complete warmup #2 → rest timer #2 starts → **warmup rows still visible**
5. Rest timer #2 finishes → separator collapses → **both warmup rows collapse immediately into "W ×2 ✓"**
6. Tap "W ×2 ✓" row → warmup rows expand back
7. Repeat step 4, but tap **Skip** on rest timer #2 → separator AND warmup rows collapse simultaneously
8. Start workout with 1 warmup set, no rest timer after last set → complete it → warmup collapses immediately on confirmation
