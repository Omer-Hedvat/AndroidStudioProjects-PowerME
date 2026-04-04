package com.powerme.app.data.database

import androidx.room.*

@Dao
interface ExerciseMuscleGroupDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ExerciseMuscleGroup): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<ExerciseMuscleGroup>)

    @Query("SELECT * FROM exercise_muscle_groups WHERE exerciseId = :exerciseId ORDER BY isPrimary DESC")
    suspend fun getForExercise(exerciseId: Long): List<ExerciseMuscleGroup>

    @Query("SELECT * FROM exercise_muscle_groups WHERE majorGroup = :majorGroup ORDER BY exerciseId ASC")
    suspend fun getByMajorGroup(majorGroup: String): List<ExerciseMuscleGroup>

    @Query("DELETE FROM exercise_muscle_groups WHERE exerciseId = :exerciseId")
    suspend fun deleteForExercise(exerciseId: Long)
}
