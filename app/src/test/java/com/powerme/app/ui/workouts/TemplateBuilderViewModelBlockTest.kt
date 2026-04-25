package com.powerme.app.ui.workouts

import androidx.lifecycle.SavedStateHandle
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.BlockType
import com.powerme.app.data.WorkoutStyle
import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.PowerMeDatabase
import com.powerme.app.data.database.RoutineBlockDao
import com.powerme.app.data.database.RoutineDao
import com.powerme.app.data.database.RoutineExerciseDao
import com.powerme.app.data.database.toSearchName
import com.powerme.app.data.repository.ExerciseRepository
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TemplateBuilderViewModelBlockTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var routineDao: RoutineDao
    private lateinit var routineExerciseDao: RoutineExerciseDao
    private lateinit var routineBlockDao: RoutineBlockDao
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var database: PowerMeDatabase
    private lateinit var firestoreSyncManager: FirestoreSyncManager
    private lateinit var appSettingsDataStore: AppSettingsDataStore
    private lateinit var viewModel: TemplateBuilderViewModel

    private val wallBall = Exercise(id = 1L, name = "Wall Ball", muscleGroup = "Full Body",
        equipmentType = "Medicine Ball", searchName = "wall ball".toSearchName())
    private val boxJump = Exercise(id = 2L, name = "Box Jump", muscleGroup = "Legs",
        equipmentType = "Box", searchName = "box jump".toSearchName())
    private val doubleUnder = Exercise(id = 3L, name = "Double Under", muscleGroup = "Cardio",
        equipmentType = "Jump Rope", searchName = "double under".toSearchName())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        routineDao = mock()
        routineExerciseDao = mock()
        routineBlockDao = mock()
        exerciseRepository = mock()
        database = mock()
        firestoreSyncManager = mock()
        appSettingsDataStore = mock()

        runBlocking {
            whenever(routineBlockDao.getBlocksForRoutineOnce("new")).thenReturn(emptyList())
            whenever(appSettingsDataStore.workoutStyle).thenReturn(flowOf(WorkoutStyle.PURE_FUNCTIONAL))
        }

        viewModel = TemplateBuilderViewModel(
            routineDao = routineDao,
            routineExerciseDao = routineExerciseDao,
            routineBlockDao = routineBlockDao,
            exerciseRepository = exerciseRepository,
            database = database,
            firestoreSyncManager = firestoreSyncManager,
            appSettingsDataStore = appSettingsDataStore,
            savedStateHandle = SavedStateHandle(mapOf("routineId" to "new"))
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeAmrapBlock() = DraftBlock(
        type = BlockType.AMRAP,
        name = "AMRAP 12min",
        durationSeconds = 720
    )

    // ── addFunctionalBlock ─────────────────────────────────────────────────────

    @Test
    fun `addFunctionalBlock adds block to draftBlocks`() = runTest(testDispatcher) {
        whenever(exerciseRepository.getExercisesByIds(listOf(1L, 2L))).thenReturn(listOf(wallBall, boxJump))

        val block = makeAmrapBlock()
        viewModel.addFunctionalBlock(block, listOf(1L, 2L))
        advanceUntilIdle()

        assertEquals(1, viewModel.draftBlocks.value.size)
        assertEquals("AMRAP 12min", viewModel.draftBlocks.value.first().name)
        assertEquals(BlockType.AMRAP, viewModel.draftBlocks.value.first().type)
    }

    @Test
    fun `addFunctionalBlock assigns blockId to added exercises`() = runTest(testDispatcher) {
        whenever(exerciseRepository.getExercisesByIds(listOf(1L, 2L))).thenReturn(listOf(wallBall, boxJump))

        val block = makeAmrapBlock()
        viewModel.addFunctionalBlock(block, listOf(1L, 2L))
        advanceUntilIdle()

        val expectedBlockId = viewModel.draftBlocks.value.first().id
        val exercises = viewModel.draftExercises.value
        assertEquals(2, exercises.size)
        assertTrue(exercises.all { it.blockId == expectedBlockId })
    }

    @Test
    fun `addFunctionalBlock second block gets order 1`() = runTest(testDispatcher) {
        whenever(exerciseRepository.getExercisesByIds(listOf(1L))).thenReturn(listOf(wallBall))
        whenever(exerciseRepository.getExercisesByIds(listOf(2L))).thenReturn(listOf(boxJump))

        viewModel.addFunctionalBlock(makeAmrapBlock(), listOf(1L))
        advanceUntilIdle()

        val rftBlock = DraftBlock(type = BlockType.RFT, name = "5 RFT", targetRounds = 5)
        viewModel.addFunctionalBlock(rftBlock, listOf(2L))
        advanceUntilIdle()

        val blocks = viewModel.draftBlocks.value
        assertEquals(2, blocks.size)
        assertEquals(0, blocks[0].order)
        assertEquals(1, blocks[1].order)
    }

    // ── deleteBlock ─────────────────────────────────────────────────────────────

    @Test
    fun `deleteBlock removes block and its exercises`() = runTest(testDispatcher) {
        whenever(exerciseRepository.getExercisesByIds(listOf(1L, 2L))).thenReturn(listOf(wallBall, boxJump))

        val block = makeAmrapBlock()
        viewModel.addFunctionalBlock(block, listOf(1L, 2L))
        advanceUntilIdle()

        val blockId = viewModel.draftBlocks.value.first().id
        viewModel.deleteBlock(blockId)

        assertEquals(0, viewModel.draftBlocks.value.size)
        assertEquals(0, viewModel.draftExercises.value.size)
    }

    @Test
    fun `deleteBlock reindexes remaining blocks`() = runTest(testDispatcher) {
        whenever(exerciseRepository.getExercisesByIds(listOf(1L))).thenReturn(listOf(wallBall))
        whenever(exerciseRepository.getExercisesByIds(listOf(2L))).thenReturn(listOf(boxJump))
        whenever(exerciseRepository.getExercisesByIds(listOf(3L))).thenReturn(listOf(doubleUnder))

        val block1 = makeAmrapBlock()
        viewModel.addFunctionalBlock(block1, listOf(1L))
        advanceUntilIdle()

        val block2 = DraftBlock(type = BlockType.RFT, name = "5 RFT")
        viewModel.addFunctionalBlock(block2, listOf(2L))
        advanceUntilIdle()

        val block3 = DraftBlock(type = BlockType.EMOM, name = "EMOM 10min")
        viewModel.addFunctionalBlock(block3, listOf(3L))
        advanceUntilIdle()

        // Delete middle block
        val middleId = viewModel.draftBlocks.value[1].id
        viewModel.deleteBlock(middleId)

        val remaining = viewModel.draftBlocks.value
        assertEquals(2, remaining.size)
        assertEquals(0, remaining[0].order)
        assertEquals(1, remaining[1].order)
    }

    @Test
    fun `deleteBlock leaves exercises from other blocks intact`() = runTest(testDispatcher) {
        whenever(exerciseRepository.getExercisesByIds(listOf(1L))).thenReturn(listOf(wallBall))
        whenever(exerciseRepository.getExercisesByIds(listOf(2L))).thenReturn(listOf(boxJump))

        val block1 = makeAmrapBlock()
        viewModel.addFunctionalBlock(block1, listOf(1L))
        advanceUntilIdle()

        val block2 = DraftBlock(type = BlockType.TABATA, name = "Tabata 8rds")
        viewModel.addFunctionalBlock(block2, listOf(2L))
        advanceUntilIdle()

        val block1Id = viewModel.draftBlocks.value[0].id
        viewModel.deleteBlock(block1Id)

        val remaining = viewModel.draftExercises.value
        assertEquals(1, remaining.size)
        assertEquals(2L, remaining.first().exerciseId)
    }

    // ── reorderBlocks ──────────────────────────────────────────────────────────

    @Test
    fun `reorderBlocks swaps block positions and updates order`() = runTest(testDispatcher) {
        whenever(exerciseRepository.getExercisesByIds(listOf(1L))).thenReturn(listOf(wallBall))
        whenever(exerciseRepository.getExercisesByIds(listOf(2L))).thenReturn(listOf(boxJump))

        val block1 = makeAmrapBlock()
        viewModel.addFunctionalBlock(block1, listOf(1L))
        advanceUntilIdle()

        val block2 = DraftBlock(type = BlockType.RFT, name = "5 RFT")
        viewModel.addFunctionalBlock(block2, listOf(2L))
        advanceUntilIdle()

        val originalFirstId = viewModel.draftBlocks.value[0].id
        val originalSecondId = viewModel.draftBlocks.value[1].id

        viewModel.reorderBlocks(0, 1)

        val reordered = viewModel.draftBlocks.value
        assertEquals(originalSecondId, reordered[0].id)
        assertEquals(originalFirstId, reordered[1].id)
        assertEquals(0, reordered[0].order)
        assertEquals(1, reordered[1].order)
    }

    // ── setPendingBlock / completePendingBlock ─────────────────────────────────

    @Test
    fun `setPendingBlock stores block for later completion`() = runTest(testDispatcher) {
        val block = makeAmrapBlock()
        viewModel.setPendingBlock(block)

        assertNotNull(viewModel.pendingBlock.value)
        assertEquals("AMRAP 12min", viewModel.pendingBlock.value!!.name)
    }

    @Test
    fun `completePendingBlock adds block and clears pending`() = runTest(testDispatcher) {
        whenever(exerciseRepository.getExercisesByIds(listOf(1L, 2L))).thenReturn(listOf(wallBall, boxJump))

        val block = makeAmrapBlock()
        viewModel.setPendingBlock(block)
        viewModel.completePendingBlock(listOf(1L, 2L))
        advanceUntilIdle()

        assertNull(viewModel.pendingBlock.value)
        assertEquals(1, viewModel.draftBlocks.value.size)
        assertEquals(2, viewModel.draftExercises.value.size)
    }

    @Test
    fun `completePendingBlock with existing block only adds exercises, does not duplicate block`() = runTest(testDispatcher) {
        whenever(exerciseRepository.getExercisesByIds(listOf(1L))).thenReturn(listOf(wallBall))
        whenever(exerciseRepository.getExercisesByIds(listOf(2L))).thenReturn(listOf(boxJump))

        val block = makeAmrapBlock()
        viewModel.addFunctionalBlock(block, listOf(1L))
        advanceUntilIdle()

        // Simulate "Add exercise to block" overflow → setPendingBlock with existing block → picker returns
        val existingBlock = viewModel.draftBlocks.value.first()
        viewModel.setPendingBlock(existingBlock)
        viewModel.completePendingBlock(listOf(2L))
        advanceUntilIdle()

        assertEquals(1, viewModel.draftBlocks.value.size)
        assertEquals(2, viewModel.draftExercises.value.size)
        assertTrue(viewModel.draftExercises.value.all { it.blockId == existingBlock.id })
    }

    @Test
    fun `completePendingBlock with no pending block is a no-op`() = runTest(testDispatcher) {
        viewModel.completePendingBlock(listOf(1L))
        advanceUntilIdle()

        assertEquals(0, viewModel.draftBlocks.value.size)
        assertEquals(0, viewModel.draftExercises.value.size)
    }

    // ── addExercises with blockId ──────────────────────────────────────────────

    @Test
    fun `addExercises with blockId assigns correct blockId to new exercises`() = runTest(testDispatcher) {
        whenever(exerciseRepository.getExercisesByIds(listOf(1L))).thenReturn(listOf(wallBall))
        whenever(exerciseRepository.getExercisesByIds(listOf(2L))).thenReturn(listOf(boxJump))

        val block = makeAmrapBlock()
        viewModel.addFunctionalBlock(block, listOf(1L))
        advanceUntilIdle()

        val blockId = viewModel.draftBlocks.value.first().id
        viewModel.addExercises(listOf(2L), blockId = blockId)
        advanceUntilIdle()

        val exercises = viewModel.draftExercises.value
        assertEquals(2, exercises.size)
        assertTrue(exercises.all { it.blockId == blockId })
    }

    // ── workoutStyle ───────────────────────────────────────────────────────────

    @Test
    fun `workoutStyle is PURE_FUNCTIONAL from store`() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertEquals(WorkoutStyle.PURE_FUNCTIONAL, viewModel.workoutStyle.value)
    }
}
