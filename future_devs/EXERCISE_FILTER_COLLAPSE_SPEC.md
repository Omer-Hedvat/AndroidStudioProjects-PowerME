# Exercise Library — Collapsible Filter Panel

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

The Exercise Library filter area (Equipment row, Muscle Group row, Functional chip, and any future type chips) takes significant vertical space — especially as more filter rows are added. This feature adds a **collapse/expand toggle** so users can hide the filter panel when browsing freely and reveal it on demand.

**Design note:** The collapsed vs expanded states, toggle affordance (icon, label, animation), and exact layout are not pre-specified. **Invoke `/ui-ux-pro-max` at the start of implementation** to get a polished design recommendation before writing any code. Stack: Jetpack Compose + Material Design 3, Pro Tracker v6.0 palette.

---

## Behaviour

- Filter panel defaults to **collapsed** when no filters are active; defaults to **expanded** when one or more filters are active (so active filters are always visible without requiring a tap).
- Collapsing the panel does **not** clear any active filters — they remain in effect and are shown in a compact summary (e.g. a count badge or pill labels on the toggle row) so the user knows filters are applied.
- Expanding and collapsing is animated (Material motion — see `ui-ux-pro-max` recommendation).
- Toggle state is **session-local** (not persisted across app restarts).
- All existing filter rows (Equipment, Muscle Group, Functional, and any future rows added by other tasks) live inside the collapsible panel — no filter row sits outside it.

---

## UI Changes

Design is deferred to `/ui-ux-pro-max`. At minimum the implementation needs:

- A **toggle row** always visible above the list: shows a label ("Filters") and an expand/collapse affordance, plus a compact indicator of active filter count or labels when collapsed.
- The **filter panel** below the toggle row: contains all chip rows; animates in/out with `AnimatedVisibility` or equivalent.
- Active filter count badge or chip summary visible in the collapsed state so users know filters are applied without expanding.
- Use `MaterialTheme.colorScheme.*` tokens throughout — no hardcoded colors.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/exercises/ExercisesScreen.kt` — wrap filter rows in collapsible container; add toggle row composable
- `app/src/main/java/com/powerme/app/ui/exercises/ExercisesViewModel.kt` — add `filtersExpanded: Boolean` to `ExercisesUiState` + `onFiltersExpandedToggled()` handler; auto-expand logic when filters become active

---

## How to QA

1. Open Exercise Library with no filters active → filter panel is collapsed by default.
2. Tap the toggle → panel expands, all chip rows visible; tap again → panel collapses.
3. Select a filter (e.g. Equipment = Barbell) while expanded → collapse the panel → verify the active filter is still applied (list is still filtered) and a count/label indicator is visible on the toggle row.
4. With an active filter, navigate away and return → filter still applied, panel shows collapsed-with-indicator state.
5. Clear all filters → panel returns to collapsed default.
6. Verify expand/collapse animation is smooth (no jank on first open).
7. Verify layout with all filter rows visible (Equipment + Muscle Group + Functional + Type chips) fits comfortably when expanded.
