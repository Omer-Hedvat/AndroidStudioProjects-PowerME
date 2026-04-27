# BUG: Routine preview screen cannot scroll for long workouts

## Status
[x] Fixed

## Severity
P0 blocker — "Start Workout" button is hidden below the fold, making it impossible to start a long routine

## Description
The routine preview sheet/screen does not scroll. When a routine has enough exercises or blocks to overflow the screen, the content is clipped and the "Start Workout" button at the bottom is unreachable — the user cannot start the workout at all.

## Steps to Reproduce
1. Create a routine with enough exercises/blocks to overflow the screen.
2. Tap the routine to open the preview.
3. Observe: the bottom exercises and the "Start Workout" button are cut off; no scrolling is possible and the workout cannot be started.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ui/workouts/WorkoutsScreen.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
Added `.verticalScroll(rememberScrollState())` to the `Column` inside `RoutineOverviewSheet` in `WorkoutsScreen.kt`. The entire sheet content (header + exercises + Start Workout button) is now scrollable.
