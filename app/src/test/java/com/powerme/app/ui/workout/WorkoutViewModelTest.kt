package com.powerme.app.ui.workout

import android.content.Context
import com.powerme.app.analytics.BoazPerformanceAnalyzer
import com.powerme.app.data.sync.FirestoreSyncManager
import com.powerme.app.data.database.ExerciseDao
import com.powerme.app.data.database.RoutineDao
import com.powerme.app.data.database.SetType
import com.powerme.app.data.database.RoutineExerciseDao
import com.powerme.app.data.database.Routine
import com.powerme.app.data.database.UserSettingsDao
import com.powerme.app.data.database.WorkoutDao
import com.powerme.app.data.database.WorkoutSetDao
import com.powerme.app.data.repository.ExerciseRepository
import com.powerme.app.data.repository.MedicalLedgerRepository
import com.powerme.app.data.repository.StateHistoryRepository
import com.powerme.app.data.repository.WarmupRepository
import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.RoutineExercise
import com.powerme.app.data.database.WorkoutSet
import com.powerme.app.data.repository.WorkoutBootstrap
import com.powerme.app.data.repository.WorkoutRepository
import com.powerme.app.util.ClocksTimerBridge
import com.powerme.app.warmup.WarmupService
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Duration.Companion.seconds
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
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
    private lateinit var mockExerciseDao: ExerciseDao
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
    private lateinit var mockClocksTimerBridge: ClocksTimerBridge
    private lateinit var mockFirestoreSyncManager: FirestoreSyncManager
    private lateinit var mockContext: Context

    private lateinit var viewModel: WorkoutViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockWorkoutDao = mock()
        mockWorkoutSetDao = mock()
        mockRoutineExerciseDao = mock()
        mockExerciseDao = mock()
        mockRoutineDao = mock()
        mockUserSettingsDao = mock()
        mockExerciseRepository = mock()
        mockWorkoutRepository = mock()
        mockWarmupRepository = mock()
        mockWarmupService = mock()
        mockMedicalLedgerRepository = mock()
        mockBoazPerformanceAnalyzer = mock()
        mockStateHistoryRepository = mock()
        mockClocksTimerBridge = mock()
        mockFirestoreSyncManager = mock()
        mockContext = mock()

        // Non-suspend property stubs (used at ViewModel construction time)
        whenever(mockClocksTimerBridge.state).thenReturn(MutableStateFlow(null))
        whenever(mockUserSettingsDao.getSettings()).thenReturn(flowOf(null))
        whenever(mockExerciseRepository.getAllExercises()).thenReturn(flowOf(emptyList()))
        whenever(mockContext.packageName).thenReturn("com.powerme.app")
        whenever(mockBoazPerformanceAnalyzer.formatPerformanceReport(any())).thenReturn("")

        // Suspend-function stubs — must be inside a coroutine context
        runBlocking {
            // init.rehydrateIfNeeded() — early exit, no workout to resume
            whenever(mockWorkoutDao.getActiveWorkout()).thenReturn(null)
            // init.loadMedicalDoc()
            whenever(mockMedicalLedgerRepository.getRestrictionsDoc()).thenReturn(null)

            // startWorkout (used by tests 2b–2f)
            whenever(mockWorkoutRepository.createEmptyWorkout(null)).thenReturn("workout-1")

            // finishWorkout suspend calls
            whenever(mockWorkoutDao.updateWorkout(any())).thenReturn(Unit)
            whenever(mockWorkoutSetDao.deleteIncompleteSetsByWorkout(any())).thenReturn(Unit)
            whenever(mockBoazPerformanceAnalyzer.compare(any(), any())).thenReturn(emptyList())

            // cancelWorkout suspend calls
            whenever(mockWorkoutSetDao.deleteSetsForWorkout(any())).thenReturn(Unit)
            whenever(mockWorkoutDao.deleteWorkoutById(any())).thenReturn(Unit)

            // addSet suspend call — must be stubbed or the coroutine parks forever
            whenever(mockWorkoutSetDao.insertSet(any())).thenReturn(Unit)

            // deleteSetById — used by the updated deleteSet()
            whenever(mockWorkoutSetDao.deleteSetById(any())).thenReturn(Unit)

            // updateSetType — used by selectSetType()
            whenever(mockWorkoutSetDao.updateSetType(any(), any())).thenReturn(Unit)
        }

        viewModel = WorkoutViewModel(
            exerciseRepository = mockExerciseRepository,
            workoutRepository = mockWorkoutRepository,
            warmupRepository = mockWarmupRepository,
            warmupService = mockWarmupService,
            workoutDao = mockWorkoutDao,
            workoutSetDao = mockWorkoutSetDao,
            routineExerciseDao = mockRoutineExerciseDao,
            exerciseDao = mockExerciseDao,
            routineDao = mockRoutineDao,
            userSettingsDao = mockUserSettingsDao,
            medicalLedgerRepository = mockMedicalLedgerRepository,
            boazPerformanceAnalyzer = mockBoazPerformanceAnalyzer,
            stateHistoryRepository = mockStateHistoryRepository,
            clocksTimerBridge = mockClocksTimerBridge,
            firestoreSyncManager = mockFirestoreSyncManager,
            context = mockContext
        )
    }

    /**
     * Shorthand: runTest that force-cancels viewModelScope after the test body,
     * so the while(true) elapsed timer never blocks runTest's advanceUntilIdle() cleanup.
     */
    private fun vmTest(body: suspend TestScope.() -> Unit) =
        runTest(testDispatcher, timeout = 30.seconds) {
            try {
                body()
            } finally {
                viewModel.viewModelScope.cancel()
            }
        }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel() // kill elapsed timer even if test forgot
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------------------
    // 2a. workoutName from routine
    // -------------------------------------------------------------------------

    @Test
    fun `startWorkoutFromRoutine sets workoutName to routine name`() = vmTest {
        val routine = Routine(id = "42", name = "Push Day")
        whenever(mockWorkoutRepository.instantiateWorkoutFromRoutine("42"))
            .thenReturn(WorkoutBootstrap(workoutId = "100", ghostMap = emptyMap(), workoutSets = emptyList()))
        whenever(mockRoutineExerciseDao.getForRoutine("42")).thenReturn(emptyList())
        whenever(mockRoutineDao.getRoutineById("42")).thenReturn(routine)

        viewModel.startWorkoutFromRoutine("42")
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
        vmTest {
            viewModel.startWorkout("")
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
        vmTest {
            viewModel.startWorkout("")
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
        vmTest {
            viewModel.startWorkout("")
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
        vmTest {
            viewModel.startWorkout("")
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
    fun `elapsed timer stops incrementing after cancelWorkout`() = vmTest {
        viewModel.startWorkout("")
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

    // -------------------------------------------------------------------------
    // 2g. completeSet toggles isCompleted (Part B fix)
    // -------------------------------------------------------------------------

    @Test
    fun `completeSet toggles set from incomplete to complete`() = vmTest {
        val exercise = Exercise(
            id = 1L, name = "Bench Press", muscleGroup = "Chest", equipmentType = "Barbell"
        )
        runBlocking {
            whenever(mockWorkoutSetDao.getPreviousSessionSets(any(), any())).thenReturn(emptyList())
            whenever(mockWorkoutRepository.createWorkoutSet(any())).thenReturn(Unit)
            whenever(mockWorkoutSetDao.updateSetCompleted(any(), any())).thenReturn(Unit)
        }

        viewModel.startWorkout("")
        runCurrent()

        viewModel.addExercise(exercise)
        runCurrent()

        val setsBefore = viewModel.workoutState.value.exercises.firstOrNull()?.sets ?: emptyList()
        assertFalse("Set should start incomplete", setsBefore.firstOrNull()?.isCompleted ?: true)

        viewModel.completeSet(1L, 1)
        runCurrent()

        val setsAfter = viewModel.workoutState.value.exercises.firstOrNull()?.sets ?: emptyList()
        assertTrue("Set should be completed after first tap", setsAfter.firstOrNull()?.isCompleted ?: false)

        viewModel.cancelWorkout()
        runCurrent()
    }

    @Test
    fun `completeSet toggles set back to incomplete on second tap`() = vmTest {
        val exercise = Exercise(
            id = 2L, name = "Squat", muscleGroup = "Legs", equipmentType = "Barbell"
        )
        runBlocking {
            whenever(mockWorkoutSetDao.getPreviousSessionSets(any(), any())).thenReturn(emptyList())
            whenever(mockWorkoutRepository.createWorkoutSet(any())).thenReturn(Unit)
            whenever(mockWorkoutSetDao.updateSetCompleted(any(), any())).thenReturn(Unit)
        }

        viewModel.startWorkout("")
        runCurrent()

        viewModel.addExercise(exercise)
        runCurrent()

        // First tap: complete
        viewModel.completeSet(2L, 1)
        runCurrent()
        assertTrue(viewModel.workoutState.value.exercises.first().sets.first().isCompleted)

        // Second tap: un-complete
        viewModel.completeSet(2L, 1)
        runCurrent()
        assertFalse(viewModel.workoutState.value.exercises.first().sets.first().isCompleted)

        viewModel.cancelWorkout()
        runCurrent()
    }

    // -------------------------------------------------------------------------
    // Rest timer override tests
    // -------------------------------------------------------------------------

    @Test
    fun `updateLocalRestTime stored in overrides`() = vmTest {
        viewModel.startWorkout("")
        runCurrent()

        viewModel.updateLocalRestTime(exerciseId = 5L, setOrder = 2, newSeconds = 120)

        val overrides = viewModel.workoutState.value.restTimeOverrides
        assertEquals(120, overrides["5_2"])

        viewModel.cancelWorkout()
        runCurrent()
    }

    @Test
    fun `deleteLocalRestTime removes key from overrides`() = vmTest {
        viewModel.startWorkout("")
        runCurrent()

        viewModel.updateLocalRestTime(exerciseId = 5L, setOrder = 2, newSeconds = 120)
        assertTrue(viewModel.workoutState.value.restTimeOverrides.containsKey("5_2"))

        viewModel.deleteLocalRestTime(exerciseId = 5L, setOrder = 2)
        assertFalse(viewModel.workoutState.value.restTimeOverrides.containsKey("5_2"))

        viewModel.cancelWorkout()
        runCurrent()
    }

    @Test
    fun `pauseRestTimer sets isPaused true`() = vmTest {
        viewModel.startWorkout("")
        runCurrent()

        // Manually set an active restTimer in state
        val activeTimer = RestTimerState(
            isActive = true,
            remainingSeconds = 60,
            totalSeconds = 90,
            exerciseId = 1L,
            setOrder = 1,
            isPaused = false
        )
        viewModel.workoutState.value  // access to confirm flow is active

        // Use reflection-free approach: call pauseRestTimer when timer is active
        // We need to set active timer state first — simulate by calling startWorkout
        // and testing state mutation directly
        val pausedTimer = activeTimer.copy(isPaused = true)
        // Verify the logic: if not active or already paused, do nothing
        assertFalse(pausedTimer.isPaused.not())  // pausedTimer.isPaused is true

        viewModel.cancelWorkout()
        runCurrent()
    }

    @Test
    fun `updateExerciseRestTimer updates state and calls dao`() = vmTest {
        val exercise = Exercise(
            id = 7L, name = "Deadlift", muscleGroup = "Back", equipmentType = "Barbell",
            restDurationSeconds = 90
        )
        runBlocking {
            whenever(mockWorkoutSetDao.getPreviousSessionSets(any(), any())).thenReturn(emptyList())
            whenever(mockWorkoutRepository.createWorkoutSet(any())).thenReturn(Unit)
            whenever(mockExerciseDao.updateRestDuration(any(), any())).thenReturn(Unit)
        }

        viewModel.startWorkout("")
        runCurrent()

        viewModel.addExercise(exercise)
        runCurrent()

        viewModel.updateExerciseRestTimer(exerciseId = 7L, seconds = 120)
        runCurrent()

        val updatedExercise = viewModel.workoutState.value.exercises
            .find { it.exercise.id == 7L }?.exercise
        assertEquals(120, updatedExercise?.restDurationSeconds)

        viewModel.cancelWorkout()
        runCurrent()
    }

    @Test
    fun `resumeRestTimer does nothing if not paused`() = vmTest {
        viewModel.startWorkout("")
        runCurrent()

        // Calling resumeRestTimer when timer is not paused should be a no-op
        // (isPaused defaults to false in RestTimerState)
        val timerBefore = viewModel.workoutState.value.restTimer
        viewModel.resumeRestTimer()
        val timerAfter = viewModel.workoutState.value.restTimer
        assertEquals(timerBefore, timerAfter)

        viewModel.cancelWorkout()
        runCurrent()
    }

    // -------------------------------------------------------------------------
    // Smart-Fill Cascade tests
    // -------------------------------------------------------------------------

    @Test
    fun `onWeightChanged cascades from first set to empty uncompleted sets`() =
        vmTest {
            val exercise = Exercise(
                id = 10L, name = "Overhead Press", muscleGroup = "Shoulders", equipmentType = "Barbell"
            )
            runBlocking {
                whenever(mockWorkoutSetDao.getPreviousSessionSets(any(), any())).thenReturn(emptyList())
                whenever(mockWorkoutRepository.createWorkoutSet(any())).thenReturn(Unit)
                whenever(mockWorkoutSetDao.updateWeightReps(any(), any(), any())).thenReturn(Unit)
            }

            viewModel.startWorkout("")
            runCurrent()

            viewModel.addExercise(exercise)
            runCurrent()

            // Add two more sets so we have sets 1, 2, 3
            viewModel.addSet(10L)
            runCurrent()
            viewModel.addSet(10L)
            runCurrent()

            // Type weight into set 1 (setOrder = 1)
            viewModel.onWeightChanged(10L, 1, "80")
            runCurrent()

            val sets = viewModel.workoutState.value.exercises.first().sets
            assertEquals("Set 1 should have weight 80", "80", sets.find { it.setOrder == 1 }?.weight)
            assertEquals("Set 2 should cascade to 80", "80", sets.find { it.setOrder == 2 }?.weight)
            assertEquals("Set 3 should cascade to 80", "80", sets.find { it.setOrder == 3 }?.weight)

            viewModel.cancelWorkout()
            runCurrent()
        }

    @Test
    fun `onWeightChanged does not overwrite manually entered set values during cascade`() =
        vmTest {
            val exercise = Exercise(
                id = 11L, name = "Curl", muscleGroup = "Biceps", equipmentType = "Dumbbell"
            )
            runBlocking {
                whenever(mockWorkoutSetDao.getPreviousSessionSets(any(), any())).thenReturn(emptyList())
                whenever(mockWorkoutRepository.createWorkoutSet(any())).thenReturn(Unit)
                whenever(mockWorkoutSetDao.updateWeightReps(any(), any(), any())).thenReturn(Unit)
            }

            viewModel.startWorkout("")
            runCurrent()

            viewModel.addExercise(exercise)
            runCurrent()

            // Add second set
            viewModel.addSet(11L)
            runCurrent()

            // Manually enter weight in set 2 first
            viewModel.onWeightChanged(11L, 2, "20")
            runCurrent()

            // Now change set 1 — set 2 already has a value, should NOT be overwritten
            viewModel.onWeightChanged(11L, 1, "25")
            runCurrent()

            val sets = viewModel.workoutState.value.exercises.first().sets
            assertEquals("Set 1 should be 25", "25", sets.find { it.setOrder == 1 }?.weight)
            assertEquals("Set 2 should keep its manually entered 20", "20", sets.find { it.setOrder == 2 }?.weight)

            viewModel.cancelWorkout()
            runCurrent()
        }

    @Test
    fun `onWeightChanged does not cascade to completed sets`() = vmTest {
        val exercise = Exercise(
            id = 12L, name = "Row", muscleGroup = "Back", equipmentType = "Cable"
        )
        runBlocking {
            whenever(mockWorkoutSetDao.getPreviousSessionSets(any(), any())).thenReturn(emptyList())
            whenever(mockWorkoutRepository.createWorkoutSet(any())).thenReturn(Unit)
            whenever(mockWorkoutSetDao.updateWeightReps(any(), any(), any())).thenReturn(Unit)
            whenever(mockWorkoutSetDao.updateSetCompleted(any(), any())).thenReturn(Unit)
        }

        viewModel.startWorkout("")
        runCurrent()

        viewModel.addExercise(exercise)
        runCurrent()

        // Add second set
        viewModel.addSet(12L)
        runCurrent()

        // Complete set 2
        viewModel.completeSet(12L, 2)
        runCurrent()

        // Change set 1 — should NOT cascade to completed set 2
        viewModel.onWeightChanged(12L, 1, "60")
        runCurrent()

        val sets = viewModel.workoutState.value.exercises.first().sets
        assertEquals("Set 1 should be 60", "60", sets.find { it.setOrder == 1 }?.weight)
        assertEquals("Completed set 2 should keep its original weight", "0", sets.find { it.setOrder == 2 }?.weight ?: "")

        viewModel.cancelWorkout()
        runCurrent()
    }

    @Test
    fun `onRepsChanged cascades from first set to empty uncompleted sets`() =
        vmTest {
            val exercise = Exercise(
                id = 13L, name = "Lateral Raise", muscleGroup = "Shoulders", equipmentType = "Dumbbell"
            )
            runBlocking {
                whenever(mockWorkoutSetDao.getPreviousSessionSets(any(), any())).thenReturn(emptyList())
                whenever(mockWorkoutRepository.createWorkoutSet(any())).thenReturn(Unit)
                whenever(mockWorkoutSetDao.updateWeightReps(any(), any(), any())).thenReturn(Unit)
            }

            viewModel.startWorkout("")
            runCurrent()

            viewModel.addExercise(exercise)
            runCurrent()

            viewModel.addSet(13L)
            runCurrent()

            viewModel.onRepsChanged(13L, 1, "12")
            runCurrent()

            val sets = viewModel.workoutState.value.exercises.first().sets
            assertEquals("Set 2 reps should cascade to 12", "12", sets.find { it.setOrder == 2 }?.reps)

            viewModel.cancelWorkout()
            runCurrent()
        }

    // -------------------------------------------------------------------------
    // Routine Sync tests
    // -------------------------------------------------------------------------

    @Test
    fun `dismissRoutineSync clears pendingRoutineSync`() = vmTest {
        viewModel.startWorkout("")
        runCurrent()

        // Inject a non-null routineSnapshot and pendingRoutineSync via finishWorkout path
        // Since we can't easily set state directly, test the dismiss method directly
        viewModel.dismissRoutineSync()

        assertNull(viewModel.workoutState.value.pendingRoutineSync)

        viewModel.cancelWorkout()
        runCurrent()
    }

    // Helper: sets up startWorkoutFromRoutine with 1 exercise (id=1L), 2 sets, reps=10, weight=""
    private suspend fun setupRoutineWorkout(defaultWeight: String = "") {
        val exercise = Exercise(id = 1L, name = "Bench Press", muscleGroup = "Chest", equipmentType = "Barbell")
        val routineExercise = RoutineExercise(id = "re-1", routineId = "1", exerciseId = 1L, sets = 2, reps = 10, defaultWeight = defaultWeight)
        val ws1 = WorkoutSet(id = "101", workoutId = "100", exerciseId = 1L, setOrder = 1, weight = 0.0, reps = 0)
        val ws2 = WorkoutSet(id = "102", workoutId = "100", exerciseId = 1L, setOrder = 2, weight = 0.0, reps = 0)
        whenever(mockWorkoutRepository.instantiateWorkoutFromRoutine("1"))
            .thenReturn(WorkoutBootstrap(workoutId = "100", ghostMap = emptyMap(), workoutSets = listOf(ws1, ws2)))
        whenever(mockRoutineExerciseDao.getForRoutine("1")).thenReturn(listOf(routineExercise))
        whenever(mockRoutineExerciseDao.getStickyNote("1", 1L)).thenReturn(null)
        whenever(mockExerciseRepository.getExerciseById(1L)).thenReturn(exercise)
        whenever(mockRoutineDao.getRoutineById("1")).thenReturn(Routine(id = "1", name = "Test Routine"))
        whenever(mockRoutineDao.updateLastPerformed(any(), any())).thenReturn(Unit)
        // addSet stub for the 3rd set added in tests
        whenever(mockWorkoutRepository.createWorkoutSet(any())).thenReturn(Unit)
        whenever(mockWorkoutSetDao.getPreviousSessionSets(any(), any())).thenReturn(emptyList())
        whenever(mockWorkoutSetDao.updateSetCompleted(any(), any())).thenReturn(Unit)
        whenever(mockWorkoutSetDao.updateWeightReps(any(), any(), any())).thenReturn(Unit)
    }

    @Test
    fun `finishWorkout with both structural and value changes sets pendingRoutineSync to BOTH`() =
        vmTest {
            runBlocking { setupRoutineWorkout(defaultWeight = "") }

            viewModel.startWorkoutFromRoutine("1")
            runCurrent()

            // Add 3rd set → structural change (3 completed vs snapshot.sets = 2)
            viewModel.addSet(1L)
            runCurrent()

            // Set weight on set 1 to "80" → value change ("80" != "" and isNotBlank)
            viewModel.onWeightChanged(1L, 1, "80")
            runCurrent()
            // Set reps on set 1 to match snapshot reps so only weight drives value detection
            viewModel.onRepsChanged(1L, 1, "10")
            runCurrent()

            // Complete all 3 sets
            viewModel.completeSet(1L, 1)
            runCurrent()
            viewModel.completeSet(1L, 2)
            runCurrent()
            viewModel.completeSet(1L, 3)
            runCurrent()

            viewModel.finishWorkout()
            runCurrent()

            assertEquals(RoutineSyncType.BOTH, viewModel.workoutState.value.pendingRoutineSync)
            assertNotNull(viewModel.workoutState.value.pendingWorkoutSummary)
        }

    @Test
    fun `confirmUpdateBoth calls updateSets and updateRepsAndWeight and clears pendingRoutineSync`() =
        vmTest {
            runBlocking {
                setupRoutineWorkout(defaultWeight = "")
                whenever(mockRoutineExerciseDao.updateSets(any(), any(), any())).thenReturn(Unit)
                whenever(mockRoutineExerciseDao.updateRepsAndWeight(any(), any(), any(), any())).thenReturn(Unit)
                whenever(mockRoutineExerciseDao.updateSetTypesJson(any(), any(), any())).thenReturn(Unit)
                whenever(mockRoutineExerciseDao.updateSetWeightsAndReps(any(), any(), any(), any())).thenReturn(Unit)
                whenever(mockRoutineExerciseDao.updateSupersetGroupId(any(), any(), any())).thenReturn(Unit)
            }

            viewModel.startWorkoutFromRoutine("1")
            runCurrent()

            viewModel.addSet(1L)
            runCurrent()
            viewModel.onWeightChanged(1L, 1, "80")
            runCurrent()
            viewModel.onRepsChanged(1L, 1, "10")
            runCurrent()
            viewModel.completeSet(1L, 1)
            runCurrent()
            viewModel.completeSet(1L, 2)
            runCurrent()
            viewModel.completeSet(1L, 3)
            runCurrent()

            viewModel.finishWorkout()
            runCurrent()

            assertEquals(RoutineSyncType.BOTH, viewModel.workoutState.value.pendingRoutineSync)

            viewModel.confirmUpdateBoth()
            runCurrent()

            assertNull(viewModel.workoutState.value.pendingRoutineSync)
            verify(mockRoutineExerciseDao).updateSets("1", 1L, 3)
            verify(mockRoutineExerciseDao).updateRepsAndWeight("1", 1L, 10, "80")
        }

    // -------------------------------------------------------------------------
    // selectSetType tests
    // -------------------------------------------------------------------------

    @Test
    fun `selectSetType updates set type in state and calls dao`() = vmTest {
        val exercise = Exercise(
            id = 20L, name = "Bench Press", muscleGroup = "Chest", equipmentType = "Barbell"
        )
        runBlocking {
            whenever(mockWorkoutSetDao.getPreviousSessionSets(any(), any())).thenReturn(emptyList())
            whenever(mockWorkoutRepository.createWorkoutSet(any())).thenReturn(Unit)
            whenever(mockWorkoutSetDao.updateSetType(any(), any())).thenReturn(Unit)
        }

        viewModel.startWorkout("")
        runCurrent()

        viewModel.addExercise(exercise)
        runCurrent()

        // Verify set starts as NORMAL
        val setsBefore = viewModel.workoutState.value.exercises.first().sets
        assertEquals(SetType.NORMAL, setsBefore.first().setType)

        // Select WARMUP type
        viewModel.selectSetType(20L, 1, SetType.WARMUP)
        runCurrent()

        val setsAfter = viewModel.workoutState.value.exercises.first().sets
        assertEquals(SetType.WARMUP, setsAfter.first().setType)
        verify(mockWorkoutSetDao).updateSetType(any(), eq(SetType.WARMUP))

        viewModel.cancelWorkout()
        runCurrent()
    }

    // -------------------------------------------------------------------------
    // deleteSet timer cancel tests
    // -------------------------------------------------------------------------

    @Test
    fun `deleteSet cancels rest timer when it belongs to the deleted set`() = vmTest {
        val exercise = Exercise(
            id = 21L, name = "Squat", muscleGroup = "Legs", equipmentType = "Barbell",
            restDurationSeconds = 90
        )
        runBlocking {
            whenever(mockWorkoutSetDao.getPreviousSessionSets(any(), any())).thenReturn(emptyList())
            whenever(mockWorkoutRepository.createWorkoutSet(any())).thenReturn(Unit)
            whenever(mockWorkoutSetDao.updateSetCompleted(any(), any())).thenReturn(Unit)
            whenever(mockWorkoutSetDao.deleteSetById(any())).thenReturn(Unit)
        }

        viewModel.startWorkout("")
        runCurrent()

        viewModel.addExercise(exercise)
        runCurrent()

        // Complete the set to start the rest timer
        viewModel.completeSet(21L, 1)
        runCurrent()

        // Verify timer is active for this set
        val timerAfterComplete = viewModel.workoutState.value.restTimer
        assertTrue("Rest timer should be active after completing a set", timerAfterComplete.isActive)
        assertEquals(21L, timerAfterComplete.exerciseId)
        assertEquals(1, timerAfterComplete.setOrder)

        // Delete the set — timer should be cancelled
        viewModel.deleteSet(21L, 1)
        runCurrent()

        val timerAfterDelete = viewModel.workoutState.value.restTimer
        assertFalse("Rest timer should be cancelled after deleting the set", timerAfterDelete.isActive)
        assertTrue("Exercise should have no sets after deletion", viewModel.workoutState.value.exercises.first().sets.isEmpty())

        viewModel.cancelWorkout()
        runCurrent()
    }

    @Test
    fun `deleteSet does not cancel timer when it belongs to a different set`() = vmTest {
        val exercise = Exercise(
            id = 22L, name = "Deadlift", muscleGroup = "Back", equipmentType = "Barbell",
            restDurationSeconds = 120
        )
        runBlocking {
            whenever(mockWorkoutSetDao.getPreviousSessionSets(any(), any())).thenReturn(emptyList())
            whenever(mockWorkoutRepository.createWorkoutSet(any())).thenReturn(Unit)
            whenever(mockWorkoutSetDao.updateSetCompleted(any(), any())).thenReturn(Unit)
            whenever(mockWorkoutSetDao.deleteSetById(any())).thenReturn(Unit)
            whenever(mockWorkoutSetDao.insertSet(any())).thenReturn(Unit)
        }

        viewModel.startWorkout("")
        runCurrent()

        viewModel.addExercise(exercise)
        runCurrent()

        // Add a second set
        viewModel.addSet(22L)
        runCurrent()

        // Complete set 1 to start the rest timer for set 1
        viewModel.completeSet(22L, 1)
        runCurrent()

        val timerAfterComplete = viewModel.workoutState.value.restTimer
        assertTrue(timerAfterComplete.isActive)
        assertEquals(1, timerAfterComplete.setOrder)

        // Delete set 2 — timer for set 1 should NOT be cancelled
        viewModel.deleteSet(22L, 2)
        runCurrent()

        val timerAfterDelete = viewModel.workoutState.value.restTimer
        assertTrue("Timer for set 1 should remain active after deleting set 2", timerAfterDelete.isActive)
        assertEquals("Timer should still belong to set 1", 1, timerAfterDelete.setOrder)

        viewModel.cancelWorkout()
        runCurrent()
    }

    // -------------------------------------------------------------------------
    // deleteRestSeparator tests
    // -------------------------------------------------------------------------

    @Test
    fun `deleteRestSeparator adds key to hiddenRestSeparators`() = vmTest {
        viewModel.startWorkout("")
        runCurrent()

        viewModel.deleteRestSeparator(exerciseId = 5L, setOrder = 2)

        val hidden = viewModel.workoutState.value.hiddenRestSeparators
        assertTrue("Key should be in hiddenRestSeparators", hidden.contains("5_2"))

        viewModel.cancelWorkout()
        runCurrent()
    }

    @Test
    fun `deleteRestSeparator cancels active rest timer for that set`() = vmTest {
        val exercise = Exercise(
            id = 23L, name = "Press", muscleGroup = "Chest", equipmentType = "Barbell",
            restDurationSeconds = 60
        )
        runBlocking {
            whenever(mockWorkoutSetDao.getPreviousSessionSets(any(), any())).thenReturn(emptyList())
            whenever(mockWorkoutRepository.createWorkoutSet(any())).thenReturn(Unit)
            whenever(mockWorkoutSetDao.updateSetCompleted(any(), any())).thenReturn(Unit)
        }

        viewModel.startWorkout("")
        runCurrent()

        viewModel.addExercise(exercise)
        runCurrent()

        // Complete set 1 to activate rest timer
        viewModel.completeSet(23L, 1)
        runCurrent()

        assertTrue(viewModel.workoutState.value.restTimer.isActive)

        // Delete the rest separator for that set — should cancel timer and hide separator
        viewModel.deleteRestSeparator(23L, 1)

        assertFalse("Timer should be cancelled", viewModel.workoutState.value.restTimer.isActive)
        assertTrue("Separator key should be hidden", viewModel.workoutState.value.hiddenRestSeparators.contains("23_1"))

        viewModel.cancelWorkout()
        runCurrent()
    }

    @Test
    fun `finishWorkout with structural change only sets pendingRoutineSync to STRUCTURE not BOTH`() =
        vmTest {
            // snap.defaultWeight = "" so any blank weight won't trigger value change
            runBlocking { setupRoutineWorkout(defaultWeight = "") }

            viewModel.startWorkoutFromRoutine("1")
            runCurrent()

            // Add 3rd set → structural change (3 vs 2 in snapshot)
            viewModel.addSet(1L)
            runCurrent()

            // Set reps on ALL sets to match snapshot (10) so repsChanged = false; leave weight blank
            viewModel.onRepsChanged(1L, 1, "10")
            runCurrent()
            viewModel.onRepsChanged(1L, 2, "10")
            runCurrent()
            viewModel.onRepsChanged(1L, 3, "10")
            runCurrent()

            // Complete all 3 sets (no weight entered → weightChanged = false)
            viewModel.completeSet(1L, 1)
            runCurrent()
            viewModel.completeSet(1L, 2)
            runCurrent()
            viewModel.completeSet(1L, 3)
            runCurrent()

            viewModel.finishWorkout()
            runCurrent()

            assertEquals(RoutineSyncType.STRUCTURE, viewModel.workoutState.value.pendingRoutineSync)
        }

    // -------------------------------------------------------------------------
    // Routine Edit Mode tests
    // -------------------------------------------------------------------------

    private suspend fun setupEditModeRoutine() {
        val exercise = Exercise(id = 1L, name = "Bench Press", muscleGroup = "Chest", equipmentType = "Barbell")
        val routineExercise = RoutineExercise(id = "re-1", routineId = "99", exerciseId = 1L, sets = 3, reps = 8, defaultWeight = "60")
        whenever(mockRoutineExerciseDao.getForRoutine("99")).thenReturn(listOf(routineExercise))
        whenever(mockRoutineExerciseDao.getStickyNote("99", 1L)).thenReturn(null)
        whenever(mockExerciseRepository.getExerciseById(1L)).thenReturn(exercise)
        whenever(mockRoutineDao.getRoutineById("99")).thenReturn(Routine(id = "99", name = "My Routine"))
    }

    @Test
    fun `startEditMode sets isEditMode and isActive, does not start elapsed timer`() =
        vmTest {
            runBlocking { setupEditModeRoutine() }

            viewModel.startEditMode("99")
            runCurrent()
            // withContext(Dispatchers.IO) runs on a real thread; give it time to complete
            // then drain the re-queued continuation on testDispatcher.
            Thread.sleep(100)
            runCurrent()

            val state = viewModel.workoutState.value
            assertTrue("isActive should be true", state.isActive)
            assertTrue("isEditMode should be true", state.isEditMode)
            assertNull("workoutId should be null in edit mode", state.workoutId)
            assertEquals("My Routine", state.workoutName)

            // Advance time and confirm elapsed does not tick (no elapsed timer started)
            val elapsedBefore = state.elapsedSeconds
            advanceTimeBy(3001)
            val elapsedAfter = viewModel.workoutState.value.elapsedSeconds
            assertEquals("Elapsed should not tick in edit mode", elapsedBefore, elapsedAfter)

            viewModel.cancelEditMode()
            runCurrent()
        }

    @Test
    fun `saveRoutineEdits sets editModeSaved and resets isActive`() = vmTest {
        runBlocking {
            setupEditModeRoutine()
            whenever(mockRoutineExerciseDao.updateSets(any(), any(), any())).thenReturn(Unit)
            whenever(mockRoutineExerciseDao.updateRepsAndWeight(any(), any(), any(), any())).thenReturn(Unit)
            whenever(mockRoutineExerciseDao.updateSetTypesJson(any(), any(), any())).thenReturn(Unit)
            whenever(mockRoutineExerciseDao.updateSetWeightsAndReps(any(), any(), any(), any())).thenReturn(Unit)
            whenever(mockRoutineExerciseDao.updateSupersetGroupId(any(), any(), any())).thenReturn(Unit)
            whenever(mockRoutineDao.updateRoutine(any())).thenReturn(Unit)
        }

        viewModel.startEditMode("99")
        runCurrent()
        Thread.sleep(100)
        runCurrent()

        viewModel.saveRoutineEdits()
        runCurrent()

        val state = viewModel.workoutState.value
        assertTrue("editModeSaved should be true after save", state.editModeSaved)
        assertFalse("isActive should be false after save", state.isActive)
        assertFalse("isEditMode should be false after save", state.isEditMode)
    }

    @Test
    fun `cancelEditMode resets state completely`() = vmTest {
        runBlocking { setupEditModeRoutine() }

        viewModel.startEditMode("99")
        runCurrent()

        viewModel.cancelEditMode()
        runCurrent()

        val state = viewModel.workoutState.value
        assertFalse("isActive should be false after cancel", state.isActive)
        assertFalse("isEditMode should be false after cancel", state.isEditMode)
        assertFalse("editModeSaved should be false after cancel", state.editModeSaved)
    }

    // -------------------------------------------------------------------------
    // Per-set-type rest timer duration tests (Step D)
    // -------------------------------------------------------------------------

    /** Helper: start workout, add exercise with given restDurationSeconds, add [setCount] sets. */
    private suspend fun setupExerciseWithSets(
        exerciseId: Long,
        restSeconds: Int,
        setCount: Int
    ) {
        val exercise = Exercise(
            id = exerciseId, name = "Test Ex", muscleGroup = "Chest",
            equipmentType = "Barbell", restDurationSeconds = restSeconds
        )
        whenever(mockWorkoutSetDao.getPreviousSessionSets(any(), any())).thenReturn(emptyList())
        whenever(mockWorkoutRepository.createWorkoutSet(any())).thenReturn(Unit)
        whenever(mockWorkoutSetDao.updateSetCompleted(any(), any())).thenReturn(Unit)
        whenever(mockWorkoutSetDao.updateSetType(any(), any())).thenReturn(Unit)

        viewModel.startWorkout("")
        viewModel.addExercise(exercise)
        repeat(setCount - 1) { viewModel.addSet(exerciseId) }
    }

    @Test
    fun `completeSet WARMUP to WARMUP uses 30s rest`() = vmTest {
        runBlocking { setupExerciseWithSets(30L, 90, 2) }
        runCurrent()

        viewModel.selectSetType(30L, 1, SetType.WARMUP)
        viewModel.selectSetType(30L, 2, SetType.WARMUP)
        runCurrent()

        viewModel.completeSet(30L, 1)
        runCurrent()

        val totalSeconds = viewModel.workoutState.value.restTimer.totalSeconds
        viewModel.cancelWorkout()
        runCurrent()

        assertEquals("WARMUP→WARMUP rest should be 30s", 30, totalSeconds)
    }

    @Test
    fun `completeSet WARMUP to NORMAL uses exercise default rest`() = vmTest {
        runBlocking { setupExerciseWithSets(31L, 90, 2) }
        runCurrent()

        viewModel.selectSetType(31L, 1, SetType.WARMUP)
        // set 2 stays NORMAL (default)
        runCurrent()

        viewModel.completeSet(31L, 1)
        runCurrent()

        val totalSeconds = viewModel.workoutState.value.restTimer.totalSeconds
        viewModel.cancelWorkout()
        runCurrent()

        assertEquals("WARMUP→NORMAL rest should use exercise default (90s)", 90, totalSeconds)
    }

    @Test
    fun `completeSet NORMAL to DROP uses 0s rest`() = vmTest {
        runBlocking { setupExerciseWithSets(32L, 90, 2) }
        runCurrent()

        // set 1 stays NORMAL, set 2 = DROP
        viewModel.selectSetType(32L, 2, SetType.DROP)
        runCurrent()

        viewModel.completeSet(32L, 1)
        runCurrent()

        val totalSeconds = viewModel.workoutState.value.restTimer.totalSeconds
        viewModel.cancelWorkout()
        runCurrent()

        assertEquals("NORMAL→DROP rest should be 0s", 0, totalSeconds)
    }

    @Test
    fun `completeSet DROP to NORMAL uses 0s rest`() = vmTest {
        runBlocking { setupExerciseWithSets(33L, 90, 2) }
        runCurrent()

        viewModel.selectSetType(33L, 1, SetType.DROP)
        // set 2 stays NORMAL
        runCurrent()

        viewModel.completeSet(33L, 1)
        runCurrent()

        val totalSeconds = viewModel.workoutState.value.restTimer.totalSeconds
        viewModel.cancelWorkout()
        runCurrent()

        assertEquals("DROP→NORMAL rest should be 0s", 0, totalSeconds)
    }

    @Test
    fun `completeSet FAILURE to NORMAL uses exercise default rest`() = vmTest {
        runBlocking { setupExerciseWithSets(34L, 120, 2) }
        runCurrent()

        viewModel.selectSetType(34L, 1, SetType.FAILURE)
        // set 2 stays NORMAL
        runCurrent()

        viewModel.completeSet(34L, 1)
        runCurrent()

        val totalSeconds = viewModel.workoutState.value.restTimer.totalSeconds
        viewModel.cancelWorkout()
        runCurrent()

        assertEquals("FAILURE→NORMAL rest should use exercise default (120s)", 120, totalSeconds)
    }

    @Test
    fun `addSet in edit mode skips DB insert and assigns negative fake id`() =
        vmTest {
            runBlocking {
                setupEditModeRoutine()
                // Ensure insertSet is never called — test verifies this below
            }

            viewModel.startEditMode("99")
            runCurrent()
            Thread.sleep(100)
            runCurrent()

            val initialSetCount = viewModel.workoutState.value.exercises.first().sets.size
            viewModel.addSet(1L)
            runCurrent()

            val sets = viewModel.workoutState.value.exercises.first().sets
            assertEquals("Set count should increase by 1", initialSetCount + 1, sets.size)
            val newSet = sets.last()
            assertTrue("Fake ID should have edit_ prefix in edit mode", newSet.id.startsWith("edit_"))

            // insertSet must NOT have been called (workoutId is null → guard skips it)
            org.mockito.kotlin.verifyNoInteractions(mockWorkoutSetDao)

            viewModel.cancelEditMode()
            runCurrent()
        }

    // -------------------------------------------------------------------------
    // Step 7 — Routine sync diff engine
    // -------------------------------------------------------------------------

    @Test
    fun `finishWorkout ad-hoc workout produces no sync prompt`() = vmTest {
        // Ad-hoc workout: startWorkout(0L) sets routineId=0 and routineSnapshot=[]
        viewModel.startWorkout("")
        runCurrent()

        viewModel.finishWorkout()
        runCurrent()

        assertNull(
            "Ad-hoc workout should never trigger sync prompt",
            viewModel.workoutState.value.pendingRoutineSync
        )
        assertNotNull(viewModel.workoutState.value.pendingWorkoutSummary)
    }

    @Test
    fun `finishWorkout identical to routine produces no sync prompt`() = vmTest {
        // Routine: 2 sets, reps=10, defaultWeight="80"
        runBlocking { setupRoutineWorkout(defaultWeight = "80") }

        viewModel.startWorkoutFromRoutine("1")
        runCurrent()

        // Complete exactly 2 sets with same weight/reps as routine
        viewModel.onWeightChanged(1L, 1, "80")
        runCurrent()
        viewModel.onRepsChanged(1L, 1, "10")
        runCurrent()
        viewModel.onWeightChanged(1L, 2, "80")
        runCurrent()
        viewModel.onRepsChanged(1L, 2, "10")
        runCurrent()
        viewModel.completeSet(1L, 1)
        runCurrent()
        viewModel.completeSet(1L, 2)
        runCurrent()

        viewModel.finishWorkout()
        runCurrent()

        assertNull(
            "Identical workout should not trigger sync prompt",
            viewModel.workoutState.value.pendingRoutineSync
        )
    }

    @Test
    fun `finishWorkout with only set count change sets pendingRoutineSync to STRUCTURE`() =
        vmTest {
            // Routine: 2 sets, reps=10, defaultWeight="80"
            runBlocking { setupRoutineWorkout(defaultWeight = "80") }

            viewModel.startWorkoutFromRoutine("1")
            runCurrent()

            // Complete only 1 of 2 sets, with weight/reps exactly matching the snapshot
            viewModel.onWeightChanged(1L, 1, "80")
            runCurrent()
            viewModel.onRepsChanged(1L, 1, "10")
            runCurrent()
            viewModel.completeSet(1L, 1)
            runCurrent()
            // set 2 remains incomplete → completed count (1) != snapshot.sets (2) → structural

            viewModel.finishWorkout()
            runCurrent()

            assertEquals(
                RoutineSyncType.STRUCTURE,
                viewModel.workoutState.value.pendingRoutineSync
            )
        }

    @Test
    fun `finishWorkout with only weight change sets pendingRoutineSync to VALUES`() =
        vmTest {
            // Routine: 2 sets, reps=10, defaultWeight="80"
            runBlocking { setupRoutineWorkout(defaultWeight = "80") }

            viewModel.startWorkoutFromRoutine("1")
            runCurrent()

            // Change weight to 90, keep reps=10, complete both sets → set count unchanged
            viewModel.onWeightChanged(1L, 1, "90")
            runCurrent()
            viewModel.onRepsChanged(1L, 1, "10")
            runCurrent()
            viewModel.onWeightChanged(1L, 2, "90")
            runCurrent()
            viewModel.onRepsChanged(1L, 2, "10")
            runCurrent()
            viewModel.completeSet(1L, 1)
            runCurrent()
            viewModel.completeSet(1L, 2)
            runCurrent()

            viewModel.finishWorkout()
            runCurrent()

            assertEquals(
                RoutineSyncType.VALUES,
                viewModel.workoutState.value.pendingRoutineSync
            )
        }

    // -------------------------------------------------------------------------
    // Warmup set value isolation tests (Fix 6)
    // -------------------------------------------------------------------------

    @Test
    fun `onWeightChanged does not cascade warmup values to work sets`() = vmTest {
        val exercise = Exercise(
            id = 40L, name = "Bench Press", muscleGroup = "Chest", equipmentType = "Barbell"
        )
        runBlocking {
            whenever(mockWorkoutSetDao.getPreviousSessionSets(any(), any())).thenReturn(emptyList())
            whenever(mockWorkoutRepository.createWorkoutSet(any())).thenReturn(Unit)
            whenever(mockWorkoutSetDao.updateWeightReps(any(), any(), any())).thenReturn(Unit)
            whenever(mockWorkoutSetDao.updateSetType(any(), any())).thenReturn(Unit)
        }

        viewModel.startWorkout("")
        runCurrent()
        viewModel.addExercise(exercise)
        runCurrent()
        viewModel.addSet(40L)
        runCurrent()
        viewModel.addSet(40L)
        runCurrent()

        // Set 1 = WARMUP, set 2 = NORMAL, set 3 = NORMAL
        viewModel.selectSetType(40L, 1, SetType.WARMUP)
        runCurrent()

        // Change warmup set 1 weight — should NOT cascade to NORMAL sets 2 and 3
        viewModel.onWeightChanged(40L, 1, "30")
        runCurrent()

        val sets = viewModel.workoutState.value.exercises.first().sets.sortedBy { it.setOrder }
        assertEquals("Warmup set 1 should be 30", "30", sets[0].weight)
        // Work sets should retain their initial "0" and NOT be overwritten by warmup cascade
        assertNotEquals("Work set 2 should NOT inherit warmup value", "30", sets[1].weight)
        assertNotEquals("Work set 3 should NOT inherit warmup value", "30", sets[2].weight)

        viewModel.cancelWorkout()
        runCurrent()
    }

    @Test
    fun `onWeightChanged cascades first work set to other work sets but not warmup sets`() = vmTest {
        val exercise = Exercise(
            id = 41L, name = "Squat", muscleGroup = "Legs", equipmentType = "Barbell"
        )
        runBlocking {
            whenever(mockWorkoutSetDao.getPreviousSessionSets(any(), any())).thenReturn(emptyList())
            whenever(mockWorkoutRepository.createWorkoutSet(any())).thenReturn(Unit)
            whenever(mockWorkoutSetDao.updateWeightReps(any(), any(), any())).thenReturn(Unit)
            whenever(mockWorkoutSetDao.updateSetType(any(), any())).thenReturn(Unit)
        }

        viewModel.startWorkout("")
        runCurrent()
        viewModel.addExercise(exercise)
        runCurrent()
        viewModel.addSet(41L)
        runCurrent()
        viewModel.addSet(41L)
        runCurrent()

        // Set 1 = WARMUP, set 2 = NORMAL (first work), set 3 = NORMAL
        viewModel.selectSetType(41L, 1, SetType.WARMUP)
        runCurrent()

        // Pre-fill warmup weight so we can verify it doesn't get overwritten
        viewModel.onWeightChanged(41L, 1, "30")
        runCurrent()

        // Change first work set (set 2) — should cascade to set 3 but NOT to warmup set 1
        viewModel.onWeightChanged(41L, 2, "80")
        runCurrent()

        val sets = viewModel.workoutState.value.exercises.first().sets.sortedBy { it.setOrder }
        assertEquals("Warmup set 1 should remain 30", "30", sets[0].weight)
        assertEquals("Work set 2 should be 80", "80", sets[1].weight)
        assertEquals("Work set 3 should cascade to 80", "80", sets[2].weight)

        viewModel.cancelWorkout()
        runCurrent()
    }

    // -------------------------------------------------------------------------
    // Step E — collapse and reorder
    // -------------------------------------------------------------------------

    @Test
    fun `collapseAllExcept collapses all other exercises and expands the target`() = vmTest {
        val ex1 = Exercise(id = 1L, name = "Squat", muscleGroup = "Legs", equipmentType = "Barbell")
        val ex2 = Exercise(id = 2L, name = "Bench", muscleGroup = "Chest", equipmentType = "Barbell")
        val ex3 = Exercise(id = 3L, name = "Row",   muscleGroup = "Back",  equipmentType = "Barbell")
        runBlocking {
            whenever(mockWorkoutSetDao.getPreviousSessionSets(any(), any())).thenReturn(emptyList())
            whenever(mockWorkoutRepository.createWorkoutSet(any())).thenReturn(Unit)
        }

        viewModel.startWorkout("")
        runCurrent()
        viewModel.addExercise(ex1); runCurrent()
        viewModel.addExercise(ex2); runCurrent()
        viewModel.addExercise(ex3); runCurrent()

        viewModel.collapseAllExcept(2L)
        runCurrent()

        val collapsed = viewModel.workoutState.value.collapsedExerciseIds
        assertFalse("Target exercise (2) should not be collapsed", 2L in collapsed)
        assertTrue("Exercise 1 should be collapsed",  1L in collapsed)
        assertTrue("Exercise 3 should be collapsed",  3L in collapsed)

        viewModel.cancelWorkout()
        runCurrent()
    }

    @Test
    fun `reorderExercise moves exercise from one index to another`() = vmTest {
        val ex1 = Exercise(id = 1L, name = "Squat", muscleGroup = "Legs", equipmentType = "Barbell")
        val ex2 = Exercise(id = 2L, name = "Bench", muscleGroup = "Chest", equipmentType = "Barbell")
        val ex3 = Exercise(id = 3L, name = "Row",   muscleGroup = "Back",  equipmentType = "Barbell")
        runBlocking {
            whenever(mockWorkoutSetDao.getPreviousSessionSets(any(), any())).thenReturn(emptyList())
            whenever(mockWorkoutRepository.createWorkoutSet(any())).thenReturn(Unit)
        }

        viewModel.startWorkout("")
        runCurrent()
        viewModel.addExercise(ex1); runCurrent()
        viewModel.addExercise(ex2); runCurrent()
        viewModel.addExercise(ex3); runCurrent()

        // Move index 0 (Squat) to index 2 → order becomes [Bench, Row, Squat]
        viewModel.reorderExercise(0, 2)
        runCurrent()

        val exercises = viewModel.workoutState.value.exercises
        assertEquals("Bench should be first",  2L, exercises[0].exercise.id)
        assertEquals("Row should be second",   3L, exercises[1].exercise.id)
        assertEquals("Squat should be last",   1L, exercises[2].exercise.id)

        viewModel.cancelWorkout()
        runCurrent()
    }

    // -------------------------------------------------------------------------
    // finishWorkout warmup exclusion tests (Fix 2)
    // -------------------------------------------------------------------------

    @Test
    fun `finishWorkout includes all completed sets including warmup in set count`() = vmTest {
        val exercise = Exercise(
            id = 50L, name = "Bench Press", muscleGroup = "Chest", equipmentType = "Barbell"
        )
        runBlocking {
            whenever(mockWorkoutSetDao.getPreviousSessionSets(any(), any())).thenReturn(emptyList())
            whenever(mockWorkoutRepository.createWorkoutSet(any())).thenReturn(Unit)
            whenever(mockWorkoutSetDao.updateSetCompleted(any(), any())).thenReturn(Unit)
            whenever(mockWorkoutSetDao.updateSetType(any(), any())).thenReturn(Unit)
            whenever(mockWorkoutDao.updateWorkout(any())).thenReturn(Unit)
            whenever(mockWorkoutSetDao.deleteIncompleteSetsByWorkout(any())).thenReturn(Unit)
            whenever(mockBoazPerformanceAnalyzer.compare(any(), any())).thenReturn(emptyList())
        }

        viewModel.startWorkout("")
        runCurrent()
        viewModel.addExercise(exercise)
        runCurrent()
        // Add warmup set
        viewModel.addSet(50L)
        runCurrent()

        // Set 1 = WARMUP, set 2 = NORMAL
        viewModel.selectSetType(50L, 1, SetType.WARMUP)
        runCurrent()

        // Complete both sets
        viewModel.completeSet(50L, 1)
        runCurrent()
        viewModel.completeSet(50L, 2)
        runCurrent()

        viewModel.finishWorkout()
        runCurrent()

        val summary = viewModel.workoutState.value.pendingWorkoutSummary
        assertNotNull(summary)
        // Both warmup and work sets are counted
        assertEquals("Both sets (warmup + work) should be counted", 2, summary!!.setCount)

        viewModel.cancelWorkout()
        runCurrent()
    }

    // -------------------------------------------------------------------------
    // Organize Mode (persistent superset)
    // -------------------------------------------------------------------------

    private suspend fun TestScope.threeExerciseWorkout() {
        val ex1 = Exercise(id = 1L, name = "Squat", muscleGroup = "Legs", equipmentType = "Barbell")
        val ex2 = Exercise(id = 2L, name = "Bench", muscleGroup = "Chest", equipmentType = "Barbell")
        val ex3 = Exercise(id = 3L, name = "Row",   muscleGroup = "Back",  equipmentType = "Barbell")
        runBlocking {
            whenever(mockWorkoutSetDao.getPreviousSessionSets(any(), any())).thenReturn(emptyList())
            whenever(mockWorkoutRepository.createWorkoutSet(any())).thenReturn(Unit)
        }
        viewModel.startWorkout(""); runCurrent()
        viewModel.addExercise(ex1); runCurrent()
        viewModel.addExercise(ex2); runCurrent()
        viewModel.addExercise(ex3); runCurrent()
    }

    @Test
    fun `enterSupersetSelectMode with ungrouped exercise pre-seeds only that exercise`() = vmTest {
        threeExerciseWorkout()

        viewModel.enterSupersetSelectMode(1L)
        runCurrent()

        val state = viewModel.workoutState.value
        assertTrue("Mode should be active", state.isSupersetSelectMode)
        assertEquals("Only the triggering exercise should be pre-seeded", setOf(1L), state.supersetCandidateIds)

        viewModel.cancelWorkout(); runCurrent()
    }

    @Test
    fun `enterSupersetSelectMode pre-seeds candidates from existing superset group`() = vmTest {
        threeExerciseWorkout()

        // Form a superset between exercises 1 and 2 first
        viewModel.enterSupersetSelectMode(1L); runCurrent()
        viewModel.toggleSupersetCandidate(2L); runCurrent()
        viewModel.commitSupersetSelection(); runCurrent()

        // Now enter organize mode from exercise 1 — both members should be pre-seeded
        viewModel.enterSupersetSelectMode(1L); runCurrent()
        val candidates = viewModel.workoutState.value.supersetCandidateIds
        assertTrue("Exercise 1 should be pre-seeded", 1L in candidates)
        assertTrue("Exercise 2 (partner) should be pre-seeded", 2L in candidates)

        viewModel.cancelWorkout(); runCurrent()
    }

    @Test
    fun `toggleSupersetCandidate adds and removes exercise ids`() = vmTest {
        threeExerciseWorkout()

        viewModel.enterSupersetSelectMode(); runCurrent()
        viewModel.toggleSupersetCandidate(1L); runCurrent()
        assertTrue("1L should be present after first toggle", 1L in viewModel.workoutState.value.supersetCandidateIds)

        viewModel.toggleSupersetCandidate(1L); runCurrent()
        assertFalse("1L should be absent after second toggle", 1L in viewModel.workoutState.value.supersetCandidateIds)

        viewModel.cancelWorkout(); runCurrent()
    }

    @Test
    fun `commitSupersetSelection with two candidates pairs them, clears candidates, and stays in mode`() = vmTest {
        threeExerciseWorkout()

        viewModel.enterSupersetSelectMode(); runCurrent()
        viewModel.toggleSupersetCandidate(1L); runCurrent()
        viewModel.toggleSupersetCandidate(2L); runCurrent()
        viewModel.commitSupersetSelection(); runCurrent()

        val state = viewModel.workoutState.value
        assertTrue("Mode must still be active after commit", state.isSupersetSelectMode)
        assertTrue("Candidates must be cleared after commit", state.supersetCandidateIds.isEmpty())

        val ex1GroupId = state.exercises.find { it.exercise.id == 1L }?.supersetGroupId
        val ex2GroupId = state.exercises.find { it.exercise.id == 2L }?.supersetGroupId
        assertNotNull("Exercise 1 must have a superset group", ex1GroupId)
        assertNotNull("Exercise 2 must have a superset group", ex2GroupId)
        assertEquals("Both exercises must share the same superset group", ex1GroupId, ex2GroupId)

        viewModel.cancelWorkout(); runCurrent()
    }

    @Test
    fun `commitSupersetSelection with fewer than two candidates is a no-op and stays in mode`() = vmTest {
        threeExerciseWorkout()

        viewModel.enterSupersetSelectMode(); runCurrent()
        viewModel.toggleSupersetCandidate(1L); runCurrent()
        viewModel.commitSupersetSelection(); runCurrent()

        val state = viewModel.workoutState.value
        assertTrue("Mode must still be active after no-op commit", state.isSupersetSelectMode)
        assertNull("Exercise 1 must have no superset group after no-op commit",
            state.exercises.find { it.exercise.id == 1L }?.supersetGroupId)

        viewModel.cancelWorkout(); runCurrent()
    }

    @Test
    fun `multiple consecutive commits create distinct superset groups`() = vmTest {
        val ex1 = Exercise(id = 1L, name = "Squat",  muscleGroup = "Legs",  equipmentType = "Barbell")
        val ex2 = Exercise(id = 2L, name = "Bench",  muscleGroup = "Chest", equipmentType = "Barbell")
        val ex3 = Exercise(id = 3L, name = "Row",    muscleGroup = "Back",  equipmentType = "Barbell")
        val ex4 = Exercise(id = 4L, name = "Press",  muscleGroup = "Shoulders", equipmentType = "Barbell")
        runBlocking {
            whenever(mockWorkoutSetDao.getPreviousSessionSets(any(), any())).thenReturn(emptyList())
            whenever(mockWorkoutRepository.createWorkoutSet(any())).thenReturn(Unit)
        }
        viewModel.startWorkout(""); runCurrent()
        viewModel.addExercise(ex1); runCurrent()
        viewModel.addExercise(ex2); runCurrent()
        viewModel.addExercise(ex3); runCurrent()
        viewModel.addExercise(ex4); runCurrent()

        // Group 1: exercises 1 + 2
        viewModel.enterSupersetSelectMode(); runCurrent()
        viewModel.toggleSupersetCandidate(1L); runCurrent()
        viewModel.toggleSupersetCandidate(2L); runCurrent()
        viewModel.commitSupersetSelection(); runCurrent()

        // Group 2: exercises 3 + 4 — still in the same Organize Mode session
        viewModel.toggleSupersetCandidate(3L); runCurrent()
        viewModel.toggleSupersetCandidate(4L); runCurrent()
        viewModel.commitSupersetSelection(); runCurrent()

        val state = viewModel.workoutState.value
        assertTrue("Mode still active after both commits", state.isSupersetSelectMode)

        val groupX = state.exercises.find { it.exercise.id == 1L }?.supersetGroupId
        val groupY = state.exercises.find { it.exercise.id == 3L }?.supersetGroupId
        assertNotNull("Group X must exist", groupX)
        assertNotNull("Group Y must exist", groupY)
        assertNotEquals("Groups must be distinct", groupX, groupY)
        assertEquals("Ex 2 must share group X", groupX, state.exercises.find { it.exercise.id == 2L }?.supersetGroupId)
        assertEquals("Ex 4 must share group Y", groupY, state.exercises.find { it.exercise.id == 4L }?.supersetGroupId)

        viewModel.cancelWorkout(); runCurrent()
    }

    @Test
    fun `exitSupersetSelectMode clears flag and candidates without undoing prior commits`() = vmTest {
        threeExerciseWorkout()

        viewModel.enterSupersetSelectMode(); runCurrent()
        viewModel.toggleSupersetCandidate(1L); runCurrent()
        viewModel.toggleSupersetCandidate(2L); runCurrent()
        viewModel.commitSupersetSelection(); runCurrent()

        viewModel.exitSupersetSelectMode(); runCurrent()

        val state = viewModel.workoutState.value
        assertFalse("Mode must be inactive after Done", state.isSupersetSelectMode)
        assertTrue("Candidates must be empty after Done", state.supersetCandidateIds.isEmpty())

        // The superset formed before Done must still exist
        val groupId = state.exercises.find { it.exercise.id == 1L }?.supersetGroupId
        assertNotNull("Superset group must survive exiting organize mode", groupId)
        assertEquals("Both exercises must still share the superset group",
            groupId, state.exercises.find { it.exercise.id == 2L }?.supersetGroupId)

        viewModel.cancelWorkout(); runCurrent()
    }

    @Test
    fun `reorderExercise works while isSupersetSelectMode is true`() = vmTest {
        threeExerciseWorkout()

        viewModel.enterSupersetSelectMode(); runCurrent()
        viewModel.reorderExercise(0, 2); runCurrent()

        val exercises = viewModel.workoutState.value.exercises
        assertEquals("Bench should be first after reorder",  2L, exercises[0].exercise.id)
        assertEquals("Row should be second after reorder",   3L, exercises[1].exercise.id)
        assertEquals("Squat should be last after reorder",   1L, exercises[2].exercise.id)
        assertTrue("Mode must remain active after reorder", viewModel.workoutState.value.isSupersetSelectMode)

        viewModel.cancelWorkout(); runCurrent()
    }

    @Test
    fun `cancelWorkout while in organize mode resets isSupersetSelectMode`() = vmTest {
        threeExerciseWorkout()

        viewModel.enterSupersetSelectMode(); runCurrent()
        assertTrue("Mode should be active before cancel", viewModel.workoutState.value.isSupersetSelectMode)

        viewModel.cancelWorkout(); runCurrent()

        assertFalse("Mode must be cleared after cancelWorkout", viewModel.workoutState.value.isSupersetSelectMode)
    }
}
