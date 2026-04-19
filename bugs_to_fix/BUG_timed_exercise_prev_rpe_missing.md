# BUG: RPE missing from PREV column for timed exercises

## Status
[x] Fixed

## Severity
P2 normal
- PREV column for timed exercises shows weight and time but omits RPE, unlike standard exercises which show full `weight x reps @ RPE` format.

## Description
In the active workout screen, timed exercise set rows show PREV data (from the prior session) but without the RPE value. Standard exercises correctly show `weight x reps @ RPE` in the PREV column. Timed exercises should show the equivalent format including RPE (e.g. `60s @ 8`).

## Steps to Reproduce
1. Have a routine with a timed exercise that has prior session data including RPE
2. Start a new workout using that routine
3. Look at the timed exercise set row → PREV column
4. Observe: RPE is not shown in PREV (e.g. shows "60s" instead of "60s @ 8")

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `WorkoutViewModel.kt`, `ActiveWorkoutScreen.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
Root cause: `formatGhostTimedLabel(weight, timeSeconds)` in `ActiveWorkoutScreen.kt` had no `rpe` parameter and never appended RPE to the PREV label. Both IDLE and COMPLETED state call sites omitted `ghostRpe`.

Fix: updated `formatGhostTimedLabel` to accept a third `rpe: String?` parameter and appended `@$rpe` to the base label when non-null (matching the `formatGhostLabel` pattern for strength exercises). Updated both call sites to pass `set.ghostRpe`.
