package com.powerme.app.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class WorkoutExerciseNameRow(
    val id: String,
    val routineId: String?,
    val timestamp: Long,
    val durationSeconds: Int,
    val totalVolume: Double,
    val notes: String?,
    val isCompleted: Boolean,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val exerciseName: String?,
    val routineName: String?,
    val setCount: Int,
    val hasPR: Int = 0   // always 0 from query; PR detection done in Kotlin
)

/** Lightweight projection used for O(N) application-layer PR detection. */
data class PRDetectionRow(
    val workoutId: String,
    val exerciseId: String,
    val weight: Double,
    val reps: Int,
    val timestamp: Long
)

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts WHERE isArchived = 0 ORDER BY timestamp DESC")
    fun getAllWorkouts(): Flow<List<Workout>>

    @Query("SELECT * FROM workouts WHERE id = :workoutId")
    suspend fun getWorkoutById(workoutId: String): Workout?

    @Query("SELECT * FROM workouts WHERE routineId = :routineId AND isArchived = 0 ORDER BY timestamp DESC")
    fun getWorkoutsForRoutine(routineId: String): Flow<List<Workout>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: Workout)  // returns Unit — caller pre-generates UUID

    @Update
    suspend fun updateWorkout(workout: Workout)

    @Delete
    suspend fun deleteWorkout(workout: Workout)

    @Query("SELECT * FROM workouts WHERE isCompleted = 0 AND isArchived = 0 LIMIT 1")
    suspend fun getActiveWorkout(): Workout?

    // Retained for rehydration cleanup of abandoned (never-completed) sessions only.
    // User-initiated deletes use soft delete (isArchived = true) instead.
    @Query("DELETE FROM workouts WHERE id = :workoutId")
    suspend fun deleteWorkoutById(workoutId: String)

    @Query("UPDATE workouts SET sessionRating = :rating, updatedAt = :updatedAt WHERE id = :workoutId")
    suspend fun updateSessionRating(workoutId: String, rating: Int, updatedAt: Long)

    @Query("""
        WITH set_counts AS (
            SELECT workoutId, COUNT(*) AS cnt
            FROM workout_sets
            WHERE isCompleted = 1 AND setType != 'WARMUP'
            GROUP BY workoutId
        )
        SELECT w.id, w.routineId, w.timestamp, w.durationSeconds, w.totalVolume,
               w.notes, w.isCompleted, w.startTimeMs, w.endTimeMs, e.name AS exerciseName,
               COALESCE(w.routineName, r.name) AS routineName,
               COALESCE(sc.cnt, 0) AS setCount,
               0 AS hasPR
        FROM workouts w
        LEFT JOIN (
            SELECT DISTINCT workoutId, exerciseId FROM workout_sets
        ) ws ON ws.workoutId = w.id
        LEFT JOIN exercises e ON ws.exerciseId = e.id
        LEFT JOIN routines r ON r.id = w.routineId
        LEFT JOIN set_counts sc ON sc.workoutId = w.id
        WHERE w.isCompleted = 1 AND w.isArchived = 0
        ORDER BY w.timestamp DESC
    """)
    fun getAllCompletedWorkoutsWithExerciseNames(): Flow<List<WorkoutExerciseNameRow>>

    /**
     * Fetches all completed non-warmup sets with their workout IDs, exercise IDs, and Epley
     * e1RM inputs. Results are sorted oldest-first so the caller can do a single-pass O(N) scan
     * to determine which workouts contain a PR.
     */
    @Query("""
        SELECT ws.workoutId, ws.exerciseId, ws.weight, ws.reps, w.timestamp
        FROM workout_sets ws
        INNER JOIN workouts w ON ws.workoutId = w.id
        WHERE ws.isCompleted = 1
          AND ws.setType != 'WARMUP'
          AND w.isCompleted = 1
          AND w.isArchived = 0
        ORDER BY w.timestamp ASC
    """)
    fun getAllCompletedSetsForPRDetection(): Flow<List<PRDetectionRow>>

    @Query("""
        SELECT * FROM workouts
        WHERE isCompleted = 1
          AND isArchived = 0
          AND timestamp >= :sinceMs
        ORDER BY timestamp ASC
    """)
    suspend fun getCompletedWorkoutsSince(sinceMs: Long): List<Workout>
}
