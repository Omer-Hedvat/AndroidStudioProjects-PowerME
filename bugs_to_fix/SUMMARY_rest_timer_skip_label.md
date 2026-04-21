# Fix Summary: Rest timer TimerControlsSheet skip button shows wrong label

## Root Cause
`TimerControlsSheet` had a `TextButton` with `Text("Skip")`. The intended label per spec was "Next" (skip remaining rest, proceed to next set). The label was simply wrong.

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/workout/ActiveWorkoutScreen.kt` | Changed `Text("Skip")` → `Text("Next")` in `TimerControlsSheet` |

## Surfaces Fixed
- Rest timer controls bottom sheet — the skip/proceed action now reads "Next" instead of "Skip"

## How to QA
1. Start a workout with a rest timer configured on an exercise.
2. Complete a set — rest timer starts and the rest separator turns primary-colored.
3. Tap the rest separator to open the `TimerControlsSheet`.
4. Verify: the full-width text button at the bottom reads **"Next"** (not "Skip" and not a checkmark icon).
5. Tap "Next" and verify the rest timer dismisses and the next set is ready.
