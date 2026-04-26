# Tabata Block — Full Config Parity with Clocks Tab

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `in-progress` |
| **Effort** | S |
| **Depends on** | AMRAP/RFT/EMOM/TABATA overlays ✅ |
| **Blocks** | — |
| **Touches** | `ui/workouts/FunctionalBlockWizard.kt`, `data/database/WorkoutBlock.kt`, `data/database/RoutineBlock.kt` |

---

## Overview

The Clocks → Tabata timer exposes: work seconds, rest seconds, rounds, skip last rest. The functional block Tabata wizard should expose the same four parameters. Currently `tabataSkipLastRest` is already stored in the DB entity but the wizard does not surface it. This feature adds the "Skip last rest" toggle and verifies the other three fields are also present in the wizard UI.

---

## Behaviour

- Tabata wizard params step must expose:
  1. **Work (sec)** — numeric, default 20
  2. **Rest (sec)** — numeric, default 10
  3. **Rounds** — numeric, default 8
  4. **Skip last rest** — boolean toggle, default `false`
- All four values are written to `RoutineBlock`/`WorkoutBlock` columns that already exist.
- `FunctionalBlockRunner.mapToTimerSpec()` already maps `tabataSkipLastRest` to `TimerSpec.Tabata.skipLastRest`.

---

## UI Changes

- Tabata params step in `FunctionalBlockWizard`: add `Switch` row for "Skip last rest" below the Rounds input.
- Use `onSurface` thumb + `primary`/`surfaceVariant` track tokens (see `THEME_SPEC.md §9.1`).

---

## Files to Touch

- `ui/workouts/FunctionalBlockWizard.kt` — add skip-last-rest toggle to Tabata params step; wire `DraftBlock.tabataSkipLastRest`

---

## How to QA

1. Create a Tabata block in the wizard. Confirm Work, Rest, Rounds, and Skip Last Rest are all visible and editable.
2. Enable "Skip last rest". Save and start the block.
3. Confirm the last rest interval is skipped and the block ends immediately after the final work interval.
