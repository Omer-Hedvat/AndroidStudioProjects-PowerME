# BUG: Delete workout from History navigates back to deleted workout summary instead of History list

## Status
[x] Fixed

## Severity
P1 high
- Visible regression affecting daily use: deleting a workout leaves the user on a dead/stale screen instead of returning to the History list.

## Description
After deleting a workout from the History detail / summary screen, the app navigates back to the (now-deleted) workout's summary/detail page instead of popping all the way back to the main History list. The deleted workout no longer exists in the database, so the screen is showing stale or empty data.

Likely root cause: the delete action pops only one back-stack entry (the confirmation dialog or an intermediate screen) rather than popping back to the `history` route. The `WorkoutDetailScreen` or `WorkoutSummaryScreen` back-stack entry remains after the delete, and the navigator lands on it.

## Steps to Reproduce
1. Open the app and navigate to the **History** tab.
2. Tap any workout to open its detail/summary screen.
3. Trigger the delete action (e.g. overflow menu → Delete, or swipe-to-delete within the detail screen).
4. Confirm deletion.
5. Observe: the app stays on or returns to the now-deleted workout's detail/summary screen instead of the History list.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `WorkoutDetailScreen.kt`, `WorkoutDetailViewModel.kt`, `PowerMeNavigation.kt`, `HistoryViewModel.kt`

## Assets
- Related spec: `HISTORY_ANALYTICS_SPEC.md`, `NAVIGATION_SPEC.md`

## Fix Notes
Added `onDeleted: () -> Unit` parameter to `WorkoutDetailScreen` (defaults to `onNavigateBack` for backward compatibility). The delete confirmation now calls `onDeleted()` instead of `onNavigateBack()`. In `PowerMeNavigation.kt`, the `WorkoutDetailScreen` composable is wired with `onDeleted = { navController.popBackStack(Screen.History.route, inclusive = false) }`, which clears both the `workout_detail` and `workout_summary` back-stack entries and lands the user on the History list. Two unit tests added to `WorkoutDetailViewModelTest` covering callback invocation on successful delete and on missing workout.
