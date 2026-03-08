package com.omerhedvat.powerme.ui.workout

import android.content.Context
import com.omerhedvat.powerme.analytics.BoazPerformanceAnalyzer
import com.omerhedvat.powerme.data.AppSettingsDataStore
import com.omerhedvat.powerme.data.database.RoutineDao
import com.omerhedvat.powerme.data.database.RoutineExerciseDao
import com.omerhedvat.powerme.data.database.Routine
import com.omerhedvat.powerme.data.database.UserSettingsDao
import com.omerhedvat.powerme.data.database.WorkoutDao
import com.omerhedvat.powerme.data.database.WorkoutSetDao
import com.omerhedvat.powerme.data.repository.ExerciseRepository
import com.omerhedvat.powerme.data.repository.MedicalLedgerRepository
import com.omerhedvat.powerme.data.repository.StateHistoryRepository
import com.omerhedvat.powerme.data.repository.WarmupRepository
import com.omerhedvat.powerme.data.repository.WorkoutBootstrap
import com.omerhedvat.powerme.data.repository.WorkoutRepository
import com.omerhedvat.powerme.warmup.WarmupService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit tests for WorkoutViewModel state transitions introduced in the Strong-Inspired UI Overhaul.
 *
 * Covers:
 *  2a. workoutName set from routine via startWorkoutFromRoutine()
 *  2b. workoutName "Empty Workout" via startWorkout(0L)
 *  2c. elapsedSeconds increments per second
 *  2d. finishWorkout() populates pendingWorkoutSummary
 *  2e. dismissWorkoutSummary() clears summary and resets isActive
 *  2f. elapsed timer stops after cancelWorkout()
 *
 * Time-control strategy
 * ─────────────────────
 * A single StandardTestDispatcher is used for both Dispatchers.Main and runTest. This ensures
 * that delay() inside viewModelScope coroutines writes virtual-time tasks to the same scheduler
 * that advanceTimeBy() advances. Coroutines are lazy (StandardTestDispatcher) — use runCurrent()
 * to drain tasks that are ready at the current virtual time.
 *
 * Each test that starts the elapsed timer must cancel it (cancelWorkout or finishWorkout) and
 * then call runCurrent() before the test body ends so advanceUntilIdle() cleanup finds no
 * looping tasks.
 *
 * IMPORTANT: In test 2c, elapsed is captured and the timer is cancelled BEFORE the assertion.
 * If the assertion were placed before cancelWorkout(), a failing assertion would leave the timer
 * running, causing cleanup's advanceUntilIdle() to spin forever.
 *
 * mock-maker-inline (test/resources/mockito-extensions/) enables mocking final Kotlin classes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // DAOs (interfaces — no mock-maker-inline needed)
    private lateinit var mockWorkoutDao: WorkoutDao
    private lateinit var mockWorkoutSetDao: WorkoutSetDao
    private lateinit var mockRoutineExerciseDao: RoutineExerciseDao
    private lateinit var mockRoutineDao: RoutineDao
    private lateinit var mockUserSettingsDao: UserSettingsDao

    // Concrete classes — require mock-maker-inline
    private lateinit var mockExerciseRepository: ExerciseRepository
    private lateinit var mockWorkoutRepository: WorkoutRepository
    private lateinit var mockWarmupRepository: WarmupRepository
    private lateinit var mockWarmupService: WarmupService
    private lateinit var mockMedicalLedgerRepository: MedicalLedgerRepository
    private lateinit var mockBoazPerformanceAnalyzer: BoazPerformanceAnalyzer
    private lateinit var mockStateHistoryRepository: StateHistoryRepository
    private lateinit var mockAppSettingsDataStore: AppSettingsDataStore
    private lateinit var mockContext: Context

    private lateinit var viewModel: WorkoutViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockWorkoutDao = mock()
        mockWorkoutSetDao = mock()
        mockRoutineExerciseDao = mock()
        mockRoutineDao = mock()
        mockUserSettingsDao = mock()
        mockExerciseRepository = mock()
        mockWorkoutRepository = mock()
        mockWarmupRepository = mock()
        mockWarmupService = mock()
        mockMedicalLedgerRepository = mock()
        mockBoazPerformanceAnalyzer = mock()
        mockStateHistoryRepository = mock()
        mockAppSettingsDataStore = mock()
        mockContext = mock()

        // Non-suspend property stubs (used at ViewModel construction time)
        whenever(mockAppSettingsDataStore.keepScreenOn).thenReturn(flowOf(true))
        whenever(mockUserSettingsDao.getSettings()).thenReturn(flowOf(null))
        whenever(mockExerciseRepository.getAllExercises()).thenReturn(flowOf(emptyList()))
        whenever(mockContext.packageName).thenReturn("com.omerhedvat.powerme")
        whenever(mockBoazPerformanceAnalyzer.formatPerformanceReport(any())).thenReturn("")

        // Suspend-function stubs — must be inside a coroutine context
        runBlocking {
            // init.rehydrateIfNeeded() — early exit, no workout to resume
            whenever(mockWorkoutDao.getActiveWorkout()).thenReturn(null)
            // init.loadMedicalDoc()
            whenever(mockMedicalLedgerRepository.getRestrictionsDoc()).thenReturn(null)

            // startWorkout (used by tests 2b–2f)
            whenever(mockWorkoutRepository.createEmptyWorkout(null)).thenReturn(1L)

            // finishWorkout suspend calls
            whenever(mockWorkoutDao.updateWorkout(any())).thenReturn(Unit)
            whenever(mockWorkoutSetDao.deleteIncompleteSetsByWorkout(any())).thenReturn(Unit)
            whenever(mockBoazPerformanceAnalyzer.compare(any(), any())).thenReturn(emptyList())

            // cancelWorkout suspend calls
            whenever(mockWorkoutSetDao.deleteSetsForWorkout(any())).thenReturn(Unit)
            whenever(mockWorkoutDao.deleteWorkoutById(any())).thenReturn(Unit)
        }

        viewModel = WorkoutViewModel(
            exerciseRepository = mockExerciseRepository,
            workoutRepository = mockWorkoutRepository,
            warmupRepository = mockWarmupRepository,
            warmupService = mockWarmupService,
            workoutDao = mockWorkoutDao,
            workoutSetDao = mockWorkoutSetDao,
            routineExerciseDao = mockRoutineExerciseDao,
            routineDao = mockRoutineDao,
            userSettingsDao = mockUserSettingsDao,
            medicalLedgerRepository = mockMedicalLedgerRepository,
            boazPerformanceAnalyzer = mockBoazPerformanceAnalyzer,
            stateHistoryRepository = mockStateHistoryRepository,
            appSettingsDataStore = mockAppSettingsDataStore,
            context = mockContext
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // 2a. workoutName from routine
    // -------------------------------------------------------------------------

    @Test
    fun `startWorkoutFromRoutine sets workoutName to routine name`() = runTest(testDispatcher) {
        val routine = Routine(id = 42L, name = "Push Day")
        whenever(mockWorkoutRepository.instantiateWorkoutFromRoutine(42L))
            .thenReturn(WorkoutBootstrap(workoutId = 100L, ghostMap = emptyMap(), workoutSets = emptyList()))
        whenever(mockRoutineExerciseDao.getForRoutine(42L)).thenReturn(emptyList())
        whenever(mockRoutineDao.getRoutineById(42L)).thenReturn(routine)

        viewModel.startWorkoutFromRoutine(42L)
        runCurrent()  // drain init tasks + startWorkoutFromRoutine; timer parked at delay(1000)

        assertEquals("Push Day", viewModel.workoutState.value.workoutName)
        assertTrue(viewModel.workoutState.value.isActive)

        // Cancel elapsed timer before test body ends so runTest cleanup doesn't spin
        viewModel.cancelWorkout()
        runCurrent()
    }

    // -------------------------------------------------------------------------
    // 2b. workoutName for empty workout
    // -------------------------------------------------------------------------

    @Test
    fun `startWorkout with zero routineId sets workoutName to Empty Workout`() =
        runTest(testDispatcher) {
            viewModel.startWorkout(0L)
            runCurrent()  // drain init tasks + startWorkout; timer parked

            assertEquals("Empty Workout", viewModel.workoutState.value.workoutName)
            assertTrue(viewModel.workoutState.value.isActive)

            viewModel.cancelWorkout()
            runCurrent()
        }

    // -------------------------------------------------------------------------
    // 2c. Elapsed timer increments
    // -------------------------------------------------------------------------

    @Test
    fun `elapsed timer increments elapsedSeconds each second after startWorkout`() =
        runTest(testDispatcher) {
            viewModel.startWorkout(0L)
            runCurrent()  // drain init tasks + startWorkout; timer now at delay(1000)

            // advanceTimeBy uses exclusive upper-bound semantics: tasks at time < currentTime+N
            // are processed. To tick delay(1000) exactly 3 times (at t=1000, 2000, 3000),
            // advance by 3_001 so t=3000 is included.
            advanceTimeBy(3_001)

            // Capture elapsed and cancel BEFORE asserting — if the assertion were placed before
            // cancelWorkout(), a failure would leave the timer running and freeze cleanup.
            val elapsed = viewModel.workoutState.value.elapsedSeconds
            viewModel.cancelWorkout()
            runCurrent()

            assertEquals(3, elapsed)
        }

    // -------------------------------------------------------------------------
    // 2d. finishWorkout() produces pendingWorkoutSummary
    // -------------------------------------------------------------------------

    @Test
    fun `finishWorkout populates pendingWorkoutSummary and clears isActive`() =
        runTest(testDispatcher) {
            viewModel.startWorkout(0L)
            runCurrent()

            // finishWorkout() synchronously cancels elapsedTimerJob, then launches its coroutine
            viewModel.finishWorkout()
            runCurrent()  // run finishWorkout coroutine (DB update, summary population)

            val summary = viewModel.workoutState.value.pendingWorkoutSummary
            assertNotNull(summary)
            assertEquals("Empty Workout", summary!!.workoutName)
            assertTrue(summary.durationSeconds >= 0)
            assertEquals(0, summary.setCount)
            assertTrue(summary.exerciseNames.isEmpty())
            assertFalse(viewModel.workoutState.value.isActive)
            // Timer already cancelled by finishWorkout — no extra cleanup needed
        }

    // -------------------------------------------------------------------------
    // 2e. dismissWorkoutSummary() resets state
    // -------------------------------------------------------------------------

    @Test
    fun `dismissWorkoutSummary clears pendingWorkoutSummary and resets isActive`() =
        runTest(testDispatcher) {
            viewModel.startWorkout(0L)
            runCurrent()
            viewModel.finishWorkout()
            runCurrent()

            assertNotNull(viewModel.workoutState.value.pendingWorkoutSummary)

            // dismissWorkoutSummary is a synchronous StateFlow update — no runCurrent needed
            viewModel.dismissWorkoutSummary()

            assertNull(viewModel.workoutState.value.pendingWorkoutSummary)
            assertFalse(viewModel.workoutState.value.isActive)
        }

    // -------------------------------------------------------------------------
    // 2f. Elapsed timer cancelled on cancelWorkout()
    // -------------------------------------------------------------------------

    @Test
    fun `elapsed timer stops incrementing after cancelWorkout`() = runTest(testDispatcher) {
        viewModel.startWorkout(0L)
        runCurrent()

        advanceTimeBy(2_000)
        val elapsedBeforeCancel = viewModel.workoutState.value.elapsedSeconds
        assertTrue("Timer should have ticked at least once", elapsedBeforeCancel >= 1)

        // cancelWorkout() synchronously cancels elapsedTimerJob
        viewModel.cancelWorkout()
        runCurrent()  // run DB cleanup + state reset

        assertFalse(viewModel.workoutState.value.isActive)
        assertEquals(0, viewModel.workoutState.value.elapsedSeconds)

        // Advance further — timer is cancelled; nothing should increment
        advanceTimeBy(5_000)
        assertEquals(0, viewModel.workoutState.value.elapsedSeconds)
    }
}
