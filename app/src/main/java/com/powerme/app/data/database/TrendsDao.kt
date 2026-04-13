package com.powerme.app.data.database

import androidx.room.Dao
import androidx.room.Query

// ── Projection data classes ─────────────────────────────────────────────────

data class E1RMDataPoint(val dateMs: Long, val bestE1RM: Double, val setCount: Int)

data class WeeklyVolumeRow(
    val weekBucket: Long,
    val weekStartMs: Long,
    val totalVolume: Double,
    val workoutCount: Int
)

data class MuscleGroupVolumeRow(
    val weekBucket: Long,
    val weekStartMs: Long,
    val majorGroup: String,
    val volume: Double
)

data class EffectiveSetsRow(
    val weekBucket: Long,
    val weekStartMs: Long,
    val majorGroup: String,
    val effectiveSets: Int
)

data class WorkoutTimeRow(
    val id: String,
    val startTimeMs: Long,
    val totalVolume: Double,
    val startHour: Int
)

data class ExerciseWithHistory(val id: Long, val name: String)

@Dao
interface TrendsDao {

    /**
     * Per-session peak e1RM (Epley) for a given exercise.
     * Groups by workout (session), returns MAX(weight * (1 + reps/30)).
     */
    @Query("""
        SELECT w.timestamp AS dateMs,
               MAX(ws.weight * (1 + ws.reps / 30.0)) AS bestE1RM,
               COUNT(ws.id) AS setCount
        FROM workout_sets ws
        JOIN workouts w ON ws.workoutId = w.id
        WHERE ws.exerciseId = :exerciseId
          AND w.isCompleted = 1
          AND w.isArchived = 0
          AND w.timestamp >= :sinceMs
          AND ws.isCompleted = 1
          AND ws.setType != 'WARMUP'
          AND ws.weight > 0
          AND ws.reps > 0
        GROUP BY w.id
        ORDER BY w.timestamp ASC
    """)
    suspend fun getE1RMHistory(exerciseId: Long, sinceMs: Long): List<E1RMDataPoint>

    /**
     * Weekly total volume aggregated from completed workouts.
     * Uses pre-computed Workout.totalVolume field.
     */
    @Query("""
        SELECT (timestamp / 604800000) AS weekBucket,
               MIN(timestamp) AS weekStartMs,
               SUM(totalVolume) AS totalVolume,
               COUNT(id) AS workoutCount
        FROM workouts
        WHERE isCompleted = 1
          AND isArchived = 0
          AND timestamp >= :sinceMs
        GROUP BY weekBucket
        ORDER BY weekBucket ASC
    """)
    suspend fun getWeeklyVolume(sinceMs: Long): List<WeeklyVolumeRow>

    /**
     * Weekly volume per muscle group with primary/secondary credit (100%/50%).
     */
    @Query("""
        SELECT (w.timestamp / 604800000) AS weekBucket,
               MIN(w.timestamp) AS weekStartMs,
               emg.majorGroup,
               SUM(
                   CASE WHEN emg.isPrimary = 1 THEN ws.weight * ws.reps
                        ELSE ws.weight * ws.reps * 0.5
                   END
               ) AS volume
        FROM workout_sets ws
        JOIN workouts w ON ws.workoutId = w.id
        JOIN exercise_muscle_groups emg ON ws.exerciseId = emg.exerciseId
        WHERE w.isCompleted = 1
          AND w.isArchived = 0
          AND ws.isCompleted = 1
          AND ws.setType != 'WARMUP'
          AND ws.weight > 0
          AND ws.reps > 0
          AND w.timestamp >= :sinceMs
        GROUP BY weekBucket, emg.majorGroup
        ORDER BY weekBucket ASC, volume DESC
    """)
    suspend fun getWeeklyMuscleGroupVolume(sinceMs: Long): List<MuscleGroupVolumeRow>

    /**
     * Weekly effective sets per muscle group (RPE >= 70, i.e. RPE 7.0+).
     * Primary muscle groups only (no 50% secondary credit for set counts).
     */
    @Query("""
        SELECT (w.timestamp / 604800000) AS weekBucket,
               MIN(w.timestamp) AS weekStartMs,
               emg.majorGroup,
               COUNT(ws.id) AS effectiveSets
        FROM workout_sets ws
        JOIN workouts w ON ws.workoutId = w.id
        JOIN exercise_muscle_groups emg ON ws.exerciseId = emg.exerciseId
        WHERE w.isCompleted = 1
          AND w.isArchived = 0
          AND ws.isCompleted = 1
          AND ws.rpe IS NOT NULL
          AND ws.rpe >= 70
          AND ws.setType NOT IN ('WARMUP', 'DROP')
          AND ws.weight > 0
          AND ws.reps > 0
          AND emg.isPrimary = 1
          AND w.timestamp >= :sinceMs
        GROUP BY weekBucket, emg.majorGroup
        ORDER BY weekBucket ASC
    """)
    suspend fun getWeeklyEffectiveSets(sinceMs: Long): List<EffectiveSetsRow>

    /**
     * Completed workouts with start time and volume for chronotype scatter plot.
     */
    @Query("""
        SELECT id,
               startTimeMs,
               totalVolume,
               (startTimeMs / 3600000 % 24) AS startHour
        FROM workouts
        WHERE isCompleted = 1
          AND isArchived = 0
          AND startTimeMs > 0
          AND timestamp >= :sinceMs
        ORDER BY timestamp ASC
    """)
    suspend fun getWorkoutsByTimeOfDay(sinceMs: Long): List<WorkoutTimeRow>

    /**
     * Exercises that have completed sets with weight data in the given window.
     * Used for exercise picker in e1RM Progression card.
     */
    @Query("""
        SELECT DISTINCT e.id, e.name
        FROM exercises e
        JOIN workout_sets ws ON ws.exerciseId = e.id
        JOIN workouts w ON ws.workoutId = w.id
        WHERE w.isCompleted = 1
          AND w.isArchived = 0
          AND ws.isCompleted = 1
          AND ws.setType != 'WARMUP'
          AND ws.weight > 0
          AND ws.reps > 0
          AND w.timestamp >= :sinceMs
        ORDER BY e.name ASC
    """)
    suspend fun getExercisesWithHistory(sinceMs: Long): List<ExerciseWithHistory>
}
