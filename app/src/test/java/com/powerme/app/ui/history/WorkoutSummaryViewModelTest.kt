package com.powerme.app.ui.history

import androidx.lifecycle.SavedStateHandle
import com.powerme.app.analytics.StatisticalEngine
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.UnitSystem
import com.powerme.app.data.database.ExerciseType
import com.powerme.app.data.database.SetType
import com.powerme.app.data.database.Workout
import com.powerme.app.data.database.WorkoutBlock
import com.powerme.app.data.database.WorkoutBlockDao
import com.powerme.app.data.database.WorkoutDao
import com.powerme.app.data.database.WorkoutSet
import com.powerme.app.data.database.WorkoutSetDao
import com.powerme.app.data.database.WorkoutSetWithExercise
import com.powerme.app.data.sync.FirestoreSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutSummaryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var workoutDao: WorkoutDao
    private lateinit var workoutSetDao: WorkoutSetDao
    private lateinit var workoutBlockDao: WorkoutBlockDao
    private lateinit var appSettingsDataStore: AppSettingsDataStore
    private lateinit var firestoreSyncManager: FirestoreSyncManager

    private val workoutId = "test-workout-uuid"
    private val exerciseId1 = 1L
    private val exerciseId2 = 2L
    private val timestamp = 1_700_000_000_000L

    private val baseWorkout = Workout(
        id = workoutId,
        routineId = null,
        routineName = "Push Day",
        timestamp = timestamp,
        durationSeconds = 3600,
        totalVolume = 5000.0,
        notes = null,
        isCompleted = true,
        startTimeMs = timestamp,
        endTimeMs = timestamp + 3_600_000L,
        updatedAt = timestamp
    )

    private fun makeSetWithExercise(
        exerciseId: Long = exerciseId1,
        weight: Double = 100.0,
        reps: Int = 5,
        rpe: Int? = null,
        setType: SetType = SetType.NORMAL,
        setOrder: Int = 1,
        isCompleted: Boolean = true,
        supersetGroupId: String? = null,
        exerciseName: String = "Bench Press",
        muscleGroup: String? = "Chest"
    ) = WorkoutSetWithExercise(
        id = UUID.randomUUID().toString(),
        workoutId = workoutId,
        exerciseId = exerciseId,
        setOrder = setOrder,
        weight = weight,
        reps = reps,
        rpe = rpe,
        setType = setType,
        setNotes = null,
        supersetGroupId = supersetGroupId,
        isCompleted = isCompleted,
        exerciseName = exerciseName,
        muscleGroup = muscleGroup,
        equipmentType = null,
        exerciseType = ExerciseType.STRENGTH,
        distance = null,
        timeSeconds = null
    )

    private fun makeWorkoutSet(
        exerciseId: Long = exerciseId1,
        weight: Double = 80.0,
        reps: Int = 5,
        setType: SetType = SetType.NORMAL
    ) = WorkoutSet(
        id = UUID.randomUUID().toString(),
        workoutId = "prev-workout",
        exerciseId = exerciseId,
        setOrder = 1,
        weight = weight,
        reps = reps,
        rpe = null,
        setType = setType,
        setNotes = null,
        supersetGroupId = null,
        isCompleted = true
    )

    private fun buildViewModel(
        isPostWorkout: Boolean = false,
        syncType: String = "NONE"
    ): WorkoutSummaryViewModel {
        val handle = SavedStateHandle(
            mapOf(
                "workoutId" to workoutId,
                "isPostWorkout" to isPostWorkout,
                "syncType" to syncType
            )
        )
        return WorkoutSummaryViewModel(handle, workoutDao, workoutSetDao, workoutBlockDao, appSettingsDataStore, firestoreSyncManager)
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        workoutDao = mock()
        workoutSetDao = mock()
        workoutBlockDao = mock()
        appSettingsDataStore = mock()
        firestoreSyncManager = mock()

        whenever(appSettingsDataStore.unitSystem).thenReturn(flowOf(UnitSystem.METRIC))
        runBlocking { whenever(workoutBlockDao.getFunctionalBlocksForWorkout(any())).thenReturn(emptyList()) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Loading ───────────────────────────────────────────────────────────────

    @Test
    fun `loads workout and sets on init`() = runTest(testDispatcher) {
        val sets = listOf(makeSetWithExercise())
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(sets)
        whenever(workoutSetDao.getPreviousSessionCompletedSets(any(), any())).thenReturn(emptyList())
        whenever(workoutSetDao.getHistoricalBestE1RM(any(), any())).thenReturn(null)

        val vm = buildViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(baseWorkout, state.workout)
        assertEquals(1, state.exerciseCards.size)
    }

    @Test
    fun `null workout results in empty state`() = runTest(testDispatcher) {
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(null)

        val vm = buildViewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.workout)
        assertTrue(vm.uiState.value.exerciseCards.isEmpty())
    }

    // ── Best set selection ────────────────────────────────────────────────────

    @Test
    fun `best set is the one with highest e1RM`() = runTest(testDispatcher) {
        // Set A: 100kg x 5 → e1RM = 100 * (1 + 5/30) = 116.67
        // Set B: 80kg x 10 → e1RM = 80 * (1 + 10/30) = 106.67
        // Set C: 120kg x 1 → e1RM = 120 * (1 + 1/30) = 124.0
        val sets = listOf(
            makeSetWithExercise(weight = 100.0, reps = 5),
            makeSetWithExercise(weight = 80.0, reps = 10),
            makeSetWithExercise(weight = 120.0, reps = 1)
        )
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(sets)
        whenever(workoutSetDao.getPreviousSessionCompletedSets(any(), any())).thenReturn(emptyList())
        whenever(workoutSetDao.getHistoricalBestE1RM(any(), any())).thenReturn(null)

        val vm = buildViewModel()
        advanceUntilIdle()

        val card = vm.uiState.value.exerciseCards.first()
        assertEquals(120.0, card.bestSetWeight, 0.01)
        assertEquals(1, card.bestSetReps)
        assertEquals(StatisticalEngine.calculate1RM(120.0, 1), card.e1RM, 0.01)
    }

    // ── WARMUP exclusion ──────────────────────────────────────────────────────

    @Test
    fun `WARMUP sets excluded from card stats`() = runTest(testDispatcher) {
        val warmupSet = makeSetWithExercise(weight = 50.0, reps = 10, setType = SetType.WARMUP)
        val workingSet = makeSetWithExercise(weight = 100.0, reps = 5, setType = SetType.NORMAL)
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(listOf(warmupSet, workingSet))
        whenever(workoutSetDao.getPreviousSessionCompletedSets(any(), any())).thenReturn(emptyList())
        whenever(workoutSetDao.getHistoricalBestE1RM(any(), any())).thenReturn(null)

        val vm = buildViewModel()
        advanceUntilIdle()

        val card = vm.uiState.value.exerciseCards.first()
        // Only the working set counts
        assertEquals(1, card.setCount)
        assertEquals(100.0, card.bestSetWeight, 0.01)
    }

    // ── Volume delta ──────────────────────────────────────────────────────────

    @Test
    fun `volume delta is null when no previous session`() = runTest(testDispatcher) {
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId))
            .thenReturn(listOf(makeSetWithExercise(weight = 100.0, reps = 5)))
        whenever(workoutSetDao.getPreviousSessionCompletedSets(eq(exerciseId1), any())).thenReturn(emptyList())
        whenever(workoutSetDao.getHistoricalBestE1RM(any(), any())).thenReturn(null)

        val vm = buildViewModel()
        advanceUntilIdle()

        assertNull(vm.uiState.value.exerciseCards.first().volumeDeltaPercent)
    }

    @Test
    fun `volume delta positive when current session volume is higher`() = runTest(testDispatcher) {
        // Current: 100kg x 5 = 500 volume
        // Previous: 80kg x 5 = 400 volume → delta = (500-400)/400 * 100 = +25%
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId))
            .thenReturn(listOf(makeSetWithExercise(weight = 100.0, reps = 5)))
        whenever(workoutSetDao.getPreviousSessionCompletedSets(eq(exerciseId1), any()))
            .thenReturn(listOf(makeWorkoutSet(weight = 80.0, reps = 5)))
        whenever(workoutSetDao.getHistoricalBestE1RM(any(), any())).thenReturn(null)

        val vm = buildViewModel()
        advanceUntilIdle()

        val delta = vm.uiState.value.exerciseCards.first().volumeDeltaPercent
        assertNotNull(delta)
        assertEquals(25.0, delta!!, 0.01)
    }

    @Test
    fun `volume delta negative when current session volume is lower`() = runTest(testDispatcher) {
        // Current: 60kg x 5 = 300 volume
        // Previous: 80kg x 5 = 400 volume → delta = (300-400)/400 * 100 = -25%
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId))
            .thenReturn(listOf(makeSetWithExercise(weight = 60.0, reps = 5)))
        whenever(workoutSetDao.getPreviousSessionCompletedSets(eq(exerciseId1), any()))
            .thenReturn(listOf(makeWorkoutSet(weight = 80.0, reps = 5)))
        whenever(workoutSetDao.getHistoricalBestE1RM(any(), any())).thenReturn(null)

        val vm = buildViewModel()
        advanceUntilIdle()

        val delta = vm.uiState.value.exerciseCards.first().volumeDeltaPercent
        assertNotNull(delta)
        assertEquals(-25.0, delta!!, 0.01)
    }

    // ── Avg RPE ───────────────────────────────────────────────────────────────

    @Test
    fun `avgRpe is null when fewer than 50 percent of sets have RPE`() = runTest(testDispatcher) {
        // 1 of 3 sets has RPE → 33% → below 50% threshold → avgRpe is null
        // RPE stored on ×10 scale: 80 = RPE 8.0
        val sets = listOf(
            makeSetWithExercise(rpe = 80),
            makeSetWithExercise(rpe = null),
            makeSetWithExercise(rpe = null)
        )
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(sets)
        whenever(workoutSetDao.getPreviousSessionCompletedSets(any(), any())).thenReturn(emptyList())
        whenever(workoutSetDao.getHistoricalBestE1RM(any(), any())).thenReturn(null)

        val vm = buildViewModel()
        advanceUntilIdle()

        assertNull(vm.uiState.value.exerciseCards.first().avgRpe)
    }

    @Test
    fun `avgRpe computed when at least 50 percent of sets have RPE`() = runTest(testDispatcher) {
        // 2 of 3 sets have RPE → 66.7% → shown
        // Storage values: 80 (RPE 8.0) + 90 (RPE 9.0) → avg storage = 85 → display = 8.5
        val sets = listOf(
            makeSetWithExercise(rpe = 80),
            makeSetWithExercise(rpe = 90),
            makeSetWithExercise(rpe = null)
        )
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(sets)
        whenever(workoutSetDao.getPreviousSessionCompletedSets(any(), any())).thenReturn(emptyList())
        whenever(workoutSetDao.getHistoricalBestE1RM(any(), any())).thenReturn(null)

        val vm = buildViewModel()
        advanceUntilIdle()

        val avgRpe = vm.uiState.value.exerciseCards.first().avgRpe
        assertNotNull(avgRpe)
        assertEquals(8.5, avgRpe!!, 0.01)
    }

    @Test
    fun `avgRpe divides stored x10 values by 10 for display`() = runTest(testDispatcher) {
        // Regression test: storage RPE 80 (8.0), 95 (9.5), 70 (7.0), 100 (10.0)
        // Raw average = (80+95+70+100)/4 = 86.25; display avg = 8.625
        val sets = listOf(
            makeSetWithExercise(rpe = 80),
            makeSetWithExercise(rpe = 95),
            makeSetWithExercise(rpe = 70),
            makeSetWithExercise(rpe = 100)
        )
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(sets)
        whenever(workoutSetDao.getPreviousSessionCompletedSets(any(), any())).thenReturn(emptyList())
        whenever(workoutSetDao.getHistoricalBestE1RM(any(), any())).thenReturn(null)

        val vm = buildViewModel()
        advanceUntilIdle()

        val avgRpe = vm.uiState.value.exerciseCards.first().avgRpe
        assertNotNull(avgRpe)
        // Must be display-scale (8.625), NOT raw storage average (86.25)
        assertEquals(8.625, avgRpe!!, 0.001)
    }

    // ── Golden zone ───────────────────────────────────────────────────────────

    @Test
    fun `isGoldenZone true when avgRpe in 8 to 9 range`() = runTest(testDispatcher) {
        // Storage: 80 (8.0) + 90 (9.0) → display avg = 8.5 → golden zone
        val sets = listOf(makeSetWithExercise(rpe = 80), makeSetWithExercise(rpe = 90))
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(sets)
        whenever(workoutSetDao.getPreviousSessionCompletedSets(any(), any())).thenReturn(emptyList())
        whenever(workoutSetDao.getHistoricalBestE1RM(any(), any())).thenReturn(null)

        val vm = buildViewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.exerciseCards.first().isGoldenZone)
    }

    @Test
    fun `isGoldenZone false when avgRpe outside 8 to 9 range`() = runTest(testDispatcher) {
        // Storage: 70 + 70 → display avg = 7.0 → not golden zone
        val sets = listOf(makeSetWithExercise(rpe = 70), makeSetWithExercise(rpe = 70))
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(sets)
        whenever(workoutSetDao.getPreviousSessionCompletedSets(any(), any())).thenReturn(emptyList())
        whenever(workoutSetDao.getHistoricalBestE1RM(any(), any())).thenReturn(null)

        val vm = buildViewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.exerciseCards.first().isGoldenZone)
    }

    // ── PR detection ──────────────────────────────────────────────────────────

    @Test
    fun `isPR true when current e1RM exceeds historical best`() = runTest(testDispatcher) {
        // Current e1RM = 100 * (1 + 5/30) = 116.67; historical = 110.0
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId))
            .thenReturn(listOf(makeSetWithExercise(weight = 100.0, reps = 5)))
        whenever(workoutSetDao.getPreviousSessionCompletedSets(any(), any())).thenReturn(emptyList())
        whenever(workoutSetDao.getHistoricalBestE1RM(eq(exerciseId1), any())).thenReturn(110.0)

        val vm = buildViewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.exerciseCards.first().isPR)
    }

    @Test
    fun `isPR false when current e1RM equals historical best`() = runTest(testDispatcher) {
        val e1RM = StatisticalEngine.calculate1RM(100.0, 5)
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId))
            .thenReturn(listOf(makeSetWithExercise(weight = 100.0, reps = 5)))
        whenever(workoutSetDao.getPreviousSessionCompletedSets(any(), any())).thenReturn(emptyList())
        whenever(workoutSetDao.getHistoricalBestE1RM(eq(exerciseId1), any())).thenReturn(e1RM)

        val vm = buildViewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.exerciseCards.first().isPR)
    }

    @Test
    fun `isPR true on first session ever (null historical best treated as 0)`() = runTest(testDispatcher) {
        // Historical best is null (no prior sessions) → defaults to 0.0
        // Any valid lift e1RM > 0 qualifies as a PR
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId))
            .thenReturn(listOf(makeSetWithExercise(weight = 100.0, reps = 5)))
        whenever(workoutSetDao.getPreviousSessionCompletedSets(any(), any())).thenReturn(emptyList())
        whenever(workoutSetDao.getHistoricalBestE1RM(any(), any())).thenReturn(null)

        val vm = buildViewModel()
        advanceUntilIdle()

        // First-ever session → historical = 0.0 → current e1RM (116.67) > 0 → IS a PR
        assertTrue(vm.uiState.value.exerciseCards.first().isPR)
    }

    // ── PR count ──────────────────────────────────────────────────────────────

    @Test
    fun `prCount reflects number of exercises with PRs`() = runTest(testDispatcher) {
        val sets = listOf(
            makeSetWithExercise(exerciseId = exerciseId1, exerciseName = "Bench"),
            makeSetWithExercise(exerciseId = exerciseId2, exerciseName = "Squat", muscleGroup = "Legs")
        )
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(sets)
        whenever(workoutSetDao.getPreviousSessionCompletedSets(any(), any())).thenReturn(emptyList())
        // Exercise 1: historical = 110 → current ~116 → PR
        whenever(workoutSetDao.getHistoricalBestE1RM(eq(exerciseId1), any())).thenReturn(110.0)
        // Exercise 2: historical = 130 → current ~116 → no PR
        whenever(workoutSetDao.getHistoricalBestE1RM(eq(exerciseId2), any())).thenReturn(130.0)

        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.prCount)
    }

    // ── Muscle group bars ─────────────────────────────────────────────────────

    @Test
    fun `muscle group bars computed correctly`() = runTest(testDispatcher) {
        // Chest: 100*5 = 500 volume
        // Legs: 200*3 = 600 volume → max → fraction = 1.0
        // Chest fraction = 500/600
        val sets = listOf(
            makeSetWithExercise(exerciseId = exerciseId1, weight = 100.0, reps = 5, muscleGroup = "Chest", exerciseName = "Bench"),
            makeSetWithExercise(exerciseId = exerciseId2, weight = 200.0, reps = 3, muscleGroup = "Legs", exerciseName = "Squat")
        )
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(sets)
        whenever(workoutSetDao.getPreviousSessionCompletedSets(any(), any())).thenReturn(emptyList())
        whenever(workoutSetDao.getHistoricalBestE1RM(any(), any())).thenReturn(null)

        val vm = buildViewModel()
        advanceUntilIdle()

        val bars = vm.uiState.value.muscleGroupBars
        assertEquals(2, bars.size)
        // Sorted by descending volume: Legs first
        assertEquals("Legs", bars[0].group)
        assertEquals(1.0f, bars[0].fraction, 0.001f)
        assertEquals("Chest", bars[1].group)
        assertEquals(500f / 600f, bars[1].fraction, 0.001f)
    }

    @Test
    fun `muscle group bars exclude WARMUP sets`() = runTest(testDispatcher) {
        val warmup = makeSetWithExercise(weight = 40.0, reps = 10, setType = SetType.WARMUP, muscleGroup = "Chest")
        val working = makeSetWithExercise(weight = 100.0, reps = 5, setType = SetType.NORMAL, muscleGroup = "Chest")
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(listOf(warmup, working))
        whenever(workoutSetDao.getPreviousSessionCompletedSets(any(), any())).thenReturn(emptyList())
        whenever(workoutSetDao.getHistoricalBestE1RM(any(), any())).thenReturn(null)

        val vm = buildViewModel()
        advanceUntilIdle()

        val bars = vm.uiState.value.muscleGroupBars
        assertEquals(1, bars.size)
        // Volume should only include working set: 100 * 5 = 500
        assertEquals(500.0, bars[0].volume, 0.01)
    }

    // ── Session rating ────────────────────────────────────────────────────────

    @Test
    fun `setSessionRating updates state and calls DAO`() = runTest(testDispatcher) {
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(emptyList())

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.setSessionRating(4)
        advanceUntilIdle()

        verify(workoutDao).updateSessionRating(eq(workoutId), eq(4), any())
        verify(firestoreSyncManager).pushWorkout(workoutId)
        assertEquals(4, vm.uiState.value.workout?.sessionRating)
    }

    // ── isPostWorkout / syncType propagated ───────────────────────────────────

    @Test
    fun `isPostWorkout flag propagated from SavedStateHandle`() = runTest(testDispatcher) {
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(emptyList())

        val vm = buildViewModel(isPostWorkout = true)
        advanceUntilIdle()

        assertTrue(vm.uiState.value.isPostWorkout)
    }

    @Test
    fun `pendingRoutineSync parsed from syncType argument`() = runTest(testDispatcher) {
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(emptyList())

        val vm = buildViewModel(syncType = "VALUES")
        advanceUntilIdle()

        assertEquals(com.powerme.app.ui.workout.RoutineSyncType.VALUES, vm.uiState.value.pendingRoutineSync)
    }

    @Test
    fun `pendingRoutineSync is null when syncType is NONE`() = runTest(testDispatcher) {
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(emptyList())

        val vm = buildViewModel(syncType = "NONE")
        advanceUntilIdle()

        assertNull(vm.uiState.value.pendingRoutineSync)
    }

    // ── Set detail list ───────────────────────────────────────────────────────

    @Test
    fun `sets populated with correct labels for mixed set types`() = runTest(testDispatcher) {
        val sets = listOf(
            makeSetWithExercise(setType = SetType.WARMUP, setOrder = 1, weight = 40.0, reps = 10, rpe = null),
            makeSetWithExercise(setType = SetType.NORMAL, setOrder = 2, weight = 80.0, reps = 5,  rpe = 80),
            makeSetWithExercise(setType = SetType.NORMAL, setOrder = 3, weight = 90.0, reps = 3,  rpe = 85),
            makeSetWithExercise(setType = SetType.DROP,   setOrder = 4, weight = 60.0, reps = 8,  rpe = null)
        )
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(sets)
        whenever(workoutSetDao.getPreviousSessionCompletedSets(any(), any())).thenReturn(emptyList())
        whenever(workoutSetDao.getHistoricalBestE1RM(any(), any())).thenReturn(null)

        val vm = buildViewModel()
        advanceUntilIdle()

        val details = vm.uiState.value.exerciseCards.first().sets
        assertEquals(4, details.size)
        assertEquals("WU", details[0].label)
        assertEquals("1", details[1].label)
        assertEquals("2", details[2].label)
        assertEquals("DROP", details[3].label)
    }

    @Test
    fun `FAILURE sets get FAIL label and do not increment working set counter`() = runTest(testDispatcher) {
        val sets = listOf(
            makeSetWithExercise(setType = SetType.NORMAL,  setOrder = 1, weight = 100.0, reps = 5),
            makeSetWithExercise(setType = SetType.FAILURE, setOrder = 2, weight = 100.0, reps = 2),
            makeSetWithExercise(setType = SetType.NORMAL,  setOrder = 3, weight = 90.0,  reps = 5)
        )
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(sets)
        whenever(workoutSetDao.getPreviousSessionCompletedSets(any(), any())).thenReturn(emptyList())
        whenever(workoutSetDao.getHistoricalBestE1RM(any(), any())).thenReturn(null)

        val vm = buildViewModel()
        advanceUntilIdle()

        val details = vm.uiState.value.exerciseCards.first().sets
        assertEquals(3, details.size)
        assertEquals("1",    details[0].label)  // first NORMAL → working set 1
        assertEquals("FAIL", details[1].label)  // FAILURE → no counter increment
        assertEquals("2",    details[2].label)  // second NORMAL → working set 2
    }

    @Test
    fun `sets contain correct weight, reps, and rpe values`() = runTest(testDispatcher) {
        val sets = listOf(
            makeSetWithExercise(setType = SetType.NORMAL, setOrder = 1, weight = 100.0, reps = 5, rpe = 85),
            makeSetWithExercise(setType = SetType.NORMAL, setOrder = 2, weight = 105.0, reps = 3, rpe = null)
        )
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(sets)
        whenever(workoutSetDao.getPreviousSessionCompletedSets(any(), any())).thenReturn(emptyList())
        whenever(workoutSetDao.getHistoricalBestE1RM(any(), any())).thenReturn(null)

        val vm = buildViewModel()
        advanceUntilIdle()

        val details = vm.uiState.value.exerciseCards.first().sets
        assertEquals(2, details.size)
        assertEquals(100.0, details[0].weight, 0.001)
        assertEquals(5, details[0].reps)
        assertEquals(85, details[0].rpe)
        assertEquals(105.0, details[1].weight, 0.001)
        assertEquals(3, details[1].reps)
        assertNull(details[1].rpe)
    }

    @Test
    fun `incomplete sets excluded from set detail list`() = runTest(testDispatcher) {
        val sets = listOf(
            makeSetWithExercise(setType = SetType.NORMAL, setOrder = 1, weight = 100.0, reps = 5, isCompleted = true),
            makeSetWithExercise(setType = SetType.NORMAL, setOrder = 2, weight = 100.0, reps = 5, isCompleted = false)
        )
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(sets)
        whenever(workoutSetDao.getPreviousSessionCompletedSets(any(), any())).thenReturn(emptyList())
        whenever(workoutSetDao.getHistoricalBestE1RM(any(), any())).thenReturn(null)

        val vm = buildViewModel()
        advanceUntilIdle()

        val details = vm.uiState.value.exerciseCards.first().sets
        assertEquals(1, details.size)
        assertEquals("1", details[0].label)
    }

    @Test
    fun `sets are ordered by setOrder regardless of input order`() = runTest(testDispatcher) {
        // Provide sets out of order
        val sets = listOf(
            makeSetWithExercise(setType = SetType.NORMAL, setOrder = 3, weight = 90.0, reps = 3),
            makeSetWithExercise(setType = SetType.WARMUP, setOrder = 1, weight = 40.0, reps = 10),
            makeSetWithExercise(setType = SetType.NORMAL, setOrder = 2, weight = 80.0, reps = 5)
        )
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(sets)
        whenever(workoutSetDao.getPreviousSessionCompletedSets(any(), any())).thenReturn(emptyList())
        whenever(workoutSetDao.getHistoricalBestE1RM(any(), any())).thenReturn(null)

        val vm = buildViewModel()
        advanceUntilIdle()

        val details = vm.uiState.value.exerciseCards.first().sets
        assertEquals(3, details.size)
        assertEquals("WU", details[0].label)   // setOrder 1
        assertEquals("1", details[1].label)    // setOrder 2
        assertEquals("2", details[2].label)    // setOrder 3
    }

    // ── Routine sync card dismiss ─────────────────────────────────────────────

    @Test
    fun `dismissRoutineSync clears pendingRoutineSync`() = runTest(testDispatcher) {
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(emptyList())

        val vm = buildViewModel(isPostWorkout = true, syncType = "VALUES")
        advanceUntilIdle()

        assertEquals(com.powerme.app.ui.workout.RoutineSyncType.VALUES, vm.uiState.value.pendingRoutineSync)

        vm.dismissRoutineSync()

        assertNull(vm.uiState.value.pendingRoutineSync)
    }

    @Test
    fun `confirmRoutineSync clears pendingRoutineSync and sets snackbarMessage`() = runTest(testDispatcher) {
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(emptyList())

        val vm = buildViewModel(isPostWorkout = true, syncType = "STRUCTURE")
        advanceUntilIdle()

        assertEquals(com.powerme.app.ui.workout.RoutineSyncType.STRUCTURE, vm.uiState.value.pendingRoutineSync)

        vm.confirmRoutineSync("Routine updated")

        assertNull(vm.uiState.value.pendingRoutineSync)
        assertEquals("Routine updated", vm.uiState.value.snackbarMessage)
    }

    @Test
    fun `consumeSnackbar clears snackbarMessage`() = runTest(testDispatcher) {
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(emptyList())

        val vm = buildViewModel(isPostWorkout = true, syncType = "BOTH")
        advanceUntilIdle()

        vm.confirmRoutineSync("Routine structure and defaults updated")
        assertNotNull(vm.uiState.value.snackbarMessage)

        vm.consumeSnackbar()
        assertNull(vm.uiState.value.snackbarMessage)
    }

    @Test
    fun `dismissRoutineSync does not set snackbarMessage`() = runTest(testDispatcher) {
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(emptyList())

        val vm = buildViewModel(isPostWorkout = true, syncType = "VALUES")
        advanceUntilIdle()

        vm.dismissRoutineSync()

        assertNull(vm.uiState.value.snackbarMessage)
    }

    // ── Notes persistence ─────────────────────────────────────────────────────

    @Test
    fun `updateNotes updates state and calls DAO and Firestore`() = runTest(testDispatcher) {
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(emptyList())

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.updateNotes("Great session!")
        advanceUntilIdle()

        verify(workoutDao).updateWorkout(any())
        verify(firestoreSyncManager).pushWorkout(workoutId)
        assertEquals("Great session!", vm.uiState.value.workout?.notes)
    }

    @Test
    fun `updateNotes with blank text stores null`() = runTest(testDispatcher) {
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout.copy(notes = "Old note"))
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(emptyList())

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.updateNotes("   ")
        advanceUntilIdle()

        assertNull(vm.uiState.value.workout?.notes)
    }

    // ── Block cards ───────────────────────────────────────────────────────────

    private fun makeBlock(
        id: String = UUID.randomUUID().toString(),
        type: String,
        durationSeconds: Int? = null,
        targetRounds: Int? = null,
        totalRounds: Int? = null,
        extraReps: Int? = null,
        finishTimeSeconds: Int? = null,
        emomRoundSeconds: Int? = null,
        rpe: Int? = null,
        roundTapLogJson: String? = null
    ) = WorkoutBlock(
        id = id,
        workoutId = workoutId,
        order = 0,
        type = type,
        name = null,
        durationSeconds = durationSeconds,
        targetRounds = targetRounds,
        totalRounds = totalRounds,
        extraReps = extraReps,
        finishTimeSeconds = finishTimeSeconds,
        emomRoundSeconds = emomRoundSeconds,
        rpe = rpe,
        roundTapLogJson = roundTapLogJson
    )

    @Test
    fun `blockCards empty when no functional blocks`() = runTest(testDispatcher) {
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(emptyList())
        runBlocking { whenever(workoutBlockDao.getFunctionalBlocksForWorkout(workoutId)).thenReturn(emptyList()) }

        val vm = buildViewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.blockCards.isEmpty())
    }

    @Test
    fun `blockCards populated from AMRAP block with extraReps`() = runTest(testDispatcher) {
        val block = makeBlock(
            type = "AMRAP",
            durationSeconds = 720, // 12 min
            totalRounds = 8,
            extraReps = 3,
            rpe = 8
        )
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(emptyList())
        runBlocking { whenever(workoutBlockDao.getFunctionalBlocksForWorkout(workoutId)).thenReturn(listOf(block)) }

        val vm = buildViewModel()
        advanceUntilIdle()

        val cards = vm.uiState.value.blockCards
        assertEquals(1, cards.size)
        assertEquals("AMRAP 12:00", cards[0].headline)
        assertEquals("8+3 rds", cards[0].score)
        assertEquals(8, cards[0].rpe)
    }

    @Test
    fun `blockCards populated from AMRAP block without extraReps`() = runTest(testDispatcher) {
        val block = makeBlock(
            type = "AMRAP",
            durationSeconds = 720,
            totalRounds = 5,
            extraReps = 0
        )
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(emptyList())
        runBlocking { whenever(workoutBlockDao.getFunctionalBlocksForWorkout(workoutId)).thenReturn(listOf(block)) }

        val vm = buildViewModel()
        advanceUntilIdle()

        val card = vm.uiState.value.blockCards.first()
        assertEquals("5 rds", card.score)
    }

    @Test
    fun `blockCards populated from RFT block`() = runTest(testDispatcher) {
        val block = makeBlock(
            type = "RFT",
            targetRounds = 5,
            finishTimeSeconds = 1122, // 18:42
            totalRounds = 5,
            rpe = 7
        )
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(emptyList())
        runBlocking { whenever(workoutBlockDao.getFunctionalBlocksForWorkout(workoutId)).thenReturn(listOf(block)) }

        val vm = buildViewModel()
        advanceUntilIdle()

        val card = vm.uiState.value.blockCards.first()
        assertEquals("5RFT", card.headline)
        assertEquals("18:42", card.score)
        assertEquals(7, card.rpe)
    }

    @Test
    fun `blockCards populated from EMOM block`() = runTest(testDispatcher) {
        val block = makeBlock(
            type = "EMOM",
            durationSeconds = 600, // 10 min
            emomRoundSeconds = 60,
            totalRounds = 10
        )
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(emptyList())
        runBlocking { whenever(workoutBlockDao.getFunctionalBlocksForWorkout(workoutId)).thenReturn(listOf(block)) }

        val vm = buildViewModel()
        advanceUntilIdle()

        val card = vm.uiState.value.blockCards.first()
        assertEquals("EMOM 10min", card.headline)
        assertEquals("10/10 rds", card.score)
    }

    @Test
    fun `blockCards populated from TABATA block`() = runTest(testDispatcher) {
        val block = makeBlock(type = "TABATA", targetRounds = 8, totalRounds = 8, rpe = 7)
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(emptyList())
        runBlocking { whenever(workoutBlockDao.getFunctionalBlocksForWorkout(workoutId)).thenReturn(listOf(block)) }

        val vm = buildViewModel()
        advanceUntilIdle()

        val card = vm.uiState.value.blockCards.first()
        assertEquals("TABATA", card.headline)
        assertEquals("8 rds", card.score)
        assertEquals(7, card.rpe)
    }

    @Test
    fun `multi-block workout has correct blockCard count`() = runTest(testDispatcher) {
        val blocks = listOf(
            makeBlock(type = "AMRAP", durationSeconds = 720, totalRounds = 6),
            makeBlock(type = "RFT", targetRounds = 3, finishTimeSeconds = 900, totalRounds = 3)
        )
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(emptyList())
        runBlocking { whenever(workoutBlockDao.getFunctionalBlocksForWorkout(workoutId)).thenReturn(blocks) }

        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.blockCards.size)
    }

    @Test
    fun `totalVolume invariant - exercise cards built from workout_sets not blocks`() = runTest(testDispatcher) {
        // Invariant §12 #3: strength sets are the only source of totalVolume / setCount.
        // Functional blocks must not pollute exercise card counts.
        val strengthSets = listOf(
            makeSetWithExercise(weight = 100.0, reps = 5, exerciseName = "Squat", muscleGroup = "Legs"),
            makeSetWithExercise(weight = 60.0, reps = 10, exerciseName = "Squat", muscleGroup = "Legs")
        )
        val functionalBlock = makeBlock(type = "AMRAP", durationSeconds = 720, totalRounds = 8, extraReps = 3)
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(strengthSets)
        whenever(workoutSetDao.getPreviousSessionCompletedSets(any(), any())).thenReturn(emptyList())
        whenever(workoutSetDao.getHistoricalBestE1RM(any(), any())).thenReturn(null)
        runBlocking { whenever(workoutBlockDao.getFunctionalBlocksForWorkout(workoutId)).thenReturn(listOf(functionalBlock)) }

        val vm = buildViewModel()
        advanceUntilIdle()

        // One exercise card from workout_sets only (not from the block)
        assertEquals(1, vm.uiState.value.exerciseCards.size)
        // Block cards come from the block query
        assertEquals(1, vm.uiState.value.blockCards.size)
        // Exercise set count should be 2 (from workout_sets, not inflated by blocks)
        assertEquals(2, vm.uiState.value.totalSets)
    }

    @Test
    fun `parseRoundSplits returns empty list for null json`() {
        val vm = buildViewModelSync()
        assertTrue(vm.parseRoundSplits(null).isEmpty())
    }

    @Test
    fun `parseRoundSplits returns empty list for empty string`() {
        val vm = buildViewModelSync()
        assertTrue(vm.parseRoundSplits("").isEmpty())
    }

    @Test
    fun `parseRoundSplits returns empty list for empty array`() {
        val vm = buildViewModelSync()
        assertTrue(vm.parseRoundSplits("[]").isEmpty())
    }

    @Test
    fun `parseRoundSplits parses valid round split json`() {
        val vm = buildViewModelSync()
        val json = """[{"round":1,"elapsedMs":65000},{"round":2,"elapsedMs":132000}]"""
        val splits = vm.parseRoundSplits(json)
        assertEquals(2, splits.size)
        assertEquals(1, splits[0].round)
        assertEquals(65000L, splits[0].elapsedMs)
        assertEquals(2, splits[1].round)
        assertEquals(132000L, splits[1].elapsedMs)
    }

    @Test
    fun `parseRoundSplits returns empty list for malformed json`() {
        val vm = buildViewModelSync()
        assertTrue(vm.parseRoundSplits("{not json}").isEmpty())
    }

    @Test
    fun `formatTimeMmSs formats correctly`() {
        assertEquals("12:00", formatTimeMmSs(720))
        assertEquals("18:42", formatTimeMmSs(1122))
        assertEquals("0:30", formatTimeMmSs(30))
        assertEquals("1:05", formatTimeMmSs(65))
    }

    private fun buildViewModelSync(): WorkoutSummaryViewModel {
        val handle = SavedStateHandle(mapOf("workoutId" to workoutId))
        return WorkoutSummaryViewModel(handle, workoutDao, workoutSetDao, workoutBlockDao, appSettingsDataStore, firestoreSyncManager)
    }
}
