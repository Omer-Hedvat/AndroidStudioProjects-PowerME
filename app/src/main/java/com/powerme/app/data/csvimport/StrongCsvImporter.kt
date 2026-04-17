package com.powerme.app.data.csvimport

import android.content.Context
import android.util.Log
import com.powerme.app.R
import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.ExerciseDao
import com.powerme.app.data.database.ExerciseMuscleGroup
import com.powerme.app.data.database.ExerciseMuscleGroupDao
import com.powerme.app.data.database.ExerciseType
import com.powerme.app.data.database.PowerMeDatabase
import com.powerme.app.data.database.SetType
import com.powerme.app.data.database.Workout
import com.powerme.app.data.database.WorkoutDao
import com.powerme.app.data.database.WorkoutSet
import com.powerme.app.data.database.WorkoutSetDao
import com.powerme.app.data.database.toSearchName
import androidx.room.withTransaction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time importer for Strong app CSV export.
 *
 * Runs once on app startup (guarded by SharedPreferences flag). Reads the bundled CSV from
 * res/raw/strong_export.csv, maps Strong exercise names to PowerME equivalents, then
 * inserts all workouts and sets as completed history.
 *
 * After confirming the import on device, remove strong_export.csv from res/raw/ and
 * optionally remove this class (the guard flag will prevent any re-run).
 */
@Singleton
class StrongCsvImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exerciseDao: ExerciseDao,
    private val exerciseMuscleGroupDao: ExerciseMuscleGroupDao,
    private val workoutDao: WorkoutDao,
    private val workoutSetDao: WorkoutSetDao,
    private val database: PowerMeDatabase
) {

    companion object {
        private const val TAG = "StrongCsvImporter"
        private const val PREFS_NAME = "strong_csv_importer"
        private const val KEY_IMPORTED = "strong_import_completed"

        /**
         * Maps Strong exercise names → PowerME exercise names.
         * Exercises not in this map are looked up verbatim in the DB.
         * If still not found, they are created as custom exercises.
         */
        val STRONG_TO_POWERME: Map<String, String> = mapOf(
            "Arnold Press (Dumbbell)" to "Arnold Press",
            "Bench Press (Barbell)" to "Barbell Flat Bench Press",
            "Bench Press (Dumbbell)" to "Dumbbell Flat Bench Press",
            "Bent Over One Arm Row (Dumbbell)" to "Dumbbell Row",
            "Bicep Curl (Cable)" to "Cable Curl",
            "Bicep Curl (Dumbbell)" to "Dumbbell Curl",
            "Chin Up" to "Chin-Up",
            "Face Pull (Cable)" to "Face Pull",
            "Farmer Carry" to "Farmer's Walk",
            "Front Raise (Dumbbell)" to "Front Raise",
            "Goblet Squat (Kettlebell)" to "Goblet Squat",
            "Hammer Curl (Dumbbell)" to "Hammer Curl",
            "Incline Bench Press (Dumbbell)" to "Incline Dumbbell Bench Press",
            "Incline Row (Dumbbell)" to "Incline Dumbbell Row",
            "Lateral Raise (Cable)" to "Cable Lateral Raise",
            "Lateral Raise (Dumbbell)" to "Lateral Raise",
            "Lunge (Dumbbell)" to "Walking Lunge",
            "Pull Up" to "Pull-Up",
            "Reverse Fly (Cable)" to "Reverse Pec Deck",
            "Reverse Fly (Dumbbell)" to "Rear Delt Fly",
            "Romanian Deadlift (Barbell)" to "Romanian Deadlift (RDL) - BB",
            "Romanian Deadlift (Dumbbell)" to "Romanian Deadlift (RDL) - DB",
            "Seated Overhead Press (Dumbbell)" to "Seated Dumbbell Overhead Press",
            "Seated Row (Cable)" to "Cable Row",
            "Squat (Barbell)" to "Barbell Back Squat",
            "Triceps Extension (Dumbbell)" to "Overhead Tricep Extension",
            "Triceps Pushdown (Cable - Straight Bar)" to "Tricep Pushdown"
        )

        /**
         * Fallback muscle group and equipment for exercises that must be created as custom.
         * Key = Strong name (after failing map lookup and DB lookup).
         */
        private val CUSTOM_EXERCISE_META: Map<String, Pair<String, String>> = mapOf(
            "Sit Up" to Pair("Core", "Bodyweight"),
            "Flat Leg Raise" to Pair("Core", "Bodyweight"),
            "Incline Chest Fly (Dumbbell)" to Pair("Chest", "Dumbbell")
        )
    }

    /**
     * Imports the Strong CSV if not already done.
     *
     * @return true if import ran, false if skipped (already imported or CSV resource missing).
     */
    suspend fun importIfNeeded(): Boolean {
        return withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_IMPORTED, false)) {
                Log.d(TAG, "Strong CSV already imported — skipping")
                return@withContext false
            }

            Log.i(TAG, "Starting Strong CSV import...")

            try {
                val csvText = context.resources.openRawResource(R.raw.strong_export)
                    .bufferedReader()
                    .use { it.readText() }

                val rows = StrongCsvParser.parse(csvText)
                Log.i(TAG, "Parsed ${rows.size} set rows from CSV")

                performImport(rows)

                prefs.edit().putBoolean(KEY_IMPORTED, true).apply()
                Log.i(TAG, "Strong CSV import complete")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Strong CSV import failed", e)
                false
            }
        }
    }

    private suspend fun performImport(rows: List<StrongCsvRow>) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

        // Build exercise name → id lookup from current DB
        val allExercises = exerciseDao.getAllExercisesSync()
        val exerciseIdByName = allExercises.associateBy({ it.name }, { it.id }).toMutableMap()

        // Resolve all unique Strong exercise names to DB IDs (create custom if needed)
        val uniqueStrongNames = rows.map { it.exerciseName }.toSet()
        val strongNameToId = mutableMapOf<String, Long>()

        for (strongName in uniqueStrongNames) {
            val powerMeName = STRONG_TO_POWERME[strongName] ?: strongName
            val existingId = exerciseIdByName[powerMeName]
            if (existingId != null) {
                strongNameToId[strongName] = existingId
            } else {
                // Create as a custom exercise
                val meta = CUSTOM_EXERCISE_META[strongName]
                    ?: Pair("Other", inferEquipmentType(strongName))
                val newExercise = Exercise(
                    name = powerMeName,
                    muscleGroup = meta.first,
                    equipmentType = meta.second,
                    exerciseType = ExerciseType.STRENGTH,
                    isCustom = true,
                    searchName = powerMeName.toSearchName(),
                    updatedAt = System.currentTimeMillis()
                )
                val newId = exerciseDao.insertExercise(newExercise)
                exerciseMuscleGroupDao.insert(
                    ExerciseMuscleGroup(exerciseId = newId, majorGroup = newExercise.muscleGroup, isPrimary = true)
                )
                exerciseIdByName[powerMeName] = newId
                strongNameToId[strongName] = newId
                Log.i(TAG, "Created custom exercise: $powerMeName (id=$newId)")
            }
        }

        // Group rows by workout number
        val rowsByWorkout = rows.groupBy { it.workoutNumber }
        Log.i(TAG, "Inserting ${rowsByWorkout.size} workouts...")

        var totalSetsInserted = 0

        database.withTransaction {
            for ((_, workoutRows) in rowsByWorkout.entries.sortedBy { it.key }) {
                val firstRow = workoutRows.first()

                val startMs = try {
                    dateFormat.parse(firstRow.date)?.time ?: System.currentTimeMillis()
                } catch (e: Exception) {
                    System.currentTimeMillis()
                }
                val durationSeconds = firstRow.durationSeconds
                val endMs = startMs + (durationSeconds * 1000L)

                // Calculate total volume = Σ(weight × reps) for all sets
                val totalVolume = workoutRows.sumOf { it.weightKg * it.reps }

                val workoutId = UUID.randomUUID().toString()
                val workout = Workout(
                    id = workoutId,
                    routineId = null,
                    routineName = firstRow.workoutName,
                    timestamp = startMs,
                    durationSeconds = durationSeconds,
                    totalVolume = totalVolume,
                    notes = workoutRows.firstOrNull { !it.workoutNotes.isNullOrEmpty() }?.workoutNotes,
                    isCompleted = true,
                    startTimeMs = startMs,
                    endTimeMs = endMs,
                    updatedAt = System.currentTimeMillis(),
                    isArchived = false
                )
                workoutDao.insertWorkout(workout)

                // Build WorkoutSet list — track sequential order per exercise
                val sets = mutableListOf<WorkoutSet>()
                val setOrderByExercise = mutableMapOf<String, Int>()

                for (row in workoutRows) {
                    val exerciseId = strongNameToId[row.exerciseName] ?: continue
                    val setType = if (row.setOrder.equals("D", ignoreCase = true)) {
                        SetType.DROP
                    } else {
                        SetType.NORMAL
                    }
                    val orderKey = row.exerciseName
                    val seqOrder = (setOrderByExercise[orderKey] ?: 0) + 1
                    setOrderByExercise[orderKey] = seqOrder

                    val rpeInt = row.rpe?.let {
                        // Strong exports RPE as Double (e.g. 6.0); store as Int
                        it.toInt().coerceIn(1, 10)
                    }

                    sets.add(
                        WorkoutSet(
                            id = UUID.randomUUID().toString(),
                            workoutId = workoutId,
                            exerciseId = exerciseId,
                            setOrder = seqOrder,
                            weight = row.weightKg,
                            reps = row.reps,
                            rpe = rpeInt,
                            setType = setType,
                            setNotes = row.notes,
                            distance = row.distanceMeters?.let { it / 1000.0 },  // meters → km
                            timeSeconds = row.seconds?.toInt(),
                            isCompleted = true
                        )
                    )
                }

                workoutSetDao.insertSets(sets)
                totalSetsInserted += sets.size
            }
        }

        Log.i(TAG, "Import complete: ${rowsByWorkout.size} workouts, $totalSetsInserted sets")
    }

    /**
     * Infers a reasonable equipment type from the exercise name for custom exercise creation.
     */
    private fun inferEquipmentType(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.contains("barbell") -> "Barbell"
            lower.contains("dumbbell") -> "Dumbbell"
            lower.contains("cable") -> "Cable"
            lower.contains("kettlebell") -> "Kettlebell"
            lower.contains("machine") -> "Machine"
            lower.contains("band") -> "Resistance Band"
            else -> "Bodyweight"
        }
    }
}
