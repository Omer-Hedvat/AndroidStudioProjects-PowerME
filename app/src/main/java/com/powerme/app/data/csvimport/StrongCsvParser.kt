package com.powerme.app.data.csvimport

/**
 * Pure Kotlin parser for Strong app CSV exports. No Android or Room dependencies —
 * this class can be unit-tested on the JVM without instrumentation.
 *
 * Format: Semicolon-delimited, all fields quoted.
 * Header: "Workout #";"Date";"Workout Name";"Duration (sec)";"Exercise Name";
 *         "Set Order";"Weight (kg)";"Reps";"RPE";"Distance (meters)";"Seconds";
 *         "Notes";"Workout Notes"
 *
 * Special rows:
 *  - Set Order == "Rest Timer" → skipped (Strong rest timer metadata rows)
 *  - Set Order == "D"          → DROP set
 *  - Numeric Set Order         → NORMAL set
 */
data class StrongCsvRow(
    val workoutNumber: Int,
    val date: String,               // "yyyy-MM-dd HH:mm:ss" literal string
    val workoutName: String,
    val durationSeconds: Int,
    val exerciseName: String,
    val setOrder: String,           // numeric string or "D"
    val weightKg: Double,
    val reps: Int,
    val rpe: Double?,               // nullable — Strong exports e.g. "6.0" or ""
    val distanceMeters: Double?,    // nullable
    val seconds: Double?,           // nullable
    val notes: String?,
    val workoutNotes: String?
)

object StrongCsvParser {

    // Column indices for the Strong CSV format
    private const val COL_WORKOUT_NUMBER = 0
    private const val COL_DATE = 1
    private const val COL_WORKOUT_NAME = 2
    private const val COL_DURATION_SEC = 3
    private const val COL_EXERCISE_NAME = 4
    private const val COL_SET_ORDER = 5
    private const val COL_WEIGHT_KG = 6
    private const val COL_REPS = 7
    private const val COL_RPE = 8
    private const val COL_DISTANCE_M = 9
    private const val COL_SECONDS = 10
    private const val COL_NOTES = 11
    private const val COL_WORKOUT_NOTES = 12
    private const val COLUMN_COUNT = 13

    /**
     * Parses a Strong CSV text string into a list of [StrongCsvRow].
     * Skips the header row and any "Rest Timer" rows.
     *
     * @param csvText Full CSV text content.
     * @return Parsed rows, excluding Rest Timer metadata rows.
     */
    fun parse(csvText: String): List<StrongCsvRow> {
        val lines = csvText.lines()
        if (lines.isEmpty()) return emptyList()

        val result = mutableListOf<StrongCsvRow>()

        // Skip header (line index 0)
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue

            val fields = splitSemicolonFields(line)
            if (fields.size < COLUMN_COUNT) continue

            val setOrder = fields[COL_SET_ORDER]

            // Skip Rest Timer metadata rows inserted by Strong between sets
            if (setOrder.equals("Rest Timer", ignoreCase = true)) continue

            val workoutNumber = fields[COL_WORKOUT_NUMBER].toIntOrNull() ?: continue

            result.add(
                StrongCsvRow(
                    workoutNumber = workoutNumber,
                    date = fields[COL_DATE],
                    workoutName = fields[COL_WORKOUT_NAME],
                    durationSeconds = fields[COL_DURATION_SEC].toIntOrNull() ?: 0,
                    exerciseName = fields[COL_EXERCISE_NAME],
                    setOrder = setOrder,
                    weightKg = fields[COL_WEIGHT_KG].toDoubleOrNull() ?: 0.0,
                    reps = fields[COL_REPS].toIntOrNull() ?: 0,
                    rpe = fields[COL_RPE].toDoubleOrNull(),
                    distanceMeters = fields[COL_DISTANCE_M].toDoubleOrNull(),
                    seconds = fields[COL_SECONDS].toDoubleOrNull(),
                    notes = fields[COL_NOTES].ifEmpty { null },
                    workoutNotes = fields[COL_WORKOUT_NOTES].ifEmpty { null }
                )
            )
        }

        return result
    }

    /**
     * Splits a semicolon-delimited line where each field is quoted.
     * Strips surrounding double-quotes from each field.
     *
     * Example: `"1";"2024-01-15 08:30:00";"Leg Day"` → ["1", "2024-01-15 08:30:00", "Leg Day"]
     */
    internal fun splitSemicolonFields(line: String): List<String> {
        return line.split(";").map { field ->
            field.trim().removeSurrounding("\"")
        }
    }
}
