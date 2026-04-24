# Exercise Library — Favourites Quick-Filter Button

| Field | Value |
|---|---|
| **Phase** | P5 |
| **Status** | `not-started` |
| **Effort** | S |
| **Depends on** | — |
| **Blocks** | — |
| **Touches** | `ui/exercises/ExercisesScreen.kt`, `ui/exercises/ExercisesViewModel.kt` |

---

## Overview

Adds a heart (favourites) toggle button directly in the Exercise Library search bar row — outside the filter dialog — so users can instantly narrow the list to their saved exercises without opening any dialog. This is a persistent, single-tap filter that stacks with all existing filters (muscle group, equipment, exercise type, functional tag, search text).

---

## Behaviour

- A filled/outlined heart `IconToggleButton` sits immediately to the left of the existing Tune (`FilterList`) icon in the search bar row.
- **Off (default):** all exercises shown (subject to other active filters). Heart is outlined (`Icons.Outlined.FavoriteBorder`, `onSurfaceVariant` tint).
- **On:** only exercises where `isFavorite == true` are shown. Heart is filled (`Icons.Filled.Favorite`, `MaterialTheme.colorScheme.error` tint — matches the existing favourite star colour elsewhere in the app).
- The favourites filter stacks with the full filter dialog (AND logic): favourite + Barbell = only favourite barbell exercises.
- The filter resets to Off when the user clears all filters (if a global "Clear all" action exists), but persists across search text changes and filter dialog open/close.
- `ExercisesUiState` gains `favoritesOnly: Boolean = false`.
- `ExercisesViewModel` gains `onFavoritesFilterToggled()`.
- Filtering logic in `ExercisesViewModel` applies `isFavorite` check when `favoritesOnly == true`.
- A non-zero active-filter count badge on the Tune icon (if one exists) does NOT count the favourites toggle — it is a separate control.

---

## UI Changes

**Search bar row layout (ExercisesScreen):**
```
[ SearchTextField (weight=1f) ] [ ❤ IconToggleButton ] [ ⚙ IconButton (Tune) ]
```

- `IconToggleButton` size: 48dp touch target (standard Material 3).
- Heart icon: `Icons.Outlined.FavoriteBorder` (off) / `Icons.Filled.Favorite` (on).
- Tint off: `MaterialTheme.colorScheme.onSurfaceVariant`.
- Tint on: `MaterialTheme.colorScheme.error` (consistent with favourite star in ExerciseCard).
- No label — icon is self-explanatory with a content description `"Show favourites only"`.

---

## Files to Touch

- `app/src/main/java/com/powerme/app/ui/exercises/ExercisesViewModel.kt` — add `favoritesOnly: Boolean` to `ExercisesUiState`, add `onFavoritesFilterToggled()`, apply filter in exercise list derivation
- `app/src/main/java/com/powerme/app/ui/exercises/ExercisesScreen.kt` — add heart `IconToggleButton` to the search bar row between SearchTextField and Tune icon

---

## How to QA

1. Open Exercise Library. Heart icon visible to the left of the Tune icon — outlined, not filled.
2. Tap the heart → icon turns filled red. List narrows to only favourited exercises.
3. Tap again → icon returns to outlined. Full list restored.
4. With heart active, also open the filter dialog and apply Equipment = Barbell → only favourite barbell exercises shown (AND logic).
5. With heart active, type a search term → favourites-only list filters further by name.
6. Heart state persists when navigating away and back (ViewModel scope).
