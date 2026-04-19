# BUG: Routine sync card doesn't dismiss after tapping Update/Keep

## Status
[x] Fixed

## Severity
P1 high
- Core post-workout flow is broken: tapping either "Update routine" or "Keep original" produces no visible response — the card stays on screen with no confirmation.

## Description
On the WorkoutSummaryScreen, the routine sync card ("Update routine?" with "Update" and "Keep original" buttons) does not dismiss after either button is tapped. No snackbar appears, the card remains visible, and tapping again repeats the no-op. The user has no way to dismiss this card.

Note: BUG_post_workout_triple_sync_prompt was fixed (card now appears once), but the dismiss/confirm action is broken.

Root cause likely: the ViewModel action (updateRoutine / keepOriginal) completes the DB operation but does not clear the sync card state flag, so the card remains visible. The snackbar trigger may also be broken.

## Steps to Reproduce
1. Complete a workout where the active sets differ from the routine template
2. On WorkoutSummaryScreen, observe the routine sync card
3. Tap "Update routine" — nothing happens, card stays
4. Tap "Keep original" — nothing happens, card stays

## Dependencies
- **Depends on:** BUG_post_workout_triple_sync_prompt ✅
- **Blocks:** —
- **Touches:** `WorkoutSummaryScreen.kt`, `WorkoutSummaryViewModel.kt`, `WorkoutSummaryViewModelTest.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`

## Fix Notes
`WorkoutSummaryViewModel` had no method to clear its own `pendingRoutineSync` state. The navigation callbacks (`onConfirmSyncValues`, `onDismissSync`, etc.) correctly called `WorkoutViewModel` methods, but `WorkoutSummaryViewModel._uiState.pendingRoutineSync` was set from `savedStateHandle` in `init` and never cleared.

Fix:
- Added `snackbarMessage: String?` to `WorkoutSummaryUiState`
- Added `confirmRoutineSync(message)`, `dismissRoutineSync()`, and `consumeSnackbar()` to `WorkoutSummaryViewModel`
- Wrapped the sync callbacks in `WorkoutSummaryScreen` to call `viewModel.confirmRoutineSync()` / `viewModel.dismissRoutineSync()` after the external navigation callback
- Added `SnackbarHost` to the screen's Scaffold and a `LaunchedEffect` to consume `snackbarMessage`
