# BUG: RPE missing from previous session values in active workout

## Status
[ ] Open

## Description
In the active workout screen, the PREV column shows weight and reps from the last session for each set, but RPE values are not displayed. When a user recorded RPE in their previous workout session, those RPE values should appear in the PREV column alongside weight/reps so the user can reference their previous effort level. Likely root cause is in `WorkoutViewModel` (previous session data loading) or `ActiveWorkoutScreen.kt` (PREV column rendering) — RPE may not be included in the previous session query or not rendered in the PREV display.

Affected screen: `ActiveWorkoutScreen.kt` — set row PREV column.

## Steps to Reproduce
1. Complete a workout with RPE values recorded on sets
2. Start a new workout using the same routine
3. Look at the PREV column on set rows
4. Observe: weight and reps from previous session are shown, but RPE is missing

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
<!-- populated after fix is applied -->
