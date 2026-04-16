package com.powerme.app.ui.history

import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.UnitSystem
import com.powerme.app.data.database.PRDetectionRow
import com.powerme.app.data.database.WorkoutExerciseNameRow
import com.powerme.app.data.repository.WorkoutRepository
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit tests for HistoryViewModel, testing:
 * - collapseRows() logic via the [HistoryViewModel.workouts] StateFlow
 * - [computePRWorkoutIds] PR detection algorithm directly
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var mockAppSettingsDataStore: AppSettingsDataStore

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
        id = id.toString(),
        routineId = null,
        timestamp = timestamp,
        durationSeconds = durationSeconds,
        totalVolume = totalVolume,
        notes = null,
        isCompleted = true,
        startTimeMs = 0L,
        endTimeMs = 0L,
        exerciseName = exerciseName,
        routineName = routineName,
        setCount = setCount,
        hasPR = 0   // always 0 from query; PR detection is done via computePRWorkoutIds
    )

    private fun makePRRow(
        workoutId: String,
        exerciseId: String,
        weight: Double,
        reps: Int,
        timestamp: Long
    ) = PRDetectionRow(
        workoutId = workoutId,
        exerciseId = exerciseId,
        weight = weight,
        reps = reps,
        timestamp = timestamp
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        workoutRepository = mock()
        mockAppSettingsDataStore = mock()
        whenever(mockAppSettingsDataStore.unitSystem).thenReturn(flowOf(UnitSystem.METRIC))
        // Default: no sets for PR detection
        whenever(workoutRepository.getAllCompletedSetsForPRDetection()).thenReturn(flowOf(emptyList()))
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

            val viewModel = HistoryViewModel(workoutRepository, mockAppSettingsDataStore)

            val results = mutableListOf<List<WorkoutWithExerciseSummary>>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.workouts.collect { results.add(it) }
            }
            advanceUntilIdle()
            job.cancel()

            val summaries = results.firstOrNull { it.isNotEmpty() } ?: emptyList()
            assertEquals(1, summaries.size)
            assertEquals("1", summaries[0].id)
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

            val viewModel = HistoryViewModel(workoutRepository, mockAppSettingsDataStore)

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

        val viewModel = HistoryViewModel(workoutRepository, mockAppSettingsDataStore)

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

            val viewModel = HistoryViewModel(workoutRepository, mockAppSettingsDataStore)

            val results = mutableListOf<List<WorkoutWithExerciseSummary>>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.workouts.collect { results.add(it) }
            }
            advanceUntilIdle()
            job.cancel()

            val summaries = results.firstOrNull { it.isNotEmpty() } ?: emptyList()
            assertEquals(2, summaries.size)
            // id=1 has higher timestamp — must come first
            assertEquals("1", summaries[0].id)
            assertEquals("Push Day", summaries[0].routineName)
            assertEquals(5, summaries[0].setCount)
            assertEquals("2", summaries[1].id)
            assertEquals("Pull Day", summaries[1].routineName)
            assertEquals(3, summaries[1].setCount)
        }

    // ── hasPR via computePRWorkoutIds() ───────────────────────────────────────

    @Test
    fun `computePRWorkoutIds - first ever set for an exercise is a PR`() {
        val sets = listOf(
            makePRRow("w1", "squat", weight = 100.0, reps = 5, timestamp = 1000L)
        )
        val prIds = computePRWorkoutIds(sets)
        assertTrue(prIds.contains("w1"))
    }

    @Test
    fun `computePRWorkoutIds - second set with higher e1RM is a PR`() {
        val sets = listOf(
            makePRRow("w1", "squat", weight = 100.0, reps = 5, timestamp = 1000L),
            makePRRow("w2", "squat", weight = 120.0, reps = 5, timestamp = 2000L)
        )
        val prIds = computePRWorkoutIds(sets)
        assertTrue(prIds.contains("w1"))
        assertTrue(prIds.contains("w2"))
    }

    @Test
    fun `computePRWorkoutIds - second set with lower e1RM is not a PR`() {
        val sets = listOf(
            makePRRow("w1", "squat", weight = 120.0, reps = 5, timestamp = 1000L),
            makePRRow("w2", "squat", weight = 100.0, reps = 5, timestamp = 2000L)
        )
        val prIds = computePRWorkoutIds(sets)
        assertTrue(prIds.contains("w1"))
        assertFalse(prIds.contains("w2"))
    }

    @Test
    fun `computePRWorkoutIds - independent exercises tracked separately`() {
        val sets = listOf(
            makePRRow("w1", "squat",  weight = 100.0, reps = 5, timestamp = 1000L),
            makePRRow("w1", "bench",  weight = 80.0,  reps = 5, timestamp = 1000L),
            makePRRow("w2", "squat",  weight = 90.0,  reps = 5, timestamp = 2000L),  // not a squat PR
            makePRRow("w2", "bench",  weight = 90.0,  reps = 5, timestamp = 2000L)   // bench PR
        )
        val prIds = computePRWorkoutIds(sets)
        assertTrue(prIds.contains("w1"))
        // w2 has a bench PR but not a squat PR — still in the set because bench was a PR
        assertTrue(prIds.contains("w2"))
    }

    @Test
    fun `computePRWorkoutIds - empty input returns empty set`() {
        assertTrue(computePRWorkoutIds(emptyList()).isEmpty())
    }

    @Test
    fun `hasPR true when workout id is in prWorkoutIds`() =
        runTest(testDispatcher) {
            val rows = listOf(makeRow(id = 1L, exerciseName = "Squat"))
            whenever(workoutRepository.getAllCompletedWorkoutsWithExerciseNames()).thenReturn(flowOf(rows))
            val prSets = listOf(makePRRow("1", "squat", 100.0, 5, 1000L))
            whenever(workoutRepository.getAllCompletedSetsForPRDetection()).thenReturn(flowOf(prSets))

            val viewModel = HistoryViewModel(workoutRepository, mockAppSettingsDataStore)
            val results = mutableListOf<List<WorkoutWithExerciseSummary>>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.workouts.collect { results.add(it) } }
            advanceUntilIdle()
            job.cancel()

            val summaries = results.firstOrNull { it.isNotEmpty() } ?: emptyList()
            assertTrue(summaries[0].hasPR)
        }

    @Test
    fun `hasPR false when workout id not in prWorkoutIds`() =
        runTest(testDispatcher) {
            val rows = listOf(makeRow(id = 1L, exerciseName = "Squat"))
            whenever(workoutRepository.getAllCompletedWorkoutsWithExerciseNames()).thenReturn(flowOf(rows))
            // No PR sets -> prWorkoutIds is empty
            whenever(workoutRepository.getAllCompletedSetsForPRDetection()).thenReturn(flowOf(emptyList()))

            val viewModel = HistoryViewModel(workoutRepository, mockAppSettingsDataStore)
            val results = mutableListOf<List<WorkoutWithExerciseSummary>>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.workouts.collect { results.add(it) } }
            advanceUntilIdle()
            job.cancel()

            val summaries = results.firstOrNull { it.isNotEmpty() } ?: emptyList()
            assertFalse(summaries[0].hasPR)
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

        val viewModel = HistoryViewModel(workoutRepository, mockAppSettingsDataStore)

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

    // ── Month group ordering ────────────────────────────────────────────────────

    /**
     * Case: routineId is null (routine was deleted after workout) but routineName is set.
     * The denormalized routineName must survive and be exposed by the ViewModel — the
     * History card should show the name rather than falling back to "Workout — date".
     */
    @Test
    fun `denormalized routineName survives when routineId is null after routine deletion`() =
        runTest(testDispatcher) {
            val rows = listOf(
                makeRow(id = 1L, exerciseName = "Squat", routineName = "Push Day", setCount = 3)
            )
            whenever(workoutRepository.getAllCompletedWorkoutsWithExerciseNames())
                .thenReturn(flowOf(rows))

            val viewModel = HistoryViewModel(workoutRepository, mockAppSettingsDataStore)

            val results = mutableListOf<List<WorkoutWithExerciseSummary>>()
            val job = launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.workouts.collect { results.add(it) }
            }
            advanceUntilIdle()
            job.cancel()

            val summaries = results.firstOrNull { it.isNotEmpty() } ?: emptyList()
            assertEquals(1, summaries.size)
            assertEquals("Push Day", summaries[0].routineName)
        }

    /**
     * Groups must be sorted most-recent month first regardless of input order.
     * Uses epoch-ms timestamps far enough apart to land in different months.
     */
    @Test
    fun `month groups are sorted most-recent first`() = runTest(testDispatcher) {
        // Jan 2024 workout (older) — id=1, timestamp smaller
        // Mar 2024 workout (newer) — id=2, timestamp larger
        val jan = 1_704_067_200_000L  // 2024-01-01 00:00 UTC
        val mar = 1_709_251_200_000L  // 2024-03-01 00:00 UTC
        val rows = listOf(
            makeRow(id = 1L, exerciseName = "Squat", timestamp = jan),
            makeRow(id = 2L, exerciseName = "Squat", timestamp = mar)
        )
        whenever(workoutRepository.getAllCompletedWorkoutsWithExerciseNames()).thenReturn(flowOf(rows))

        val viewModel = HistoryViewModel(workoutRepository, mockAppSettingsDataStore)
        val results = mutableListOf<List<HistoryGroup>>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.groups.collect { results.add(it) }
        }
        advanceUntilIdle()
        job.cancel()

        val groups = results.firstOrNull { it.isNotEmpty() } ?: emptyList()
        assertEquals(2, groups.size)
        // Most-recent group (March) must come first
        assertTrue(groups[0].workouts.first().timestamp > groups[1].workouts.first().timestamp)
    }
}
