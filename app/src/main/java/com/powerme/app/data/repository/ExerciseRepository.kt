package com.powerme.app.data.repository

import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.ExerciseDao
import com.powerme.app.data.database.matchesSearchTokens
import com.powerme.app.data.database.toSearchTokens
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
        val tokens = query.toSearchTokens()
        if (tokens.isEmpty()) return emptyList()
        return exerciseDao.getAllExercisesSync()
            .filter { it.matchesSearchTokens(tokens) }
            .sortedWith(
                compareBy<Exercise> {
                    if (it.name.startsWith(tokens.first(), ignoreCase = true)) 0 else 1
                }.thenBy { it.name }
            )
            .take(25)
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

    suspend fun toggleFavorite(exercise: Exercise) {
        exerciseDao.updateFavorite(exercise.id, !exercise.isFavorite, System.currentTimeMillis())
    }

    suspend fun toggleFunctionalTag(exercise: Exercise) {
        val hasFunctional = exercise.tags.contains("\"functional\"")
        val newTags = if (hasFunctional) removeFunctionalTag(exercise.tags) else addFunctionalTag(exercise.tags)
        exerciseDao.updateTags(exercise.id, newTags)
    }

    private fun addFunctionalTag(tags: String): String {
        return if (tags == "[]") "[\"functional\"]" else tags.dropLast(1) + ",\"functional\"]"
    }

    private fun removeFunctionalTag(tags: String): String {
        val inner = tags.trim().removePrefix("[").removeSuffix("]")
        val parts = inner.split(",").map { it.trim() }.filter { it.isNotEmpty() && it != "\"functional\"" }
        return "[${parts.joinToString(",")}]"
    }
}
