# Exercise Filter Dialog — Sticky Bottom Action Bar

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `done` |
| **Effort** | XS |
| **Depends on** | exercise_filter_dialog ✅ |
| **Blocks** | — |
| **Touches** | `ui/exercises/ExerciseFilterDialog.kt` |

---

## Overview

The current filter dialog places a "Clear All Filters" TextButton at the bottom of a scrollable column. When many filters are visible, users must scroll to the bottom to find it — the reset action is not immediately accessible. This task adds a **sticky bottom action bar** that is always visible regardless of scroll position, containing both a **Reset** button and an **Apply** button side by side.

---

## Behaviour

- The dialog layout changes from a single scrollable `Column` to a `Column` with:
  1. A scrollable content area (chip sections) that fills available space
  2. A fixed bottom bar that never scrolls away
- **Reset button** — clears all active filters, then dismisses the dialog.
- **Apply button** — dismisses the dialog without changing filters. The list already re-renders live on each chip toggle, so Apply is purely a confirm-and-close affordance.
- Both buttons close the dialog when tapped.
- Both buttons are always visible; the bottom bar does not scroll.
- If zero filters are active, the Reset button is visually disabled (`enabled = false`, reduced alpha) but still present so the layout does not shift.

---

## UI Changes

- **Bottom action bar**: `Row` with `fillMaxWidth`, `padding(horizontal=16.dp, vertical=12.dp)`, `Arrangement.spacedBy(12.dp)`.
  - **Reset**: `OutlinedButton`, weight 1f, label "Reset", `colorScheme.error` border + text color. Disabled state when `activeFilterCount == 0`.
  - **Apply**: `Button` (filled), weight 1f, label "Apply".
- Remove the "Clear All Filters" `TextButton` that currently lives inside the scrollable content.
- The dialog height (`fillMaxHeight(0.82f)`) is unchanged — the bottom bar is carved out of the existing height, not additive.
- Use `MaterialTheme.colorScheme.*` tokens throughout — no hardcoded colors.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/exercises/ExerciseFilterDialog.kt` — restructure layout: wrap chip sections in `weight(1f)` scrollable Column; add sticky bottom Row with Reset + Apply buttons; remove floating "Clear All Filters" TextButton

---

## How to QA

1. Open Exercise Library → tap Tune icon → filter dialog opens.
2. Without scrolling, verify **Reset** and **Apply** buttons are visible at the bottom of the dialog.
3. Scroll through filter sections → buttons remain fixed at the bottom (do not scroll away).
4. With no filters active → Reset button is visually dimmed/disabled.
5. Select any filter → Reset button becomes active.
6. Tap **Reset** → all chips deselected, dialog closes, badge disappears, list shows all exercises.
7. Tap **Apply** → dialog closes; active filters remain applied; badge still shows count.
8. Verify the old "Clear All Filters" text inside the scroll area is gone.
