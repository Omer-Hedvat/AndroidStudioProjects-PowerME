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

/** Projection used by [StressAccumulationEngine] to compute per-region training load. */
data class StressSetRow(
    val exerciseId: Long,
    val weight: Double,
    val reps: Int,
    val timestampMs: Long
)

// ── Per-exercise detail projections ─────────────────────────────────────────

data class ExerciseWorkoutHistoryRow(
    val workoutId: String,
    val timestamp: Long,
    val routineName: String,
    val setCount: Int,
    val totalVolume: Double
)

data class ExerciseLastPerformedRow(
    val timestamp: Long,
    val setCount: Int,
    val totalVolume: Double
)

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
     *
     * weekStartMs is computed as (weekBucket * 604800000) — the epoch-aligned start of the
     * week bucket — rather than MIN(w.timestamp). Using MIN would give each muscle group a
     * different weekStartMs depending on which day of the week it was first trained, causing
     * the chart to treat the same calendar week as multiple distinct bars.
     */
    @Query("""
        SELECT (w.timestamp / 604800000) AS weekBucket,
               (w.timestamp / 604800000 * 604800000) AS weekStartMs,
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
     *
     * weekStartMs uses the same epoch-aligned bucket computation as getWeeklyMuscleGroupVolume
     * to ensure all muscle groups trained in the same week share the same weekStartMs.
     */
    @Query("""
        SELECT (w.timestamp / 604800000) AS weekBucket,
               (w.timestamp / 604800000 * 604800000) AS weekStartMs,
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
     * Total completed non-WARMUP/non-DROP sets with weight+reps in range.
     * Used as the denominator for RPE coverage %.
     */
    @Query("""
        SELECT COUNT(ws.id)
        FROM workout_sets ws
        JOIN workouts w ON ws.workoutId = w.id
        WHERE w.isCompleted = 1
          AND w.isArchived = 0
          AND ws.isCompleted = 1
          AND ws.setType NOT IN ('WARMUP', 'DROP')
          AND ws.weight > 0
          AND ws.reps > 0
          AND w.timestamp >= :sinceMs
    """)
    suspend fun getTotalSetsCount(sinceMs: Long): Int

    /**
     * Same as getTotalSetsCount but requires rpe IS NOT NULL — numerator for RPE coverage %.
     */
    @Query("""
        SELECT COUNT(ws.id)
        FROM workout_sets ws
        JOIN workouts w ON ws.workoutId = w.id
        WHERE w.isCompleted = 1
          AND w.isArchived = 0
          AND ws.isCompleted = 1
          AND ws.setType NOT IN ('WARMUP', 'DROP')
          AND ws.weight > 0
          AND ws.reps > 0
          AND ws.rpe IS NOT NULL
          AND w.timestamp >= :sinceMs
    """)
    suspend fun getRpeCoveredSetsCount(sinceMs: Long): Int

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

    /** Batch exercise name lookup for stress attribution in the heatmap card. */
    @Query("SELECT id, name FROM exercises WHERE id IN (:ids)")
    suspend fun getExerciseNamesByIds(ids: List<Long>): List<ExerciseWithHistory>

    /**
     * Working sets (excl. WARMUP/DROP) with positive weight and reps in the given window.
     * Used as input for [com.powerme.app.analytics.StressAccumulationEngine].
     * Caller is responsible for choosing an appropriate lookback (typically 21 days = ~5 half-lives).
     */
    @Query("""
        SELECT ws.exerciseId, ws.weight, ws.reps, w.timestamp AS timestampMs
        FROM workout_sets ws
        JOIN workouts w ON ws.workoutId = w.id
        WHERE w.isCompleted = 1
          AND w.isArchived = 0
          AND ws.isCompleted = 1
          AND ws.setType NOT IN ('WARMUP', 'DROP')
          AND ws.weight > 0
          AND ws.reps > 0
          AND w.timestamp >= :sinceMs
        ORDER BY w.timestamp ASC
    """)
    suspend fun getSetsForStressAccumulation(sinceMs: Long): List<StressSetRow>

    /**
     * Paginated workout history for a specific exercise.
     * Returns sessions in reverse-chronological order.
     */
    @Query("""
        SELECT w.id AS workoutId, w.timestamp,
               COALESCE(w.routineName, '') AS routineName,
               COUNT(ws.id) AS setCount,
               SUM(ws.weight * ws.reps) AS totalVolume
        FROM workout_sets ws
        JOIN workouts w ON ws.workoutId = w.id
        WHERE ws.exerciseId = :exerciseId
          AND ws.isCompleted = 1
          AND w.isCompleted = 1 AND w.isArchived = 0
        GROUP BY w.id
        ORDER BY w.timestamp DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getExerciseWorkoutHistory(exerciseId: Long, limit: Int, offset: Int): List<ExerciseWorkoutHistoryRow>

    /** Most recent session summary for an exercise (date + set count + volume). */
    @Query("""
        SELECT w.timestamp, COUNT(ws.id) AS setCount, SUM(ws.weight * ws.reps) AS totalVolume
        FROM workout_sets ws
        JOIN workouts w ON ws.workoutId = w.id
        WHERE ws.exerciseId = :exerciseId
          AND ws.isCompleted = 1
          AND w.isCompleted = 1 AND w.isArchived = 0
        GROUP BY w.id
        ORDER BY w.timestamp DESC
        LIMIT 1
    """)
    suspend fun getExerciseLastPerformed(exerciseId: Long): ExerciseLastPerformedRow?
}
