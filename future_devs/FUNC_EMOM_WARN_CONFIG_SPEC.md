# EMOM Setup — Configurable Warning Threshold

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `in-progress` |
| **Effort** | XS |
| **Depends on** | AMRAP/RFT/EMOM/TABATA overlays ✅ |
| **Blocks** | — |
| **Touches** | `ui/workouts/FunctionalBlockWizard.kt`, `data/database/WorkoutBlock.kt`, `data/database/RoutineBlock.kt`, `ui/workout/FunctionalBlockRunner.kt` |

---

## Overview

The EMOM wizard currently hardcodes the warning threshold to 10 seconds. Users should be able to configure this in the wizard when setting up an EMOM block. The field defaults to 10s and can be changed (range 5–30s). The value is stored on the block entity and respected by `FunctionalBlockRunner.mapToTimerSpec()` via the existing `warnAtSecondsOverride` column.

---

## Behaviour

- In the EMOM wizard (params step), add a "Warn at (sec)" numeric input below the interval field.
- Default value: `10`
- Valid range: 5–30 (clamp or show error outside range)
- When saved, the value is written to `warnAtSecondsOverride` on `RoutineBlock` / `WorkoutBlock`.
- `FunctionalBlockRunner.mapToTimerSpec()` already reads `warnAtSecondsOverride` — no engine change needed.
- The second threshold (`warnAtSeconds2 = 30s`) is only added when override is `null`. If user sets a custom value, only that single threshold fires.

---

## UI Changes

- EMOM params step: add `OutlinedTextField` row labeled **"Warn at (sec)"** with numeric keyboard.
- Consistent with existing duration / interval inputs in the same sheet.

---

## Files to Touch

- `ui/workouts/FunctionalBlockWizard.kt` — add warn field to EMOM params UI and `DraftBlock`
- `data/database/RoutineBlock.kt` — `warnAtSecondsOverride` column already exists (v41)
- `data/database/WorkoutBlock.kt` — `warnAtSecondsOverride` column already exists

---

## How to QA

1. Open Workouts → New Routine → Pure Functional → Add Block → EMOM.
2. Set interval to 60s. Observe "Warn at (sec)" field shows 10 by default.
3. Change to 20. Save the block.
4. Start the workout, start the EMOM block.
5. Confirm alert fires at 20s remaining (not 10s).
