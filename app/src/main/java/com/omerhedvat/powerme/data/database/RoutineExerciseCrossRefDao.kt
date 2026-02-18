package com.omerhedvat.powerme.data.database

import androidx.room.*

@Dao
interface RoutineExerciseCrossRefDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(crossRef: RoutineExerciseCrossRef)

    @Delete
    suspend fun delete(crossRef: RoutineExerciseCrossRef)

    @Query("DELETE FROM routine_exercise_cross_ref WHERE routineId = :routineId")
    suspend fun deleteAllForRoutine(routineId: Long)
}
