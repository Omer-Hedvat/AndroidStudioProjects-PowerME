# BUG: Duplicate exercises in master_exercises.json

## Status
[x] Fixed

## Description
Four exercises appear twice in `app/src/main/res/raw/master_exercises.json` due to capitalisation differences ("Push-Up" vs "Push-up"). The duplicates were added as part of the later CrossFit/bodyweight batch and differ slightly in `equipmentType` and `familyId`, but are the same movement.

The correct version to **keep** is the first (older) entry in each pair — it has consistent capitalisation with the rest of the library and, where applicable, a YouTube ID that was already seeded into the DB.

| Keep | Delete | Difference |
|------|--------|------------|
| `Chin-Up` (Bodyweight, `pullup_family`, has YT ID) | `Chin-up` (Pull-up Bar, `pullup_family`, no YT) | Casing + equipment |
| `Decline Push-Up` (Bodyweight, `bench_family`) | `Decline Push-up` (Bench, `pushup_family`) | Casing + equipment + family |
| `Handstand Push-Up` (Bodyweight, `overhead_press_family`, has YT ID) | `Handstand Push-up` (Bodyweight, `handstand_family`) | Casing + family |
| `Neutral Grip Pull-Up` (Bodyweight, `pullup_family`, has YT ID) | `Neutral Grip Pull-up` (Pull-up Bar, `pullup_family`) | Casing + equipment |

## Steps to Reproduce
1. Open the Exercises tab
2. Search for "handstand" — two entries appear: "Handstand Push-Up" and "Handstand Push-up"
3. Same for "Chin-Up", "Decline Push-Up", "Neutral Grip Pull-Up"

## Fix Notes
- Deleted the 4 lowercase-"up" duplicate entries from `master_exercises.json` (lines ~2708, ~2848, ~2918, ~2932)
- Updated `totalExercises` in JSON header: 244 → 240; updated `version`: `1.6` → `1.7`
- Bumped `CURRENT_VERSION` in `MasterExerciseSeeder.kt` from `"1.6"` to `"1.7"` to force re-seed on existing installs
- Added stale-exercise cleanup step in `performSeed()`: deletes non-custom exercises whose names are no longer in the JSON, so existing users who already had the duplicates in their DB get them removed on next launch
- No DB migration needed (data-only seeder change)
