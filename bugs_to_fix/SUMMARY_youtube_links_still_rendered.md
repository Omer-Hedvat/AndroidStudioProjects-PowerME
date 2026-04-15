# Fix Summary: "Watch on YouTube" button still rendered in ExerciseDetailSheet

## Root Cause

`ExercisesScreen.kt` still rendered a "Watch on YouTube" `TextButton` in `ExerciseDetailSheet` despite `EXERCISES_SPEC.md` marking `youtubeVideoId` as deprecated ("Retained for schema stability only — not rendered in UI"). 103 of 244 exercises had a non-null `youtubeVideoId`, so the button appeared for the majority of exercises.

## Files Changed

| File | Change |
|---|---|
| `app/src/main/java/com/powerme/app/ui/exercises/ExercisesScreen.kt` | Removed `// YouTube link` block (`exercise.youtubeVideoId?.takeIf...let { videoId -> TextButton(...) }`), removed `val context = LocalContext.current`, and removed 4 orphaned imports (`Intent`, `Uri`, `PlayArrow`, `LocalContext`) |
| `bugs_to_fix/BUG_youtube_links_still_rendered.md` | Marked `[x] Fixed`, filled Fix Notes |

## Surfaces Fixed
- `ExerciseDetailSheet` — "Watch on YouTube" button no longer appears for any exercise

## How to QA
1. Open the **Exercises** tab
2. Tap any exercise (e.g. "Barbell Back Squat", "Conventional Deadlift", "Handstand Push-Up")
3. **Expected:** ExerciseDetailSheet opens with Form Cues banner — no YouTube button visible
4. **Not expected:** a "Watch on YouTube" TextButton appears anywhere in the sheet
