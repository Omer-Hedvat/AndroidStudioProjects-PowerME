# AI Parser Interface Layer

| Field | Value |
|---|---|
| **Phase** | P9 |
| **Status** | `done` |
| **Effort** | S |
| **Depends on** | AI Workout Generation (P7 wrapped) ✅ |
| **Blocks** | On-Device LLM (OnDeviceWorkoutParser) |
| **Touches** | `ai/WorkoutTextParser.kt` (new), `ai/WorkoutPromptUtils.kt` (new), `ai/WorkoutParserRouter.kt` (new), `di/AiModule.kt` (new) |

---

## Overview

Creates the abstraction layer that decouples `AiWorkoutViewModel` from `GeminiWorkoutParser` directly. Introduces a `WorkoutTextParser` interface, a shared prompt/JSON utility object, a cloud-only router (`WorkoutParserRouter`), and a Hilt `AiModule`. This is the prerequisite for plugging in an on-device backend (`OnDeviceWorkoutParser`) in a later session — callers never change.

---

## Behaviour

- `WorkoutTextParser` — `suspend fun parseWorkoutText(userInput: String, exerciseNames: List<String>): ParseResult`
- `WorkoutPromptUtils` — internal object housing `lenientJson`, `buildWorkoutPrompt()`, `parseWorkoutJsonResponse()`; logic copied verbatim from `GeminiWorkoutParser` (source not modified)
- `WorkoutParserRouter` — `@Singleton` that implements `WorkoutTextParser`; injects `@Named("cloud") WorkoutTextParser` and delegates every call to it; on-device routing added in a later session
- `AiModule` — `@Module @InstallIn(SingletonComponent)`: `@Binds @Singleton WorkoutTextParser → WorkoutParserRouter`; `@Provides @Singleton @Named("cloud") GeminiWorkoutParser as WorkoutTextParser`
- `GeminiWorkoutParser` is NOT modified in this task

---

## UI Changes

None — this is a pure infrastructure change. `AiWorkoutViewModel` is wired to the new interface in the companion session that updates existing files.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ai/WorkoutTextParser.kt` — new interface
- `app/src/main/java/com/powerme/app/ai/WorkoutPromptUtils.kt` — new shared utility object
- `app/src/main/java/com/powerme/app/ai/WorkoutParserRouter.kt` — new cloud-only router
- `app/src/main/java/com/powerme/app/di/AiModule.kt` — new Hilt module

---

## How to QA

1. Run `:app:assembleDebug` — must succeed with zero errors
2. Run `:app:testDebugUnitTest` — all existing tests must still pass
3. Confirm no changes to `GeminiWorkoutParser.kt` or `AiWorkoutViewModel.kt`
