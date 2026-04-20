# Fix Summary: Exercise Detail Screen crashes on scroll

## Root Cause
`WorkoutHistorySection` rendered all workout history rows inside a single `LazyColumn` `item {}` block using `history.forEach { ... }`. Since a `LazyColumn` only virtualizes at the `item` level, all rows were composed eagerly regardless of visibility. For exercises with large histories (50+ sessions), this created hundreds of `Surface`/`Row`/`Text` composables simultaneously, exhausting memory and crashing the app.

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/exercises/detail/ExerciseDetailScreen.kt` | Removed `WorkoutHistorySection` composable; replaced the single `item {}` call with individual `items(items = uiState.workoutHistory, key = ...)` calls directly in the outer `LazyColumn`. Header, rows, and footer (load-more / spacer) are now separate lazy items. |

## Surfaces Fixed
- Exercise Detail Screen — no longer crashes when scrolling on exercises with large workout history

## How to QA
1. Open **Exercises** tab → tap an exercise that has substantial history (e.g. Back Squat, Bench Press)
2. Scroll slowly through all sections: header → joints → form cues → records → trends → warm-up → muscle activation → alternatives → **workout history** → notes
3. Verify the app does **not** crash at any point during the scroll
4. In the Workout History section, confirm individual session rows render correctly (date, routine name, sets count, volume)
5. If more than 20 sessions exist, a **Load more** button appears at the bottom of the history list — tap it and verify more rows load below
6. Once all history is loaded, the Load more button disappears and a spacer replaces it
