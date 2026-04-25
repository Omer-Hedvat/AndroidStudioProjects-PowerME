package com.powerme.app.ui.exercises

import com.powerme.app.data.database.Exercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the Functional tag filter added in v50.
 *
 * Verifies the predicate: `!functional || exercise.tags.contains("\"functional\"")`
 * used in ExercisesViewModel.applyFilters().
 */
class ExerciseTagsFilterTest {

    private fun makeExercise(
        id: Long,
        name: String,
        tags: String = "[]"
    ) = Exercise(
        id = id,
        name = name,
        muscleGroup = "Legs",
        equipmentType = "Barbell",
        tags = tags
    )

    private val allExercises = listOf(
        makeExercise(1, "Barbell Back Squat"),                    // no tags
        makeExercise(2, "Pull-Up", """["functional","gymnastics"]"""),
        makeExercise(3, "Power Snatch", """["functional","olympic"]"""),
        // functional-only exercises also carry "functional" so they appear in the library chip
        makeExercise(4, "Rope Climb", """["functional","functional-only","gymnastics"]"""),
        makeExercise(5, "Kettlebell Swing", """["functional"]"""),
        makeExercise(6, "Deadlift"),                              // no tags
        makeExercise(7, "Rowing (meters)", """["functional","monostructural"]"""),
    )

    /** Simulate the filter predicate from applyFilters(). */
    private fun applyFunctionalFilter(exercises: List<Exercise>, functional: Boolean): List<Exercise> =
        exercises.filter { !functional || it.tags.contains("\"functional\"") }

    @Test
    fun `functional filter off — all exercises returned`() {
        val result = applyFunctionalFilter(allExercises, functional = false)
        assertEquals(allExercises.size, result.size)
    }

    @Test
    fun `functional filter on — only exercises with functional tag returned`() {
        val result = applyFunctionalFilter(allExercises, functional = true)
        // Should include Pull-Up, Power Snatch, Rope Climb (functional-only contains "functional"),
        // Kettlebell Swing, Rowing — but NOT Barbell Back Squat or Deadlift
        assertEquals(5, result.size)
        assertTrue(result.any { it.name == "Pull-Up" })
        assertTrue(result.any { it.name == "Power Snatch" })
        assertTrue(result.any { it.name == "Rope Climb" })
        assertTrue(result.any { it.name == "Kettlebell Swing" })
        assertTrue(result.any { it.name == "Rowing (meters)" })
        assertFalse(result.any { it.name == "Barbell Back Squat" })
        assertFalse(result.any { it.name == "Deadlift" })
    }

    @Test
    fun `functional-only exercise includes functional tag and appears in filter`() {
        // Exercises tagged "functional-only" also carry "functional" so the library chip shows them
        val ropeClimb = makeExercise(1, "Rope Climb", """["functional","functional-only","gymnastics"]""")
        val result = applyFunctionalFilter(listOf(ropeClimb), functional = true)
        assertEquals(1, result.size)
        assertEquals("Rope Climb", result[0].name)
    }

    @Test
    fun `empty tags string does not match functional filter`() {
        val ex = makeExercise(1, "Bench Press", "[]")
        val result = applyFunctionalFilter(listOf(ex), functional = true)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `exercise with only olympic tag does not match functional filter`() {
        // If an exercise had only ["olympic"] with no "functional" tag, it should be excluded
        val ex = makeExercise(1, "Olympic-Only Move", """["olympic"]""")
        val result = applyFunctionalFilter(listOf(ex), functional = true)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `toggling filter off then on is idempotent`() {
        val afterOff = applyFunctionalFilter(allExercises, functional = false)
        val afterOn = applyFunctionalFilter(allExercises, functional = true)
        val afterOffAgain = applyFunctionalFilter(allExercises, functional = false)
        assertEquals(afterOff.size, afterOffAgain.size)
        assertEquals(allExercises.size, afterOff.size)
        assertEquals(5, afterOn.size)
    }

    @Test
    fun `tags JSON parsing is robust to whitespace`() {
        // Verify that tags stored with spaces around values still match correctly
        val ex = makeExercise(1, "Exercise A", """[ "functional" , "olympic" ]""")
        val result = applyFunctionalFilter(listOf(ex), functional = true)
        assertEquals(1, result.size)
    }
}
