package com.omerhedvat.powerme.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WarmupLibraryDao {
    @Query("SELECT * FROM warmup_library ORDER BY `group`, exerciseName")
    fun getAllWarmupExercises(): Flow<List<WarmupLibrary>>

    @Query("SELECT * FROM warmup_library WHERE `group` = :group")
    suspend fun getExercisesByGroup(group: String): List<WarmupLibrary>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: WarmupLibrary): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<WarmupLibrary>)

    @Query("SELECT COUNT(*) FROM warmup_library")
    suspend fun getCount(): Int
}
