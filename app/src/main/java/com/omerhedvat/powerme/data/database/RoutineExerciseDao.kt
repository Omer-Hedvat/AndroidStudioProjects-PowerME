package com.omerhedvat.powerme.data.database

import androidx.room.*

@Dao
interface RoutineExerciseDao {
    @Query("SELECT * FROM routine_exercises WHERE routineId = :routineId ORDER BY `order` ASC")
    suspend fun getForRoutine(routineId: Long): List<RoutineExercise>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(routineExercise: RoutineExercise): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(routineExercises: List<RoutineExercise>)

    @Delete
    suspend fun delete(routineExercise: RoutineExercise)

    @Query("DELETE FROM routine_exercises WHERE routineId = :routineId AND exerciseId = :exerciseId")
    suspend fun deleteByRoutineAndExercise(routineId: Long, exerciseId: Long)

    @Query("UPDATE routine_exercises SET `order` = :order WHERE id = :id")
    suspend fun updateOrder(id: Long, order: Int)

    @Query("SELECT stickyNote FROM routine_exercises WHERE routineId = :routineId AND exerciseId = :exerciseId")
    suspend fun getStickyNote(routineId: Long, exerciseId: Long): String?

    @Query("UPDATE routine_exercises SET stickyNote = :note WHERE routineId = :routineId AND exerciseId = :exerciseId")
    suspend fun updateStickyNote(routineId: Long, exerciseId: Long, note: String?)
}
