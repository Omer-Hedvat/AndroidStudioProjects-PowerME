# History Cards Default Expanded

| Field | Value |
|---|---|
| **Phase** | P2 |
| **Status** | `done` |
| **Effort** | XS |
| **Depends on** | History card set details ✅ |
| **Blocks** | — |
| **Touches** | `WorkoutDetailScreen.kt` or `WorkoutSummaryScreen.kt` (wherever history exercise card expand state is initialized) |

---

## Overview

Exercise cards in the History workout detail view currently start collapsed (showing only summary stats). The user prefers all cards to start expanded so the full set-by-set breakdown is immediately visible, with the option to collapse individual cards.

This is a one-line default state change: flip the initial `expanded` value from `false` to `true` for cards rendered in history context (non-post-workout view).

---

## Behaviour

- In **History view** (tapping a past workout): all exercise cards start **expanded** (sets visible)
- In **post-workout summary**: already expanded by default — no change
- Collapse/expand toggle remains available on all cards
- Collapsed state is per-card and per-session only (no persistence needed)

---

## UI Changes

No new composables. Only the default value of the `expanded` remember state for history exercise cards changes from `false` → `true`.

---

## Files to Touch

- Wherever history exercise card `expanded` state is initialized — likely `WorkoutDetailScreen.kt` or the history composable in `WorkoutSummaryScreen.kt`

---

## How to QA

1. Open History tab, tap any completed workout
2. Verify all exercise cards are **expanded by default** (sets visible immediately, no tap needed)
3. Tap a card header to collapse it — card collapses correctly
4. Tap again — expands again
5. Post-workout summary: verify still expanded by default (no regression)
