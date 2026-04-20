package com.powerme.app.data.csvimport

/**
 * Converts raw CSV data rows into format-agnostic [ParsedWorkoutRow] objects.
 *
 * Pure Kotlin — no Android or Room dependencies. All weight values are returned as-is;
 * unit conversion (lbs → kg) is the responsibility of [CsvImportManager].
 */
object CsvRowParser {

    /**
     * Parses every data line (header is line index 0 and is skipped) into
     * [ParsedWorkoutRow] objects. Lines that cannot be parsed are silently skipped.
     *
     * @param lines All CSV lines including the header at index 0.
     * @param detection Format and header map from [CsvFormatDetector].
     * @param columnMapping Only required for [CsvFormat.GENERIC]; ignored otherwise.
     * @return Parsed rows; may be shorter than [lines] if some rows were invalid.
     */
    fun parseAll(
        lines: List<String>,
        detection: DetectionResult,
        columnMapping: ColumnMapping? = null
    ): List<ParsedWorkoutRow> {
        val result = mutableListOf<ParsedWorkoutRow>()
        for (i in 1 until lines.size) {
            val line = lines[i].trim()
            if (line.isEmpty()) continue
            val fields = CsvFormatDetector.splitLine(line, detection.delimiter)
            val row = when (detection.format) {
                CsvFormat.STRONG -> parseStrongRow(fields, detection.headerMap)
                CsvFormat.HEVY -> parseHevyRow(fields, detection.headerMap)
                CsvFormat.FITBOD -> parseFitBodRows(fields, detection.headerMap)?.let {
                    result.addAll(it); null   // FitBod expansion adds directly
                }
                CsvFormat.JEFIT -> parseJefitRow(fields, detection.headerMap)
                CsvFormat.GENERIC -> columnMapping?.let { parseGenericRow(fields, it) }
            }
            row?.let { result.add(it) }
        }
        return result
    }

    // ── Strong ────────────────────────────────────────────────────────────────

    private fun parseStrongRow(fields: List<String>, h: Map<String, Int>): ParsedWorkoutRow? {
        val setOrder = fields.getOrNull(h["set order"] ?: -1)?.trim() ?: return null
        // Skip Strong's injected rest-timer metadata rows
        if (setOrder.equals("Rest Timer", ignoreCase = true)) return null
        // Need at least a date and exercise name
        val date = fields.getOrNull(h["date"] ?: -1)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val exerciseName = fields.getOrNull(h["exercise name"] ?: -1)?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        return ParsedWorkoutRow(
            date = date,
            workoutName = fields.getOrNull(h["workout name"] ?: -1)?.trim() ?: "Imported Workout",
            exerciseName = exerciseName,
            setOrder = setOrder,
            weight = fields.getOrNull(h["weight (kg)"] ?: -1)?.trim()?.toDoubleOrNull() ?: 0.0,
            reps = fields.getOrNull(h["reps"] ?: -1)?.trim()?.toIntOrNull() ?: 0,
            rpe = fields.getOrNull(h["rpe"] ?: -1)?.trim()?.toDoubleOrNull(),
            durationSeconds = fields.getOrNull(h["duration (sec)"] ?: -1)?.trim()?.toIntOrNull() ?: 0,
            distanceMeters = fields.getOrNull(h["distance (meters)"] ?: -1)?.trim()?.toDoubleOrNull(),
            timeSeconds = fields.getOrNull(h["seconds"] ?: -1)?.trim()?.toDoubleOrNull(),
            notes = fields.getOrNull(h["notes"] ?: -1)?.trim()?.ifEmpty { null },
            workoutNotes = fields.getOrNull(h["workout notes"] ?: -1)?.trim()?.ifEmpty { null },
            isWarmup = false
        )
    }

    // ── Hevy ──────────────────────────────────────────────────────────────────

    private fun parseHevyRow(fields: List<String>, h: Map<String, Int>): ParsedWorkoutRow? {
        val date = fields.getOrNull(h["start_time"] ?: -1)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val exerciseName = fields.getOrNull(h["exercise_title"] ?: -1)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // Hevy set_index is 0-based; convert to 1-based
        val setIndexRaw = fields.getOrNull(h["set_index"] ?: -1)?.trim()?.toIntOrNull()
        val setOrder = if (setIndexRaw != null) (setIndexRaw + 1).toString() else ""

        return ParsedWorkoutRow(
            date = date,
            workoutName = fields.getOrNull(h["title"] ?: -1)?.trim() ?: "Imported Workout",
            exerciseName = exerciseName,
            setOrder = setOrder,
            weight = fields.getOrNull(h["weight_kg"] ?: -1)?.trim()?.toDoubleOrNull() ?: 0.0,
            reps = fields.getOrNull(h["reps"] ?: -1)?.trim()?.toIntOrNull() ?: 0,
            rpe = fields.getOrNull(h["rpe"] ?: -1)?.trim()?.toDoubleOrNull(),
            notes = fields.getOrNull(h["notes"] ?: -1)?.trim()?.ifEmpty { null },
            isWarmup = false
        )
    }

    // ── FitBod ────────────────────────────────────────────────────────────────

    /**
     * FitBod rows may have a "Sets" column > 1, meaning a single row represents N
     * identical sets. This function expands each such row into N [ParsedWorkoutRow]s.
     * Returns null if the row is unparseable.
     */
    private fun parseFitBodRows(fields: List<String>, h: Map<String, Int>): List<ParsedWorkoutRow>? {
        val date = fields.getOrNull(h["date"] ?: -1)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val exerciseName = fields.getOrNull(h["exercise"] ?: -1)?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        val setsCount = fields.getOrNull(h["sets"] ?: -1)?.trim()?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val reps = fields.getOrNull(h["reps"] ?: -1)?.trim()?.toIntOrNull() ?: 0
        // FitBod weight column header varies: "weight(lbs)" or "weight" — try both
        val weightRaw = (fields.getOrNull(h["weight(lbs)"] ?: -1)
            ?: fields.getOrNull(h["weight"] ?: -1))?.trim()?.toDoubleOrNull() ?: 0.0
        val isWarmup = (fields.getOrNull(h["iswarmup"] ?: -1)?.trim() ?: "0").let {
            it == "1" || it.equals("true", ignoreCase = true)
        }
        val duration = fields.getOrNull(h["duration"] ?: -1)?.trim()?.toIntOrNull() ?: 0
        val distance = fields.getOrNull(h["distance"] ?: -1)?.trim()?.toDoubleOrNull()

        return List(setsCount) { idx ->
            ParsedWorkoutRow(
                date = date,
                workoutName = "FitBod Workout",
                exerciseName = exerciseName,
                setOrder = (idx + 1).toString(),
                weight = weightRaw,
                reps = reps,
                durationSeconds = duration,
                distanceMeters = distance,
                isWarmup = isWarmup
            )
        }
    }

    // ── Jefit ─────────────────────────────────────────────────────────────────

    private fun parseJefitRow(fields: List<String>, h: Map<String, Int>): ParsedWorkoutRow? {
        val date = fields.getOrNull(h["log_date"] ?: -1)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val exerciseName = fields.getOrNull(h["ename"] ?: -1)?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        return ParsedWorkoutRow(
            date = date,
            workoutName = "Jefit Workout",
            exerciseName = exerciseName,
            setOrder = fields.getOrNull(h["set_id"] ?: -1)?.trim() ?: "",
            weight = fields.getOrNull(h["weight"] ?: -1)?.trim()?.toDoubleOrNull() ?: 0.0,
            reps = fields.getOrNull(h["reps"] ?: -1)?.trim()?.toIntOrNull() ?: 0
        )
    }

    // ── Generic ───────────────────────────────────────────────────────────────

    private fun parseGenericRow(fields: List<String>, m: ColumnMapping): ParsedWorkoutRow? {
        val date = fields.getOrNull(m.dateColumn)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val exerciseName = fields.getOrNull(m.exerciseColumn)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val repsRaw = fields.getOrNull(m.repsColumn)?.trim()
        if (repsRaw.isNullOrEmpty()) return null

        return ParsedWorkoutRow(
            date = date,
            workoutName = m.workoutNameColumn?.let { fields.getOrNull(it)?.trim()?.ifEmpty { null } } ?: "Imported Workout",
            exerciseName = exerciseName,
            setOrder = m.setOrderColumn?.let { fields.getOrNull(it)?.trim() } ?: "",
            weight = m.weightColumn?.let { fields.getOrNull(it)?.trim()?.toDoubleOrNull() } ?: 0.0,
            reps = repsRaw.toIntOrNull() ?: 0,
            rpe = m.rpeColumn?.let { fields.getOrNull(it)?.trim()?.toDoubleOrNull() },
            durationSeconds = m.durationColumn?.let { fields.getOrNull(it)?.trim()?.toIntOrNull() } ?: 0,
            notes = m.notesColumn?.let { fields.getOrNull(it)?.trim()?.ifEmpty { null } },
            isWarmup = m.isWarmupColumn?.let {
                val v = fields.getOrNull(it)?.trim() ?: "0"
                v == "1" || v.equals("true", ignoreCase = true)
            } ?: false
        )
    }
}
