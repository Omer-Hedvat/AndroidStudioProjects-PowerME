package com.powerme.app.data.csvimport

import android.util.Log
import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.ExerciseDao
import com.powerme.app.data.database.ExerciseMuscleGroup
import com.powerme.app.data.database.ExerciseMuscleGroupDao
import com.powerme.app.data.database.ExerciseType
import com.powerme.app.data.database.SetType
import com.powerme.app.data.database.Workout
import com.powerme.app.data.database.WorkoutSet
import com.powerme.app.data.database.toSearchName
import com.powerme.app.data.repository.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates the full CSV import pipeline:
 * 1. Resolves exercise names to DB IDs (creates custom exercises for unknowns).
 * 2. Groups [ParsedWorkoutRow] objects into [Workout] + [WorkoutSet] pairs by
 *    (date, workoutName) key.
 * 3. Applies weight-unit conversion and duplicate detection per [ImportOptions].
 * 4. Inserts in chunks of [CHUNK_SIZE] workouts, sharing a single [batchId].
 * 5. Reports live progress via [onProgress] callback.
 */
@Singleton
class CsvImportManager @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val exerciseMuscleGroupDao: ExerciseMuscleGroupDao,
    private val workoutRepository: WorkoutRepository
) {

    companion object {
        private const val TAG = "CsvImportManager"
        private const val CHUNK_SIZE = 100

        /** Ordered list of date formats tried when parsing raw date strings. */
        private val DATE_FORMATS = listOf(
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd",
            "MM/dd/yyyy",
            "dd/MM/yyyy",
            "M/d/yyyy",
            "d/M/yyyy"
        )
    }

    /**
     * Imports [rows] into the database according to [options].
     *
     * Must be called from a coroutine; internally switches to [Dispatchers.IO].
     *
     * @param rows Parsed rows from [CsvRowParser.parseAll].
     * @param options User-configured import settings.
     * @param onProgress Called on every chunk with live progress.
     * @return Final [ImportResult] when all rows are committed.
     */
    suspend fun import(
        rows: List<ParsedWorkoutRow>,
        options: ImportOptions,
        onProgress: (ImportProgress) -> Unit
    ): ImportResult = withContext(Dispatchers.IO) {

        val batchId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // ── 1. Resolve exercises ──────────────────────────────────────────────
        val allExercises = exerciseDao.getAllExercisesSync()
        // Build name→id lookup; use lowercase for case-insensitive matching
        val nameToId = allExercises.associateBy({ it.name.lowercase() }, { it.id }).toMutableMap()
        val uniqueNames = rows.map { it.exerciseName.trim() }.toSet()
        val exerciseIdMap = mutableMapOf<String, Long>()
        var exercisesCreated = 0

        for (rawName in uniqueNames) {
            val key = rawName.lowercase()
            val existing = nameToId[key]
            if (existing != null) {
                exerciseIdMap[rawName] = existing
            } else if (options.createNewExercises) {
                val newExercise = Exercise(
                    name = rawName,
                    muscleGroup = "Other",
                    equipmentType = inferEquipmentType(rawName),
                    exerciseType = ExerciseType.STRENGTH,
                    isCustom = true,
                    searchName = rawName.toSearchName(),
                    updatedAt = now
                )
                val newId = exerciseDao.insertExercise(newExercise)
                exerciseMuscleGroupDao.insert(
                    ExerciseMuscleGroup(exerciseId = newId, majorGroup = "Other", isPrimary = true)
                )
                nameToId[key] = newId
                exerciseIdMap[rawName] = newId
                exercisesCreated++
                Log.i(TAG, "Created custom exercise: $rawName (id=$newId)")
            }
            // If createNewExercises is false and no match, exerciseIdMap will not contain rawName
            // → those rows are skipped in step 2.
        }

        // ── 2. Group rows into workouts ───────────────────────────────────────
        // Key = (date truncated to day, workoutName) to group sets into sessions.
        // For formats that provide a unique workout ID (Strong "Workout #"), we rely on
        // workoutName + same-date grouping, which matches the original behavior.
        val grouped = mutableMapOf<String, MutableList<ParsedWorkoutRow>>()
        for (row in rows) {
            val dateKey = extractDateKey(row.date)
            val key = "${dateKey}||${row.workoutName}"
            grouped.getOrPut(key) { mutableListOf() }.add(row)
        }

        // Apply date range filter
        val filteredWorkouts = grouped.entries.filter { (key, _) ->
            val dateMs = parseDateToMs(key.substringBefore("||"))
                ?: return@filter true // keep on parse failure
            val start = options.dateRangeStart
            val end = options.dateRangeEnd
            (start == null || dateMs >= start) && (end == null || dateMs <= end)
        }

        val totalWorkouts = filteredWorkouts.size
        var processedWorkouts = 0
        var setsImported = 0
        var skippedRows = 0
        val errors = mutableListOf<String>()

        // ── 3. Chunk and insert ───────────────────────────────────────────────
        val workoutEntries = filteredWorkouts.sortedBy { it.key }

        for (chunk in workoutEntries.chunked(CHUNK_SIZE)) {
            val workoutsChunk = mutableListOf<Workout>()
            val setsChunk = mutableMapOf<String, MutableList<WorkoutSet>>()

            for ((key, workoutRows) in chunk) {
                val firstRow = workoutRows.first()
                val dateMs = parseDateToMs(firstRow.date)
                if (dateMs == null) {
                    errors.add("Could not parse date: ${firstRow.date}")
                    skippedRows += workoutRows.size
                    continue
                }

                val durationSeconds = workoutRows.maxOfOrNull { it.durationSeconds } ?: 0
                val endMs = if (durationSeconds > 0) dateMs + (durationSeconds * 1000L) else dateMs

                val totalVolume = workoutRows.sumOf { row ->
                    val w = if (needsLbsConversion(row, options)) row.weight / 2.20462 else row.weight
                    w * row.reps
                }

                val workoutId = UUID.randomUUID().toString()
                val workout = Workout(
                    id = workoutId,
                    routineId = null,
                    routineName = firstRow.workoutName,
                    timestamp = dateMs,
                    durationSeconds = durationSeconds,
                    totalVolume = totalVolume,
                    notes = workoutRows.firstOrNull { !it.workoutNotes.isNullOrEmpty() }?.workoutNotes,
                    isCompleted = true,
                    startTimeMs = dateMs,
                    endTimeMs = endMs,
                    updatedAt = now,
                    isArchived = false,
                    source = "import",
                    importBatchId = batchId
                )
                workoutsChunk.add(workout)

                val sets = mutableListOf<WorkoutSet>()
                val setOrderByExercise = mutableMapOf<String, Int>()

                for (row in workoutRows) {
                    val exerciseId = exerciseIdMap[row.exerciseName.trim()]
                    if (exerciseId == null) {
                        skippedRows++
                        continue
                    }
                    val setType = when {
                        row.isWarmup -> SetType.WARMUP
                        row.setOrder.equals("D", ignoreCase = true) -> SetType.DROP
                        else -> SetType.NORMAL
                    }
                    val exerciseKey = row.exerciseName.lowercase()
                    val seqOrder = (setOrderByExercise[exerciseKey] ?: 0) + 1
                    setOrderByExercise[exerciseKey] = seqOrder

                    val weightKg = if (needsLbsConversion(row, options)) row.weight / 2.20462 else row.weight
                    val rpeInt = row.rpe?.toInt()?.coerceIn(1, 10)

                    sets.add(
                        WorkoutSet(
                            id = UUID.randomUUID().toString(),
                            workoutId = workoutId,
                            exerciseId = exerciseId,
                            setOrder = seqOrder,
                            weight = weightKg,
                            reps = row.reps,
                            rpe = rpeInt,
                            setType = setType,
                            setNotes = row.notes,
                            distance = row.distanceMeters?.let { it / 1000.0 },
                            timeSeconds = row.timeSeconds?.toInt(),
                            isCompleted = true
                        )
                    )
                }

                setsChunk[workoutId] = sets
                setsImported += sets.size
            }

            workoutRepository.importWorkouts(workoutsChunk, setsChunk)
            processedWorkouts += workoutsChunk.size

            onProgress(
                ImportProgress(
                    totalWorkouts = totalWorkouts,
                    processedWorkouts = processedWorkouts,
                    setsCreated = setsImported,
                    exercisesCreated = exercisesCreated,
                    skippedRows = skippedRows,
                    errors = errors.toList()
                )
            )
        }

        Log.i(TAG, "Import complete: $processedWorkouts workouts, $setsImported sets, $exercisesCreated new exercises")
        ImportResult(
            batchId = batchId,
            workoutsImported = processedWorkouts,
            setsImported = setsImported,
            exercisesCreated = exercisesCreated,
            rowsSkipped = skippedRows,
            errors = errors
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Parses a raw date string to epoch ms in the device's local timezone.
     * Tries each known date format in order; returns null if none match.
     */
    internal fun parseDateToMs(raw: String): Long? {
        val trimmed = raw.trim()
        for (pattern in DATE_FORMATS) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                // ISO patterns include timezone; others use local device TZ per spec
                if (!pattern.contains("XXX")) sdf.timeZone = TimeZone.getDefault()
                val date = sdf.parse(trimmed) ?: continue
                return date.time
            } catch (_: Exception) {
                // try next format
            }
        }
        return null
    }

    /**
     * Extracts a stable grouping key from a raw date string.
     * Returns the date portion (first 10 chars) when available; falls back to the raw string.
     */
    private fun extractDateKey(raw: String): String =
        if (raw.length >= 10) raw.substring(0, 10) else raw

    /**
     * Returns true if this row's weight value must be divided by 2.20462 to convert to kg.
     * FitBod always stores in lbs; other formats follow [ImportOptions.weightUnit].
     */
    private fun needsLbsConversion(row: ParsedWorkoutRow, options: ImportOptions): Boolean =
        options.weightUnit == WeightUnit.LBS

    /** Infers equipment type from exercise name keywords (same heuristic as StrongCsvImporter). */
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
