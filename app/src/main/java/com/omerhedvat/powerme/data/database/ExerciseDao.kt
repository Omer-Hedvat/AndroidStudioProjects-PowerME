package com.omerhedvat.powerme.data.database

import androidx.room.*
import com.omerhedvat.powerme.util.SurgicalValidator
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

    @Transaction
    @Query("""
        SELECT e.* FROM exercises e
        INNER JOIN routine_exercises re ON e.id = re.exerciseId
        WHERE re.routineId = :routineId
        ORDER BY re.`order` ASC
    """)
    fun getExercisesForRoutine(routineId: Long): Flow<List<Exercise>>

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
     * Local fuzzy search — ProjectMap §2. Case-insensitive LIKE with prefix priority.
     * Results starting with [query] rank first; other contains-matches rank second.
     */
    @Query("""
        SELECT * FROM exercises
        WHERE name LIKE '%' || :query || '%'
        ORDER BY
            CASE WHEN name LIKE :query || '%' THEN 0 ELSE 1 END,
            name ASC
        LIMIT 25
    """)
    suspend fun searchExercises(query: String): List<Exercise>
}
