package com.powerme.app.data.csvimport

import org.junit.Assert.*
import org.junit.Test

class CsvRowParserTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private val strongHeader = "\"Workout #\";\"Date\";\"Workout Name\";\"Duration (sec)\";\"Exercise Name\";\"Set Order\";\"Weight (kg)\";\"Reps\";\"RPE\";\"Distance (meters)\";\"Seconds\";\"Notes\";\"Workout Notes\""

    private fun strongRow(
        num: Int = 1,
        date: String = "2024-09-17 20:46:39",
        workoutName: String = "Full Body",
        duration: Int = 3600,
        exercise: String = "Pull Up",
        setOrder: String = "1",
        weight: Double = 0.0,
        reps: Int = 5,
        rpe: String = "",
        distanceM: String = "",
        seconds: String = "",
        notes: String = "",
        workoutNotes: String = ""
    ) = listOf(num, date, workoutName, duration, exercise, setOrder, weight, reps, rpe, distanceM, seconds, notes, workoutNotes)
        .joinToString(";") { "\"$it\"" }

    private fun strongLines(vararg rows: String) = listOf(strongHeader) + rows.toList()

    private val hevyHeader = "id,start_time,end_time,name,description,exercise_title,superset_id,exercise_notes,set_index,set_type,weight_kg,reps,distance_km,duration_seconds,rpe"

    private fun hevyRow(
        startTime: String = "2024-09-17T20:46:39",
        name: String = "Chest Day",
        exerciseTitle: String = "Bench Press",
        setIndex: Int = 0,
        weightKg: Double = 80.0,
        reps: Int = 8,
        rpe: String = "",
        notes: String = ""
    ) = ",,,$name,,$exerciseTitle,,,${setIndex},,${weightKg},${reps},,,$rpe"
        .let { ",$startTime,,$name,,$exerciseTitle,,,${setIndex},,${weightKg},${reps},,,${rpe}" }

    private fun hevyLines(vararg rows: String) = listOf(hevyHeader) + rows.toList()

    private val fitbodHeader = "Date,Exercise,Sets,Reps,Weight(lbs),Distance,Duration,IsWarmup,bodyweight"
    private fun fitbodLines(vararg rows: String) = listOf(fitbodHeader) + rows.toList()

    private val jefitHeader = "log_date,e_id,ename,weight,reps,set_id"
    private fun jefitLines(vararg rows: String) = listOf(jefitHeader) + rows.toList()

    // ── Strong ────────────────────────────────────────────────────────────────

    @Test
    fun `Strong - parses normal row`() {
        val detection = CsvFormatDetector.detect(strongHeader)
        val lines = strongLines(strongRow(exercise = "Squat", weight = 100.0, reps = 5, rpe = "8.0"))
        val rows = CsvRowParser.parseAll(lines, detection)
        assertEquals(1, rows.size)
        val r = rows[0]
        assertEquals("Squat", r.exerciseName)
        assertEquals(100.0, r.weight, 0.001)
        assertEquals(5, r.reps)
        assertEquals(8.0, r.rpe!!, 0.001)
        assertFalse(r.isWarmup)
    }

    @Test
    fun `Strong - skips Rest Timer rows`() {
        val detection = CsvFormatDetector.detect(strongHeader)
        val lines = strongLines(
            strongRow(exercise = "Bench Press", setOrder = "1"),
            strongRow(exercise = "Bench Press", setOrder = "Rest Timer"),
            strongRow(exercise = "Bench Press", setOrder = "2")
        )
        val rows = CsvRowParser.parseAll(lines, detection)
        assertEquals(2, rows.size)
        assertTrue(rows.none { it.setOrder.equals("Rest Timer", ignoreCase = true) })
    }

    @Test
    fun `Strong - preserves D drop-set marker`() {
        val detection = CsvFormatDetector.detect(strongHeader)
        val lines = strongLines(strongRow(exercise = "Lat Pulldown", setOrder = "D", weight = 40.0, reps = 12))
        val rows = CsvRowParser.parseAll(lines, detection)
        assertEquals(1, rows.size)
        assertEquals("D", rows[0].setOrder)
    }

    @Test
    fun `Strong - empty RPE returns null`() {
        val detection = CsvFormatDetector.detect(strongHeader)
        val lines = strongLines(strongRow(rpe = ""))
        val rows = CsvRowParser.parseAll(lines, detection)
        assertNull(rows[0].rpe)
    }

    @Test
    fun `Strong - workout name populated`() {
        val detection = CsvFormatDetector.detect(strongHeader)
        val lines = strongLines(strongRow(workoutName = "Leg Day"))
        val rows = CsvRowParser.parseAll(lines, detection)
        assertEquals("Leg Day", rows[0].workoutName)
    }

    @Test
    fun `Strong - duration populated on row`() {
        val detection = CsvFormatDetector.detect(strongHeader)
        val lines = strongLines(strongRow(duration = 4200))
        val rows = CsvRowParser.parseAll(lines, detection)
        assertEquals(4200, rows[0].durationSeconds)
    }

    @Test
    fun `Strong - skips row with empty exercise name`() {
        val detection = CsvFormatDetector.detect(strongHeader)
        val lines = strongLines(strongRow(exercise = ""))
        val rows = CsvRowParser.parseAll(lines, detection)
        assertEquals(0, rows.size)
    }

    // ── Hevy ──────────────────────────────────────────────────────────────────

    @Test
    fun `Hevy - parses standard row`() {
        val detection = CsvFormatDetector.detect(hevyHeader)
        // id, start_time, end_time, name, description, exercise_title, superset_id, exercise_notes,
        // set_index, set_type, weight_kg, reps, distance_km, duration_seconds, rpe
        val line = ",2024-09-17T20:46:39,,Chest Day,,Bench Press,,,0,,80.0,8,,,7.5"
        val rows = CsvRowParser.parseAll(hevyLines(line), detection)
        assertEquals(1, rows.size)
        val r = rows[0]
        assertEquals("Bench Press", r.exerciseName)
        assertEquals(80.0, r.weight, 0.001)
        assertEquals(8, r.reps)
        assertEquals(7.5, r.rpe!!, 0.001)
    }

    @Test
    fun `Hevy - converts 0-based set_index to 1-based setOrder`() {
        val detection = CsvFormatDetector.detect(hevyHeader)
        val line = ",2024-09-17T20:46:39,,Chest Day,,Bench Press,,,0,,80.0,8,,,"
        val rows = CsvRowParser.parseAll(hevyLines(line), detection)
        assertEquals("1", rows[0].setOrder)
    }

    @Test
    fun `Hevy - set_index 2 becomes setOrder 3`() {
        val detection = CsvFormatDetector.detect(hevyHeader)
        val line = ",2024-09-17T20:46:39,,Chest Day,,Bench Press,,,2,,80.0,8,,,"
        val rows = CsvRowParser.parseAll(hevyLines(line), detection)
        assertEquals("3", rows[0].setOrder)
    }

    @Test
    fun `Hevy - date string preserved verbatim`() {
        val detection = CsvFormatDetector.detect(hevyHeader)
        val line = ",2024-11-05T09:15:00,,Chest Day,,Squat,,,0,,100.0,5,,,"
        val rows = CsvRowParser.parseAll(hevyLines(line), detection)
        assertEquals("2024-11-05T09:15:00", rows[0].date)
    }

    @Test
    fun `Hevy - skips row with empty exercise_title`() {
        val detection = CsvFormatDetector.detect(hevyHeader)
        val line = ",2024-09-17T20:46:39,,Chest Day,,,,,0,,80.0,8,,,"
        val rows = CsvRowParser.parseAll(hevyLines(line), detection)
        assertEquals(0, rows.size)
    }

    // ── FitBod ────────────────────────────────────────────────────────────────

    @Test
    fun `FitBod - parses standard row`() {
        val detection = CsvFormatDetector.detect(fitbodHeader)
        val line = "2024-09-17,Barbell Squat,1,5,135.0,,,0,"
        val rows = CsvRowParser.parseAll(fitbodLines(line), detection)
        assertEquals(1, rows.size)
        val r = rows[0]
        assertEquals("Barbell Squat", r.exerciseName)
        assertEquals(135.0, r.weight, 0.001)
        assertEquals(5, r.reps)
        assertFalse(r.isWarmup)
    }

    @Test
    fun `FitBod - expands multi-set row into N rows`() {
        val detection = CsvFormatDetector.detect(fitbodHeader)
        val line = "2024-09-17,Deadlift,3,5,225.0,,,0,"
        val rows = CsvRowParser.parseAll(fitbodLines(line), detection)
        assertEquals(3, rows.size)
        rows.forEachIndexed { idx, r ->
            assertEquals("Deadlift", r.exerciseName)
            assertEquals(225.0, r.weight, 0.001)
            assertEquals(5, r.reps)
            assertEquals((idx + 1).toString(), r.setOrder)
        }
    }

    @Test
    fun `FitBod - marks warmup rows`() {
        val detection = CsvFormatDetector.detect(fitbodHeader)
        val line = "2024-09-17,Bench Press,1,10,95.0,,,1,"
        val rows = CsvRowParser.parseAll(fitbodLines(line), detection)
        assertEquals(1, rows.size)
        assertTrue(rows[0].isWarmup)
    }

    @Test
    fun `FitBod - weight stored as-is (lbs conversion is manager job)`() {
        val detection = CsvFormatDetector.detect(fitbodHeader)
        val line = "2024-09-17,Curl,1,12,30.0,,,0,"
        val rows = CsvRowParser.parseAll(fitbodLines(line), detection)
        assertEquals(30.0, rows[0].weight, 0.001)
    }

    @Test
    fun `FitBod - workout name defaults to FitBod Workout`() {
        val detection = CsvFormatDetector.detect(fitbodHeader)
        val line = "2024-09-17,Squat,1,5,135.0,,,0,"
        val rows = CsvRowParser.parseAll(fitbodLines(line), detection)
        assertEquals("FitBod Workout", rows[0].workoutName)
    }

    // ── Jefit ─────────────────────────────────────────────────────────────────

    @Test
    fun `Jefit - parses standard row`() {
        val detection = CsvFormatDetector.detect(jefitHeader)
        val line = "2024-09-17,42,Barbell Squat,100.0,8,1"
        val rows = CsvRowParser.parseAll(jefitLines(line), detection)
        assertEquals(1, rows.size)
        val r = rows[0]
        assertEquals("Barbell Squat", r.exerciseName)
        assertEquals(100.0, r.weight, 0.001)
        assertEquals(8, r.reps)
        assertEquals("1", r.setOrder)
    }

    @Test
    fun `Jefit - workout name defaults to Jefit Workout`() {
        val detection = CsvFormatDetector.detect(jefitHeader)
        val line = "2024-09-17,42,Squat,60.0,5,1"
        val rows = CsvRowParser.parseAll(jefitLines(line), detection)
        assertEquals("Jefit Workout", rows[0].workoutName)
    }

    @Test
    fun `Jefit - skips row with empty exercise name`() {
        val detection = CsvFormatDetector.detect(jefitHeader)
        val line = "2024-09-17,42,,60.0,5,1"
        val rows = CsvRowParser.parseAll(jefitLines(line), detection)
        assertEquals(0, rows.size)
    }

    // ── Generic ───────────────────────────────────────────────────────────────

    @Test
    fun `Generic - maps columns from ColumnMapping`() {
        val header = "date,name,exercise,reps,weight"
        val detection = CsvFormatDetector.detect(header)
        val mapping = ColumnMapping(dateColumn = 0, exerciseColumn = 2, repsColumn = 3, weightColumn = 4, workoutNameColumn = 1)
        val lines = listOf(header, "2024-09-17,My Workout,Squat,5,100.0")
        val rows = CsvRowParser.parseAll(lines, detection, mapping)
        assertEquals(1, rows.size)
        val r = rows[0]
        assertEquals("2024-09-17", r.date)
        assertEquals("My Workout", r.workoutName)
        assertEquals("Squat", r.exerciseName)
        assertEquals(5, r.reps)
        assertEquals(100.0, r.weight, 0.001)
    }

    @Test
    fun `Generic - skips row when required exercise column is empty`() {
        val header = "date,exercise,reps"
        val detection = CsvFormatDetector.detect(header)
        val mapping = ColumnMapping(dateColumn = 0, exerciseColumn = 1, repsColumn = 2)
        val lines = listOf(header, "2024-09-17,,5")
        val rows = CsvRowParser.parseAll(lines, detection, mapping)
        assertEquals(0, rows.size)
    }

    @Test
    fun `Generic - skips row when required reps column is empty`() {
        val header = "date,exercise,reps"
        val detection = CsvFormatDetector.detect(header)
        val mapping = ColumnMapping(dateColumn = 0, exerciseColumn = 1, repsColumn = 2)
        val lines = listOf(header, "2024-09-17,Squat,")
        val rows = CsvRowParser.parseAll(lines, detection, mapping)
        assertEquals(0, rows.size)
    }

    @Test
    fun `Generic - weight defaults to 0 when column not mapped`() {
        val header = "date,exercise,reps"
        val detection = CsvFormatDetector.detect(header)
        val mapping = ColumnMapping(dateColumn = 0, exerciseColumn = 1, repsColumn = 2)
        val lines = listOf(header, "2024-09-17,Bodyweight Squat,10")
        val rows = CsvRowParser.parseAll(lines, detection, mapping)
        assertEquals(0.0, rows[0].weight, 0.001)
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `blank lines in data section are skipped`() {
        val detection = CsvFormatDetector.detect(strongHeader)
        val lines = listOf(strongHeader, "", strongRow(), "")
        val rows = CsvRowParser.parseAll(lines, detection)
        assertEquals(1, rows.size)
    }

    @Test
    fun `FitBod - sets count of 0 treated as 1 row`() {
        val detection = CsvFormatDetector.detect(fitbodHeader)
        val line = "2024-09-17,Squat,0,5,135.0,,,0,"
        val rows = CsvRowParser.parseAll(fitbodLines(line), detection)
        assertEquals(1, rows.size)  // coerceAtLeast(1)
    }
}
