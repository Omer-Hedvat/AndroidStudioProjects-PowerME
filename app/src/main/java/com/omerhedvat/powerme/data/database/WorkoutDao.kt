package com.omerhedvat.powerme.data.database

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
    val exerciseName: String?,
    val routineName: String?,
    val setCount: Int
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
               w.notes, w.isCompleted, e.name AS exerciseName,
               r.name AS routineName,
               (SELECT COUNT(*) FROM workout_sets ws2 WHERE ws2.workoutId = w.id AND ws2.isCompleted = 1) AS setCount
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
