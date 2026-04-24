# BUG: No way to favourite an exercise from the Exercises screen

## Status
[x] Fixed

## Severity
P1 high — the favourites quick-filter button (star toggle in search bar) is useless unless users can actually mark exercises as favourite, which currently has no UI entry point in the Exercise Library.

## Description
The `isFavorite` field exists on every `Exercise` entity and the Exercise Library now has a star toggle button that filters to favourited exercises. However, there is no button, icon, or gesture anywhere in the Exercises screen (neither on exercise cards in the list nor inside the Exercise Detail Sheet) that allows users to toggle `isFavorite` on or off. Users have no way to populate their favourites list, making the favourites filter completely non-functional.

Affected screens: `ExercisesScreen` (exercise card rows), `ExerciseDetailSheet` (bottom sheet).

## Steps to Reproduce
1. Open the **Exercises** tab.
2. Scroll through exercise cards or tap one to open the detail sheet.
3. Observe: no heart/star icon is present to mark the exercise as a favourite.
4. Tap the star toggle in the search bar to filter by favourites.
5. Observe: list is empty because no exercise can be favourited.

## Dependencies
- **Depends on:** —
- **Blocks:** `exercise_favorites_filter` (the filter is pointless without this)
- **Touches:** `ui/exercises/ExercisesScreen.kt`, `ui/exercises/ExercisesViewModel.kt`, `data/repository/ExerciseRepository.kt` (or `ExerciseDao.kt` if a direct update is needed)

## Assets
- Related spec: `EXERCISES_SPEC.md`

## Fix Notes
- Star icon on exercise cards was invisible (16dp icon, 28dp button, `onSurfaceVariant.copy(alpha=0.35f)` tint) — fixed to 22dp icon, 40dp button, full opacity
- Added `toggleFavorite()` to `ExerciseDetailViewModel` (injects `ExerciseRepository`)
- Added star `IconButton` to `ExerciseDetailScreen` TopAppBar `actions` slot — amber when favourited, `onSurfaceVariant` when not
