# SUMMARY: BUG_warmup_rest_no_collapse_on_skip (v2 — rework fix)

## Root Cause

Two related gaps in `deleteSet()` in `WorkoutViewModel.kt`:

**Issue B (this bug):** `stopRestTimer()` resets `restTimer` but does NOT touch `hiddenRestSeparators`. After skipping a warmup set, the UI's passive-separator condition `(isNotLastSet && effectiveRest > 0) && !isSeparatorHidden` still evaluated `true` for the preceding set's row, keeping the rest separator visible with no timer running.

**Issue A (auto-collapse regression, same root):** `deleteSet()` never re-checked whether remaining warmups were all completed after deletion. Deleting the only incomplete warmup while others were already done never triggered the collapse.

## What Changed

- **`WorkoutViewModel.kt`** — `deleteSet()` `_workoutState.update {}` block extended with:
  1. **Preceding separator hiding:** when deleted set is `WARMUP` and `setOrder > 1`, add `"${exerciseId}_${setOrder-1}"` to `hiddenRestSeparators` (handles both active-timer and passive cases).
  2. **Auto-collapse re-check:** after rebuilding exercises, inspect remaining warmup sets — collapse if all done, un-collapse otherwise.

- **`WorkoutViewModelTest.kt`** — Added four new tests:
  - `deleteSet auto-collapses warmups when deleting incomplete warmup leaves all remaining completed`
  - `deleteSet does not auto-collapse when some remaining warmup sets are still incomplete`
  - `deleteSet hides preceding warmup separator when deleting a warmup set`
  - `deleteSet does not hide preceding separator when deleting a NORMAL set`

## How to QA

1. Start a workout with an exercise that has 3 warmup sets (warmup rest > 0, e.g. 30s).
2. **Passive separator after skip:** Without completing anything, swipe-delete warmup set 2. The rest separator below warmup set 1 should disappear immediately.
3. **Active timer cancellation:** Complete warmup set 1 (starts its rest timer), then swipe-delete warmup set 2. Both the active timer pill and separator row should disappear immediately.
4. **Auto-collapse on partial delete:** Complete warmup sets 1 and 2, leave set 3 incomplete. Swipe-delete set 3. The warmup block should auto-collapse to "W ×2 ✓".
5. **No false collapse:** Complete only warmup set 1, swipe-delete set 3. Block should NOT collapse (set 2 still incomplete).
6. **Normal sets unaffected:** Complete a normal set and delete the next normal set — rest timer must continue running.
