# Exercise Library — exerciseType Filter Chips

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `superseded` |
| **Effort** | S |
| **Depends on** | func_exercise_tags_seed ✅ |
| **Blocks** | — |
| **Touches** | `ui/exercises/ExercisesScreen.kt`, `ui/exercises/ExercisesViewModel.kt` |

---

## Overview

Add three filter chips to the Exercise Library — **TIMED**, **CARDIO**, and **PLYOMETRIC** — that filter exercises by the existing `exerciseType` field. No new tags, no schema changes: the `exerciseType` column is already populated on every exercise and already carries these values.

**STRENGTH is intentionally omitted** — ~90 % of exercises are `STRENGTH` type, so that chip would barely narrow the list and adds no discovery value.

---

## Behaviour

- The three chips are **independent toggle filters** (any combination can be active simultaneously; zero active = no type filter applied).
- When one or more type chips are active, only exercises whose `exerciseType` matches any selected type are shown. This filter composes with the existing Equipment, Muscle Group, and Functional filters using AND logic: an exercise must pass all active filters to appear.
- Chips are single-select or multi-select (the user can enable TIMED + CARDIO at the same time to see all conditioning work).
- Chip state is **session-local** (not persisted across app restarts) — same behaviour as the existing filter chips.
- All three chips default to **off**.

---

## UI Changes

**Chip row placement:** A new row labelled "Type" sits directly below the existing "Functional" chip row and above the Exercise list. Follows the same chip row pattern as Equipment and Muscle Group rows.

**Chips:**
| Chip label | Filters for |
|---|---|
| Timed | `exerciseType == TIMED` |
| Cardio | `exerciseType == CARDIO` |
| Plyometric | `exerciseType == PLYOMETRIC` |

Use `FilterChip` from Material 3 — same component, style, and colour tokens as the existing Functional chip (`TimerGreen` selected colour, `MaterialTheme.colorScheme.surfaceVariant` unselected).

**ExercisesViewModel changes:**
- Add `timedFilter: Boolean`, `cardioFilter: Boolean`, `plyometricFilter: Boolean` to `ExercisesUiState`.
- Add `onTimedFilterToggled()`, `onCardioFilterToggled()`, `onPlyometricFilterToggled()` event handlers.
- Extend the existing filter logic: if any type filter is active, additionally require `exercise.exerciseType in selectedTypes`.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/exercises/ExercisesViewModel.kt` — add three filter booleans to `ExercisesUiState` + three toggle handlers + compose into filter predicate
- `app/src/main/java/com/powerme/app/ui/exercises/ExercisesScreen.kt` — add "Type" chip row with three `FilterChip` composables

---

## How to QA

1. Open Exercise Library. Verify three new chips (Timed / Cardio / Plyometric) appear below the Functional chip.
2. Tap **Timed** → only isometric holds and interval timers appear (planche holds, front lever holds, ring holds, wall sit, etc.).
3. Tap **Cardio** → only cardio exercises appear (Run, Rowing, Ski Erg, Assault Bike, etc.).
4. Tap **Plyometric** → only plyometric exercises appear (Box Jump, Broad Jump, Depth Jump, etc.).
5. Combine **Timed + Cardio** → both types appear simultaneously.
6. Combine a type chip with **Functional** chip → intersection: e.g. Timed + Functional shows only timed functional exercises (planche holds, lever holds, etc.).
7. Combine type chip with an Equipment filter → intersection works correctly.
8. Tap an active chip again to deselect → list returns to full/pre-type-filter state.
