# Fix Summary: Keyboard pops up after changing set type

## Bug
`BUG_keyboard_pops_on_set_type_change.md`

## Root Cause

`WorkoutSetRow` in `ActiveWorkoutScreen.kt` called `focusManager.clearFocus()` **synchronously** inside the `DropdownMenu` selection callbacks (`onDismissRequest`, `onClick` for each set type, `onClick` for "Delete Timer").

Android's `DropdownMenu` restores focus to the nearest focusable composable when it dismisses — this happens **asynchronously** after the synchronous clear. The weight `BasicTextField` (rendered inside `WorkoutInputField`) was the nearest focusable, so it regained focus after every set type change, causing the keyboard to pop up.

## Fix

**File:** `app/src/main/java/com/powerme/app/ui/workout/ActiveWorkoutScreen.kt`

### Before
```kotlin
onDismissRequest = { showSetTypeMenu = false; focusManager.clearFocus() }
// ...
onClick = { onSelectSetType(type); showSetTypeMenu = false; focusManager.clearFocus() }
// ...
onClick = { onDeleteTimer(); showSetTypeMenu = false; focusManager.clearFocus() }
```

### After
```kotlin
// All three callbacks — clearFocus() removed:
onDismissRequest = { showSetTypeMenu = false }
onClick = { onSelectSetType(type); showSetTypeMenu = false }
onClick = { onDeleteTimer(); showSetTypeMenu = false }

// New LaunchedEffect clears focus after dismiss animation completes:
LaunchedEffect(showSetTypeMenu) {
    if (!showSetTypeMenu) {
        delay(100)
        focusManager.clearFocus()
    }
}
```

The 100ms delay lets the `DropdownMenu` complete its internal dismiss/focus-restoration cycle before focus is cleared, preventing the `BasicTextField` from regaining focus.

## Bonus Fix (pre-existing compile error)

`SupersetSelectRow` referenced a local variable `supersetColor` that was never defined in its scope. Added `supersetColor: Color = Color.Transparent` as a composable parameter, and passed `supersetColorMap[exerciseWithSets.supersetGroupId] ?: Color.Transparent` from both call sites in `activeWorkoutListItems`.
