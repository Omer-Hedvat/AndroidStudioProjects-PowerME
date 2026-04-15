# BUG: Keyboard pops up after changing set type in active workout

## Status
[x] Fixed

## Description
In the active workout screen, after changing a set's type (e.g. Normal → Warmup → Drop Set via the set-type chip/button), the soft keyboard unexpectedly appears. No text field was explicitly tapped by the user — the keyboard is triggered as a side effect of the set type change interaction.

## Steps to Reproduce
1. Start a workout
2. On any exercise row, tap the set type indicator (e.g. the "W", "D", or numbered set chip) to cycle or change the set type
3. **Expected:** set type updates, keyboard stays hidden
4. **Actual:** soft keyboard slides up immediately after the type change

## Root Cause (suspected)
Changing the set type likely triggers a recomposition that causes a `TextField` (weight or reps input) on that row to request focus — either because `focusRequester.requestFocus()` is called unconditionally on composition, or because the recomposition re-runs a `LaunchedEffect` / `SideEffect` that steals focus. Check the weight/reps `BasicTextField` or `TextField` in the active set row composable for any focus-requesting side effects tied to set state changes.

## Fix Notes
Root cause: `WorkoutSetRow` called `focusManager.clearFocus()` synchronously in the `DropdownMenu` `onClick`/`onDismissRequest` callbacks. Android's `DropdownMenu` restores focus to the nearest focusable composable (the weight `BasicTextField`) when it dismisses — this happens asynchronously after the synchronous clear, so the `BasicTextField` regained focus and the keyboard appeared.

Fix (`ActiveWorkoutScreen.kt`):
1. Removed synchronous `focusManager.clearFocus()` from `onDismissRequest`, set-type `onClick`, and "Delete Timer" `onClick`.
2. Added `LaunchedEffect(showSetTypeMenu) { if (!showSetTypeMenu) { delay(100); focusManager.clearFocus() } }` to clear focus after the menu's dismiss animation completes.

Also fixed a pre-existing compile error: `SupersetSelectRow` referenced `supersetColor` which was undefined in its scope. Added `supersetColor: Color = Color.Transparent` parameter and passed `supersetColorMap[...]` from call sites.
