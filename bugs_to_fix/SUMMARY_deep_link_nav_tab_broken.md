# Fix Summary: Bottom nav broken after History → Trends deep-link

## Root Cause
`onNavigateToTrends` in `PowerMeNavigation.kt` used a plain `navController.navigate("trends?exerciseId=...")` with no `NavOptions`. This stacked the Trends destination on top of the existing `history → workout_summary` back stack entries. The bottom nav's selected-item detection and `popUpTo` logic in subsequent tab taps then operated on a corrupted back stack:
- History tab tap did nothing (the saved "history" entry was buried under workout_summary + trends, and the nav options' `launchSingleTop` short-circuited the re-navigate)
- After visiting Workouts and back, tapping History restored the stale back stack that had Trends on top instead of History

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/navigation/PowerMeNavigation.kt` | Added `NavOptions` to `onNavigateToTrends` callback: `popUpTo(startDestination) { saveState = true }` + `launchSingleTop = true` + `restoreState = true` |

## Surfaces Fixed
- "View Trend" button in WorkoutSummaryScreen now navigates to Trends tab as a proper tab switch (not a back-stack push)
- Bottom nav remains consistent after the deep-link: Trends tab shows as selected, History tab tap correctly returns to History

## How to QA
1. Open History tab
2. Tap any completed workout → WorkoutSummaryScreen opens
3. Tap **"View Trend →"** on any exercise card → Trends tab opens with exercise pre-selected ✅
4. Tap **History** in the bottom nav → should navigate back to History list (not do nothing) ✅
5. Tap **Trends** in the bottom nav → should navigate to Trends (not show History) ✅
6. Tap back through the nav to confirm no stale Trends entry appears in the back stack ✅
