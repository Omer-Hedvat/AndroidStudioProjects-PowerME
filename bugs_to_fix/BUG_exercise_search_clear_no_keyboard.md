# BUG: Exercise search — X button clears query but dismisses keyboard

## Status
[x] Fixed

## Severity
P2 normal
- Not a crash; UX friction that breaks the expected "clear and retype" flow

## Description
In the Exercise Library search bar, tapping the X (clear) button clears the search query but also dismisses the keyboard and loses focus on the text field. The expected behavior is that clearing the query keeps the keyboard visible and the field focused so the user can immediately start typing a new search without an extra tap.

The likely cause: the clear handler calls `onQueryChange("")` which may trigger a `LaunchedEffect` or state recomposition that drops focus, or the `FocusRequester` is not re-requested after the clear. The Tune (filter) icon and X (clear) icon share the trailing slot — ensure the fix doesn't affect filter icon behavior.

## Steps to Reproduce
1. Open Exercise Library (Exercises tab)
2. Tap the search bar and type any query (e.g. "bench")
3. Tap the X button to clear the query
4. Observe: keyboard dismisses and focus is lost — must tap the search bar again to type

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `app/src/main/java/com/powerme/app/ui/exercises/ExercisesScreen.kt`

## Assets
- Related spec: `future_devs/EXERCISE_FILTER_DIALOG_SPEC.md`

## Fix Notes
Added a `FocusRequester` to the search `OutlinedTextField`. The X button click handler now calls `searchFocusRequester.requestFocus()` immediately after `onSearchQueryChanged("")`, so the field regains focus. Also added `LocalSoftwareKeyboardController.current?.show()` so the keyboard re-appears even if it was manually dismissed before tapping X. The Tune filter icon is unaffected — it sits in the same trailing `Row` and its click handler is unchanged.
