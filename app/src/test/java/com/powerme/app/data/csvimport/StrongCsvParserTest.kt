package com.powerme.app.data.csvimport

import org.junit.Assert.*
import org.junit.Test

class StrongCsvParserTest {

    private val header = "\"Workout #\";\"Date\";\"Workout Name\";\"Duration (sec)\";\"Exercise Name\";\"Set Order\";\"Weight (kg)\";\"Reps\";\"RPE\";\"Distance (meters)\";\"Seconds\";\"Notes\";\"Workout Notes\""

    /**
     * Builds a properly-quoted semicolon-delimited CSV row from individual field values.
     * Each field is wrapped in double-quotes, matching the actual Strong CSV format.
     */
    private fun row(
        workoutNumber: Int = 1,
        date: String = "2024-09-17 20:46:39",
        workoutName: String = "Full Body",
        duration: Int = 3827,
        exerciseName: String = "Pull Up",
        setOrder: String = "1",
        weight: Double = 0.0,
        reps: Int = 5,
        rpe: String = "",
        distanceM: String = "",
        seconds: String = "",
        notes: String = "",
        workoutNotes: String = ""
    ): String = listOf(
        workoutNumber, date, workoutName, duration, exerciseName,
        setOrder, weight, reps, rpe, distanceM, seconds, notes, workoutNotes
    ).joinToString(";") { "\"$it\"" }

    private fun csv(vararg dataRows: String) = (listOf(header) + dataRows.toList()).joinToString("\n")

    // ─── Basic parsing ────────────────────────────────────────────────────────

    @Test
    fun `parses a normal set row correctly`() {
        val input = csv(row())
        val rows = StrongCsvParser.parse(input)
        assertEquals(1, rows.size)
        val r = rows[0]
        assertEquals(1, r.workoutNumber)
        assertEquals("2024-09-17 20:46:39", r.date)
        assertEquals("Full Body", r.workoutName)
        assertEquals(3827, r.durationSeconds)
        assertEquals("Pull Up", r.exerciseName)
        assertEquals("1", r.setOrder)
        assertEquals(0.0, r.weightKg, 0.0001)
        assertEquals(5, r.reps)
        assertNull(r.rpe)
        assertNull(r.distanceMeters)
        assertNull(r.seconds)
        assertNull(r.notes)
        assertNull(r.workoutNotes)
    }

    @Test
    fun `parses weight and reps correctly`() {
        val input = csv(row(exerciseName = "Bench Press", setOrder = "2", weight = 22.5, reps = 10, rpe = "7.0"))
        val r = StrongCsvParser.parse(input)[0]
        assertEquals(22.5, r.weightKg, 0.0001)
        assertEquals(10, r.reps)
        assertEquals(7.0, r.rpe!!, 0.0001)
    }

    @Test
    fun `parses RPE as double`() {
        val input = csv(row(exerciseName = "Squat", weight = 60.0, rpe = "8.5"))
        val r = StrongCsvParser.parse(input)[0]
        assertEquals(8.5, r.rpe!!, 0.0001)
    }

    @Test
    fun `parses notes and workout notes`() {
        val input = csv(row(notes = "felt great", workoutNotes = "chest day"))
        val r = StrongCsvParser.parse(input)[0]
        assertEquals("felt great", r.notes)
        assertEquals("chest day", r.workoutNotes)
    }

    @Test
    fun `parses distance and seconds`() {
        val input = csv(row(exerciseName = "Run", distanceM = "1000.0", seconds = "120.0"))
        val r = StrongCsvParser.parse(input)[0]
        assertEquals(1000.0, r.distanceMeters!!, 0.0001)
        assertEquals(120.0, r.seconds!!, 0.0001)
    }

    // ─── Rest Timer rows ──────────────────────────────────────────────────────

    @Test
    fun `skips Rest Timer rows`() {
        val input = csv(
            row(exerciseName = "Bench Press", setOrder = "1", weight = 60.0, reps = 10),
            row(exerciseName = "Bench Press", setOrder = "Rest Timer", weight = 0.0, reps = 0, seconds = "90.0"),
            row(exerciseName = "Bench Press", setOrder = "2", weight = 60.0, reps = 10)
        )
        val rows = StrongCsvParser.parse(input)
        assertEquals(2, rows.size)
        assertTrue(rows.none { it.setOrder.equals("Rest Timer", ignoreCase = true) })
    }

    @Test
    fun `Rest Timer case-insensitive match`() {
        val input = csv(row(setOrder = "rest timer"))
        assertEquals(0, StrongCsvParser.parse(input).size)
    }

    // ─── Drop sets ────────────────────────────────────────────────────────────

    @Test
    fun `parses drop set row with setOrder D`() {
        val input = csv(row(exerciseName = "Lateral Raise", setOrder = "D", weight = 10.0, reps = 5))
        val rows = StrongCsvParser.parse(input)
        assertEquals(1, rows.size)
        assertEquals("D", rows[0].setOrder)
    }

    // ─── Empty / null fields ──────────────────────────────────────────────────

    @Test
    fun `empty RPE parses to null`() {
        val input = csv(row(rpe = ""))
        assertNull(StrongCsvParser.parse(input)[0].rpe)
    }

    @Test
    fun `empty distance and seconds parse to null`() {
        val input = csv(row(distanceM = "", seconds = ""))
        val r = StrongCsvParser.parse(input)[0]
        assertNull(r.distanceMeters)
        assertNull(r.seconds)
    }

    @Test
    fun `empty notes parse to null`() {
        val input = csv(row(notes = "", workoutNotes = ""))
        val r = StrongCsvParser.parse(input)[0]
        assertNull(r.notes)
        assertNull(r.workoutNotes)
    }

    // ─── Multiple workouts ────────────────────────────────────────────────────

    @Test
    fun `parses rows from multiple workouts`() {
        val input = csv(
            row(workoutNumber = 1, date = "2024-09-17 20:46:39", workoutName = "Full Body", exerciseName = "Pull Up"),
            row(workoutNumber = 2, date = "2024-09-22 21:00:57", workoutName = "Leg Day", exerciseName = "Squat", weight = 60.0, reps = 8)
        )
        val rows = StrongCsvParser.parse(input)
        assertEquals(2, rows.size)
        assertEquals(1, rows[0].workoutNumber)
        assertEquals(2, rows[1].workoutNumber)
        assertEquals("Full Body", rows[0].workoutName)
        assertEquals("Leg Day", rows[1].workoutName)
    }

    // ─── Edge cases ───────────────────────────────────────────────────────────

    @Test
    fun `empty CSV returns empty list`() {
        assertEquals(emptyList<StrongCsvRow>(), StrongCsvParser.parse(""))
    }

    @Test
    fun `header-only CSV returns empty list`() {
        assertEquals(emptyList<StrongCsvRow>(), StrongCsvParser.parse(header))
    }

    @Test
    fun `skips blank lines`() {
        val input = csv("", row(), "")
        val rows = StrongCsvParser.parse(input)
        assertEquals(1, rows.size)
    }

    // ─── Field splitting ──────────────────────────────────────────────────────

    @Test
    fun `splitSemicolonFields strips quotes`() {
        val fields = StrongCsvParser.splitSemicolonFields("\"hello\";\"world\";\"foo bar\"")
        assertEquals(listOf("hello", "world", "foo bar"), fields)
    }

    @Test
    fun `splitSemicolonFields handles empty quoted fields`() {
        val fields = StrongCsvParser.splitSemicolonFields("\"1\";\"\";\"3\"")
        assertEquals(listOf("1", "", "3"), fields)
    }

    // ─── Exercise name mapping completeness ───────────────────────────────────

    @Test
    fun `mapping table covers expected Strong exercise names`() {
        val expectedStrongNames = setOf(
            "Arnold Press (Dumbbell)",
            "Bench Press (Barbell)",
            "Bench Press (Dumbbell)",
            "Bent Over One Arm Row (Dumbbell)",
            "Bicep Curl (Cable)",
            "Bicep Curl (Dumbbell)",
            "Chin Up",
            "Face Pull (Cable)",
            "Farmer Carry",
            "Front Raise (Dumbbell)",
            "Goblet Squat (Kettlebell)",
            "Hammer Curl (Dumbbell)",
            "Incline Bench Press (Dumbbell)",
            "Incline Row (Dumbbell)",
            "Lateral Raise (Cable)",
            "Lateral Raise (Dumbbell)",
            "Lunge (Dumbbell)",
            "Pull Up",
            "Reverse Fly (Cable)",
            "Reverse Fly (Dumbbell)",
            "Romanian Deadlift (Barbell)",
            "Romanian Deadlift (Dumbbell)",
            "Seated Overhead Press (Dumbbell)",
            "Seated Row (Cable)",
            "Squat (Barbell)",
            "Triceps Extension (Dumbbell)",
            "Triceps Pushdown (Cable - Straight Bar)"
        )

        val mappedNames = StrongCsvImporter.STRONG_TO_POWERME.keys
        val missing = expectedStrongNames - mappedNames
        assertTrue("Missing mappings for: $missing", missing.isEmpty())
    }

    @Test
    fun `mapping table values are non-empty strings`() {
        StrongCsvImporter.STRONG_TO_POWERME.forEach { (strongName, powerMeName) ->
            assertTrue("Empty PowerME name for $strongName", powerMeName.isNotBlank())
        }
    }
}
