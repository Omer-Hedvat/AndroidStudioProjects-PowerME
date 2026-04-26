# Exercise Functional Tag Toggle

| Field | Value |
|---|---|
| **Phase** | P5 |
| **Status** | `not-started` |
| **Effort** | S |
| **Depends on** | — |
| **Blocks** | — |
| **Touches** | `ui/exercises/ExercisesScreen.kt`, `ui/exercises/ExerciseDetailScreen.kt`, `ui/exercises/ExercisesViewModel.kt`, `data/database/ExerciseDao.kt`, `data/repository/ExerciseRepository.kt` |

---

## Overview

Users currently cannot control which exercises appear in the functional block exercise picker — the ⚡ Functional filter is driven by the `tags` field on each `Exercise` row, which is only set by the initial seed and the AI expansion. This feature lets users manually add or remove the functional tag on any exercise from the Exercise Detail screen so they can tailor the picker to their own training style (e.g. include Dumbbell Press in functional workouts, or exclude an exercise that was mis-tagged by the seed).

---

## Behaviour

- The functional tag toggle applies only to the functional family tag (`ExerciseFamily.FUNCTIONAL` or equivalent tag type). All other tags (muscle group, equipment, type, etc.) remain read-only.
- Toggling the functional tag persists to the local `exercises` DB table immediately.
- The change is reflected instantly in the ⚡ Functional filter in the exercise picker and the exercise library.
- Custom exercises (created by the user) can also be toggled.
- The toggle should NOT be available on the picker screen itself — only on the Exercise Detail screen to avoid accidental changes mid-session.
- No Firestore sync is required for this field (it's a local preference, not cloud-synced workout data).

---

## UI Changes

**Exercise Detail Screen** — add a "Functional training" toggle row in the exercise metadata section:
- Label: "Functional training"
- Sub-label: "Included in the ⚡ Functional filter"
- Control: `Switch` using project standard colors (`onSurface` thumb, `primary`/`surfaceVariant` track — see `THEME_SPEC.md §9.1`)
- Placement: below exercise type / muscle group info, above any stats section
- The row is always visible (for all exercise types); toggling it on marks the exercise as functional, toggling it off removes the functional tag

---

## Files to Touch

- `data/database/ExerciseDao.kt` — add `suspend fun setFunctionalTag(id: Long, hasTag: Boolean)` (or update tags JSON)
- `data/repository/ExerciseRepository.kt` — expose `setFunctionalTag`
- `ui/exercises/ExercisesViewModel.kt` — add `toggleFunctionalTag(exercise: Exercise)` method
- `ui/exercises/ExerciseDetailScreen.kt` — add functional toggle row in metadata section

---

## How to QA

1. Open Exercises tab → tap any strength exercise (e.g. "Bench Press").
2. On the detail screen, find the "Functional training" toggle — it should be OFF for non-functional exercises.
3. Toggle it ON → tap back.
4. Open Exercise Library → tap ⚡ Functional chip → confirm the exercise now appears.
5. Return to detail → toggle it OFF → confirm it disappears from the functional filter.
6. Repeat for a custom exercise to confirm custom exercises are also supported.
