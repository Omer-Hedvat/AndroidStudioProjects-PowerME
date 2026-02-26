# Warmup & Mobility Schema Upgrade

## 1. New Entity: WarmupLog
- `id`: Primary Key.
- `workout_id`: Foreign Key to Workout.
- `exercise_name`: String.
- `timestamp`: DateTime.
- `target_joint`: (Enum: SHOULDER, ELBOW, LOWER_BACK, HIP, KNEE).

## 2. Logic Constraint
- The system must store the last 10 warmup activities to ensure the 'Variety Engine' can filter out recent duplicates.