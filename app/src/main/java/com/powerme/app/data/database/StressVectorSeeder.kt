package com.powerme.app.data.database

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "StressVectorSeeder"
private const val PREFS_NAME = "stress_vector_seeder"
private const val KEY_SEEDED_VERSION = "seeded_version"

/**
 * Seeds [ExerciseStressVector] rows for the top 30 exercises on first install
 * and whenever [ExerciseStressVectorSeedData.SEED_VERSION] is bumped.
 *
 * Must be called after [MasterExerciseSeeder.seedIfNeeded] because it resolves
 * exercise names to their auto-generated IDs from the exercises table.
 */
@Singleton
class StressVectorSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exerciseDao: ExerciseDao,
    private val stressVectorDao: ExerciseStressVectorDao
) {

    suspend fun seedIfNeeded() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val seededVersion = prefs.getString(KEY_SEEDED_VERSION, null)
        if (seededVersion == ExerciseStressVectorSeedData.SEED_VERSION) {
            Log.d(TAG, "Stress vectors already seeded at version $seededVersion — skipping.")
            return
        }
        performSeed()
        prefs.edit { putString(KEY_SEEDED_VERSION, ExerciseStressVectorSeedData.SEED_VERSION) }
        Log.i(TAG, "Stress vectors seeded successfully (version ${ExerciseStressVectorSeedData.SEED_VERSION}).")
    }

    private suspend fun performSeed() {
        val nameToId = exerciseDao.getAllExercisesSync().associateBy({ it.name }, { it.id })

        val vectors = mutableListOf<ExerciseStressVector>()
        var skipped = 0

        for ((exerciseName, regions) in ExerciseStressVectorSeedData.vectors) {
            val exerciseId = nameToId[exerciseName]
            if (exerciseId == null) {
                Log.w(TAG, "Exercise not found in DB, skipping stress vectors: '$exerciseName'")
                skipped++
                continue
            }
            for ((region, coefficient) in regions) {
                vectors += ExerciseStressVector(
                    exerciseId = exerciseId,
                    bodyRegion = region.name,
                    stressCoefficient = coefficient
                )
            }
        }

        stressVectorDao.deleteAll()
        stressVectorDao.insertAll(vectors)
        Log.i(TAG, "Inserted ${vectors.size} stress vectors ($skipped exercises skipped).")
    }
}
