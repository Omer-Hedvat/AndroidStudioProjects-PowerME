# Numeric Keyboard — ±1 Increment/Decrement Buttons

| Field | Value |
|---|---|
| **Phase** | P1 |
| **Status** | `done` |
| **Effort** | S |
| **Depends on** | — |
| **Blocks** | — |
| **Touches** | `WorkoutInputField.kt`, `ActiveWorkoutScreen.kt` |

---

## Overview

When the numeric keyboard is shown for weight or reps input during a workout, add `−` and `+` buttons above the keyboard (or as a toolbar accessory) that decrement or increment the current value by 1. This lets users make quick fine adjustments without retyping the full number.

---

## Behaviour

- `−` button: decrements current field value by 1 (minimum 0; for weight, minimum 0 or 0.5 if decimal)
- `+` button: increments current field value by 1
- For **weight fields**: increment/decrement by the user's preferred step (default 1 kg / 2.5 lbs); consider 0.5 kg step as a long-press option (out of scope for V1)
- For **reps fields**: always ±1 (integer)
- If the field is empty, `+` starts at 1; `−` on empty does nothing
- Buttons are only shown when the relevant field is focused (keyboard visible)
- Respects `SurgicalValidator` — result is validated before being applied

---

## UI Changes

- Add a `KeyboardAccessoryBar` composable: horizontal row with `−` and `+` `FilledTonalIconButton`s, pinned above the system keyboard using `WindowInsets` / `imePadding`
- Accessory bar background: `MaterialTheme.colorScheme.surfaceContainerHigh`
- Button style: `MaterialTheme.colorScheme.secondaryContainer` / `onSecondaryContainer`
- Show only when a workout numeric field has focus

---

## Implementation Notes

- `accessoryStep` defaults to 1.0 for all weight fields in V1. Unit-aware step (1 kg / 2.5 lbs) is deferred to a future iteration.
- Focus registration uses `CompositionLocal` (`LocalKeyboardAccessoryRegistrar`) to avoid threading callbacks through the full 5-level composable chain.
- Lambdas registered on focus capture `textFieldValue` (a stable `MutableState`) and `rememberUpdatedState(onValueChange)`, so they always read/write the latest value even after recomposition.
- On blur, callbacks are NOT cleared (to avoid a gap when tabbing between fields). Instead, `LaunchedEffect(imeVisible)` clears them when the keyboard hides.
- `applyAccessoryDelta()` is extracted as an `internal` top-level function so it can be unit-tested directly.

## Files Touched

- `app/src/main/java/com/powerme/app/ui/components/KeyboardAccessoryBar.kt` *(new)* — bar composable + `KeyboardAccessoryRegistrar` + `LocalKeyboardAccessoryRegistrar`
- `app/src/main/java/com/powerme/app/ui/components/WorkoutInputField.kt` — `applyAccessoryDelta()` helper, `accessoryEnabled`/`accessoryStep` params, focus registration
- `app/src/main/java/com/powerme/app/ui/workout/ActiveWorkoutScreen.kt` — `CompositionLocalProvider`, `KeyboardAccessoryBar` overlay, `accessoryEnabled = true` on weight/reps fields
- `app/src/test/java/com/powerme/app/ui/components/KeyboardAccessoryTest.kt` *(new)* — 20 unit tests

---

## How to QA

1. Start a workout → tap weight field → `−` and `+` buttons appear above keyboard
2. Tap `+` → value increments by 1 (or preferred step)
3. Tap `−` → value decrements by 1; at 0, does nothing
4. Tap reps field → same buttons work with ±1 integer step
5. Dismiss keyboard → accessory bar disappears
6. Value validates correctly (no negative, no non-numeric)
