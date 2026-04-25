package com.powerme.app.ai

import com.powerme.app.analytics.AnalyticsTracker
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class WorkoutParserRouterTest {

    private lateinit var cloudParser: WorkoutTextParser
    private lateinit var onDeviceParser: OnDeviceWorkoutParser
    private lateinit var aiCoreAvailability: AiCoreAvailability
    private lateinit var downloadManager: AiCoreDownloadManager
    private lateinit var analyticsTracker: AnalyticsTracker
    private lateinit var router: WorkoutParserRouter

    @Before
    fun setup() {
        cloudParser = mock()
        onDeviceParser = mock()
        aiCoreAvailability = mock()
        downloadManager = mock()
        analyticsTracker = mock()

        runBlocking {
            whenever(aiCoreAvailability.check()).thenReturn(AiCoreStatus.NotSupported)
        }

        router = WorkoutParserRouter(cloudParser, onDeviceParser, aiCoreAvailability, downloadManager, analyticsTracker)
    }

    @Test
    fun `delegates parseWorkoutText to cloud parser when NotSupported`() = runTest {
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

    @Test
    fun `routes to on-device parser when AICore is Ready`() = runTest {
        val expected = ParseResult(listOf(ParsedExercise("Squat", sets = 3, reps = 5)))
        whenever(aiCoreAvailability.check()).thenReturn(AiCoreStatus.Ready)
        whenever(onDeviceParser.parseWorkoutText(any(), any())).thenReturn(expected)

        val result = router.parseWorkoutText("3x5 squat", emptyList())

        verify(onDeviceParser).parseWorkoutText("3x5 squat", emptyList())
        verify(cloudParser, never()).parseWorkoutText(any(), any())
        verify(analyticsTracker).logAiGeneration("on_device", 1)
        assertEquals(expected, result)
    }

    @Test
    fun `routes to cloud and triggers download when AICore NeedsDownload`() = runTest {
        val expected = ParseResult(listOf(ParsedExercise("Bench Press", sets = 4, reps = 10)))
        whenever(aiCoreAvailability.check()).thenReturn(AiCoreStatus.NeedsDownload)
        whenever(cloudParser.parseWorkoutText(any(), any())).thenReturn(expected)

        val result = router.parseWorkoutText("4x10 bench", emptyList())

        verify(downloadManager).triggerDownload()
        verify(cloudParser).parseWorkoutText("4x10 bench", emptyList())
        verify(onDeviceParser, never()).parseWorkoutText(any(), any())
        verify(analyticsTracker).logAiGeneration("cloud", 1)
        assertEquals(expected, result)
    }

    @Test
    fun `routes to cloud and skips download when AICore NotSupported`() = runTest {
        val expected = ParseResult(listOf(ParsedExercise("Deadlift", sets = 5, reps = 3)))
        whenever(aiCoreAvailability.check()).thenReturn(AiCoreStatus.NotSupported)
        whenever(cloudParser.parseWorkoutText(any(), any())).thenReturn(expected)

        val result = router.parseWorkoutText("5x3 deadlift", emptyList())

        verify(downloadManager, never()).triggerDownload()
        verify(cloudParser).parseWorkoutText("5x3 deadlift", emptyList())
        verify(analyticsTracker).logAiGeneration("cloud", 1)
        assertEquals(expected, result)
    }

    @Test
    fun `falls back to cloud when on-device inference throws`() = runTest {
        val cloudResult = ParseResult(listOf(ParsedExercise("Bench Press", sets = 3, reps = 8)))
        whenever(aiCoreAvailability.check()).thenReturn(AiCoreStatus.Ready)
        whenever(onDeviceParser.parseWorkoutText(any(), any())).thenThrow(RuntimeException("AICore ToS not accepted"))
        whenever(cloudParser.parseWorkoutText(any(), any())).thenReturn(cloudResult)

        val result = router.parseWorkoutText("3x8 bench", emptyList())

        verify(cloudParser).parseWorkoutText("3x8 bench", emptyList())
        verify(analyticsTracker).logAiGeneration("cloud_fallback", 1)
        assertEquals(cloudResult, result)
    }

    @Test
    fun `falls back to cloud when on-device returns error ParseResult`() = runTest {
        val cloudResult = ParseResult(listOf(ParsedExercise("Squat", sets = 5, reps = 5)))
        whenever(aiCoreAvailability.check()).thenReturn(AiCoreStatus.Ready)
        whenever(onDeviceParser.parseWorkoutText(any(), any())).thenReturn(ParseResult(emptyList(), "inference failed"))
        whenever(cloudParser.parseWorkoutText(any(), any())).thenReturn(cloudResult)

        val result = router.parseWorkoutText("5x5 squat", emptyList())

        verify(cloudParser).parseWorkoutText("5x5 squat", emptyList())
        verify(analyticsTracker).logAiGeneration("cloud_fallback", 1)
        assertEquals(cloudResult, result)
    }

    @Test
    fun `does not fall back to cloud when on-device succeeds`() = runTest {
        val onDeviceResult = ParseResult(listOf(ParsedExercise("Deadlift", sets = 1, reps = 5)))
        whenever(aiCoreAvailability.check()).thenReturn(AiCoreStatus.Ready)
        whenever(onDeviceParser.parseWorkoutText(any(), any())).thenReturn(onDeviceResult)

        val result = router.parseWorkoutText("1x5 deadlift", emptyList())

        verify(cloudParser, never()).parseWorkoutText(any(), any())
        verify(analyticsTracker).logAiGeneration("on_device", 1)
        assertEquals(onDeviceResult, result)
    }

    @Test
    fun `logs analytics with correct exercise count`() = runTest {
        val exercises = listOf(
            ParsedExercise("Squat", sets = 3, reps = 5),
            ParsedExercise("Press", sets = 3, reps = 5),
            ParsedExercise("Deadlift", sets = 1, reps = 5)
        )
        whenever(aiCoreAvailability.check()).thenReturn(AiCoreStatus.NotSupported)
        whenever(cloudParser.parseWorkoutText(any(), any())).thenReturn(ParseResult(exercises))

        router.parseWorkoutText("workout", emptyList())

        verify(analyticsTracker).logAiGeneration("cloud", 3)
    }
}
