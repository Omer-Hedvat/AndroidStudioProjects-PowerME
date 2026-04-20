# Fix Summary: Delete workout from History navigates back to deleted workout summary instead of History list

## Root Cause
`WorkoutDetailScreen` used a single `onNavigateBack` callback for both normal back navigation and post-delete navigation. After confirming delete, `onNavigateBack` was wired to `navController.popBackStack()` — popping only the `workout_detail` entry and landing on the now-deleted `workout_summary` screen rather than the History list.

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/history/WorkoutDetailScreen.kt` | Added `onDeleted: () -> Unit = onNavigateBack` parameter; delete confirmation now calls `onDeleted()` instead of `onNavigateBack()` |
| `app/src/main/java/com/powerme/app/navigation/PowerMeNavigation.kt` | Wired `onDeleted = { navController.popBackStack(Screen.History.route, inclusive = false) }` to clear both detail and summary entries and return to the History list |
| `app/src/test/java/com/powerme/app/ui/history/WorkoutDetailViewModelTest.kt` | Added two tests: callback fires on successful delete, callback fires when workout not found |

## Surfaces Fixed
- Deleting a workout from the Workout Detail screen (reached from History → Summary → Edit) now returns the user to the History list instead of the stale Summary screen

## How to QA
1. Open the **History** tab
2. Tap any workout to open the **Workout Summary** screen
3. Tap the **Edit (pencil)** icon to open **Workout History** (detail/edit screen)
4. Tap the **trash/delete** icon in the top-right
5. Confirm deletion in the dialog
6. Verify the app navigates directly to the **History list** (not back to the now-deleted Summary screen)
