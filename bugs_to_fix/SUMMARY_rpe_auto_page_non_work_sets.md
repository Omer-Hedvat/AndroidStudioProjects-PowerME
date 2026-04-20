# Fix Summary: RPE auto-page fires for non-work set types

## Root Cause
`WorkoutViewModel.toggleSetCompleted()` set `_rpeAutoPopTarget.value` whenever `useRpeAutoPopEnabled == true` with no guard on the completed set's type. The `completedSet` variable was declared *after* the RPE assignment, so the type information was never checked.

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` | Moved `ex` and `completedSet` declarations above the RPE block; added guard `completedSet?.setType == SetType.NORMAL` before setting `_rpeAutoPopTarget` |
| `app/src/test/java/com/powerme/app/ui/workout/WorkoutViewModelTest.kt` | Added 3 tests: RPE auto-pop suppressed for WARMUP, DROP, and FAILURE set types |

## Surfaces Fixed
- Active workout screen: RPE picker sheet no longer auto-appears after completing a Warmup (W), Drop (D), or Failure (F) set when RPE Auto-Pop is enabled

## How to QA
1. Enable **Settings → RPE Auto-Pop**
2. Open a routine and add a Warmup (W) set to any exercise
3. Start the workout and complete the Warmup set — confirm the RPE picker does **not** appear
4. Add a Drop (D) set and complete it — confirm no RPE picker
5. Add a Failure (F) set and complete it — confirm no RPE picker
6. Complete a regular Work set — confirm the RPE picker **does** appear
