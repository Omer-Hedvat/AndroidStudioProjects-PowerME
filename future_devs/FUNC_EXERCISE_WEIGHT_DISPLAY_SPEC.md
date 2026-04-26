# Functional Block Exercise — Weight Targets & Column Labels

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `not-started` |
| **Effort** | S |
| **Depends on** | func_active_block_card_ui ✅ |
| **Blocks** | — |
| **Touches** | `ui/workout/ActiveWorkoutScreen.kt`, `ui/workouts/TemplateBuilderScreen.kt`, `ui/workouts/TemplateBuilderViewModel.kt`, `data/database/RoutineExercise.kt` |

---

## Overview

Two related gaps in functional block exercise handling:

1. **Active workout card — missing column labels.** `FunctionalBlockActiveCard` displays weight and reps input fields per the spec, but the column headers ("Weight", "Reps") are absent. Strength block cards label their columns; functional blocks should follow the same pattern so users know which field is which.

2. **Template builder — no weight target input.** When adding exercises to a functional block in `FunctionalBlockWizard` step 3 (or the inline exercise row), only reps or hold-time can be set. There is no way to specify a target weight (e.g. "21 Thrusters @ 95 lbs"). This is core to WOD prescription: most RFT/AMRAP workouts define the weight alongside the rep count.

---

## Behaviour

### Active workout — column labels

- Add "Weight" and "Reps" (or "Hold" for time-capped exercises) header labels above the corresponding input fields inside `FunctionalBlockActiveCard`, matching the visual style of the strength exercise card column headers.
- The `blockType` (AMRAP / RFT / EMOM / TABATA) determines which label appears for the second column: AMRAP and RFT use "Reps" for rep-capped rows and "Hold" for time-capped rows; EMOM and TABATA always show "Reps".

### Template builder — weight target input

- In `FunctionalBlockWizard` step 3 and in inline exercise rows within a functional block section of `TemplateBuilderScreen`, add a weight input field alongside the existing reps/time stepper.
- Weight target stored in a new nullable `targetWeightKg: Double?` column on `RoutineExercise` (requires DB migration).
- Field is optional — blank = no prescribed weight. When set, the template exercise row renders as e.g. "21 × Thruster @ 43 kg" or "21 × Thruster @ 95 lb" depending on `UnitSystem`.
- Weight unit follows `AppSettingsDataStore.unitSystem` (same as strength weight fields). Displayed/stored in kg internally; shown in lb when unit = IMPERIAL.
- Weight input uses the same `OutlinedTextField` + `SurgicalValidator.validateWeight()` pattern as strength set rows.
- `targetWeightKg` is copied from `RoutineExercise` → `WorkoutSet.weight` as a suggestion when the workout starts (not locked — user can still edit it in the active workout).

---

## UI Changes

- `FunctionalBlockActiveCard`: add a `Row` header with `Text("Weight", style=labelSmall)` and `Text("Reps", style=labelSmall)` (or "Hold") spaced to align with their respective input fields.
- `TemplateBuilderScreen` functional exercise row: add a compact weight `OutlinedTextField` (same width/style as strength rows, `keyboardType = Decimal`) to the right of the reps/time stepper.
- `FunctionalBlockWizard` step 3 exercise rows: same weight field, collapsible or inline.
- All color tokens from `MaterialTheme.colorScheme.*` — no hardcoded values.

---

## Files to Touch

- `ui/workout/ActiveWorkoutScreen.kt` — add column header row to `FunctionalBlockActiveCard`
- `ui/workouts/TemplateBuilderScreen.kt` — add weight field to functional exercise rows
- `ui/workouts/TemplateBuilderViewModel.kt` — persist `targetWeightKg` on save
- `data/database/RoutineExercise.kt` — add `targetWeightKg: Double?` column
- `data/database/PowerMeDatabase.kt` — bump version + add migration
- `DB_UPGRADE.md` — record schema change

---

## How to QA

1. Open a routine with an RFT block in template builder → verify each exercise row shows a weight input field.
2. Enter a target weight (e.g. 43 kg for Thruster) → save routine → reopen → confirm weight persisted.
3. Start a workout from that routine → in the active workout functional block card, confirm "Weight" and "Reps" column headers appear above the input fields.
4. Confirm the weight field is pre-filled with the template target weight (user can override).
5. Repeat steps 1–4 for AMRAP, EMOM, and TABATA blocks.
