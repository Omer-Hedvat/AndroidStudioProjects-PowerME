package com.powerme.app.data.csvimport

/** Identifies which app a CSV was exported from. */
enum class CsvFormat { STRONG, HEVY, FITBOD, JEFIT, GENERIC }

/** How the importer handles a set row that already exists in the DB. */
enum class DuplicateHandling { SKIP, IMPORT_ANYWAY }

/** Weight unit of the source file. */
enum class WeightUnit { KG, LBS }

/** Result of auto-detecting the CSV format from its header line. */
data class DetectionResult(
    val format: CsvFormat,
    val headers: List<String>,       // raw headers (original casing)
    val delimiter: Char,
    val headerMap: Map<String, Int>  // lowercase header name → column index
)

/**
 * Format-agnostic intermediate representation of a single set row,
 * produced by [CsvRowParser] and consumed by [CsvImportManager].
 */
data class ParsedWorkoutRow(
    val date: String,               // raw date string — parsed to epoch ms by CsvImportManager
    val workoutName: String,        // groups rows into workout sessions
    val exerciseName: String,
    val setOrder: String,           // numeric string, "D" for drop set, or "" if unknown
    val weight: Double,             // raw value — unit determined by format / ImportOptions
    val reps: Int,
    val rpe: Double? = null,
    val durationSeconds: Int = 0,
    val distanceMeters: Double? = null,
    val timeSeconds: Double? = null,
    val notes: String? = null,
    val workoutNotes: String? = null,
    val isWarmup: Boolean = false
)

/**
 * User-provided column assignments for [CsvFormat.GENERIC] files.
 * Required columns: date, exercise, reps. All others optional.
 */
data class ColumnMapping(
    val dateColumn: Int,
    val exerciseColumn: Int,
    val repsColumn: Int,
    val weightColumn: Int? = null,
    val setOrderColumn: Int? = null,
    val rpeColumn: Int? = null,
    val notesColumn: Int? = null,
    val durationColumn: Int? = null,
    val workoutNameColumn: Int? = null,
    val isWarmupColumn: Int? = null
)

/** User-configurable options presented before the import runs. */
data class ImportOptions(
    val weightUnit: WeightUnit = WeightUnit.KG,
    val duplicateHandling: DuplicateHandling = DuplicateHandling.SKIP,
    val createNewExercises: Boolean = true,
    val dateRangeStart: Long? = null,
    val dateRangeEnd: Long? = null
)

/** Live progress snapshot emitted by [CsvImportManager.import]. */
data class ImportProgress(
    val totalWorkouts: Int,
    val processedWorkouts: Int,
    val setsCreated: Int,
    val exercisesCreated: Int,
    val skippedRows: Int,
    val errors: List<String>
) {
    val fraction: Float
        get() = if (totalWorkouts == 0) 0f else processedWorkouts.toFloat() / totalWorkouts
}

/** Final result returned by [CsvImportManager.import] on completion. */
data class ImportResult(
    val batchId: String,
    val workoutsImported: Int,
    val setsImported: Int,
    val exercisesCreated: Int,
    val rowsSkipped: Int,
    val errors: List<String>
)
