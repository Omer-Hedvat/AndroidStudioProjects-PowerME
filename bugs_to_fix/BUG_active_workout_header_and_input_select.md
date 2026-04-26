# BUG: Active workout screen — column headers missing + tap input should select all text

## Status
[x] Fixed

## Severity
P2 normal
- Cosmetic regression (column headers) + UX friction (manual cursor navigation before retyping)

## Description

Two related issues on the active workout screen input rows:

**Scope 1 — Column headers missing (regression)**
Column headers ("Reps", "Weight", "Time", etc.) are no longer visible above the set input rows in the active workout screen. A prior fix (`BUG_time_based_exercise_column_header`) addressed the wrong label; now the headers are gone entirely. Likely root cause: a recent change to the exercise card or set-row composable either removed the header composable call or wrapped it in a condition that evaluates to false in the common case.

**Scope 2 — Tap on input box should select all text**
When the user taps on a weight, reps, or time input field in the active workout screen, the cursor is placed at the tap position. The expected behaviour is that the entire field value is selected immediately, so the user can type a new value without manually clearing or selecting first.

## Steps to Reproduce

**Scope 1:**
1. Start any active workout (routine with at least one strength exercise).
2. Observe: no column headers ("Set", "Reps", "Weight", etc.) appear above the input rows.

**Scope 2:**
1. Start any active workout.
2. Tap on any numeric input field (weight, reps, time).
3. Observe: cursor placed at tap position — existing value not selected.
4. Expected: entire value selected so user can type to replace immediately.

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ui/workout/ActiveWorkoutScreen.kt`, `ui/workout/WorkoutViewModel.kt`

## Assets
- Related spec: `WORKOUT_SPEC.md`, `THEME_SPEC.md §9.3`

## Fix Notes
**Scope 1 — Column header restored:** The `allTimed` check + header Row was missing from `FunctionalBlockActiveCard` (likely removed during the "functional block card layout" refactor). Restored between the divider and the first exercise row: renders "TIME" when all exercises are `TIMED`, otherwise "REPS", aligned to the 52dp value column width.

**Scope 2 — Select-all on tap:** `FunctionalExerciseRow`'s `BasicTextField` was using a plain `String` value with no focus tracking. Converted to `TextFieldValue` state with a `selectAllTrigger` counter and a `LaunchedEffect(selectAllTrigger)` that selects all text after a 50ms delay (matching the pattern in `WorkoutInputField`). An `onFocusChanged` modifier increments the trigger when the field gains focus. External value changes are synced via `LaunchedEffect(value)`.
