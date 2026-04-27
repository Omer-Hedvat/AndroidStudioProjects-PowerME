# BUG: RPE auto-pop fires even when RPE is already recorded for the set

## Status
[x] Fixed

## Severity
P2 normal
- Quality-of-life regression: unnecessary RPE sheet interrupts workout flow when the user has already rated the set.

## Description
When Settings → RPE mode is set to always-on, completing a set that already has an RPE value still triggers the RPE keyboard/sheet auto-pop. The sheet should be suppressed if `set.rpe != null` (i.e. an RPE has already been entered for that set).

Likely root cause: `WorkoutViewModel.toggleSetCompleted()` emits `_rpeAutoPopTarget` without first checking whether the completed set already carries a non-null RPE.

## Steps to Reproduce
1. Enable RPE in Settings → Workout Style → RPE mode (set to always-on).
2. Start an active workout.
3. Enter an RPE value for a set manually.
4. Complete the same set (tap ✓).
5. Observe: RPE sheet auto-pops even though RPE is already recorded.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `WorkoutViewModel.kt`, `WorkoutViewModelTest.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md §14.3`

## Fix Notes
<!-- populated after fix is applied -->
