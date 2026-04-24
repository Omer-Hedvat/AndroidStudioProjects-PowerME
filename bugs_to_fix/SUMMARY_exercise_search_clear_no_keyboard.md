# Fix Summary: Exercise search X button clears query but dismisses keyboard and loses focus

## Root Cause
The X (clear) button called `onSearchQueryChanged("")` but did not re-request focus on the search field. Clearing the text caused a recomposition that dropped keyboard focus, requiring the user to tap the search bar again.

## Files Changed
| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/exercises/ExercisesScreen.kt` | Added `FocusRequester`, attached it to the `OutlinedTextField` via `.focusRequester()`, and called `searchFocusRequester.requestFocus()` in the X button click handler |

## Surfaces Fixed
- Exercise Library search bar: tapping X now keeps the keyboard visible and the field focused

## How to QA
1. Open the **Exercises** tab
2. Tap the search bar and type any query (e.g. "bench")
3. Tap the **X** button to clear the query
4. Verify: keyboard stays up, cursor is in the search field, and you can immediately type a new query without tapping again
5. Tap the **Tune** (filter) icon and verify the filter dialog still opens normally
