# Workouts Page — Quick Start 3-Way Chooser

| Field | Value |
|---|---|
| **Phase** | P0 |
| **Status** | `done` |
| **Effort** | S |
| **Depends on** | Quick Start Workout ✅, AI Workout Generation ✅ |
| **Blocks** | — |
| **Touches** | `ui/workouts/WorkoutsScreen.kt`, `ui/workouts/QuickStartChooserScreen.kt` (new), `navigation/PowerMeNavigation.kt`, `ui/workouts/ai/AiWorkoutGenerationScreen.kt`, `WORKOUT_SPEC.md`, `AI_SPEC.md`, `NAVIGATION_SPEC.md` |

---

## Overview

The Workouts tab previously showed three side-by-side top-level CTAs — Quick Start, Generate with AI, and Routines + — that looked similar but produced structurally different outcomes. The standalone "Generate with AI" button felt out of place as a peer to Quick Start; AI is conceptually a start-now accelerator, not a separate category.

This feature folds all "create a workout now" entry points under a single **Quick Start** button that navigates to a full-page **QuickStartChooserScreen**, removes the standalone AI button, and leaves Routines + untouched as the pure template-authoring entry point.

---

## Behaviour

- Tapping **Quick Start** navigates to `quick_start_chooser` (full-screen push, slide-in transition).
- `QuickStartChooserScreen` presents three `OutlinedCard` items in this fixed order:
  1. **Add exercises** — calls `workoutViewModel.startWorkout()`, pops `quick_start_chooser` off the stack, navigates to `workout`.
  2. **Add from picture** — navigates to `ai_workout?mode=photo` (`AiWorkoutGenerationScreen` with photo mode pre-selected).
  3. **Add from text** — navigates to `ai_workout?mode=text` (`AiWorkoutGenerationScreen` with text mode pre-selected).
- The standalone "Generate with AI" `OutlinedButton` is removed from `WorkoutsScreen`.
- `WorkoutsScreen` callback changes: `onGenerateWithAi` removed; `onQuickStart: () -> Unit` added.
- `AiWorkoutGenerationScreen` accepts `initialMode: InputMode = InputMode.TEXT` and seeds its mode state from it via `LaunchedEffect(Unit)`. No AI pipeline logic is touched.
- Routines + (the `IconButton(Icons.Default.Add)` in the Routines header row) is **unchanged**.

---

## UI Changes

**WorkoutsScreen:**

```
Workouts

[Quick Start]                         ← OutlinedButton, onClick → navigate quick_start_chooser

Routines   [Show Archived]  [ + ]     ← unchanged
[Routine Card]  [Routine Card]
```

**QuickStartChooserScreen (`quick_start_chooser` route):**

```
← Start a Workout

How do you want to build your workout?

┌──────────────────────────────────────┐
│ 🏋  Add exercises                     │
│     Start empty and add as you go    │
└──────────────────────────────────────┘
┌──────────────────────────────────────┐
│ 📷  Add from picture                  │
│     Take a photo or pick from gallery│
└──────────────────────────────────────┘
┌──────────────────────────────────────┐
│ ✏   Add from text                     │
│     Type or paste a workout plan     │
└──────────────────────────────────────┘
```

- Each row is an `OutlinedCard` (full-width, clickable) with leading icon + title + description.
- All colors via `MaterialTheme.colorScheme.*` — no hardcoded values.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/workouts/WorkoutsScreen.kt` — remove AI button; `onGenerateWithAi` → `onQuickStart: () -> Unit`; Quick Start button navigates to chooser.
- `app/src/main/java/com/powerme/app/ui/workouts/QuickStartChooserScreen.kt` — **new file**: full-page 3-card chooser screen.
- `app/src/main/java/com/powerme/app/navigation/PowerMeNavigation.kt` — new `quick_start_chooser` composable; AI route gains `?mode={mode}` arg.
- `app/src/main/java/com/powerme/app/ui/workouts/ai/AiWorkoutGenerationScreen.kt` — add `initialMode: InputMode` param; seed mode from it.
- `WORKOUT_SPEC.md` — update §16.1 screen tree.
- `AI_SPEC.md` — update §5 entry-point tree + §8.5 decision log.
- `NAVIGATION_SPEC.md` — add `quick_start_chooser` row; update `ai_workout` row.

---

## How to QA

1. Launch app → Workouts tab.
2. Confirm **only two** CTAs above Routines: `Quick Start` and Routines `+`.
3. Tap Quick Start → full-page chooser opens with 3 cards: Add exercises / Add from picture / Add from text.
4. Tap **Add exercises** → `ActiveWorkoutScreen` opens with an empty session.
5. Back → Quick Start → **Add from picture** → `AiWorkoutGenerationScreen` opens with photo mode pre-selected (Camera / Gallery buttons visible immediately).
6. Back → Quick Start → **Add from text** → `AiWorkoutGenerationScreen` opens with text mode pre-selected (text input field visible).
7. Back → tap Routines `+` → Template Builder opens (unchanged).
8. Build passes + all unit tests pass.
