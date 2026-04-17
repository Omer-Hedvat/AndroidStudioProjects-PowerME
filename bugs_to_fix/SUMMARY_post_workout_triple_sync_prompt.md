# Fix Summary: Post-workout routine sync prompt appears 3 times

## Root Cause

Two separate UI components owned the routine sync prompt simultaneously:

1. **`PostWorkoutSummarySheet`** (`ActiveWorkoutScreen.kt`) — the original sync UI. It rendered immediately after `finishWorkout()` set `pendingWorkoutSummary`, first without sync options (async diff in-flight), then re-rendered seconds later with sync buttons — appearing as two prompts.
2. **`RoutineSyncCard`** (`WorkoutSummaryScreen.kt`) — introduced when `WorkoutSummaryScreen` became the unified post-workout view. `PostWorkoutSummarySheet` was never removed, so both fired in sequence.

Secondary bug: the diff engine compared 0 completed sets against the routine snapshot's N sets, always finding a "structural" change — producing a false-positive `RoutineSyncType.STRUCTURE` even for aborted/empty workouts.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` | Added `hasCompletedWorkSets` guard before the routine sync diff block; diff now skips entirely when 0 non-warmup sets are completed |
| `app/src/main/java/com/powerme/app/ui/workout/ActiveWorkoutScreen.kt` | Removed `PostWorkoutSummarySheet` usage block and its ~175-line composable definition; added `LaunchedEffect(pendingWorkoutSummary)` that auto-calls `onWorkoutFinished()` when summary becomes non-null |
| `app/src/main/java/com/powerme/app/ui/history/WorkoutSummaryScreen.kt` | Added "Save as Routine" `TextButton` above the Done button in the post-workout footer (dialog and callback were already wired, trigger was missing) |
| `app/src/test/java/com/powerme/app/ui/workout/WorkoutViewModelTest.kt` | Added test: `finishWorkout with routine snapshot but 0 completed sets returns null routineSync` |
| `WORKOUT_SPEC.md` | Updated §7.3, §7.3b, §7.5, §8, rendering order list, invariants #4 and #20 |
| `NAVIGATION_SPEC.md` | Updated `workout_summary` route note to reflect `LaunchedEffect` auto-navigation |

## Surfaces Fixed

- **Post-workout flow:** After tapping Finish Workout, app navigates directly to `WorkoutSummaryScreen` without any interim bottom sheet
- **Sync prompt:** `RoutineSyncCard` appears once (in `WorkoutSummaryScreen`) if and only if there are completed non-warmup sets that differ from the routine template
- **Empty workout:** Finishing with 0 completed sets no longer shows any sync prompt
- **Save as Routine:** Now accessible from the Done button area in `WorkoutSummaryScreen`

## How to QA

1. **Empty/aborted workout:**
   - Start a routine-based workout → immediately tap **Finish Workout**
   - ✅ App navigates directly to `WorkoutSummaryScreen` (no bottom sheet)
   - ✅ No "Update Routine?" card appears in the summary

2. **Normal workout with changes:**
   - Start a routine workout → complete ≥1 set with a different weight than the template default → tap **Finish**
   - ✅ App navigates directly to `WorkoutSummaryScreen` (no bottom sheet)
   - ✅ `RoutineSyncCard` appears **once** in the summary
   - ✅ Tap "Update routine" → card disappears, snackbar confirms

3. **Save as Routine (post-workout):**
   - On `WorkoutSummaryScreen` with `isPostWorkout=true`
   - ✅ "Save as Routine" button appears above the Done button
   - ✅ Tap it → name dialog → confirm → new routine created

4. **History view (no sync):**
   - Tap any completed workout from the History tab
   - ✅ No `RoutineSyncCard` shown (`isPostWorkout=false`)
