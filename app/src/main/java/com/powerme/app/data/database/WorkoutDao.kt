package com.powerme.app.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class WorkoutExerciseNameRow(
    val id: Long,
    val routineId: Long?,
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
    val hasPR: Int = 0
)

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY timestamp DESC")
    fun getAllWorkouts(): Flow<List<Workout>>

    @Query("SELECT * FROM workouts WHERE id = :workoutId")
    suspend fun getWorkoutById(workoutId: Long): Workout?

    @Query("SELECT * FROM workouts WHERE routineId = :routineId ORDER BY timestamp DESC")
    fun getWorkoutsForRoutine(routineId: Long): Flow<List<Workout>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkout(workout: Workout): Long

    @Update
    suspend fun updateWorkout(workout: Workout)

    @Delete
    suspend fun deleteWorkout(workout: Workout)

    @Query("SELECT * FROM workouts WHERE isCompleted = 0 LIMIT 1")
    suspend fun getActiveWorkout(): Workout?

    @Query("DELETE FROM workouts WHERE id = :workoutId")
    suspend fun deleteWorkoutById(workoutId: Long)

    @Query("""
        SELECT w.id, w.routineId, w.timestamp, w.durationSeconds, w.totalVolume,
               w.notes, w.isCompleted, w.startTimeMs, w.endTimeMs, e.name AS exerciseName,
               r.name AS routineName,
               (SELECT COUNT(*) FROM workout_sets ws2 WHERE ws2.workoutId = w.id AND ws2.isCompleted = 1 AND ws2.setType != 'WARMUP') AS setCount,
               (SELECT CASE WHEN EXISTS (
                   SELECT 1 FROM workout_sets ws3
                   WHERE ws3.workoutId = w.id
                     AND ws3.isCompleted = 1
                     AND ws3.setType != 'WARMUP'
                     AND (
                         NOT EXISTS (
                             SELECT 1 FROM workout_sets ws4
                             INNER JOIN workouts w4 ON ws4.workoutId = w4.id
                             WHERE ws4.exerciseId = ws3.exerciseId
                               AND ws4.isCompleted = 1
                               AND w4.isCompleted = 1
                               AND w4.timestamp < w.timestamp
                         )
                         OR
                         (ws3.weight * (1 + ws3.reps / 30.0)) > (
                             SELECT COALESCE(MAX(ws4.weight * (1 + ws4.reps / 30.0)), 0)
                             FROM workout_sets ws4
                             INNER JOIN workouts w4 ON ws4.workoutId = w4.id
                             WHERE ws4.exerciseId = ws3.exerciseId
                               AND ws4.isCompleted = 1
                               AND w4.isCompleted = 1
                               AND w4.timestamp < w.timestamp
                         )
                     )
               ) THEN 1 ELSE 0 END) AS hasPR
        FROM workouts w
        LEFT JOIN (
            SELECT DISTINCT workoutId, exerciseId FROM workout_sets
        ) ws ON ws.workoutId = w.id
        LEFT JOIN exercises e ON ws.exerciseId = e.id
        LEFT JOIN routines r ON r.id = w.routineId
        WHERE w.isCompleted = 1
        ORDER BY w.timestamp DESC
    """)
    fun getAllCompletedWorkoutsWithExerciseNames(): Flow<List<WorkoutExerciseNameRow>>
}
