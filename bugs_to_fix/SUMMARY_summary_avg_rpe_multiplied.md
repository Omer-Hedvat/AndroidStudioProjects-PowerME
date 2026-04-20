# Fix Summary: Workout summary avg RPE shown as 10× actual value

## Root Cause
`buildExerciseCard()` in `WorkoutSummaryViewModel.kt` computed `rpeValues.average()` without dividing by 10. RPE is stored on a ×10 scale (80 = RPE 8.0, 95 = RPE 9.5), so the displayed average was 10× too large (e.g. 86.25 instead of 8.625 for sets @8.0, 9.5, 7.0, 10.0).

The `isGoldenZone` check (`avgRpe >= 8.0 && avgRpe <= 9.0`) was also broken as a consequence — a raw average of 86.25 would never fall in that range.

Individual set RPE rendering in `SetDetailRow` (screen-side) was already correct — it manually divides by 10 via `val whole = set.rpe / 10; val fraction = set.rpe % 10`.

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/history/WorkoutSummaryViewModel.kt` | Divided `rpeValues.average()` by `10.0` in `buildExerciseCard()`; added comment explaining ×10 storage scale |
| `app/src/test/java/com/powerme/app/ui/history/WorkoutSummaryViewModelTest.kt` | Fixed existing RPE test inputs from display-scale (8, 9, 7) to storage-scale (80, 90, 70); added new regression test `avgRpe divides stored x10 values by 10 for display` |

## Surfaces Fixed
- Workout Summary screen (post-workout and history): avg RPE per exercise now shows 8.6 instead of 86.3

## How to QA
1. Complete a workout with RPE logged on 4 sets (e.g. RPE 8, 9.5, 7, 10)
2. View the post-workout WorkoutSummaryScreen
3. Verify: avg RPE chip shows ~8.6 (not ~86.3)
4. Verify: if avg RPE is between 8.0 and 9.0, the golden zone badge ("RPE 8–9 ✦") is shown
5. Verify: individual set rows still show e.g. "@ 8.5" (not "@ 85") — this path was already correct
