# BUG: Timed exercise row has wrong columns (no PREV, spurious RPE field)

## Status
[x] Fixed

## Description
In the Active Workout screen, timed (time-based) exercises render incorrect columns in their set rows:

1. **Missing PREV column** — regular exercises show a PREV column to display the previous session's performance. Timed exercise rows have no such column, so there is no historical reference while working out.
2. **RPE column present but shouldn't be** — timed exercises do not require an RPE rating. The RPE column is shown in the header but serves no purpose for this exercise type.
3. **Orphaned '-' box with no header** — there is a box showing only a dash (`-`) that has no corresponding column header above it. This appears to be a misaligned or leftover element in the row layout.

## Severity
P1

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ActiveWorkoutScreen.kt`, `WorkoutViewModel.kt`

## Steps to Reproduce
1. Create or open a routine that contains at least one timed exercise (e.g. Bird-Dog).
2. Start an active workout.
3. Scroll to the timed exercise card.
4. Observe the column headers (SET, WEIGHT, TIME(S), RPE) and the set row contents.

## Assets
- Screenshot: provided by user (Bird-Dog card in active workout, headers SET | WEIGHT | TIME(S) | RPE with a '-' box and play/check buttons in the row)

## Fix Notes
- Added `ghostTimeSeconds: String?` field to `ActiveSet` and populated it from `ghost?.timeSeconds` in the workout loading logic.
- Added `formatGhostTimedLabel(weight, timeSeconds)` helper in `ActiveWorkoutScreen.kt`.
- Rewrote `TimedHeader()` to use the shared column-weight constants (`SET_COL_WEIGHT`, `PREV_COL_WEIGHT`, `WEIGHT_COL_WEIGHT`) plus a 0.25f TIME(S) column and 0.20f trailing spacer — matching IDLE/COMPLETED data-row weights exactly.
- Replaced RPE inline input field in IDLE and COMPLETED states with a PREV ghost-data box aligned to `PREV_COL_WEIGHT`.
- RPE is still persisted via the auto-pop RPE picker sheet; the inline column was the only thing removed.
