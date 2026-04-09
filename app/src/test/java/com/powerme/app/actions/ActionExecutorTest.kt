package com.powerme.app.actions

import com.powerme.app.data.database.GymProfile
import com.powerme.app.data.database.RoutineDao
import com.powerme.app.data.database.RoutineExerciseDao
import com.powerme.app.data.database.WorkoutSet
import com.powerme.app.data.repository.ExerciseRepository
import com.powerme.app.data.repository.GymProfileRepository
import com.powerme.app.data.repository.MedicalLedgerRepository
import com.powerme.app.data.repository.WorkoutRepository
import com.powerme.app.util.GoalDocumentManager
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

/**
 * Unit tests for ActionExecutor.
 *
 * Tests Sprint 3 & 5 functionality:
 * - UpdateWeight action execution
 * - SwitchGym action execution
 * - UpdateEquipment action execution
 * - Error handling for invalid actions
 * - Edge cases (no active workout, missing gym profile, etc.)
 *
 * Success Criteria: All 10 tests must pass.
 */
class ActionExecutorTest {

    private lateinit var executor: ActionExecutor
    private lateinit var mockWorkoutRepository: WorkoutRepository
    private lateinit var mockGymProfileRepository: GymProfileRepository
    private lateinit var mockExerciseRepository: ExerciseRepository
    private lateinit var mockGoalDocumentManager: GoalDocumentManager
    private lateinit var mockMedicalLedgerRepository: MedicalLedgerRepository
    private lateinit var mockRoutineDao: RoutineDao
    private lateinit var mockRoutineExerciseDao: RoutineExerciseDao

    private val testSet = WorkoutSet(
        id = "set-1",
        workoutId = "workout-100",
        exerciseId = 1L,
        setOrder = 1,
        weight = 80.0,
        reps = 10,
        rpe = null,
        setType = com.powerme.app.data.database.SetType.NORMAL,
        setNotes = null
    )

    private val homeGym = GymProfile(
        id = 1,
        name = "Home",
        equipment = "Dumbbells,Resistance Bands,Pull-up Bar",
        isActive = false
    )

    private val workGym = GymProfile(
        id = 2,
        name = "Work",
        equipment = "Barbell,Dumbbells,Cable,Machine",
        isActive = true
    )

    @Before
    fun setup() {
        mockWorkoutRepository = mock(WorkoutRepository::class.java)
        mockGymProfileRepository = mock(GymProfileRepository::class.java)
        mockExerciseRepository = mock(ExerciseRepository::class.java)
        mockGoalDocumentManager = mock(GoalDocumentManager::class.java)
        mockMedicalLedgerRepository = mock(MedicalLedgerRepository::class.java)
        mockRoutineDao = mock(RoutineDao::class.java)
        mockRoutineExerciseDao = mock(RoutineExerciseDao::class.java)
        executor = ActionExecutor(
            mockWorkoutRepository,
            mockGymProfileRepository,
            mockExerciseRepository,
            mockGoalDocumentManager,
            mockMedicalLedgerRepository,
            mockRoutineDao,
            mockRoutineExerciseDao
        )
    }

    /**
     * Test Case 1: UpdateWeight - Success (Last Set)
     */
    @Test
    fun testUpdateWeightLastSetSuccess() = runTest {
        // Arrange: Active workout with one set
        val activeWorkoutId = "workout-100"
        val sets = listOf(testSet)
        whenever(mockWorkoutRepository.getSetsForWorkout(activeWorkoutId)).thenReturn(flowOf(sets))

        val action = ActionBlock.UpdateWeight(weightKg = 92.0, setIndex = null)

        // Act
        val result = executor.execute(action, activeWorkoutId)

        // Assert
        assertTrue("Should succeed", result is ActionResult.Success)
        val successMsg = (result as ActionResult.Success).message
        assertTrue("Should mention weight", successMsg.contains("92.0"))
    }

    /**
     * Test Case 2: UpdateWeight - Success (Specific Set)
     */
    @Test
    fun testUpdateWeightSpecificSetSuccess() = runTest {
        // Arrange: Active workout with multiple sets
        val activeWorkoutId = "workout-100"
        val set1 = testSet.copy(id = "set-1", setOrder = 1)
        val set2 = testSet.copy(id = "set-2", setOrder = 2)
        val sets = listOf(set1, set2)
        whenever(mockWorkoutRepository.getSetsForWorkout(activeWorkoutId)).thenReturn(flowOf(sets))

        val action = ActionBlock.UpdateWeight(weightKg = 100.0, setIndex = 2)

        // Act
        val result = executor.execute(action, activeWorkoutId)

        // Assert
        assertTrue("Should succeed", result is ActionResult.Success)
        val successMsg = (result as ActionResult.Success).message
        assertTrue("Should mention set 2", successMsg.contains("set 2"))
    }

    /**
     * Test Case 3: UpdateWeight - Failure (No Active Workout)
     */
    @Test
    fun testUpdateWeightNoActiveWorkout() = runTest {
        // Arrange: No active workout
        val action = ActionBlock.UpdateWeight(weightKg = 92.0)

        // Act
        val result = executor.execute(action, activeWorkoutId = null)

        // Assert
        assertTrue("Should fail", result is ActionResult.Failure)
        val errorMsg = (result as ActionResult.Failure).error
        assertTrue("Should mention missing workout", errorMsg.contains("No active workout"))
    }

    /**
     * Test Case 4: UpdateWeight - Failure (No Sets)
     */
    @Test
    fun testUpdateWeightNoSets() = runTest {
        // Arrange: Active workout but no sets
        val activeWorkoutId = "workout-100"
        whenever(mockWorkoutRepository.getSetsForWorkout(activeWorkoutId)).thenReturn(flowOf(emptyList()))

        val action = ActionBlock.UpdateWeight(weightKg = 92.0)

        // Act
        val result = executor.execute(action, activeWorkoutId)

        // Assert
        assertTrue("Should fail", result is ActionResult.Failure)
        val errorMsg = (result as ActionResult.Failure).error
        assertTrue("Should mention no sets", errorMsg.contains("No sets found"))
    }

    /**
     * Test Case 5: UpdateWeight - Failure (Set Index Out Of Bounds)
     */
    @Test
    fun testUpdateWeightSetNotFound() = runTest {
        // Arrange: Active workout with 1 set, but trying to update set 3
        val activeWorkoutId = "workout-100"
        val sets = listOf(testSet)
        whenever(mockWorkoutRepository.getSetsForWorkout(activeWorkoutId)).thenReturn(flowOf(sets))

        val action = ActionBlock.UpdateWeight(weightKg = 92.0, setIndex = 3)

        // Act
        val result = executor.execute(action, activeWorkoutId)

        // Assert
        assertTrue("Should fail", result is ActionResult.Failure)
        val errorMsg = (result as ActionResult.Failure).error
        assertTrue("Should mention set not found", errorMsg.contains("Set 3 not found"))
    }

    /**
     * Test Case 6: SwitchGym - Success
     */
    @Test
    fun testSwitchGymSuccess() = runTest {
        // Arrange: Home gym exists
        whenever(mockGymProfileRepository.setActiveProfileByName("Home")).thenReturn(true)

        val action = ActionBlock.SwitchGym(gymProfileName = "Home")

        // Act
        val result = executor.execute(action, activeWorkoutId = null)

        // Assert
        assertTrue("Should succeed", result is ActionResult.Success)
        val successMsg = (result as ActionResult.Success).message
        assertTrue("Should mention Home", successMsg.contains("Home"))
        verify(mockGymProfileRepository).setActiveProfileByName("Home")
    }

    /**
     * Test Case 7: SwitchGym - Failure (Gym Not Found)
     */
    @Test
    fun testSwitchGymNotFound() = runTest {
        // Arrange: Gym doesn't exist
        whenever(mockGymProfileRepository.setActiveProfileByName("NonExistent")).thenReturn(false)

        val action = ActionBlock.SwitchGym(gymProfileName = "NonExistent")

        // Act
        val result = executor.execute(action, activeWorkoutId = null)

        // Assert
        assertTrue("Should fail", result is ActionResult.Failure)
        val errorMsg = (result as ActionResult.Failure).error
        assertTrue("Should mention not found", errorMsg.contains("not found"))
    }

    /**
     * Test Case 8: UpdateEquipment - Success
     */
    @Test
    fun testUpdateEquipmentSuccess() = runTest {
        // Arrange: Work gym exists
        whenever(mockGymProfileRepository.getProfileByName("Work")).thenReturn(workGym)

        val action = ActionBlock.UpdateEquipment(
            gymProfileName = "Work",
            equipment = listOf("Barbell", "Dumbbells", "Cable", "Leg Press")
        )

        // Act
        val result = executor.execute(action, activeWorkoutId = null)

        // Assert
        assertTrue("Should succeed", result is ActionResult.Success)
        val successMsg = (result as ActionResult.Success).message
        assertTrue("Should mention 4 items", successMsg.contains("4 items"))
        verify(mockGymProfileRepository).updateProfile(any())
    }

    /**
     * Test Case 9: UpdateEquipment - Failure (Gym Not Found)
     */
    @Test
    fun testUpdateEquipmentGymNotFound() = runTest {
        // Arrange: Gym doesn't exist
        whenever(mockGymProfileRepository.getProfileByName("NonExistent")).thenReturn(null)

        val action = ActionBlock.UpdateEquipment(
            gymProfileName = "NonExistent",
            equipment = listOf("Barbell")
        )

        // Act
        val result = executor.execute(action, activeWorkoutId = null)

        // Assert
        assertTrue("Should fail", result is ActionResult.Failure)
        val errorMsg = (result as ActionResult.Failure).error
        assertTrue("Should mention not found", errorMsg.contains("not found"))
    }

    /**
     * Test Case 10: UpdateInjury - Not Yet Implemented
     */
    @Test
    fun testUpdateInjuryNotImplemented() = runTest {
        // Arrange
        val action = ActionBlock.UpdateInjury(
            joint = "LOWER_BACK",
            severity = 7,
            notes = "Sharp pain"
        )

        // Act
        val result = executor.execute(action, activeWorkoutId = null)

        // Assert
        assertTrue("Should fail with not implemented message", result is ActionResult.Failure)
        val errorMsg = (result as ActionResult.Failure).error
        assertTrue("Should mention Sprint 6", errorMsg.contains("Sprint 6"))
    }
}
