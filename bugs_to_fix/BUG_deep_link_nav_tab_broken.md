# BUG: Bottom nav broken after History → Trends deep-link

## Status
[x] Fixed

## Severity
P1 high
- After using the "View Trend" deep-link from History, the bottom navigation bar stops working correctly — History tab tap does nothing, and further taps misdirect to Trends instead of the intended tab.

## Description
After tapping "View Trend" on a History exercise card (which deep-links to the Trends tab with an exercise pre-selected), the bottom navigation bar enters a broken state:

1. Tapping **History** tab → nothing happens (no navigation)
2. Tapping **Workouts** tab → navigates correctly
3. After step 2, tapping **History** tab → navigates to **Trends** instead of History

Root cause: the deep-link from History to Trends uses `navController.navigate(Routes.TRENDS)`, which pushes Trends onto the back stack on top of the History destination. The bottom nav's selected item tracking and `NavGraph` back stack get out of sync — History is still the "current" route in the back stack's eyes but Trends is the visible destination. Subsequent History taps either do nothing (already "selected") or resolve to the wrong back stack entry.

Fix likely involves using `NavOptions` with `popUpTo` + `launchSingleTop = true` + `restoreState = true` when navigating via the deep-link, the same way bottom nav item taps themselves navigate — so Trends replaces the current back stack entry rather than stacking on top of it.

## Steps to Reproduce
1. Open History tab, tap any completed workout
2. Tap **"View Trend"** on an exercise card → Trends opens with exercise pre-selected ✅
3. Tap **History** in the bottom nav → nothing happens ❌
4. Tap **Workouts** → navigates correctly ✅
5. Tap **History** → navigates to **Trends** instead of History ❌

## Dependencies
- **Depends on:** History → Trends deep-link (Step B) ✅
- **Blocks:** —
- **Touches:** `PowerMeNavigation.kt`, `WorkoutSummaryScreen.kt` (where "View Trend" triggers navigation)

## Assets
- Related spec: `NAVIGATION_SPEC.md`, `HISTORY_SUMMARY_REDESIGN_SPEC.md`

## Fix Notes
The `onNavigateToTrends` callback in `PowerMeNavigation.kt` (inside the `WorkoutSummaryScreen` composable registration) was using a plain `navController.navigate(...)` call with no `NavOptions`. This pushed the Trends destination onto the back stack on top of History + WorkoutSummary, leaving the NavGraph in an inconsistent state where the back stack didn't match the bottom nav's expectations.

Fixed by adding the same `NavOptions` block that bottom nav tab taps use:
- `popUpTo(navController.graph.findStartDestination().id) { saveState = true }` — collapses the back stack down to the graph root, saving History/WorkoutSummary state
- `launchSingleTop = true` — prevents duplicate Trends entries if Trends is already on top
- `restoreState = true` — restores any previously saved Trends state on re-entry

With this fix, tapping "View Trend" navigates to Trends the same way as tapping the Trends tab, keeping the bottom nav and back stack in sync.
