# Settings — Move RPE Mode Selector into Workout Style Card

| Field | Value |
|---|---|
| **Phase** | P1 |
| **Status** | `completed` |
| **Effort** | XS |
| **Depends on** | RPE mode selector ✅, Settings page reorder ✅ |
| **Blocks** | — |
| **Touches** | `ui/settings/SettingsScreen.kt`, `SETTINGS_SPEC.md` |

---

## Overview

The RPE auto-pop mode selector (`RpeMode`: Strength only / Functional only / All workouts / Off) currently lives in the Display & Workout card. Because its options map directly to workout style, it is more discoverable and semantically coherent when placed immediately below the Workout Style segmented button in the Workout Style card. No logic changes — this is a UI co-location only.

---

## Behaviour

- Remove the RPE mode `RadioButton` group from the Display & Workout card.
- Add it to the Workout Style card, below the segmented button row and the info sheet trigger.
- Label and options remain identical: "RPE prompts" header + four `RadioButton` rows (Strength only / Functional only / All workouts / Off).
- State management (`SettingsViewModel.rpeMode`, DataStore read/write) is unchanged.

---

## UI Changes

- **Workout Style card** — append the RPE mode radio group below the existing content. Use the same `RadioButton` layout pattern already in the card.
- **Display & Workout card** — remove the RPE mode section entirely.
- No color, shape, or typography changes needed.

---

## Files to Touch

- `ui/settings/SettingsScreen.kt` — move RPE mode composable block from `DisplayAndWorkoutCard` into `WorkoutStyleCard`
- `SETTINGS_SPEC.md` — update card content descriptions to reflect the new placement

---

## How to QA

1. Open Settings → confirm the Workout Style card now shows the RPE mode selector below the style segmented button.
2. Confirm the Display & Workout card no longer contains an RPE mode section.
3. Change RPE mode to "Functional only" → close Settings → reopen → confirm it persisted.
4. Start a strength workout → confirm RPE auto-pop does not appear (Functional only mode).
