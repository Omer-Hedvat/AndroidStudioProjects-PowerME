# Workout Summary — Set Type Labels

| Field | Value |
|---|---|
| **Phase** | P2 |
| **Status** | `done` |
| **Effort** | S |
| **Depends on** | Summary RPE inline format ✅ |
| **Blocks** | — |
| **Touches** | `WorkoutSummaryScreen.kt`, `WorkoutSummaryViewModel.kt` |

---

## Overview

The Workout Summary screen currently lists all sets with a sequential row number (1, 2, 3 …) regardless of set type. This makes it impossible to tell at a glance which sets were warmups, which were working sets, and which were special sets (drop or failure).

This feature replaces the raw row number with type-aware labels: warmup sets get a `WU` badge, working sets are renumbered starting from 1 (skipping warmups), drop sets are tagged `DROP`, and failure sets are tagged `FAIL`.

---

## Behaviour

### Label rules

| `SetType` | Display label | Example |
|---|---|---|
| `WARMUP` | `WU` (no number) | `WU  0 × 10` |
| `NORMAL` | Working set counter, starting at 1 | `1  60 × 8` |
| `DROP` | `DROP` (no counter increment) | `DROP  50 × 10` |
| `FAILURE` | `FAIL` (no counter increment) | `FAIL  50 × 1 @ 6` |

- Working set counter increments only for `NORMAL` sets.
- `DROP` and `FAILURE` sets do **not** increment the working set counter (they are continuations of the preceding working set's intensity).
- Labels are rendered in the same column position as the current row number.

### Visual treatment

- `WU` label: rendered in `MaterialTheme.colorScheme.onSurfaceVariant` (subdued) to visually de-emphasise warmups relative to working sets.
- `1`, `2`, `3` … working set numbers: rendered in `MaterialTheme.colorScheme.onSurface` (normal weight), same as current.
- `DROP` and `FAIL` labels: rendered in `MaterialTheme.colorScheme.tertiary` to visually distinguish them.
- All labels use the same `TextStyle` as the current row-number text — no font size change.

### Edge cases

- An exercise with **only warmup sets** (no `NORMAL`/`DROP`/`FAILURE`) shows all rows labelled `WU`.
- An exercise where the first set is `DROP` or `FAILURE` (unusual but possible via CSV import): counter stays at 0 and those sets display their label without a preceding normal set.
- Existing `@RPE` inline format is preserved unchanged; labels replace only the leading number column.

---

## UI Changes

**Screen:** `WorkoutSummaryScreen.kt` — exercise set list rows (the expanded section already rendered when a card is tapped open).

**Current rendering:** `Text("${index + 1}")` (or similar) followed by `"weight × reps"` and optional `"@ RPE"`.

**New rendering:** Replace the leading index with a computed label string:
- Maintain a `workingSetCounter` that starts at 0 per exercise and increments for each `NORMAL` set encountered in order.
- Pass the computed label into the same `Text` composable slot.

No new composables required — this is a data-mapping change inside the existing set-row layout.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/history/WorkoutSummaryScreen.kt` — replace raw index with type-aware label in the set row renderer
- `app/src/main/java/com/powerme/app/ui/history/WorkoutSummaryViewModel.kt` — optionally pre-compute labels in the UI state (preferred over in-Composable logic)

---

## How to QA

1. Complete or open a historical workout that contains warmup sets followed by working sets (e.g. Barbell Back Squat with 3 WU + 3 working sets).
2. Open the Workout Summary screen and expand the exercise card.
3. Verify the first 3 rows show `WU` in subdued color, rows 4–6 show `1`, `2`, `3` in normal color.
4. Open an exercise that includes a drop set. Verify the drop row shows `DROP` in tertiary color and the working set counter does not increment for it.
5. Open an exercise that includes a failure set. Verify it shows `FAIL` in tertiary color.
6. Open an exercise with only warmup sets. Verify all rows show `WU`.
