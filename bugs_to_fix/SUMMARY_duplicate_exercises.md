# Fix Summary: Duplicate exercises in master_exercises.json

## Root Cause
A later CrossFit/bodyweight batch added 4 exercises with lowercase "up" capitalisation (e.g. "Chin-up") that duplicated existing entries with uppercase "Up" (e.g. "Chin-Up"), differing slightly in `equipmentType` and `familyId`.

## Files Changed

| File | Change |
|------|--------|
| `app/src/main/res/raw/master_exercises.json` | Deleted 4 duplicate lowercase-"up" entries; `totalExercises` 244→240; `version` 1.6→1.7 |
| `app/src/main/java/com/powerme/app/data/database/MasterExerciseSeeder.kt` | Bumped `CURRENT_VERSION` to `"1.7"`; added stale-exercise cleanup step; single-pass `masterNames` build |
| `app/src/main/java/com/powerme/app/data/database/ExerciseDao.kt` | Added `deleteExercises(List<Exercise>)` for batch deletion |

## Duplicates Removed

| Deleted entry | Kept entry |
|---------------|------------|
| `Chin-up` (Pull-up Bar, no YT ID) | `Chin-Up` (Bodyweight, has YT ID) |
| `Decline Push-up` (Bench, pushup_family) | `Decline Push-Up` (Bodyweight, bench_family) |
| `Handstand Push-up` (handstand_family, no YT ID) | `Handstand Push-Up` (overhead_press_family, has YT ID) |
| `Neutral Grip Pull-up` (Pull-up Bar, no YT ID) | `Neutral Grip Pull-Up` (Bodyweight, has YT ID) |

## Surfaces Fixed
- Exercises tab search returns a single result for "handstand", "chin", "decline push", "neutral grip pull"
- Existing-user handling: seeder v1.6→1.7 mismatch triggers cleanup on first launch, removing stale rows in a single transaction

## How to QA
1. Open the **Exercises** tab
2. Search "handstand" — confirm only **"Handstand Push-Up"** appears (not "Handstand Push-up")
3. Search "chin" — confirm only **"Chin-Up"** appears
4. Search "decline push" — confirm only **"Decline Push-Up"** appears
5. Search "neutral grip" — confirm only **"Neutral Grip Pull-Up"** appears
