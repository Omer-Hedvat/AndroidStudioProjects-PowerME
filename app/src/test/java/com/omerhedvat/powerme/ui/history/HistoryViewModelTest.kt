package com.omerhedvat.powerme.ui.history

import com.omerhedvat.powerme.data.database.WorkoutExerciseNameRow
import com.omerhedvat.powerme.data.repository.WorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit tests for HistoryViewModel.collapseRows() logic, tested via the exposed
 * [HistoryViewModel.workouts] StateFlow.
 *
 * Verifies that WorkoutExerciseNameRow groups are correctly collapsed into
 * WorkoutWithExerciseSummary — including routineName, setCount, exerciseNames,
 * and timestamp-descending ordering.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var workoutRepository: WorkoutRepository

    // Helper to build a WorkoutExerciseNameRow with sensible defaults.
    private fun makeRow(
        id: Long,
        exerciseName: String?,
        routineName: String? = null,
        setCount: Int = 0,
        timestamp: Long = id * 1000L,
        durationSeconds: Int = 3600,
        totalVolume: Double = 1000.0
    ) = WorkoutExerciseNameRow(
        id = id,
        routineId = null,
        timestamp = timestamp,
        durationSeconds = durationSeconds,
        totalVolume = totalVolume,
        notes = null,
        isCompleted = true,
        exerciseName = exerciseName,
        routineName = routineName,
        setCount = setCount
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        workoutRepository = mock()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Case 1: Single workout, single exercise.
     * All fields (routineName, setCount, durationSeconds, totalVolume) must propagate.
     */
    @Test
    fun `single workout single exercise collapses to one summary with all fields`() =
        runTest(testDispatcher) {
            val rows = listOf(
                makeRow(id = 1L, exerciseName = "Squat", routineName = "Push Day", setCount = 5)
            )
            whenever(workoutRepository.getAllCompletedWorkoutsWithExerciseNames())
                .thenReturn(flowOf(rows))

            val viewModel = HistoryViewModel(workoutRepository)

            val results = mutableListOf<List<WorkoutWithExerciseSummary>>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.workouts.collect { results.add(it) }
            }
            advanceUntilIdle()
            job.cancel()

            val summaries = results.firstOrNull { it.isNotEmpty() } ?: emptyList()
            assertEquals(1, summaries.size)
            assertEquals(1L, summaries[0].id)
            assertEquals("Push Day", summaries[0].routineName)
            assertEquals(5, summaries[0].setCount)
            assertEquals(3600, summaries[0].durationSeconds)
            assertEquals(1000.0, summaries[0].totalVolume, 0.01)
            assertEquals(listOf("Squat"), summaries[0].exerciseNames)
        }

    /**
     * Case 2: Single workout, multiple exercise-name rows.
     * All exercise names must be collected; routineName/setCount from the first row.
     */
    @Test
    fun `single workout multiple exercises collects all exercise names`() =
        runTest(testDispatcher) {
            val rows = listOf(
                makeRow(id = 1L, exerciseName = "Squat",       routineName = "Push Day", setCount = 9),
                makeRow(id = 1L, exerciseName = "Bench Press", routineName = "Push Day", setCount = 9),
                makeRow(id = 1L, exerciseName = "Deadlift",    routineName = "Push Day", setCount = 9)
            )
            whenever(workoutRepository.getAllCompletedWorkoutsWithExerciseNames())
                .thenReturn(flowOf(rows))

            val viewModel = HistoryViewModel(workoutRepository)

            val results = mutableListOf<List<WorkoutWithExerciseSummary>>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.workouts.collect { results.add(it) }
            }
            advanceUntilIdle()
            job.cancel()

            val summaries = results.firstOrNull { it.isNotEmpty() } ?: emptyList()
            assertEquals(1, summaries.size)
            assertEquals(3, summaries[0].exerciseNames.size)
            assertTrue(
                summaries[0].exerciseNames.containsAll(
                    listOf("Squat", "Bench Press", "Deadlift")
                )
            )
            assertEquals("Push Day", summaries[0].routineName)
            assertEquals(9, summaries[0].setCount)
        }

    /**
     * Case 3: routineName is null.
     * WorkoutWithExerciseSummary.routineName must be null — no crash.
     */
    @Test
    fun `null routineName propagates to summary without crash`() = runTest(testDispatcher) {
        val rows = listOf(
            makeRow(id = 1L, exerciseName = "OHP", routineName = null, setCount = 2)
        )
        whenever(workoutRepository.getAllCompletedWorkoutsWithExerciseNames())
            .thenReturn(flowOf(rows))

        val viewModel = HistoryViewModel(workoutRepository)

        val results = mutableListOf<List<WorkoutWithExerciseSummary>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.workouts.collect { results.add(it) }
        }
        advanceUntilIdle()
        job.cancel()

        val summaries = results.firstOrNull { it.isNotEmpty() } ?: emptyList()
        assertEquals(1, summaries.size)
        assertNull(summaries[0].routineName)
        assertEquals(2, summaries[0].setCount)
    }

    /**
     * Case 4: Multiple workouts — each must be grouped independently with its own setCount,
     * sorted descending by timestamp.
     */
    @Test
    fun `multiple workouts are grouped separately sorted by timestamp descending`() =
        runTest(testDispatcher) {
            val rows = listOf(
                makeRow(id = 1L, exerciseName = "Squat",       routineName = "Push Day", setCount = 5, timestamp = 2000L),
                makeRow(id = 2L, exerciseName = "Bench Press", routineName = "Pull Day", setCount = 3, timestamp = 1000L)
            )
            whenever(workoutRepository.getAllCompletedWorkoutsWithExerciseNames())
                .thenReturn(flowOf(rows))

            val viewModel = HistoryViewModel(workoutRepository)

            val results = mutableListOf<List<WorkoutWithExerciseSummary>>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.workouts.collect { results.add(it) }
            }
            advanceUntilIdle()
            job.cancel()

            val summaries = results.firstOrNull { it.isNotEmpty() } ?: emptyList()
            assertEquals(2, summaries.size)
            // id=1 has higher timestamp — must come first
            assertEquals(1L, summaries[0].id)
            assertEquals("Push Day", summaries[0].routineName)
            assertEquals(5, summaries[0].setCount)
            assertEquals(2L, summaries[1].id)
            assertEquals("Pull Day", summaries[1].routineName)
            assertEquals(3, summaries[1].setCount)
        }

    /**
     * Case 5: exerciseName is null — exerciseNames list must be empty (null filtered out).
     */
    @Test
    fun `null exerciseName row produces empty exerciseNames list`() = runTest(testDispatcher) {
        val rows = listOf(
            makeRow(id = 1L, exerciseName = null, routineName = "Empty Session", setCount = 0)
        )
        whenever(workoutRepository.getAllCompletedWorkoutsWithExerciseNames())
            .thenReturn(flowOf(rows))

        val viewModel = HistoryViewModel(workoutRepository)

        val results = mutableListOf<List<WorkoutWithExerciseSummary>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.workouts.collect { results.add(it) }
        }
        advanceUntilIdle()
        job.cancel()

        val summaries = results.firstOrNull { it.isNotEmpty() } ?: emptyList()
        assertEquals(1, summaries.size)
        assertTrue(summaries[0].exerciseNames.isEmpty())
    }
}
