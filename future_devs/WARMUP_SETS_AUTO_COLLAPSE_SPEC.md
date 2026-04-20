# Warmup Sets — Auto-Collapse After Completion

| Field | Value |
|---|---|
| **Phase** | P1 |
| **Status** | `done` |
| **Effort** | S |
| **Depends on** | — |
| **Blocks** | — |
| **Touches** | `ActiveWorkoutScreen.kt`, `WorkoutViewModel.kt` |

---

## Overview

Warmup sets are preparatory — once completed, they don't need to remain visually prominent. After all warmup sets for an exercise are done, they auto-collapse into a compact summary row, keeping the screen focused on the upcoming working sets.

---

## Behaviour

- When the **last warmup set** of an exercise is confirmed, the warmup rows animate closed and collapse into a single compact row (e.g. "3 warmup sets ✓")
- Collapse fires **immediately on set confirmation** (inside `completeSet()`) — not on rest timer completion
- Collapsed state shows: count of warmup sets, a checkmark, and a chevron to re-expand
- Tapping the collapsed row expands warmup sets back to full view
- If a warmup set is partially done (some confirmed, some not), no collapse occurs
- Only warmup sets collapse — drop sets, failure sets, and working sets are unaffected
- Collapse is per-exercise, not global
- Un-completing any warmup set (tapping the checkmark again) un-collapses the warmup rows
- Deleting a warmup set re-evaluates collapse state (auto-collapses if remaining warmups are all done)

---

## Rest Timer — Skip Behaviour (all set types)

When the user taps **Skip** on a rest timer (`skipRestTimer()`), the active separator row for that set immediately collapses. This applies to all set types (warmup and working sets). Before this fix the skip only stopped the countdown; the passive separator row remained visible until the next compose recomposition cleared it naturally.

Fix: `skipRestTimer()` now captures `exerciseId` and `setOrder` from `restTimer` before clearing it, and adds `"${exerciseId}_${setOrder}"` to `hiddenRestSeparators` in the same state update.

---

## UI Changes

- `warmupsCollapsed: Boolean` flag per exercise derived from `ActiveWorkoutState.collapsedWarmupExerciseIds: Set<Long>`
- Collapsed warmup row: single `ListItem` with "W ×N ✓" label, `onSurfaceVariant` text, trailing `ExpandMore`/`ExpandLess` icon
- Animate collapse/expand using `AnimatedVisibility` (shrink + fade out / expand + fade in, 150–200ms)
- Warmup rows (including any active rest separator) are inside the `AnimatedVisibility(!warmupsCollapsed)` block; they collapse as a unit

---

## Implementation Notes

### `WorkoutViewModel.kt`

- **`completeSet(exerciseId, setOrder)`**: after toggling set completion, checks `completedSetType == SetType.WARMUP` and updates `collapsedWarmupExerciseIds` — fires before `startRestTimer()`.
- **`deleteSet(exerciseId, setOrder)`**: re-checks warmup collapse state for the exercise after deleting a warmup set.
- **`skipRestTimer()`**: captures active timer's `exerciseId`/`setOrder`, adds to `hiddenRestSeparators`, resets `restTimer` — all in one atomic state update.
- **`toggleWarmupsCollapsed(exerciseId)`**: toggled by the collapsed row's tap callback.

### `ActiveWorkoutScreen.kt`

- Warmup rows are wrapped in `AnimatedVisibility(visible = !warmupsCollapsed)`.
- Collapsed summary row is in `AnimatedVisibility(visible = warmupsCollapsed)`.
- Both are rendered only when `hasWarmups = warmupSets.isNotEmpty()`.

---

## Files Touched

- `app/src/main/java/com/powerme/app/ui/workout/ActiveWorkoutScreen.kt` — collapsed warmup row composable + AnimatedVisibility
- `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` — `warmupsCollapsed` state, trigger on last warmup confirm, `skipRestTimer()` separator hiding

---

## How to QA

1. Start a workout with 2 warmup sets + 3 working sets
2. Complete both warmup sets → they collapse into "W ×2 ✓" row **immediately** (no waiting for rest timer)
3. Tap the collapsed row → expands back to individual warmup rows
4. Complete only 1 of 2 warmup sets → no auto-collapse
5. Working sets are unaffected throughout
6. Complete any set (any type) → rest timer starts → tap Skip → rest separator row **immediately collapses** (does not linger as a passive row)
7. Skip with no active timer → no change to hidden separators
