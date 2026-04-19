# Exercise Animations — ExerciseDetailSheet

| Field | Value |
|---|---|
| **Phase** | P5 |
| **Status** | `completed` |
| **Effort** | S |
| **Depends on** | — |

---

## Overview

Display an animated WebP exercise demonstration inside `ExerciseDetailSheet`. Assets are already generated and live in `app/src/main/assets/exercise_animations/{searchName}.webp`. The implementation task is purely UI: load the asset, render it, and handle missing-file gracefully.

---

## Assets

- **Location:** `app/src/main/assets/exercise_animations/`
- **Naming:** `{exercise.searchName}.webp` — `searchName` is computed by `String.toSearchName()` in `Exercise.kt` (lowercase, strip `[\s\-()'/]`)
- **Format:** Animated WebP, looping, 400×400 px, ≤200 KB
- **Coverage:** 240 exercises (some are placeholder grey frames for exercises WGER/ExerciseDB didn't have)

---

## UI

### Placement
At the top of `ExerciseDetailSheet`, above the form cues banner. Full-width, 16:9 aspect ratio clip (center-crop the 400×400 asset to avoid letter-boxing).

### Loading
Use Coil with the `coil-gif` decoder (already a project dependency) to load from `assets://`:

```kotlin
val imageLoader = LocalContext.current.imageLoader  // configured in App with GifDecoder
AsyncImage(
    model = ImageRequest.Builder(LocalContext.current)
        .data("file:///android_asset/exercise_animations/${exercise.searchName}.webp")
        .crossfade(true)
        .build(),
    contentDescription = exercise.name,
    contentScale = ContentScale.Crop,
    modifier = Modifier
        .fillMaxWidth()
        .aspectRatio(16f / 9f)
        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
)
```

### Fallback
If the file does not exist (custom exercises, future additions), show a `Box` with `surfaceVariant` background and a centred `Icon(Icons.Default.FitnessCenter)` at 48dp.

### Placeholder detection
Grey placeholder WebPs (generated when no API match was found) are indistinguishable from real ones at runtime — treat them identically. Over time, real animations will replace placeholders as the library is improved.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/exercises/ExercisesScreen.kt` — `ExerciseDetailSheet`: add animation slot above form cues
- `app/src/main/java/com/powerme/app/ui/exercises/ExercisesScreen.kt` — ensure `AsyncImage` import + Coil usage is present (likely already used elsewhere)

---

## How to QA

1. Open Exercises tab → tap any exercise → sheet opens with animation playing at the top
2. Tap a second exercise — new animation loads for the new exercise
3. Create a custom exercise → sheet opens → shows fitness-centre icon placeholder (no crash)
4. Tap "Plank" → static placeholder frame shows (no real animation for this) — acceptable, no crash
5. Rotate device — animation restarts cleanly, no layout overflow
