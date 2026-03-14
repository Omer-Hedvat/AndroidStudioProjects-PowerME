package com.omerhedvat.powerme.data.database

import androidx.room.*

data class RoutineExerciseWithName(
    val exerciseId: Long,
    val exerciseName: String,
    val muscleGroup: String,
    val equipmentType: String,
    val sets: Int,
    val reps: Int,
    val order: Int,
    val supersetGroupId: String?
)

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

    @Query("""
        SELECT re.exerciseId, e.name AS exerciseName, e.muscleGroup, e.equipmentType,
               re.sets, re.reps, re.`order`, re.supersetGroupId
        FROM routine_exercises re
        JOIN exercises e ON re.exerciseId = e.id
        WHERE re.routineId = :routineId
        ORDER BY re.`order` ASC
    """)
    suspend fun getExercisesWithNamesForRoutine(routineId: Long): List<RoutineExerciseWithName>

    @Query("DELETE FROM routine_exercises WHERE routineId = :routineId")
    suspend fun deleteAllForRoutine(routineId: Long)

    @Query("UPDATE routine_exercises SET sets = :sets WHERE routineId = :routineId AND exerciseId = :exerciseId")
    suspend fun updateSets(routineId: Long, exerciseId: Long, sets: Int)

    @Query("UPDATE routine_exercises SET reps = :reps, defaultWeight = :weight WHERE routineId = :routineId AND exerciseId = :exerciseId")
    suspend fun updateRepsAndWeight(routineId: Long, exerciseId: Long, reps: Int, weight: String)
}
