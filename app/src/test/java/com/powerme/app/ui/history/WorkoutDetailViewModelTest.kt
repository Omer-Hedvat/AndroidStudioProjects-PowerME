package com.powerme.app.ui.history

import androidx.lifecycle.SavedStateHandle
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.UnitSystem
import com.powerme.app.data.database.ExerciseType
import com.powerme.app.data.database.PowerMeDatabase
import com.powerme.app.data.database.SetType
import com.powerme.app.data.database.Workout
import com.powerme.app.data.database.WorkoutDao
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var workoutDao: WorkoutDao
    private lateinit var workoutSetDao: WorkoutSetDao
    private lateinit var database: PowerMeDatabase
    private lateinit var firestoreSyncManager: FirestoreSyncManager
    private lateinit var appSettingsDataStore: AppSettingsDataStore

    private val workoutId = "test-workout-uuid"

    private val baseWorkout = Workout(
        id = workoutId,
        routineId = null,
        routineName = "Push Day",
        timestamp = 1_700_000_000_000L,
        durationSeconds = 3600,
        totalVolume = 5000.0,
        notes = null,
        isCompleted = true,
        startTimeMs = 1_700_000_000_000L,
        endTimeMs = 1_700_003_600_000L,
        updatedAt = 1_700_000_000_000L
    )

    private fun makeSet(
        exerciseId: Long = 1L,
        weight: Double = 100.0,
        reps: Int = 5,
        setOrder: Int = 1
    ) = WorkoutSetWithExercise(
        id = UUID.randomUUID().toString(),
        workoutId = workoutId,
        exerciseId = exerciseId,
        setOrder = setOrder,
        weight = weight,
        reps = reps,
        rpe = null,
        setType = SetType.NORMAL,
        setNotes = null,
        supersetGroupId = null,
        isCompleted = true,
        exerciseName = "Bench Press",
        muscleGroup = "Chest",
        equipmentType = null,
        exerciseType = ExerciseType.STRENGTH,
        distance = null,
        timeSeconds = null
    )

    private fun buildViewModel(): WorkoutDetailViewModel {
        val handle = SavedStateHandle(mapOf("workoutId" to workoutId))
        return WorkoutDetailViewModel(handle, workoutDao, workoutSetDao, database, firestoreSyncManager, appSettingsDataStore)
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        workoutDao = mock()
        workoutSetDao = mock()
        database = mock()
        firestoreSyncManager = mock()
        appSettingsDataStore = mock()

        whenever(appSettingsDataStore.unitSystem).thenReturn(flowOf(UnitSystem.METRIC))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `hasUnsavedChanges returns false when no edits made`() = runTest(testDispatcher) {
        val set = makeSet(weight = 100.0, reps = 5)
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(listOf(set))

        val vm = buildViewModel()
        advanceUntilIdle()

        assertFalse(vm.hasUnsavedChanges())
    }

    @Test
    fun `hasUnsavedChanges returns true when weight modified`() = runTest(testDispatcher) {
        val set = makeSet(weight = 100.0, reps = 5)
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(listOf(set))

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.updatePendingWeight(set.id, "110")
        assertTrue(vm.hasUnsavedChanges())
    }

    @Test
    fun `hasUnsavedChanges returns true when reps modified`() = runTest(testDispatcher) {
        val set = makeSet(weight = 100.0, reps = 5)
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(listOf(set))

        val vm = buildViewModel()
        advanceUntilIdle()

        vm.updatePendingReps(set.id, "8")
        assertTrue(vm.hasUnsavedChanges())
    }

    @Test
    fun `load populates pendingEdits with current set values`() = runTest(testDispatcher) {
        val set = makeSet(weight = 80.0, reps = 10)
        whenever(workoutDao.getWorkoutById(workoutId)).thenReturn(baseWorkout)
        whenever(workoutSetDao.getSetsWithExerciseForWorkout(workoutId)).thenReturn(listOf(set))

        val vm = buildViewModel()
        advanceUntilIdle()

        val edits = vm.uiState.value.pendingEdits
        assertEquals(1, edits.size)
        val edit = edits[set.id]
        assertNotNull(edit)
        assertEquals("10", edit!!.reps)
    }
}
