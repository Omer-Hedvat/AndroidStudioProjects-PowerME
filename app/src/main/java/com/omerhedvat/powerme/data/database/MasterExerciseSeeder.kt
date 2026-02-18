package com.omerhedvat.powerme.data.database

import android.content.Context
import android.util.Log
import com.omerhedvat.powerme.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Seeds master exercises from JSON resource into the database.
 *
 * Purpose:
 * - Load 150+ curated exercises with YouTube integration on first app launch
 * - Preserve custom user-added exercises across reseeds
 * - Update existing exercises with new YouTube IDs and biomechanical notes
 *
 * Usage:
 * ```kotlin
 * masterExerciseSeeder.seedIfNeeded()  // Automatic on app launch
 * masterExerciseSeeder.forceSeed()     // Manual reseed (preserves custom exercises)
 * ```
 *
 * Safety:
 * - Preserves all custom exercises (isCustom = true)
 * - Preserves favorite flags
 * - Can be safely re-run without duplicates
 */
@Singleton
class MasterExerciseSeeder @Inject constructor(
    private val context: Context,
    private val exerciseDao: ExerciseDao
) {
    companion object {
        private const val TAG = "MasterExerciseSeeder"
        private const val PREFS_NAME = "master_exercise_seeder"
        private const val KEY_SEEDED_VERSION = "seeded_version"
        private const val CURRENT_VERSION = "1.0"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Seeds exercises if not already seeded or version changed.
     *
     * @return true if seeding occurred, false if skipped
     */
    suspend fun seedIfNeeded(): Boolean {
        return withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val seededVersion = prefs.getString(KEY_SEEDED_VERSION, null)

            if (seededVersion == CURRENT_VERSION) {
                Log.d(TAG, "Exercises already seeded (version $CURRENT_VERSION)")
                return@withContext false
            }

            Log.i(TAG, "Seeding exercises (previous version: $seededVersion, current: $CURRENT_VERSION)")
            performSeed()

            // Mark as seeded
            prefs.edit().putString(KEY_SEEDED_VERSION, CURRENT_VERSION).apply()
            true
        }
    }

    /**
     * Forces a reseed, preserving custom exercises and favorites.
     *
     * Use for:
     * - Adding new YouTube video IDs
     * - Updating biomechanical notes
     * - Adding new master exercises
     *
     * @return Number of exercises seeded
     */
    suspend fun forceSeed(): Int {
        return withContext(Dispatchers.IO) {
            Log.i(TAG, "Force reseeding exercises...")
            performSeed()
        }
    }

    /**
     * Performs the actual seeding operation.
     *
     * Strategy:
     * 1. Load JSON from res/raw
     * 2. Get all existing exercises
     * 3. Identify custom exercises (preserve)
     * 4. Update existing non-custom exercises
     * 5. Insert new exercises
     *
     * @return Number of exercises seeded
     */
    private suspend fun performSeed(): Int {
        try {
            // Step 1: Parse JSON
            val inputStream = context.resources.openRawResource(R.raw.master_exercises)
            val jsonString = inputStream.bufferedReader().use { it.readText() }
            val masterData = json.decodeFromString<MasterExerciseData>(jsonString)

            Log.d(TAG, "Loaded ${masterData.exercises.size} exercises from JSON (version ${masterData.version})")

            // Step 2: Get existing exercises
            val existingExercises = exerciseDao.getAllExercisesSync()
            val existingByName = existingExercises.associateBy { it.name }
            val customExercises = existingExercises.filter { it.isCustom }

            Log.d(TAG, "Found ${existingExercises.size} existing exercises (${customExercises.size} custom)")

            // Step 3: Process master exercises
            var insertedCount = 0
            var updatedCount = 0
            var skippedCount = 0

            masterData.exercises.forEach { masterExercise ->
                val existing = existingByName[masterExercise.name]

                when {
                    existing == null -> {
                        // New exercise - insert
                        exerciseDao.insertExercise(masterExercise)
                        insertedCount++
                    }
                    existing.isCustom -> {
                        // User's custom exercise with same name - skip to preserve user data
                        skippedCount++
                        Log.d(TAG, "Skipping custom exercise: ${existing.name}")
                    }
                    else -> {
                        // Existing master exercise - update with new data, preserve favorites
                        val updated = masterExercise.copy(
                            id = existing.id,  // Preserve database ID
                            isFavorite = existing.isFavorite  // Preserve favorite flag
                        )
                        exerciseDao.updateExercise(updated)
                        updatedCount++
                    }
                }
            }

            val totalSeeded = insertedCount + updatedCount
            Log.i(TAG, """
                Seeding complete:
                - Inserted: $insertedCount new exercises
                - Updated: $updatedCount existing exercises
                - Skipped: $skippedCount custom exercises
                - Total master exercises: ${masterData.exercises.size}
                - Total database exercises: ${exerciseDao.getExerciseCountSync()}
            """.trimIndent())

            return totalSeeded

        } catch (e: Exception) {
            Log.e(TAG, "Failed to seed exercises", e)
            throw e
        }
    }

    /**
     * Gets seeding statistics.
     */
    suspend fun getStats(): SeedStats {
        return withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val seededVersion = prefs.getString(KEY_SEEDED_VERSION, null)
            val totalExercises = exerciseDao.getExerciseCountSync()
            val customExercises = exerciseDao.getCustomExercisesSync().size
            val favoriteExercises = exerciseDao.getFavoriteExercisesSync().size
            val exercisesWithVideo = exerciseDao.getExercisesWithVideoSync().size

            SeedStats(
                seededVersion = seededVersion,
                totalExercises = totalExercises,
                customExercises = customExercises,
                favoriteExercises = favoriteExercises,
                exercisesWithVideo = exercisesWithVideo
            )
        }
    }

    /**
     * Clears all non-custom exercises (dangerous - for testing only).
     */
    suspend fun clearNonCustomExercises() {
        withContext(Dispatchers.IO) {
            val customExercises = exerciseDao.getCustomExercisesSync()
            exerciseDao.deleteAllExercises()
            customExercises.forEach { exerciseDao.insertExercise(it) }

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().remove(KEY_SEEDED_VERSION).apply()

            Log.i(TAG, "Cleared all non-custom exercises, preserved ${customExercises.size} custom exercises")
        }
    }
}

/**
 * Statistics about seeded exercises.
 */
data class SeedStats(
    val seededVersion: String?,
    val totalExercises: Int,
    val customExercises: Int,
    val favoriteExercises: Int,
    val exercisesWithVideo: Int
)

/**
 * JSON structure for master_exercises.json
 */
@Serializable
data class MasterExerciseData(
    val version: String,
    val lastUpdated: String,
    val totalExercises: Int,
    val exercises: List<Exercise>
)
