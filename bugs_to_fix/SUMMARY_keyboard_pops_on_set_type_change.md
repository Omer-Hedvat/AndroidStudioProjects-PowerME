# Fix Summary: Keyboard pops up when changing set type in active workout

## Root Cause
`WorkoutSetRow` called `focusManager.clearFocus()` synchronously inside the `DropdownMenu` `onClick` and `onDismissRequest` callbacks. Android's `DropdownMenu` restores focus to the nearest focusable composable (the weight `BasicTextField`) asynchronously after dismissal — after the synchronous clear — so the weight field regained focus and the keyboard appeared.

## Files Changed
| File | Change |
|---|---|
| `ui/workout/ActiveWorkoutScreen.kt` | Removed synchronous `focusManager.clearFocus()` from `onDismissRequest`, set-type `onClick`, and "Delete Timer" `onClick`. Added `LaunchedEffect(showSetTypeMenu) { if (!showSetTypeMenu) { delay(100); focusManager.clearFocus() } }` to clear focus after the menu's dismiss animation completes. Also added missing `supersetColor: Color` parameter to `SupersetSelectRow` (pre-existing compile gap surfaced during fix). |

## Surfaces Fixed
- Changing set type (Normal / Warmup / Drop / Failure) no longer triggers the keyboard
- Closing the set type dropdown no longer triggers the keyboard

## How to QA
1. Start any workout
2. On any exercise row, tap the set type indicator (numbered chip or "W"/"D" label)
3. Select a different set type from the dropdown
4. **Expected:** set type updates, keyboard stays hidden
5. **Not expected:** keyboard slides up after selection
6. Repeat by dismissing the dropdown without selecting (tap outside) — keyboard should also stay hidden
