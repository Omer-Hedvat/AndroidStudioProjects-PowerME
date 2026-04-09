package com.powerme.app.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

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
        ORDER BY ws.exerciseId, ws.setOrder
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
}
