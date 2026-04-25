# AI Review — Full Workout Management Options

| Field | Value |
|---|---|
| **Phase** | P7 |
| **Epic** | [AI Workout Generation](../AI_SPEC.md) |
| **Status** | `in-progress` |
| **Effort** | M |
| **Depends on** | AI workout generation core ✅ |
| **Blocks** | — |
| **Touches** | `ui/workouts/ai/AiWorkoutGenerationScreen.kt`, `ui/workouts/ai/AiWorkoutViewModel.kt` |

> **Full spec:** `AI_SPEC.md` — read §4 (PREVIEW step) and §5 (matching UX) before touching any file. Also read `WORKOUT_SPEC.md` for superset and rest-timer invariants.

---

## Overview

The AI workout review (PREVIEW step) currently shows a flat list of `PreviewExerciseCard` rows with match-type badges and a swap-exercise action for UNMATCHED rows. Beyond swapping, users have no way to organise the generated workout before starting or saving it.

This task brings the full suite of workout management options into the PREVIEW step — the same capabilities available in the template builder and active workout — so users can tailor the AI output before committing to it.

---

## Behaviour

### Management actions to add

| Action | Where it lives today | What to add in PREVIEW |
|---|---|---|
| **Reorder exercises** | Template builder drag-and-drop (Organize mode) | Drag handles on `PreviewExerciseCard` rows; long-press to enter reorder mode |
| **Create supersets** | Template builder ManagementHubSheet → Superset | Superset toggle available on each card (group consecutive exercises) |
| **Replace exercise** | Already exists for UNMATCHED rows | Extend to ALL rows — any matched exercise can be swapped via the same picker |
| **Set rest time** | Template builder per-exercise rest field | Rest time field accessible from each `PreviewExerciseCard` overflow or ManagementHubSheet |
| **Exercise notes** | Template builder notes field | Notes field per exercise in PREVIEW |

### Entry point

Tapping the `⋮` overflow or long-pressing a `PreviewExerciseCard` opens a lightweight `ManagementHubSheet` (reuse the existing composable if possible, or create a `PreviewManagementSheet`) with: Reorder, Superset, Replace, Set rest time, Notes.

### State management

All edits in PREVIEW update an in-memory `RoutineDraft` (already exists in `AiWorkoutViewModel`). Changes are not persisted until the user taps **Save as Routine** or **Start Workout**.

### Superset rules

Follow `WORKOUT_SPEC.md` superset invariants:
- A superset groups 2–4 consecutive exercises.
- Supersets are stored as a shared `supersetId` on the draft exercise rows.
- Visual: the grouped cards get a left-border accent and a superset label ("Superset A", "B", …).

### Replace for matched exercises

Currently replace/swap is only shown for UNMATCHED rows. Extend so every row has a "Replace exercise" action. On selection, navigate to the exercise picker (full-screen route, same as other entry points). The chosen exercise replaces the card; its `matchType` becomes `MANUAL`.

### Reorder

Use the existing `sh.calvin.reorderable:reorderable-compose` library (already a dependency). Drag handles appear when the user enters Organize mode (button in the PREVIEW toolbar or via ManagementHubSheet). Reorder updates exercise positions in the draft.

### Rest time

Rest time defaults from the parsed JSON (if Gemini returned it) or falls back to the user's global rest preference. Editing in PREVIEW writes to the draft exercise row only.

### Notes

Free-text field per exercise, optional. Stored in the draft and carried into the saved routine/started workout.

---

## UI Changes

- **PREVIEW toolbar** — add an "Organize" icon button (drag icon) to enter reorder mode. Existing Regenerate button stays.
- **`PreviewExerciseCard`** — add `⋮` overflow icon. Long-press enters reorder mode.
- **`PreviewManagementSheet`** (new, or extend `ManagementHubSheet`) — options: Reorder, Superset, Replace, Set Rest Time, Notes.
- **Superset visual** — left-border accent using `MaterialTheme.colorScheme.tertiary`, superset label in `labelSmall`.
- **Drag handles** — `MaterialTheme.colorScheme.onSurfaceVariant` drag indicator icon, visible only in Organize mode.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/workouts/ai/AiWorkoutGenerationScreen.kt` — PREVIEW step: toolbar, card overflow, ManagementHubSheet wiring, superset visual, drag handles
- `app/src/main/java/com/powerme/app/ui/workouts/ai/AiWorkoutViewModel.kt` — draft mutation methods: reorderExercise(), toggleSuperset(), replaceExercise(), setRestTime(), setExerciseNote()

---

## How to QA

1. Generate a workout via AI (text prompt). Reach the PREVIEW step.
2. Long-press any exercise card → verify ManagementHubSheet opens with Reorder, Superset, Replace, Set Rest Time, Notes.
3. **Reorder:** enter Organize mode → drag an exercise to a different position → verify order persists when you save the routine.
4. **Superset:** select two consecutive exercises → tap Superset → verify they are visually grouped with a left-border accent and label.
5. **Replace (matched):** tap Replace on a matched exercise → pick a different exercise from the library → verify the card updates.
6. **Set rest time:** tap Set Rest Time → enter a value → verify it appears on the card and carries into the saved routine.
7. **Notes:** tap Notes → enter text → verify it appears on the card and carries into the saved routine.
8. Start the workout from PREVIEW → verify all edits (order, supersets, rest times, notes) are reflected in the active workout.
9. Save as Routine → open the routine in the template builder → verify all edits are present.
