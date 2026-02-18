package com.omerhedvat.powerme.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSetDao {
    @Query("SELECT * FROM workout_sets WHERE workoutId = :workoutId ORDER BY setOrder ASC")
    fun getSetsForWorkout(workoutId: Long): Flow<List<WorkoutSet>>

    @Query("""
        SELECT * FROM workout_sets
        WHERE workoutId = :workoutId AND exerciseId = :exerciseId
        ORDER BY setOrder ASC
    """)
    fun getSetsForExerciseInWorkout(workoutId: Long, exerciseId: Long): Flow<List<WorkoutSet>>

    @Query("SELECT * FROM workout_sets WHERE id = :setId")
    suspend fun getSetById(setId: Long): WorkoutSet?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(workoutSet: WorkoutSet): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(workoutSets: List<WorkoutSet>)

    @Update
    suspend fun updateSet(workoutSet: WorkoutSet)

    @Delete
    suspend fun deleteSet(workoutSet: WorkoutSet)

    @Query("DELETE FROM workout_sets WHERE workoutId = :workoutId")
    suspend fun deleteSetsForWorkout(workoutId: Long)

    @Query("""
        SELECT ws.* FROM workout_sets ws
        INNER JOIN workouts w ON ws.workoutId = w.id
        WHERE ws.exerciseId = :exerciseId
        AND w.timestamp < :currentTimestamp
        AND w.timestamp IS NOT NULL
        ORDER BY w.timestamp DESC, ws.setOrder ASC
        LIMIT 10
    """)
    suspend fun getPreviousSessionSets(exerciseId: Long, currentTimestamp: Long): List<WorkoutSet>
}
