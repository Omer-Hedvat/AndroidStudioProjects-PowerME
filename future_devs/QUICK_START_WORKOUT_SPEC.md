# Quick Start Workout

| Field | Value |
|---|---|
| **Phase** | P0 |
| **Status** | `done` |
| **Effort** | XS |
| **Depends on** | — |
| **Blocks** | AI Workout Generation |
| **Touches** | `WorkoutsScreen.kt`, `PowerMeNavigation.kt`, `WorkoutViewModel.kt` |

---

## Overview

Users currently must have a pre-built routine to start a workout. Quick Start lets them begin an empty workout immediately and add exercises as they go — no routine required. This also serves as the landing UI for the future AI Workout Generation feature, which will populate the empty workout before the user starts.

---

## Behaviour

- A **"Quick Start"** button appears on the Workouts tab (e.g. a secondary FAB or a card at the top).
- Tapping it starts an active workout with no routine ID and an empty exercise list.
- The user adds exercises inline during the workout using the existing exercise picker flow.
- On finish: the same post-workout summary flow applies. The user is offered the option to **"Save as Routine"** (which already exists in WorkoutSummaryScreen) so a one-off workout can be promoted to a routine.
- No routine sync diff runs (there is no source routine to compare against).

---

## UI Changes

- `WorkoutsScreen.kt` — add Quick Start entry point. Options:
  - A secondary FAB alongside the existing routine FAB (recommended — consistent with existing patterns)
  - Or a "Quick Start" card at the top of the routines list
- `WorkoutViewModel.kt` — `startBlankWorkout()` method: creates a `Workout` entity with no `routineId`, empty set list, starts the active workout state machine.
- `PowerMeNavigation.kt` — ensure `startBlankWorkout()` navigates correctly to the active workout screen (same route, no routineId parameter).

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/workout/WorkoutsScreen.kt` — add Quick Start button
- `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` — add `startBlankWorkout()`
- `app/src/main/java/com/powerme/app/navigation/PowerMeNavigation.kt` — wire the new entry point

---

## Implementation Notes

- The "Quick Start" button is an `OutlinedButton` at the top of the Workouts tab list (above the Routines header).
- Implemented via `WorkoutViewModel.startWorkout("")` — no new method required; existing `startWorkout(routineId: String = "")` handles the blank case.
- Nav wiring in `PowerMeNavigation.kt` already branched on empty `routineId` → calls `startWorkout()` and navigates to `Routes.WORKOUT`.
- `saveWorkoutAsRoutine()` already existed and is wired into `WorkoutSummaryScreen`.
- No schema change. No new tests needed — `startWorkout("")` has extensive coverage in `WorkoutViewModelTest`.

---

## How to QA

1. Open the Workouts tab
2. Tap **Quick Start** — active workout screen opens with no exercises
3. Add an exercise via the exercise picker — it appears in the workout
4. Complete a set and finish the workout
5. Confirm post-workout summary appears with **"Save as Routine"** option
6. Tap "Save as Routine" — enter a name, confirm a new routine appears in the Workouts tab
