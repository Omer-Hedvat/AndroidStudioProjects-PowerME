package com.powerme.app.data.database

import androidx.room.*
import com.powerme.app.util.SurgicalValidator
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name ASC")
    fun getAllExercises(): Flow<List<Exercise>>

    @Query("SELECT * FROM exercises WHERE id = :exerciseId")
    suspend fun getExerciseById(exerciseId: Long): Exercise?

    @Query("SELECT * FROM exercises WHERE muscleGroup = :muscleGroup ORDER BY name ASC")
    fun getExercisesByMuscleGroup(muscleGroup: String): Flow<List<Exercise>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(exercise: Exercise): Long

    @Update
    suspend fun updateExercise(exercise: Exercise)

    @Delete
    suspend fun deleteExercise(exercise: Exercise)

    // Synchronous methods for seeder
    @Query("SELECT * FROM exercises ORDER BY name ASC")
    suspend fun getAllExercisesSync(): List<Exercise>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun getExerciseCountSync(): Int

    @Query("SELECT * FROM exercises WHERE isCustom = 1 ORDER BY name ASC")
    suspend fun getCustomExercisesSync(): List<Exercise>

    @Query("SELECT * FROM exercises WHERE isFavorite = 1 ORDER BY name ASC")
    suspend fun getFavoriteExercisesSync(): List<Exercise>

    @Query("SELECT * FROM exercises WHERE youtubeVideoId IS NOT NULL ORDER BY name ASC")
    suspend fun getExercisesWithVideoSync(): List<Exercise>

    @Query("DELETE FROM exercises")
    suspend fun deleteAllExercises()

    @Query(SurgicalValidator.MIGRATION_SQL)
    suspend fun clearLeakedMetricNotes()

    /**
     * Local fuzzy search — ProjectMap §2. Searches pre-normalized searchName column.
     * [normalizedQuery] must already be lowercase with hyphens/spaces/parens stripped.
     * Results starting with [normalizedQuery] rank first; other contains-matches rank second.
     */
    @Query("""
        SELECT * FROM exercises
        WHERE searchName LIKE '%' || :normalizedQuery || '%'
        ORDER BY
            CASE WHEN searchName LIKE :normalizedQuery || '%' THEN 0 ELSE 1 END,
            name ASC
        LIMIT 25
    """)
    suspend fun searchExercises(normalizedQuery: String): List<Exercise>

    @Query("SELECT * FROM exercises WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<Exercise>

    @Query("UPDATE exercises SET restDurationSeconds = :seconds WHERE id = :exerciseId")
    suspend fun updateRestDuration(exerciseId: Long, seconds: Int)

    @Query("SELECT DISTINCT muscleGroup FROM exercises ORDER BY muscleGroup ASC")
    suspend fun getDistinctMuscleGroups(): List<String>

    @Query("SELECT DISTINCT equipmentType FROM exercises ORDER BY equipmentType ASC")
    suspend fun getDistinctEquipmentTypes(): List<String>
}
