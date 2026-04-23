package com.powerme.app.data.repository

import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.ExerciseDao
import com.powerme.app.data.database.UserExerciseSynonym
import com.powerme.app.data.database.UserSynonymDao
import com.powerme.app.data.database.toSearchName
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserSynonymRepository @Inject constructor(
    private val dao: UserSynonymDao,
    private val exerciseDao: ExerciseDao
) {

    /**
     * Looks up a canonical [Exercise] for the given user-typed name.
     *
     * The name is normalised via [toSearchName] before the DB lookup.
     * On a hit, [useCount] is incremented and the resolved [Exercise] is returned.
     * Returns null if no synonym mapping exists.
     */
    suspend fun findExercise(rawName: String): Exercise? {
        val normalised = rawName.toSearchName()
        val synonym = dao.findByRawName(normalised) ?: return null
        dao.incrementUseCount(normalised)
        return exerciseDao.getExerciseById(synonym.exerciseId)
    }

    /**
     * Persists a mapping from [rawName] to [exerciseId].
     *
     * The name is normalised via [toSearchName] before storage.
     * If the normalised alias already exists, `REPLACE` updates the mapping.
     */
    suspend fun saveSynonym(rawName: String, exerciseId: Long) {
        val normalised = rawName.toSearchName()
        dao.insertOrReplace(UserExerciseSynonym(rawName = normalised, exerciseId = exerciseId))
    }
}
