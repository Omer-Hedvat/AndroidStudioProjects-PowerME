package com.powerme.app.ui.history

import androidx.lifecycle.SavedStateHandle
import com.powerme.app.analytics.StatisticalEngine
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.UnitSystem
import com.powerme.app.data.database.ExerciseType
import com.powerme.app.data.database.SetType
import com.powerme.app.data.database.Workout
import com.powerme.app.data.database.WorkoutDao
import com.powerme.app.data.database.WorkoutSet
import com.powerme.app.data.database.WorkoutSetDao
import com.powerme.app.data.database.WorkoutSetWithExercise
import com.powerme.app.data.sync.FirestoreSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
        supersetGroupId: String? = null,
        exerciseName: String = "Bench Press",
        muscleGroup: String? = "Chest"
    ) = WorkoutSetWithExercise(
        id = UUID.randomUUID().toString(),
        workoutId = workoutId,
        exerciseId = exerciseId,
        setOrder = 1,
        weight = weight,
        reps = reps,
        rpe = rpe,
        setType = setType,
        setNotes = null,
        supersetGroupId = supersetGroupId,
        isCompleted = true,
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
        return WorkoutSummaryViewModel(handle, workoutDao, workoutSetDao, appSettingsDataStore, firestoreSyncManager)
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        workoutDao = mock()
        workoutSetDao = mock()
        appSettingsDataStore = mock()
        firestoreSyncManager = mock()

        whenever(appSettingsDataStore.unitSystem).thenReturn(flowOf(UnitSystem.METRIC))
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
        // 2 sets: 1 with RPE, 1 without → 50% threshold not met (needs >= 50%)
        val sets = listOf(
            makeSetWithExercise(rpe = 8),
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
        // 2 of 3 sets have RPE → 66.7% → shown; avg = (8+9)/2 = 8.5
        val sets = listOf(
            makeSetWithExercise(rpe = 8),
            makeSetWithExercise(rpe = 9),
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

    // ── Golden zone ───────────────────────────────────────────────────────────

    @Test
    fun `isGoldenZone true when avgRpe in 8 to 9 range`() = runTest(testDispatcher) {
        val sets = listOf(makeSetWithExercise(rpe = 8), makeSetWithExercise(rpe = 9))
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
        val sets = listOf(makeSetWithExercise(rpe = 7), makeSetWithExercise(rpe = 7))
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
}
