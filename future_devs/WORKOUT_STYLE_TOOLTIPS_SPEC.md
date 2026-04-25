# Workout Style — Contextual Explanation

| Field | Value |
|---|---|
| **Phase** | P1 |
| **Status** | `wrapped` |
| **Effort** | XS |
| **Depends on** | — |
| **Blocks** | — |
| **Touches** | `ui/settings/SettingsScreen.kt` |

> Read `SETTINGS_SPEC.md` and `FUNCTIONAL_TRAINING_SPEC.md §1.2` before touching any file.

---

## Overview

The Workout Style segmented selector (`Pure Strength` / `Hybrid` / `Pure Functional`) in Settings is self-labelled but has no explanation of what each mode actually does. Users unfamiliar with functional training won't know the difference. Add a `(?)` info button next to the card title that opens a bottom sheet explaining each style.

---

## Behaviour

### Info button

A small outlined `IconButton` with `Icons.Outlined.Info` (16dp, `onSurfaceVariant` tint) placed to the right of the "Workout Style" label inside the `SettingsCard`.

Tapping it opens a `ModalBottomSheet` with three rows — one per style — explaining the mode in plain language.

### Info sheet content

```
Pure Strength
Strength training only. The "+" button goes straight to the exercise library.
No functional blocks.

Hybrid  (recommended)
Mix strength exercises and functional blocks (AMRAP, RFT, EMOM) in the same
routine. The "+" button lets you choose which to add.

Pure Functional
Functional blocks only (AMRAP, RFT, EMOM, Tabata). The "+" button opens the
Functional Block Wizard directly.
```

Each row: style name in `titleMedium` weight, description in `bodyMedium`, `onSurface` color. Rows separated by a `HorizontalDivider`.

### State

`showWorkoutStyleInfoSheet: Boolean` in `SettingsUiState`, toggled by `showWorkoutStyleInfo()` / `dismissWorkoutStyleInfo()` in `SettingsViewModel`. Sheet is dismissed on outside tap or drag-down (standard `ModalBottomSheet` behaviour).

---

## UI Changes

- **Workout Style `SettingsCard` header row:** add trailing `IconButton(Icons.Outlined.Info)` — tap opens info sheet.
- **`WorkoutStyleInfoSheet`** (new composable in `SettingsScreen.kt`): `ModalBottomSheet` with three explanation rows as described above.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/settings/SettingsScreen.kt` — add info `IconButton` + `WorkoutStyleInfoSheet` composable
- `app/src/main/java/com/powerme/app/ui/settings/SettingsViewModel.kt` — add `showWorkoutStyleInfoSheet` to `SettingsUiState`, add `showWorkoutStyleInfo()` / `dismissWorkoutStyleInfo()`

---

## How to QA

1. Open Settings. Find the Workout Style segmented row. Verify a small `(ℹ)` icon appears to the right of the "Workout Style" label.
2. Tap the icon → verify a bottom sheet opens with three sections: Pure Strength, Hybrid, Pure Functional — each with a plain-language description.
3. Dismiss by tapping outside or dragging down → verify sheet closes.
4. Verify the Workout Style selector still works normally after opening and closing the sheet.
