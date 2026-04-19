package com.powerme.app.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ── Per-exercise detail projections ─────────────────────────────────────────

data class ExerciseSessionVolume(
    val workoutId: String,
    val timestamp: Long,
    val totalVolume: Double,
    val setCount: Int
)

data class ExerciseSessionMaxWeight(
    val timestamp: Long,
    val maxWeight: Double
)

data class ExerciseSessionBestSet(
    val timestamp: Long,
    val weight: Double,
    val reps: Int,
    val volume: Double
)

data class ExercisePRSet(
    val weight: Double,
    val reps: Int,
    val timestamp: Long,
    val setVolume: Double
)

data class ExerciseRpeTrendPoint(
    val timestamp: Long,
    val avgRpe: Double,
    val avgWeight: Double,
    val setCount: Int
)

// ── Existing projection ─────────────────────────────────────────────────────

data class WorkoutSetWithExercise(
    val id: String,
    val workoutId: String,
    val exerciseId: Long,
    val setOrder: Int,
    val weight: Double,
    val reps: Int,
    val rpe: Int?,
    val setType: SetType,
    val setNotes: String?,
    val supersetGroupId: String?,
    val isCompleted: Boolean,
    val exerciseName: String,
    val muscleGroup: String?,
    val equipmentType: String?,
    val exerciseType: ExerciseType,
    val distance: Double?,
    val timeSeconds: Int?
)

@Dao
interface WorkoutSetDao {

    @Query("""
        SELECT ws.id, ws.workoutId, ws.exerciseId, ws.setOrder, ws.weight, ws.reps,
               ws.rpe, ws.setType, ws.setNotes, ws.supersetGroupId, ws.isCompleted,
               e.name AS exerciseName, e.muscleGroup, e.equipmentType, e.exerciseType,
               ws.distance, ws.timeSeconds
        FROM workout_sets ws
        JOIN exercises e ON ws.exerciseId = e.id
        WHERE ws.workoutId = :workoutId AND ws.isCompleted = 1
        ORDER BY ws.rowid ASC, ws.setOrder ASC
    """)
    suspend fun getSetsWithExerciseForWorkout(workoutId: String): List<WorkoutSetWithExercise>

    @Query("SELECT * FROM workout_sets WHERE workoutId = :workoutId ORDER BY setOrder ASC")
    fun getSetsForWorkout(workoutId: String): Flow<List<WorkoutSet>>

    @Query("""
        SELECT * FROM workout_sets
        WHERE workoutId = :workoutId AND exerciseId = :exerciseId
        ORDER BY setOrder ASC
    """)
    fun getSetsForExerciseInWorkout(workoutId: String, exerciseId: Long): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM workout_sets WHERE id = :setId")
    suspend fun getSetById(setId: String): WorkoutSet?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(workoutSet: WorkoutSet)    // returns Unit — caller pre-generates UUID

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(workoutSets: List<WorkoutSet>)

    @Update
    suspend fun updateSet(workoutSet: WorkoutSet)

    @Delete
    suspend fun deleteSet(workoutSet: WorkoutSet)

    @Query("DELETE FROM workout_sets WHERE workoutId = :workoutId")
    suspend fun deleteSetsForWorkout(workoutId: String)

    @Query("UPDATE workout_sets SET weight = :weight, reps = :reps WHERE id = :id")
    suspend fun updateWeightReps(id: String, weight: Double, reps: Int)

    @Query("UPDATE workout_sets SET isCompleted = :completed WHERE id = :id")
    suspend fun updateSetCompleted(id: String, completed: Boolean)

    @Query("UPDATE workout_sets SET setType = :setType WHERE id = :id")
    suspend fun updateSetType(id: String, setType: SetType)

    @Query("UPDATE workout_sets SET rpe = :rpe WHERE id = :id")
    suspend fun updateRpe(id: String, rpe: Int?)

    @Query("UPDATE workout_sets SET weight = :weight, timeSeconds = :timeSeconds, rpe = :rpe, isCompleted = :completed WHERE id = :id")
    suspend fun updateTimedSet(id: String, weight: Double, timeSeconds: Int, rpe: Int?, completed: Boolean)

    @Query("UPDATE workout_sets SET distance = :distance, timeSeconds = :timeSeconds, rpe = :rpe, isCompleted = :completed WHERE id = :id")
    suspend fun updateCardioSet(id: String, distance: Double, timeSeconds: Int, rpe: Int?, completed: Boolean)

    @Query("DELETE FROM workout_sets WHERE id = :setId")
    suspend fun deleteSetById(setId: String)

    @Query("DELETE FROM workout_sets WHERE workoutId = :workoutId AND isCompleted = 0")
    suspend fun deleteIncompleteSetsByWorkout(workoutId: String)

    @Query("""
        SELECT ws.* FROM workout_sets ws
        INNER JOIN workouts w ON ws.workoutId = w.id
        WHERE ws.exerciseId = :exerciseId
        AND w.timestamp < :currentTimestamp
        AND w.timestamp IS NOT NULL
        AND w.isCompleted = 1
        AND w.isArchived = 0
        ORDER BY w.timestamp DESC, ws.setOrder ASC
        LIMIT 10
    """)
    suspend fun getPreviousSessionSets(exerciseId: Long, currentTimestamp: Long): List<WorkoutSet>

    @Query("""
        SELECT ws.* FROM workout_sets ws
        INNER JOIN workouts w ON ws.workoutId = w.id
        WHERE ws.exerciseId = :exerciseId
          AND w.isCompleted = 1 AND w.isArchived = 0
          AND w.timestamp < :beforeTimestamp
          AND ws.isCompleted = 1 AND ws.setType != 'WARMUP'
          AND w.id = (
              SELECT w2.id FROM workouts w2
              INNER JOIN workout_sets ws2 ON ws2.workoutId = w2.id
              WHERE ws2.exerciseId = :exerciseId
                AND w2.isCompleted = 1 AND w2.isArchived = 0
                AND w2.timestamp < :beforeTimestamp
              ORDER BY w2.timestamp DESC LIMIT 1
          )
        ORDER BY ws.setOrder ASC
    """)
    suspend fun getPreviousSessionCompletedSets(exerciseId: Long, beforeTimestamp: Long): List<WorkoutSet>

    @Query("""
        SELECT MAX(ws.weight * (1 + CAST(ws.reps AS REAL) / 30.0))
        FROM workout_sets ws
        INNER JOIN workouts w ON ws.workoutId = w.id
        WHERE ws.exerciseId = :exerciseId
          AND ws.isCompleted = 1 AND ws.setType != 'WARMUP'
          AND w.isCompleted = 1 AND w.isArchived = 0
          AND w.timestamp < :beforeTimestamp
    """)
    suspend fun getHistoricalBestE1RM(exerciseId: Long, beforeTimestamp: Long): Double?

    /** Per-session volume for a specific exercise. Used for the volume trend chart in ExerciseDetailScreen. */
    @Query("""
        SELECT w.id AS workoutId, w.timestamp,
               SUM(ws.weight * ws.reps) AS totalVolume,
               COUNT(ws.id) AS setCount
        FROM workout_sets ws
        JOIN workouts w ON ws.workoutId = w.id
        WHERE ws.exerciseId = :exerciseId
          AND ws.isCompleted = 1 AND ws.setType != 'WARMUP'
          AND w.isCompleted = 1 AND w.isArchived = 0
          AND w.timestamp >= :sinceMs
        GROUP BY w.id
        ORDER BY w.timestamp ASC
    """)
    suspend fun getExerciseSessionVolume(exerciseId: Long, sinceMs: Long): List<ExerciseSessionVolume>

    /** Per-session max weight for a specific exercise. Used for the max weight trend chart. */
    @Query("""
        SELECT w.timestamp, MAX(ws.weight) AS maxWeight
        FROM workout_sets ws
        JOIN workouts w ON ws.workoutId = w.id
        WHERE ws.exerciseId = :exerciseId
          AND ws.isCompleted = 1 AND ws.setType != 'WARMUP'
          AND w.isCompleted = 1 AND w.isArchived = 0
          AND ws.weight > 0
          AND w.timestamp >= :sinceMs
        GROUP BY w.id
        ORDER BY w.timestamp ASC
    """)
    suspend fun getExerciseSessionMaxWeight(exerciseId: Long, sinceMs: Long): List<ExerciseSessionMaxWeight>

    /** Per-session best set (highest weight × reps product) for an exercise. */
    @Query("""
        SELECT w.timestamp, ws.weight, ws.reps, MAX(ws.weight * ws.reps) AS volume
        FROM workout_sets ws
        JOIN workouts w ON ws.workoutId = w.id
        WHERE ws.exerciseId = :exerciseId
          AND ws.isCompleted = 1 AND ws.setType != 'WARMUP'
          AND w.isCompleted = 1 AND w.isArchived = 0
          AND ws.weight > 0 AND ws.reps > 0
          AND w.timestamp >= :sinceMs
        GROUP BY w.id
        ORDER BY w.timestamp ASC
    """)
    suspend fun getExerciseSessionBestSet(exerciseId: Long, sinceMs: Long): List<ExerciseSessionBestSet>

    /** All completed non-warmup sets for an exercise across all time — used for PR computation in Kotlin. */
    @Query("""
        SELECT ws.weight, ws.reps, w.timestamp, ws.weight * ws.reps AS setVolume
        FROM workout_sets ws
        JOIN workouts w ON ws.workoutId = w.id
        WHERE ws.exerciseId = :exerciseId
          AND ws.isCompleted = 1 AND ws.setType != 'WARMUP'
          AND w.isCompleted = 1 AND w.isArchived = 0
          AND ws.weight > 0 AND ws.reps > 0
        ORDER BY w.timestamp ASC
    """)
    suspend fun getAllSetsForExercisePRs(exerciseId: Long): List<ExercisePRSet>

    /** Total number of distinct sessions this exercise has appeared in. */
    @Query("""
        SELECT COUNT(DISTINCT w.id)
        FROM workout_sets ws
        JOIN workouts w ON ws.workoutId = w.id
        WHERE ws.exerciseId = :exerciseId
          AND ws.isCompleted = 1
          AND w.isCompleted = 1 AND w.isArchived = 0
    """)
    suspend fun getExerciseSessionCount(exerciseId: Long): Int

    /** Per-session average RPE trend for an exercise. Only returns sessions where RPE was logged. */
    @Query("""
        SELECT w.timestamp, AVG(CAST(ws.rpe AS REAL)) AS avgRpe,
               AVG(ws.weight) AS avgWeight, COUNT(ws.id) AS setCount
        FROM workout_sets ws
        JOIN workouts w ON ws.workoutId = w.id
        WHERE ws.exerciseId = :exerciseId
          AND ws.isCompleted = 1 AND ws.setType != 'WARMUP'
          AND ws.rpe IS NOT NULL AND ws.rpe > 0
          AND w.isCompleted = 1 AND w.isArchived = 0
          AND w.timestamp >= :sinceMs
        GROUP BY w.id
        ORDER BY w.timestamp ASC
    """)
    suspend fun getExerciseRpeTrend(exerciseId: Long, sinceMs: Long): List<ExerciseRpeTrendPoint>
}
