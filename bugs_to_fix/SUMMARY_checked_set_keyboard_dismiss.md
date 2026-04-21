# Fix Summary: Keyboard does not dismiss when a set is checked off

## Root Cause
The CHECK box in `WorkoutSetRow` used `.clickable(onClick = onCompleteSet)` which called the completion callback but never released keyboard focus. The `LocalFocusManager` was already available in the composable scope but unused at this call site.

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/workout/ActiveWorkoutScreen.kt` | Changed CHECK box `.clickable(onClick = onCompleteSet)` to `.clickable { focusManager.clearFocus(); onCompleteSet() }` |

## Surfaces Fixed
- Active workout screen — keyboard now collapses immediately when the set ✓ button is tapped

## How to QA
1. Start a workout with any weighted exercise.
2. Tap the WEIGHT or REPS field on any set row — keyboard appears.
3. Tap the ✓ (green check) button in the rightmost column of that row.
4. Verify: keyboard collapses as the set is marked complete.
