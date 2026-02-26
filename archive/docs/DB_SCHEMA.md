# PowerME Data Schema (Room DB)

## Entities
1. **Routine:** `id`, `name`, `last_performed`, `is_custom`.
2. **Workout:** `id`, `routine_id`, `timestamp`, `duration_seconds`, `total_volume`, `notes`.
3. **Exercise:** `id`, `name`, `muscle_group`, `equipment_type`, `instructions_url`.
4. **WorkoutSet:** `id`, `workout_id`, `exercise_id`, `set_order`, `weight`, `reps`, `rpe`, `set_type` (Enum: WARMUP, NORMAL, DROP, FAILURE).

## Relationships
- A **Routine** has many **Exercises**.
- A **Workout** is an instance of a **Routine**.
- A **Workout** contains multiple **WorkoutSets** grouped by **Exercise**.