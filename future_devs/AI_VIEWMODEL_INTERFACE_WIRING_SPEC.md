# AI ViewModel Interface Wiring

| Field | Value |
|---|---|
| **Phase** | P9 |
| **Status** | `done` |
| **Effort** | XS |
| **Depends on** | AI parser interface layer ✅ |
| **Blocks** | Session B (AICore on-device), Session C (synonym learning) |
| **Touches** | `ui/workouts/ai/AiWorkoutViewModel.kt`, `app/src/test/java/com/powerme/app/ui/workouts/ai/AiWorkoutViewModelTest.kt` |

---

## Overview

The AI parser interface layer (`WorkoutTextParser`, `WorkoutParserRouter`, `AiModule`) is shipped and wrapped. However, `AiWorkoutViewModel` still injects the concrete `GeminiWorkoutParser` directly instead of the `WorkoutTextParser` interface. This task completes the wiring so the router is actually used end-to-end, and updates the corresponding test to mock the interface rather than the concrete class.

---

## Behaviour

- `AiWorkoutViewModel` constructor param changes from `GeminiWorkoutParser` to `WorkoutTextParser`
- Hilt injects `WorkoutParserRouter` (bound to `WorkoutTextParser` in `AiModule`) at runtime
- All existing AI generation flows (text + photo) continue working via the cloud path through the router
- No user-visible change

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/workouts/ai/AiWorkoutViewModel.kt` — change constructor param type from `GeminiWorkoutParser` to `WorkoutTextParser`; update import
- `app/src/test/java/com/powerme/app/ui/workouts/ai/AiWorkoutViewModelTest.kt` — line 40: mock type `GeminiWorkoutParser` → `WorkoutTextParser`; update import on line 4; all 11 `whenever(geminiParser.parseWorkoutText(...))` stubs are unchanged (same method signature)

---

## How to QA

1. Build passes: `:app:assembleDebug`
2. All 16 AiWorkoutViewModelTest tests pass
3. Text → workout flow works end-to-end on device (cloud path, via router, via interface) — no user-visible change
