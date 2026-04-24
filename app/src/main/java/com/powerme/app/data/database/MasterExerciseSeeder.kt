package com.powerme.app.data.database

import android.content.Context
import timber.log.Timber
import com.powerme.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
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
    private val exerciseDao: ExerciseDao,
    private val exerciseMuscleGroupDao: ExerciseMuscleGroupDao
) {
    companion object {
        private const val TAG = "MasterExerciseSeeder"
        private const val PREFS_NAME = "master_exercise_seeder"
        private const val KEY_SEEDED_VERSION = "seeded_version"
        private const val CURRENT_VERSION = "2.3.3"  // add 15 yoga-based STRETCH entries (Warrior I/II/III, Triangle, Bridge, Cobra, Supine Spinal Twist, Lizard, Reclined Butterfly, Standing Forward Fold, Low Lunge, Happy Baby, Legs Up the Wall, Seated Forward Fold, Wide-Legged Forward Fold)
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
                Timber.d("Exercises already seeded (version $CURRENT_VERSION)")
                return@withContext false
            }

            Timber.i("Seeding exercises (previous version: $seededVersion, current: $CURRENT_VERSION)")
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
            Timber.i("Force reseeding exercises...")
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

            Timber.d("Loaded ${masterData.exercises.size} exercises from JSON (version ${masterData.version})")

            // Step 2: Get existing exercises
            val existingExercises = exerciseDao.getAllExercisesSync()
            val existingByName = existingExercises.associateBy { it.name }
            val customExercises = existingExercises.filter { it.isCustom }

            Timber.d("Found ${existingExercises.size} existing exercises (${customExercises.size} custom)")

            // Step 3: Process master exercises
            var insertedCount = 0
            var updatedCount = 0
            var skippedCount = 0
            val masterNames = mutableSetOf<String>()

            // Fetch all exerciseIds that already have EMG rows — used to avoid duplicate inserts
            val existingEmgIds = exerciseMuscleGroupDao.getAllExerciseIds().toHashSet()

            // Collect all updates so we can flush them in a single batch (one transaction,
            // one Room Flow invalidation) — prevents the timing window where the ViewModel
            // reads exercises before tags are written.
            val pendingUpdates = mutableListOf<Exercise>()
            val pendingEmgInserts = mutableListOf<ExerciseMuscleGroup>()

            masterData.exercises.forEach { masterExercise ->
                masterNames += masterExercise.name
                val existing = existingByName[masterExercise.name]

                when {
                    existing == null -> {
                        // New exercise - insert immediately (needs its generated ID for EMG row)
                        val newId = exerciseDao.insertExercise(masterExercise.copy(searchName = masterExercise.name.toSearchName()))
                        pendingEmgInserts += ExerciseMuscleGroup(exerciseId = newId, majorGroup = masterExercise.muscleGroup, isPrimary = true)
                        existingEmgIds += newId
                        insertedCount++
                    }
                    existing.isCustom -> {
                        // User's custom exercise with same name - skip to preserve user data
                        skippedCount++
                        Timber.d("Skipping custom exercise: ${existing.name}")
                    }
                    else -> {
                        // Existing master exercise - collect update, preserve user-modified fields
                        val updated = masterExercise.copy(
                            id = existing.id,  // Preserve database ID
                            isFavorite = existing.isFavorite,  // Preserve favorite flag
                            syncId = existing.syncId.ifEmpty { UUID.randomUUID().toString() }, // Preserve syncId (v35)
                            updatedAt = existing.updatedAt, // Preserve updatedAt — avoid Firestore push storm on reseed
                            searchName = masterExercise.name.toSearchName()
                        )
                        pendingUpdates += updated
                        // Ensure EMG row exists for this exercise (may be missing on fresh install or after new seeder versions)
                        if (existing.id !in existingEmgIds) {
                            pendingEmgInserts += ExerciseMuscleGroup(exerciseId = existing.id, majorGroup = masterExercise.muscleGroup, isPrimary = true)
                            existingEmgIds += existing.id
                        }
                        updatedCount++
                    }
                }
            }

            // Flush all updates in one batch — single Room transaction, single Flow invalidation.
            // This eliminates the timing window where the Exercise Library sees exercises with
            // empty tags (tags = "[]") mid-reseed.
            if (pendingUpdates.isNotEmpty()) {
                exerciseDao.updateAll(pendingUpdates)
            }
            if (pendingEmgInserts.isNotEmpty()) {
                exerciseMuscleGroupDao.insertAll(pendingEmgInserts)
                existingEmgIds += pendingEmgInserts.map { it.exerciseId }
            }

            // Delete non-custom exercises removed from the JSON master list
            val staleExercises = existingExercises.filter { !it.isCustom && it.name !in masterNames }
            if (staleExercises.isNotEmpty()) {
                exerciseDao.deleteExercises(staleExercises)
                staleExercises.forEach { Timber.d("Deleted stale exercise: ${it.name}") }
            }
            val deletedCount = staleExercises.size

            // Backfill: ensure every exercise (including custom) has at least a primary EMG row.
            // This repairs upgrade users and any edge cases where EMG rows were missed.
            val allExercisesAfterSeed = exerciseDao.getAllExercisesSync()
            val backfillEntries = allExercisesAfterSeed
                .filter { it.id !in existingEmgIds }
                .map { ExerciseMuscleGroup(exerciseId = it.id, majorGroup = it.muscleGroup, isPrimary = true) }
            if (backfillEntries.isNotEmpty()) {
                exerciseMuscleGroupDao.insertAll(backfillEntries)
                Timber.i("Backfilled ${backfillEntries.size} missing exercise_muscle_groups rows")
            }

            val totalSeeded = insertedCount + updatedCount
            Timber.i("""
                Seeding complete:
                - Inserted: $insertedCount new exercises
                - Updated: $updatedCount existing exercises
                - Skipped: $skippedCount custom exercises
                - Deleted (stale): $deletedCount exercises
                - Total master exercises: ${masterData.exercises.size}
                - Total database exercises: ${exerciseDao.getExerciseCountSync()}
            """.trimIndent())

            return totalSeeded

        } catch (e: Exception) {
            Timber.e(e, "Failed to seed exercises")
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

            Timber.i("Cleared all non-custom exercises, preserved ${customExercises.size} custom exercises")
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
