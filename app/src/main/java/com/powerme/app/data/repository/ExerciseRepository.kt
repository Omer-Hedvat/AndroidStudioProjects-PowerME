package com.powerme.app.data.repository

import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.ExerciseDao
import com.powerme.app.data.database.toSearchName
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExerciseRepository @Inject constructor(
    private val exerciseDao: ExerciseDao
) {
    fun getAllExercises(): Flow<List<Exercise>> {
        return exerciseDao.getAllExercises()
    }

    suspend fun getExerciseById(exerciseId: Long): Exercise? {
        return exerciseDao.getExerciseById(exerciseId)
    }

    fun getExercisesByMuscleGroup(muscleGroup: String): Flow<List<Exercise>> {
        return exerciseDao.getExercisesByMuscleGroup(muscleGroup)
    }

    suspend fun insertExercise(exercise: Exercise): Long {
        return exerciseDao.insertExercise(exercise)
    }

    suspend fun updateExercise(exercise: Exercise) {
        exerciseDao.updateExercise(exercise)
    }

    suspend fun deleteExercise(exercise: Exercise) {
        exerciseDao.deleteExercise(exercise)
    }

    suspend fun searchExercises(query: String): List<Exercise> {
        return exerciseDao.searchExercises(query.trim().toSearchName())
    }

    suspend fun getExercisesByIds(ids: List<Long>): List<Exercise> {
        return exerciseDao.getByIds(ids)
    }

    suspend fun getDistinctMuscleGroups(): List<String> {
        return exerciseDao.getDistinctMuscleGroups()
    }

    suspend fun getDistinctEquipmentTypes(): List<String> {
        return exerciseDao.getDistinctEquipmentTypes()
    }
}
