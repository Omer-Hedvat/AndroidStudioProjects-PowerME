# Fix Summary: Routine sync card doesn't dismiss after tapping Update/Keep

## Root Cause
`WorkoutSummaryViewModel` initialises `pendingRoutineSync` from `savedStateHandle` in `init` and never clears it. The navigation callbacks (`onConfirmSyncValues`, `onDismissSync`, etc.) called methods on `WorkoutViewModel` that clear *its* `pendingRoutineSync` state, but `WorkoutSummaryViewModel._uiState.pendingRoutineSync` was never touched. The card's visibility is driven by the summary ViewModel's state, so it remained visible indefinitely.

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/history/WorkoutSummaryViewModel.kt` | Added `snackbarMessage: String?` to `WorkoutSummaryUiState`; added `confirmRoutineSync(message)`, `dismissRoutineSync()`, `consumeSnackbar()` methods |
| `app/src/main/java/com/powerme/app/ui/history/WorkoutSummaryScreen.kt` | Wrapped sync callbacks to call `viewModel.confirmRoutineSync()` / `viewModel.dismissRoutineSync()`; added `SnackbarHost` to Scaffold and `LaunchedEffect` to show snackbar |
| `app/src/test/java/com/powerme/app/ui/history/WorkoutSummaryViewModelTest.kt` | Added 4 tests covering dismiss, confirm, consumeSnackbar, and no-message-on-dismiss behaviour |

## Surfaces Fixed
- Post-workout summary screen: tapping "Update Values", "Update Routine", "Update Values & Structure" now dismisses the sync card and shows a confirmation snackbar
- Post-workout summary screen: tapping "Keep Original" now dismisses the sync card (no snackbar)

## How to QA
1. Complete a workout using a routine where you deliberately changed weights or reps from the defaults.
2. On the post-workout summary screen, observe the "Update Routine?" card.
3. Tap **"Update Values"** (or "Update Routine") — confirm the card disappears and a snackbar like "Routine defaults updated" appears briefly at the bottom.
4. Repeat the workout, this time tap **"Keep Original"** — confirm the card disappears without a snackbar.
5. Confirm the card does not reappear after dismissal.
