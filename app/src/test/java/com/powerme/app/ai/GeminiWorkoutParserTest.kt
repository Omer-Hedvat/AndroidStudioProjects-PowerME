package com.powerme.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests the JSON parsing logic in GeminiWorkoutParser.
 * Does NOT make network calls — uses the internal parseJsonResponse() method directly.
 */
class GeminiWorkoutParserTest {

    private lateinit var parser: GeminiWorkoutParser

    @Before
    fun setup() {
        // GeminiWorkoutParser has no-arg Hilt constructor; instantiate directly.
        // The `model` lazy property is never touched by parseJsonResponse.
        parser = GeminiWorkoutParser()
    }

    // ── Valid JSON ────────────────────────────────────────────────────────────

    @Test
    fun `valid JSON array returns parsed exercises`() {
        val json = """
            [
              {"exerciseName":"Barbell Bench Press","sets":3,"reps":8,"weight":80.0,"restSeconds":90},
              {"exerciseName":"Barbell Back Squat","sets":4,"reps":5,"weight":100.0,"restSeconds":120}
            ]
        """.trimIndent()
        val result = parser.parseJsonResponse(json)
        assertNull(result.error)
        assertEquals(2, result.exercises.size)
        assertEquals("Barbell Bench Press", result.exercises[0].name)
        assertEquals(3, result.exercises[0].sets)
        assertEquals(8, result.exercises[0].reps)
        assertEquals(80.0, result.exercises[0].weight)
        assertEquals(90, result.exercises[0].restSeconds)
    }

    @Test
    fun `null weight and restSeconds are accepted`() {
        val json = """[{"exerciseName":"Pull-Up","sets":3,"reps":10,"weight":null,"restSeconds":null}]"""
        val result = parser.parseJsonResponse(json)
        assertNull(result.error)
        assertEquals(1, result.exercises.size)
        assertNull(result.exercises[0].weight)
        assertNull(result.exercises[0].restSeconds)
    }

    @Test
    fun `extra unknown fields are ignored`() {
        val json = """[{"exerciseName":"Deadlift","sets":3,"reps":5,"weight":120.0,"restSeconds":180,"notes":"heavy day","tempo":"3-1-3"}]"""
        val result = parser.parseJsonResponse(json)
        assertNull(result.error)
        assertEquals(1, result.exercises.size)
        assertEquals("Deadlift", result.exercises[0].name)
    }

    @Test
    fun `empty array returns empty list without error`() {
        val result = parser.parseJsonResponse("[]")
        assertNull(result.error)
        assertTrue(result.exercises.isEmpty())
    }

    // ── Markdown-wrapped JSON ─────────────────────────────────────────────────

    @Test
    fun `strips markdown json fences`() {
        val json = """
            ```json
            [{"exerciseName":"Overhead Press","sets":4,"reps":6,"weight":60.0,"restSeconds":90}]
            ```
        """.trimIndent()
        val result = parser.parseJsonResponse(json)
        assertNull(result.error)
        assertEquals(1, result.exercises.size)
        assertEquals("Overhead Press", result.exercises[0].name)
    }

    @Test
    fun `strips plain backtick fences`() {
        val json = "```\n[{\"exerciseName\":\"Squat\",\"sets\":3,\"reps\":10,\"weight\":null,\"restSeconds\":null}]\n```"
        val result = parser.parseJsonResponse(json)
        assertNull(result.error)
        assertEquals(1, result.exercises.size)
    }

    @Test
    fun `handles prose before and after JSON array`() {
        val json = "Here is your workout plan:\n[{\"exerciseName\":\"Row\",\"sets\":3,\"reps\":12,\"weight\":null,\"restSeconds\":60}]\nEnjoy your session!"
        val result = parser.parseJsonResponse(json)
        assertNull(result.error)
        assertEquals(1, result.exercises.size)
        assertEquals("Row", result.exercises[0].name)
    }

    // ── Malformed JSON ────────────────────────────────────────────────────────

    @Test
    fun `malformed JSON returns error`() {
        val result = parser.parseJsonResponse("{not valid json}")
        assertNotNull(result.error)
        assertTrue(result.exercises.isEmpty())
    }

    @Test
    fun `missing array brackets returns error`() {
        val result = parser.parseJsonResponse("exerciseName: Bench Press, sets: 3")
        assertNotNull(result.error)
        assertTrue(result.exercises.isEmpty())
    }

    @Test
    fun `empty string returns error`() {
        val result = parser.parseJsonResponse("")
        assertNotNull(result.error)
        assertTrue(result.exercises.isEmpty())
    }

    // ── Filtering ─────────────────────────────────────────────────────────────

    @Test
    fun `entries with blank name are filtered out`() {
        val json = """[{"exerciseName":"","sets":3,"reps":8,"weight":null,"restSeconds":null},{"exerciseName":"Bench Press","sets":3,"reps":8,"weight":null,"restSeconds":null}]"""
        val result = parser.parseJsonResponse(json)
        assertNull(result.error)
        assertEquals(1, result.exercises.size)
        assertEquals("Bench Press", result.exercises[0].name)
    }

    @Test
    fun `entries with zero sets are filtered out`() {
        val json = """[{"exerciseName":"Squat","sets":0,"reps":5,"weight":null,"restSeconds":null}]"""
        val result = parser.parseJsonResponse(json)
        assertNull(result.error)
        assertTrue(result.exercises.isEmpty())
    }
}
