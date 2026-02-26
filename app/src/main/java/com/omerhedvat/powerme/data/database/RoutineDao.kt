package com.omerhedvat.powerme.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class RoutineExerciseNameRow(
    val id: Long,
    val name: String,
    val lastPerformed: Long?,
    val isCustom: Boolean,
    val isArchived: Boolean,
    val exerciseName: String?
)

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY lastPerformed DESC")
    fun getAllRoutines(): Flow<List<Routine>>

    @Query("SELECT * FROM routines WHERE id = :routineId")
    suspend fun getRoutineById(routineId: Long): Routine?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: Routine): Long

    @Update
    suspend fun updateRoutine(routine: Routine)

    @Delete
    suspend fun deleteRoutine(routine: Routine)

    @Query("UPDATE routines SET lastPerformed = :timestamp WHERE id = :routineId")
    suspend fun updateLastPerformed(routineId: Long, timestamp: Long)

    @Query("DELETE FROM routines WHERE id = :routineId")
    suspend fun deleteRoutineById(routineId: Long)

    @Query("""
        SELECT r.id, r.name, r.lastPerformed, r.isCustom, r.isArchived,
               e.name AS exerciseName
        FROM routines r
        LEFT JOIN routine_exercises re ON re.routineId = r.id
        LEFT JOIN exercises e ON e.id = re.exerciseId
        WHERE r.isArchived = 0
        ORDER BY r.lastPerformed DESC
    """)
    fun getAllActiveRoutinesWithExerciseNames(): Flow<List<RoutineExerciseNameRow>>

    @Query("""
        SELECT r.id, r.name, r.lastPerformed, r.isCustom, r.isArchived,
               e.name AS exerciseName
        FROM routines r
        LEFT JOIN routine_exercises re ON re.routineId = r.id
        LEFT JOIN exercises e ON e.id = re.exerciseId
        WHERE r.isArchived = 1
        ORDER BY r.lastPerformed DESC
    """)
    fun getAllArchivedRoutinesWithExerciseNames(): Flow<List<RoutineExerciseNameRow>>
}
