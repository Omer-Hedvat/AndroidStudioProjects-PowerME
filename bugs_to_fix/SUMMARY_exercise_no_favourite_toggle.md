# SUMMARY: BUG_exercise_no_favourite_toggle

## What was fixed

The `isFavorite` toggle had no visible UI entry point. The star icon existed on exercise cards but was rendered at 35% opacity with a 16dp icon inside a 28dp button — effectively invisible. No toggle existed at all on the Exercise Detail screen.

## Changes made

- **`ExercisesScreen.kt`** — increased `IconButton` to 40dp, `Icon` to 22dp, removed `copy(alpha = 0.35f)` so the unfavourited star renders at full `onSurfaceVariant` opacity
- **`ExerciseDetailViewModel.kt`** — injected `ExerciseRepository`, added `toggleFavorite()` which calls `exerciseRepository.toggleFavorite(exercise)` then reloads the exercise from the DB
- **`ExerciseDetailScreen.kt`** — added `actions` slot to `TopAppBar` with an amber (filled) / `onSurfaceVariant` (outlined) star `IconButton` wired to `viewModel::toggleFavorite`
