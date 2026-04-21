# BUG: Exercise history detail shows RPE as raw decimal (0–1) instead of scaled (1–10)

## Status
[x] Fixed

## Severity
P2 normal — cosmetic inconsistency; data is correct but displayed in wrong scale

## Description
There is a display inconsistency between two paths to workout detail:
- **History tab → workout → set rows**: RPE shown correctly as 1–10 integer (e.g. "8")
- **Exercises tab → exercise → History tab → specific workout**: RPE shown as raw 0–1 decimal (e.g. "0.8")

Root cause area: the ExerciseDetail history path likely reads RPE directly from the DB (stored as 0.0–1.0) and renders it without multiplying by 10, whereas the History detail path goes through a formatter that scales it.

## Steps to Reproduce
1. Complete a workout with RPE values set on sets (e.g. RPE 8).
2. Go to **History** tab → tap the workout → observe set rows — RPE shows as "8". ✅
3. Go to **Exercises** tab → tap an exercise used in that workout → open **History** tab → tap the specific session entry.
4. Observe: RPE shows as "0.8" (raw decimal). ❌

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `ExerciseDetailScreen.kt`, `ExerciseDetailViewModel.kt`, `ExerciseDetailRepository.kt`, possibly `WorkoutDetailScreen.kt`

## Assets
- Related spec: `EXERCISES_SPEC.md`, `HISTORY_ANALYTICS_SPEC.md`

## Fix Notes
Both CSV importers (`StrongCsvImporter.kt`, `CsvImportManager.kt`) were storing RPE as raw 1–10
integers while the rest of the app uses a ×10 scale (60–100). Imported workouts had `rpe = 8`
while natively logged workouts had `rpe = 80`. The display code divides by 10, so imported sets
showed "0.8" instead of "8".

Fix:
- `StrongCsvImporter.kt`: `(it * 10).toInt().coerceIn(10, 100)` — preserves decimal precision (8.5 → 85)
- `CsvImportManager.kt`: same fix
- DB migration v48: `UPDATE workout_sets SET rpe = rpe * 10 WHERE rpe BETWEEN 1 AND 10` — backfills existing imported data
- `ExerciseDetailRepositoryTest.kt`: new test for RPE trend scaling
