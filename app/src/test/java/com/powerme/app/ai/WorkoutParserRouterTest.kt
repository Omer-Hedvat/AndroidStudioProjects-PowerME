package com.powerme.app.ai

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WorkoutParserRouterTest {

    private lateinit var cloudParser: WorkoutTextParser
    private lateinit var router: WorkoutParserRouter

    @Before
    fun setup() {
        cloudParser = mock()
        router = WorkoutParserRouter(cloudParser)
    }

    @Test
    fun `delegates parseWorkoutText to cloud parser`() = runTest {
        val expected = ParseResult(listOf(ParsedExercise("Bench Press", sets = 3, reps = 8)))
        whenever(cloudParser.parseWorkoutText("3x8 bench", listOf("Bench Press"))).thenReturn(expected)

        val result = router.parseWorkoutText("3x8 bench", listOf("Bench Press"))

        verify(cloudParser).parseWorkoutText("3x8 bench", listOf("Bench Press"))
        assertEquals(expected, result)
    }

    @Test
    fun `returns cloud parser result unchanged on success`() = runTest {
        val exercises = listOf(
            ParsedExercise("Squat", sets = 5, reps = 5, weight = 100.0),
            ParsedExercise("Deadlift", sets = 3, reps = 5, weight = 140.0)
        )
        val expected = ParseResult(exercises)
        whenever(cloudParser.parseWorkoutText(any(), any())).thenReturn(expected)

        val result = router.parseWorkoutText("5x5 squat 100kg, 3x5 deadlift 140kg", emptyList())

        assertEquals(2, result.exercises.size)
        assertNull(result.error)
    }

    @Test
    fun `returns cloud parser error result unchanged`() = runTest {
        val errorResult = ParseResult(emptyList(), error = "API_KEY_MISSING")
        whenever(cloudParser.parseWorkoutText(any(), any())).thenReturn(errorResult)

        val result = router.parseWorkoutText("some workout", emptyList())

        assertTrue(result.exercises.isEmpty())
        assertEquals("API_KEY_MISSING", result.error)
    }

    @Test
    fun `passes exercise names list through to cloud parser`() = runTest {
        val names = listOf("Bench Press", "Squat", "Deadlift")
        whenever(cloudParser.parseWorkoutText(any(), any())).thenReturn(ParseResult(emptyList()))

        router.parseWorkoutText("push day", names)

        verify(cloudParser).parseWorkoutText("push day", names)
    }

    @Test
    fun `handles empty input forwarded to cloud parser`() = runTest {
        val errorResult = ParseResult(emptyList(), error = "No input provided")
        whenever(cloudParser.parseWorkoutText("", emptyList())).thenReturn(errorResult)

        val result = router.parseWorkoutText("", emptyList())

        assertEquals("No input provided", result.error)
    }
}
