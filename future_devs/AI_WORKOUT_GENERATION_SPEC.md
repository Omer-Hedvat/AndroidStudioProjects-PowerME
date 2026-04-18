# AI Workout Generation

| Field | Value |
|---|---|
| **Phase** | P7 |
| **Status** | `done` |
| **Effort** | XL |
| **Depends on** | Quick Start Workout |
| **Blocks** | — |
| **Touches** | `AiWorkoutGenerationScreen.kt` (new), `AiWorkoutViewModel.kt` (new), `WorkoutRepository.kt`, `PowerMeNavigation.kt`, `WorkoutsScreen.kt` |

---

## Overview

Users can generate a workout plan from a photo (e.g. a gym whiteboard, printed program, or screenshot) or free-form text (e.g. `"3x8 bench press, 4x5 squat"` or `"give me a push day with 5 exercises"`). The AI parses the input, matches exercises to the app's library, and presents a preview screen where the user can edit before choosing to start immediately or save as a routine.

---

## Architecture Decision

**Resolved:** Gemini Flash API (cloud) + ML Kit Text Recognition (on-device OCR for photos).

| Option | Pros | Cons |
|--------|------|------|
| **Gemini Flash API** (recommended) | Highest accuracy for exercise name matching, no download, already in Firebase/Google stack, handles ambiguous NL well | Requires network, photos sent to Google |
| **On-device Gemma 4** (via MediaPipe LLM Inference) | Fully offline, zero cost, private | ~1–4 GB model download, needs RAG with exercise library context, higher hallucination risk on exercise names |
| **Hybrid** | Best of both — offline for simple/structured, cloud for ambiguous | More complex, two code paths to maintain |

**Decision required before starting implementation.** Note current leaning: Gemini Flash for accuracy, with ML Kit Text Recognition (offline, no download) handling the photo → text OCR step in all cases.

---

## Input Paths

### Path A — Free Text
- User types or pastes a workout description
- Supports structured (`"3x8 bench press, 4x5 squat, 3x10 cable row"`) and natural language (`"push day, 5 exercises, intermediate"`)
- No preprocessing needed — text passed directly to AI

### Path B — Photo Capture
1. User taps camera icon → Android camera or gallery picker
2. **ML Kit Text Recognition** (on-device, no download required via Play Services) extracts text from the image
3. Extracted text is shown to the user for a quick review/edit before sending to AI
4. Corrected text passed to the same AI pipeline as Path A

---

## AI Pipeline (text → structured workout)

1. **Prompt construction:** Inject the app's exercise library (or a relevant subset by muscle group keyword detection) as context so the model matches to real exercise names rather than hallucinating variants
2. **Model call:** Send text + exercise library context → request JSON response:
   ```json
   [
     { "exerciseName": "Barbell Flat Bench Press", "sets": 3, "reps": 8, "weight": null, "restSeconds": 90 },
     ...
   ]
   ```
3. **Fuzzy matching:** Map returned `exerciseName` strings to the app's exercise library using Jaro-Winkler similarity (threshold 0.85). Unmatched exercises are flagged for user review.
4. **Preview construction:** Build a workout plan from matched exercises

---

## User Flow

```
Workouts tab
  └── Quick Start
        └── "Generate with AI" option  (or separate entry point on Workouts tab)
              ├── [Photo] → camera/gallery → ML Kit OCR → text review
              └── [Text] → text input field
                    └── AI pipeline
                          └── Preview Screen
                                ├── Edit exercises, sets, reps inline
                                ├── Swap unmatched exercises
                                └── [Start Workout] → active workout (Quick Start flow)
                                    [Save as Routine] → save then optionally start
```

---

## UI Changes

- `WorkoutsScreen.kt` — "Generate with AI" entry point (secondary button or within Quick Start flow)
- `AiWorkoutGenerationScreen.kt` (new) — two-panel: input (text field + camera button) and preview (editable exercise list)
- `AiWorkoutViewModel.kt` (new) — manages input state, AI call, exercise matching, preview state
- `PowerMeNavigation.kt` — new route `ai_workout_generation`

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/workout/WorkoutsScreen.kt` — add AI entry point
- `app/src/main/java/com/powerme/app/ui/workout/AiWorkoutGenerationScreen.kt` — new screen
- `app/src/main/java/com/powerme/app/ui/workouts/ai/AiWorkoutViewModel.kt` — new ViewModel (actual path)
- `app/src/main/java/com/powerme/app/ui/workouts/ai/AiWorkoutGenerationScreen.kt` — new screen (actual path)
- `app/src/main/java/com/powerme/app/navigation/PowerMeNavigation.kt` — new route `ai_workout`
- `app/src/main/java/com/powerme/app/data/repository/WorkoutRepository.kt` — `createWorkoutFromPlan()` + `PlanExercise` model
- `app/src/main/java/com/powerme/app/data/repository/RoutineRepository.kt` — `createRoutineFromPlan()`
- `app/src/main/java/com/powerme/app/ui/workout/WorkoutViewModel.kt` — `startWorkoutFromPlan()`
- `app/src/main/java/com/powerme/app/ai/GeminiWorkoutParser.kt` — new (Gemini Flash API client)
- `app/src/main/java/com/powerme/app/ai/TextRecognitionService.kt` — new (ML Kit OCR)
- `app/src/main/java/com/powerme/app/util/JaroWinkler.kt` — new (Jaro-Winkler algorithm)
- `app/src/main/java/com/powerme/app/util/ExerciseMatcher.kt` — new (4-tier matching cascade)

---

## How to QA

1. Open Workouts tab → Quick Start → Generate with AI
2. **Text path:** Type `"3x8 barbell bench press, 3x8 dumbbell row, 3x10 overhead press"` → tap Generate
3. Verify preview shows 3 exercises matched to the app's library with correct sets/reps
4. **Photo path:** Take a photo of a handwritten workout → verify OCR extracts the text → tap Generate
5. Edit one exercise name in the preview → tap "Start Workout"
6. Confirm active workout opens with the generated exercises pre-loaded
7. **Natural language:** Type `"upper body push day, 4 exercises"` → verify AI returns a sensible 4-exercise plan
8. Tap "Save as Routine" → confirm routine is created and appears in Workouts tab
