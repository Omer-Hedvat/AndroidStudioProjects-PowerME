# BUG: AI text generator does not generate workout from natural language input

## Status
[x] Fixed

## Severity
P1 high — core AI feature broken; entering a prompt like "upper body workout, 5 exercises" produces no result

## Description
When the user types a natural language prompt (e.g. "upper body workout, 5 exercises") into the AI workout text generator and submits, no workout is generated. The issue may lie in: prompt not being sent to Gemini, the WorkoutParserRouter returning an error silently, the parsed response not being surfaced to the UI, or the ViewModel not advancing state after a result arrives.

## Steps to Reproduce
1. Navigate to the AI workout generation screen (Workouts tab → AI generate)
2. Select the text input (manual prompt) mode
3. Type: "upper body workout, 5 exercises"
4. Tap the Generate / Submit button
5. Observe: no workout is generated; screen remains unchanged or shows no output

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `app/src/main/java/com/powerme/app/ui/workouts/ai/AiWorkoutGenerationScreen.kt`, `app/src/main/java/com/powerme/app/ui/workouts/ai/AiWorkoutViewModel.kt`, `app/src/main/java/com/powerme/app/ai/WorkoutParserRouter.kt`

## Assets
- Related spec: `AI_SPEC.md`

## Fix Notes
Fixed as a side effect of the "AI key + parser abstraction" feature (WorkoutParserRouter). The full flow was already implemented: `AiWorkoutViewModel.processTextInput()` calls `workoutParser.parseWorkoutText()`, handles errors, maps results to `PreviewExercise` list, and transitions to `AiWorkoutStep.PREVIEW`. `WorkoutParserRouter` routes to on-device when available, falls back to cloud (`GeminiWorkoutParser`) otherwise. No code changes were required — the bug was stale.
