# BUG: Rest timer doesn't collapse when skipped (all set types)

## Status
[x] Fixed

## Severity
P1 high
- Skipping a rest timer (tap Skip) leaves the rest timer row visible for ALL set types (warmup and working sets). Rest timers only collapse when they naturally finish — not when the user actively skips them.

## Description
When the user taps "Skip" on a rest timer, the rest timer row should immediately collapse/hide.
Currently it only collapses when the timer reaches zero naturally. This affects all set types —
warmup sets and working sets alike. The skip action likely calls `stopRestTimer()` which cancels
the timer but does not update `hiddenRestSeparators`, leaving the separator row visible.

Previous fix attempts addressed warmup-specific delete scenarios but not the general skip path.

## Steps to Reproduce
1. Start a workout (any exercise, any set type)
2. Complete a set → rest timer starts
3. Tap **Skip** on the rest timer
4. Observe: rest timer row stays visible instead of collapsing

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `WorkoutViewModel.kt`, `ActiveWorkoutScreen.kt`, `WorkoutViewModelTest.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
`skipRestTimer()` in `WorkoutViewModel.kt` now captures `exerciseId` and `setOrder` from the active
`restTimer` before clearing it, and adds `"${exerciseId}_${setOrder}"` to `hiddenRestSeparators`
in the same atomic state update. Three new unit tests cover: working-set skip, warmup-to-work skip
(which also verifies warmup collapse fires on confirmation before the timer ends), and no-op skip
when no timer is active.
