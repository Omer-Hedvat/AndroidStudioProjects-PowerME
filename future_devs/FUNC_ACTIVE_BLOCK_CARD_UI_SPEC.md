# Functional Block Active Workout Card UI

| Field | Value |
|---|---|
| **Phase** | P8 |
| **Epic** | [Functional Training](../FUNCTIONAL_TRAINING_SPEC.md) |
| **Status** | `completed` |
| **Effort** | M |
| **Depends on** | func_active_strength_blocks ✅ |
| **Blocks** | func_active_functional_runner |
| **Touches** | `ui/workout/ActiveWorkoutScreen.kt`, `ui/workout/WorkoutViewModel.kt` |

---

## Overview

Exercises inside a functional block (AMRAP, RFT, EMOM, TABATA) are currently rendered with the standard strength exercise card UI — showing sets, a PRE (personal record estimate) indicator, an RPE field, and a checkmark / "V" confirmation button. This is wrong for functional training: functional blocks have no concept of sets, PRE is meaningless inside timed or rounds-based blocks, and the checkmark implies marking a set done which doesn't apply.

This task replaces the strength card UI for functional-block exercises with a single grouped card that shows the block type badge + metadata at the top, followed by an ordered list of exercises with only weight and reps fields.

---

## Behaviour

- **Grouping:** All exercises belonging to the same functional block render inside one `Card` in the active workout list. The card is not collapsible.
- **Block header row (top of card):** Shows the `BlockType` badge (colour-coded: AMRAP=green, RFT=purple, EMOM=amber, TABATA=red) and the block's parameter summary (e.g. "12 min", "5 rounds", "E2MOM / 10 min", "Tabata 8 rds").
- **Exercise rows (inside the card):**
  - Exercise name + muscle group chip.
  - Weight input field (same style as strength exercises).
  - Reps input field (or hold time for TIMED exercises in AMRAP/RFT).
  - **No sets column.**
  - **No PRE indicator.**
  - **No RPE field.**
  - **No checkmark / approved "V" button.**
- **STRENGTH blocks** are unaffected — they keep the existing per-exercise card with sets, PRE, RPE, and checkmark.
- **Unblocked exercises** are unaffected.
- **Edit mode:** the same grouped card layout applies when the user is editing a saved workout containing functional blocks.

---

## UI Changes

- Functional block exercises in `ActiveWorkoutScreen` use a new `FunctionalBlockActiveCard` composable (or a conditional branch inside the existing exercise card composable).
- The card uses `MaterialTheme.colorScheme.surface` background with `MaterialTheme.shapes.medium` corner radius, consistent with existing exercise cards.
- Block type badge uses the same colour tokens as the existing `BlockHeader` composable in `TemplateBuilderScreen`.
- Exercise rows inside the card have no `Card` wrapper of their own — they are plain rows with dividers between them.
- Weight and reps fields use the same `OutlinedTextField` / stepper pattern as the strength exercise rows.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/workout/ActiveWorkoutScreen.kt` — add `FunctionalBlockActiveCard` composable; update the `LazyColumn` items builder to group functional block exercises under their block card; suppress sets/PRE/RPE/checkmark for functional exercise rows
- `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` — confirm block metadata (type, params) is available in `ActiveWorkoutState` for display; no new DB queries needed if `WorkoutBlock` is already loaded (it is, via `func_active_strength_blocks`)

---

## How to QA

1. Start a workout from a PURE_FUNCTIONAL routine with at least 2 blocks (e.g. AMRAP 12min + RFT 5rds), each with 2–3 exercises.
2. Verify each block renders as a single card: block type badge + metadata at the top, exercises listed below inside the same card boundary.
3. Verify no individual card border appears around each exercise row inside a functional block.
4. Verify exercise rows show only name, weight, and reps — **no sets stepper, no PRE chip, no RPE field, no checkmark button**.
5. Start a workout from a PURE_GYM routine — verify strength exercises still show sets, PRE, RPE, and checkmark (no regression).
6. Start a HYBRID routine with 1 STRENGTH block + 1 AMRAP block — verify STRENGTH exercises use the old card; AMRAP block uses the new grouped card.
7. Edit a completed workout containing functional blocks — verify the grouped card layout is used in edit mode too.
