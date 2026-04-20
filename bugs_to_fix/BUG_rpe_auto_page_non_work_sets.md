# BUG: RPE auto-page fires for non-work set types (warmup, drop, failure)

## Status
[x] Fixed

## Severity
P2

## Description
When "RPE Auto-Pop" is enabled in settings, the RPE picker sheet is auto-shown after any set is completed — including Warmup (W), Drop (D), and Failure (F) sets. It should only fire for regular Work sets (`SetType.NORMAL`).

Warmup sets are intentionally low-effort and shouldn't be RPE-rated. Drop sets and failure sets are used as supplementary sets within a working block; auto-popping RPE on them interrupts the workout flow unnecessarily.

The root cause is in `WorkoutViewModel.toggleSetCompleted()` around line 1050: `_rpeAutoPopTarget.value` is set whenever `useRpeAutoPopEnabled == true` with no guard on `completedSet?.setType`.

## Steps to Reproduce
1. Enable Settings → "RPE Auto-Pop".
2. Add a Warmup (W), Drop (D), or Failure (F) set to an exercise.
3. Start an active workout and complete one of those non-work sets.
4. Observe: the RPE picker sheet appears automatically — it should not.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `WorkoutViewModel.kt`, `WorkoutViewModelTest.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md §14.3` (RPE System)

## Fix Notes
In `WorkoutViewModel.toggleSetCompleted()`, the `_rpeAutoPopTarget` assignment now checks `completedSet?.setType == SetType.NORMAL` before firing. The `ex` and `completedSet` variable declarations were moved above the RPE block (they were previously below it) so the type guard can be applied inline. Three unit tests added to `WorkoutViewModelTest.kt` covering WARMUP, DROP, and FAILURE set completion with RPE auto-pop enabled — all confirm the signal stays null.
