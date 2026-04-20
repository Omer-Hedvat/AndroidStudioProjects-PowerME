# BUG: Timed exercise rows missing RPE column

## Status
[x] Completed

## Severity
P2 normal
- RPE is a valid and useful measure for timed exercises (Plank, Bird-Dog, etc.) and is already collected via the auto-pop sheet. The inline column is missing, making timed rows inconsistent with weighted rows.

## Description
When the timed exercise column layout was fixed (BUG_timed_exercise_row_columns), the RPE column was intentionally omitted from the header and row. However, RPE is equally meaningful for timed exercises — effort/difficulty of a timed hold is a valid signal for ReadinessEngine and trends data. The RPE column should be re-added to timed exercise rows, consistent with how weighted exercise rows display it.

## Steps to Reproduce
1. Start a workout containing a timed exercise (e.g. Plank, Bird-Dog)
2. Observe the column header: SET | PREV | WEIGHT | TIME(S) — no RPE column
3. Complete a set — RPE auto-pop appears (if enabled) but there is no inline RPE column in the row

## Dependencies
- **Depends on:** BUG_timed_exercise_row_columns ✅
- **Blocks:** —
- **Touches:** `ActiveWorkoutScreen.kt`, `WorkoutViewModel.kt`, `WORKOUT_SPEC.md`

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
- Added `onUpdateRpe: (Int?) -> Unit` parameter to `TimedSetRow` — routes to `viewModel.updateRpe()` (same path as strength rows), properly persisting `rpeValue` (Int) to DB via `workoutSetDao.updateRpe()`. Previously the picker routed through `onUpdateSet` → `updateTimedSet(rpeValue=null)` which only stored the text field and never updated `rpeValue`.
- Updated `TimedHeader` to show "RPE" column label (weight 0.10). Weights adjusted: WEIGHT 0.25→0.22, TIME 0.25→0.20, trailing Spacer 0.20→0.18.
- IDLE state: added tappable RPE cell (weight 0.10) between TIME and the play button. WEIGHT 0.25→0.22, TIME 0.25→0.20, PLAY 0.14→0.12. Shows "—" or numeric value.
- COMPLETED state: replaced `Spacer(0.10)` with RPE cell (weight 0.10) including category indicator (✦ golden, colored dots for max/moderate/low effort). WEIGHT 0.25→0.22, TIME 0.25→0.20, added Spacer(0.08) before CHECK.
- Updated `WORKOUT_SPEC.md` section 4.8 to document the new column layout.
