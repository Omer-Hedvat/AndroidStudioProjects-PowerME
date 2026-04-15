# BUG: "Watch on YouTube" button still rendered in ExerciseDetailSheet

## Status
[x] Fixed

## Description
`EXERCISES_SPEC.md §data-contract` marks `youtubeVideoId` as **deprecated** — "Retained for schema stability only — not rendered in UI." However, `ExercisesScreen.kt` (lines 415–433) still checks `exercise.youtubeVideoId` and renders a "Watch on YouTube" `TextButton` when the field is non-null.

103 of 244 exercises have a `youtubeVideoId` in the JSON, so the button appears for a majority of exercises. A visible example is **Handstand Push-Up** (the CrossFit-batch version at JSON index ~173 with ID `hW_X76N_fE8`).

## Steps to Reproduce
1. Open Exercises tab
2. Tap any exercise that has a YouTube ID (e.g. "Handstand Push-Up", "Barbell Back Squat", "Conventional Deadlift")
3. ExerciseDetailSheet opens — a "Watch on YouTube" TextButton is visible

## Fix Notes
Removed the `// YouTube link` block from `ExercisesScreen.kt` — the entire `.let { videoId -> TextButton(...) }` block. Also removed the now-unused `val context = LocalContext.current` line and four orphaned imports (`android.content.Intent`, `android.net.Uri`, `material.icons.filled.PlayArrow`, `compose.ui.platform.LocalContext`). The `youtubeVideoId` entity field and DAO column are retained unchanged for schema stability.
