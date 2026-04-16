# BUG: Post-workout routine sync prompt appears multiple times (triple prompt)

## Status
[ ] Open

## Description
After finishing a workout the user is asked about routine sync 3 times:

1. **`PostWorkoutSummarySheet`** (bottom sheet, `ActiveWorkoutScreen.kt` ~line 164) — first appears with just "Save as Routine" + "Done" (no sync options), then seconds later re-renders with the sync buttons ("Update routine" / "Keep original routine") after the async diff completes. The sheet's two states (pre-diff and post-diff) appear as two separate prompts to the user.

2. **`WorkoutSummaryScreen`** (`WorkoutSummaryScreen.kt` ~line 122) — after the bottom sheet is dismissed and the app navigates to the full-screen summary, a `RoutineSyncCard` ("Update Routine?") is shown inline again because `isPostWorkout=true && pendingRoutineSync != null` is still true.

**Spec violation:** `WORKOUT_SPEC.md §7.5` states:
> *"The PostWorkoutSummarySheet is the sole owner of the sync UI. No AlertDialogs or intermediate screens should be used to resolve the routine sync."*

The `WorkoutSummaryScreen` (introduced in `HISTORY_SUMMARY_REDESIGN_SPEC.md` as the unified post-workout + history detail view) now owns the sync inline card — but `PostWorkoutSummarySheet` was never removed. The two UIs coexist and fire in sequence.

**Secondary issue — false positive diff:**
In the screenshots the workout contains 0 completed sets / 0 kg volume, yet `RoutineSyncType.STRUCTURE` is detected. The diff engine may be treating an empty/aborted workout as a structural change relative to the routine template.

## Steps to Reproduce
1. Open a routine-based workout.
2. Immediately tap "Finish Workout" (or do a short session with any completion).
3. Observe: the summary bottom sheet appears, then updates to show Update/Keep buttons, then after dismissal the full WorkoutSummaryScreen also shows the Update Routine card.

## Assets
- Screenshots:
  - `bugs_to_fix/assets/post_workout_triple_sync_prompt/Screenshot_20260415_154527_PowerME.jpg` — bottom sheet, no sync options yet
  - `bugs_to_fix/assets/post_workout_triple_sync_prompt/Screenshot_20260415_154532_PowerME.jpg` — bottom sheet, sync options now visible
  - `bugs_to_fix/assets/post_workout_triple_sync_prompt/Screenshot_20260415_154549_PowerME.jpg` — WorkoutSummaryScreen with inline sync card
- Related spec: `WORKOUT_SPEC.md §7` (Routine Sync), `§7.5` (Implementation Invariant), `§8` (Post-Workout Summary Sheet)
- Related spec: `future_devs/HISTORY_SUMMARY_REDESIGN_SPEC.md` (WorkoutSummaryScreen introduction)

## Fix Notes
<!-- populated after fix is applied -->
<!--
Expected fix direction:
- Remove the `PostWorkoutSummarySheet` from ActiveWorkoutScreen (it is superseded by WorkoutSummaryScreen).
- `finishWorkout()` should navigate directly to `workout_summary/{id}?isPostWorkout=true&syncType={type}` — no interim bottom sheet.
- `WorkoutSummaryScreen` becomes the sole owner of sync UI per §7.5.
- The diff should only fire a non-null RoutineSyncType when there are actual completed sets to compare against; an all-incomplete session should return null (no prompt).
-->
