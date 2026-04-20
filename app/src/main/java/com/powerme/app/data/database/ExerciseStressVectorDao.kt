package com.powerme.app.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ExerciseStressVectorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(vectors: List<ExerciseStressVector>)

    @Query("SELECT * FROM exercise_stress_vectors WHERE exerciseId = :exerciseId")
    suspend fun getForExercise(exerciseId: Long): List<ExerciseStressVector>

    @Query("SELECT * FROM exercise_stress_vectors WHERE exerciseId IN (:exerciseIds)")
    suspend fun getForExercises(exerciseIds: List<Long>): List<ExerciseStressVector>

    @Query("SELECT * FROM exercise_stress_vectors")
    suspend fun getAll(): List<ExerciseStressVector>

    @Query("DELETE FROM exercise_stress_vectors")
    suspend fun deleteAll()
}
