# BUG: exerciseType mismatches in master_exercises.json

## Status
[x] Fixed

## Severity
P2 normal
- Not a crash, but affects exercise tracking UX for specific exercises (wrong input fields shown)

## Description
Several exercises in `master_exercises.json` have incorrect `exerciseType` values:

### Invalid enum values (deserialization bugs)
1. **Triple Under** — `exerciseType: "BODYWEIGHT"` but `BODYWEIGHT` is not a valid `ExerciseType` enum value (valid: STRENGTH, CARDIO, TIMED, PLYOMETRIC, STRETCH). Should be `STRENGTH` (rep-counted like other jump rope exercises).
2. **Speed Rope (calories)** — `exerciseType: "BODYWEIGHT"` (same invalid enum). Should be `CARDIO` (calorie-tracked like other "(calories)" exercises).

### Wrong type assignments
3. **Shadow Boxing** — `exerciseType: STRENGTH` but shadow boxing is never done by reps. It's always done for timed rounds (e.g. 3 rounds × 3 minutes). Should be `TIMED`.
4. **Battle Ropes** — `exerciseType: CARDIO` but CARDIO tracks distance+time+pace. Battle ropes are done for time intervals with no distance concept. Should be `TIMED`.

## Steps to Reproduce
1. Open Exercise Library, search "Triple Under" or "Speed Rope"
2. Add to a workout
3. Observe: wrong input fields shown (or potential deserialization failure for BODYWEIGHT type)

## Dependencies
- **Depends on:** —
- **Blocks:** —
- **Touches:** `app/src/main/res/raw/master_exercises.json`, `app/src/main/java/com/powerme/app/data/database/MasterExerciseSeeder.kt`

## Assets
- Related spec: `future_devs/FUNC_CARDIO_EXERCISE_SEED_SPEC.md`

## Fix Notes
Bumped seeder to v2.3.1. Four exerciseType corrections:
- Triple Under: `BODYWEIGHT` → `STRENGTH` (invalid enum → consistent with other jump rope exercises)
- Speed Rope (calories): `BODYWEIGHT` → `CARDIO` (invalid enum → calorie tracking like Rowing/Assault Bike cals)
- Shadow Boxing: `STRENGTH` → `TIMED` (done for timed rounds, not reps)
- Battle Ropes: `CARDIO` → `TIMED` (done for time intervals; CARDIO implies distance+pace which doesn't apply)
