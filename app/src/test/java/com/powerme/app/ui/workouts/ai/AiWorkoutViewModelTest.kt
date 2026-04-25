package com.powerme.app.ui.workouts.ai

import com.powerme.app.ai.AiCoreDownloadManager
import com.powerme.app.ai.DownloadState
import com.powerme.app.ai.ParseResult
import com.powerme.app.ai.WorkoutTextParser
import com.powerme.app.ai.ParsedExercise
import com.powerme.app.ai.TextRecognitionService
import com.powerme.app.analytics.AnalyticsTracker
import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.toSearchName
import com.powerme.app.data.repository.ExerciseRepository
import com.powerme.app.data.repository.PlanExercise
import com.powerme.app.data.repository.RoutineRepository
import com.powerme.app.data.repository.UserSynonymRepository
import com.powerme.app.data.repository.WorkoutBootstrap
import com.powerme.app.data.repository.WorkoutRepository
import com.powerme.app.util.ExerciseMatcher
import com.powerme.app.util.MatchResult
import com.powerme.app.util.MatchType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class AiWorkoutViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var workoutParser: WorkoutTextParser
    private lateinit var exerciseMatcher: ExerciseMatcher
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var workoutRepository: WorkoutRepository
    private lateinit var routineRepository: RoutineRepository
    private lateinit var textRecognitionService: TextRecognitionService
    private lateinit var userSynonymRepository: UserSynonymRepository
    private lateinit var analyticsTracker: AnalyticsTracker
    private lateinit var downloadManager: AiCoreDownloadManager
    private lateinit var viewModel: AiWorkoutViewModel

    private val benchPress = Exercise(id = 1, name = "Barbell Bench Press", muscleGroup = "Chest",
        equipmentType = "Barbell", searchName = "Barbell Bench Press".toSearchName())
    private val squat = Exercise(id = 2, name = "Barbell Back Squat", muscleGroup = "Legs",
        equipmentType = "Barbell", searchName = "Barbell Back Squat".toSearchName())
    private val ohp = Exercise(id = 3, name = "Overhead Press", muscleGroup = "Shoulders",
        equipmentType = "Barbell", searchName = "Overhead Press".toSearchName())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        workoutParser = mock()
        exerciseMatcher = mock()
        exerciseRepository = mock()
        workoutRepository = mock()
        routineRepository = mock()
        textRecognitionService = mock()
        userSynonymRepository = mock()
        analyticsTracker = mock()
        downloadManager = mock()

        runBlocking {
            whenever(exerciseRepository.getAllExercises()).thenReturn(flowOf(listOf(benchPress, squat, ohp)))
            whenever(downloadManager.downloadState).thenReturn(MutableStateFlow(DownloadState.Idle))
        }

        viewModel = AiWorkoutViewModel(
            workoutParser, exerciseMatcher, exerciseRepository,
            workoutRepository, routineRepository, textRecognitionService,
            userSynonymRepository, analyticsTracker, downloadManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun TestScope.loadTwoExercisePreview() {
        advanceUntilIdle()
        runBlocking {
            whenever(workoutParser.parseWorkoutText(any(), any()))
                .thenReturn(ParseResult(listOf(
                    ParsedExercise("Bench Press", 3, 8),
                    ParsedExercise("Squat", 4, 5)
                )))
            whenever(exerciseMatcher.matchExercise(any(), any()))
                .thenReturn(MatchResult(benchPress, 1.0, MatchType.EXACT))
                .thenReturn(MatchResult(squat, 1.0, MatchType.EXACT))
        }
        viewModel.updateInputText("workout")
        viewModel.processTextInput()
        advanceUntilIdle()
    }

    private fun TestScope.loadThreeExercisePreview() {
        advanceUntilIdle()
        runBlocking {
            whenever(workoutParser.parseWorkoutText(any(), any()))
                .thenReturn(ParseResult(listOf(
                    ParsedExercise("Bench Press", 3, 8),
                    ParsedExercise("Squat", 4, 5),
                    ParsedExercise("OHP", 3, 8)
                )))
            whenever(exerciseMatcher.matchExercise(any(), any()))
                .thenReturn(MatchResult(benchPress, 1.0, MatchType.EXACT))
                .thenReturn(MatchResult(squat, 1.0, MatchType.EXACT))
                .thenReturn(MatchResult(ohp, 1.0, MatchType.EXACT))
        }
        viewModel.updateInputText("workout")
        viewModel.processTextInput()
        advanceUntilIdle()
    }

    // ── Initial state ─────────────────────────────────────────────────────────

    @Test
    fun `initial state is INPUT step`() = runTest(testDispatcher) {
        advanceUntilIdle()
        assertEquals(AiWorkoutStep.INPUT, viewModel.uiState.value.step)
        assertTrue(viewModel.uiState.value.previewExercises.isEmpty())
        assertNull(viewModel.uiState.value.error)
    }

    // ── updateInputText ───────────────────────────────────────────────────────

    @Test
    fun `updateInputText updates state and clears error`() = runTest(testDispatcher) {
        viewModel.updateInputText("bench press")
        assertEquals("bench press", viewModel.uiState.value.inputText)
        assertNull(viewModel.uiState.value.error)
    }

    // ── processTextInput — success ────────────────────────────────────────────

    @Test
    fun `processTextInput transitions to PREVIEW on success`() = runTest(testDispatcher) {
        advanceUntilIdle()

        val parsedExercise = ParsedExercise(name = "Bench Press", sets = 3, reps = 8)
        whenever(workoutParser.parseWorkoutText(any(), any())).thenReturn(ParseResult(listOf(parsedExercise)))
        whenever(exerciseMatcher.matchExercise(any(), any())).thenReturn(
            MatchResult(benchPress, 1.0, MatchType.EXACT)
        )

        viewModel.updateInputText("3x8 bench press")
        viewModel.processTextInput()
        advanceUntilIdle()

        assertEquals(AiWorkoutStep.PREVIEW, viewModel.uiState.value.step)
        assertEquals(1, viewModel.uiState.value.previewExercises.size)
        assertEquals(benchPress, viewModel.uiState.value.previewExercises[0].matchedExercise)
        assertFalse(viewModel.uiState.value.isProcessing)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `processTextInput populates preview with correct sets and reps`() = runTest(testDispatcher) {
        advanceUntilIdle()

        val parsedList = listOf(
            ParsedExercise(name = "Bench Press", sets = 4, reps = 5, weight = 80.0),
            ParsedExercise(name = "Squat", sets = 3, reps = 10)
        )
        whenever(workoutParser.parseWorkoutText(any(), any())).thenReturn(ParseResult(parsedList))
        whenever(exerciseMatcher.matchExercise(any(), any()))
            .thenReturn(MatchResult(benchPress, 1.0, MatchType.EXACT))
            .thenReturn(MatchResult(squat, 0.92, MatchType.FUZZY))

        viewModel.updateInputText("push day")
        viewModel.processTextInput()
        advanceUntilIdle()

        val preview = viewModel.uiState.value.previewExercises
        assertEquals(2, preview.size)
        assertEquals(4, preview[0].sets)
        assertEquals(5, preview[0].reps)
        assertEquals(80.0, preview[0].weight)
        assertEquals(3, preview[1].sets)
    }

    // ── processTextInput — error ──────────────────────────────────────────────

    @Test
    fun `processTextInput stays at INPUT on parser error`() = runTest(testDispatcher) {
        advanceUntilIdle()

        whenever(workoutParser.parseWorkoutText(any(), any()))
            .thenReturn(ParseResult(emptyList(), "Network error"))

        viewModel.updateInputText("some workout")
        viewModel.processTextInput()
        advanceUntilIdle()

        assertEquals(AiWorkoutStep.INPUT, viewModel.uiState.value.step)
        assertEquals("Network error", viewModel.uiState.value.error)
        assertFalse(viewModel.uiState.value.isProcessing)
    }

    @Test
    fun `processTextInput with blank text sets error`() = runTest(testDispatcher) {
        viewModel.updateInputText("  ")
        viewModel.processTextInput()

        assertNotNull(viewModel.uiState.value.error)
        assertEquals(AiWorkoutStep.INPUT, viewModel.uiState.value.step)
    }

    // ── goBackToInput ─────────────────────────────────────────────────────────

    @Test
    fun `goBackToInput returns to INPUT step`() = runTest(testDispatcher) {
        advanceUntilIdle()

        whenever(workoutParser.parseWorkoutText(any(), any()))
            .thenReturn(ParseResult(listOf(ParsedExercise("Bench", 3, 8))))
        whenever(exerciseMatcher.matchExercise(any(), any()))
            .thenReturn(MatchResult(benchPress, 1.0, MatchType.EXACT))

        viewModel.updateInputText("bench")
        viewModel.processTextInput()
        advanceUntilIdle()

        assertEquals(AiWorkoutStep.PREVIEW, viewModel.uiState.value.step)
        viewModel.goBackToInput()
        assertEquals(AiWorkoutStep.INPUT, viewModel.uiState.value.step)
    }

    // ── removeExercise ────────────────────────────────────────────────────────

    @Test
    fun `removeExercise removes correct index`() = runTest(testDispatcher) {
        this.loadTwoExercisePreview()

        assertEquals(2, viewModel.uiState.value.previewExercises.size)
        viewModel.removeExercise(0)
        assertEquals(1, viewModel.uiState.value.previewExercises.size)
        assertEquals(squat, viewModel.uiState.value.previewExercises[0].matchedExercise)
    }

    // ── swapExercise ──────────────────────────────────────────────────────────

    @Test
    fun `swapExercise updates matched exercise at index`() = runTest(testDispatcher) {
        advanceUntilIdle()

        whenever(workoutParser.parseWorkoutText(any(), any()))
            .thenReturn(ParseResult(listOf(ParsedExercise("Unknown Exercise", 3, 8))))
        whenever(exerciseMatcher.matchExercise(any(), any()))
            .thenReturn(MatchResult(null, 0.4, MatchType.UNMATCHED))

        viewModel.updateInputText("workout")
        viewModel.processTextInput()
        advanceUntilIdle()

        assertEquals(MatchType.UNMATCHED, viewModel.uiState.value.previewExercises[0].matchType)

        viewModel.swapExercise(0, benchPress)

        assertEquals(benchPress, viewModel.uiState.value.previewExercises[0].matchedExercise)
        assertEquals(MatchType.EXACT, viewModel.uiState.value.previewExercises[0].matchType)
        assertEquals(1.0, viewModel.uiState.value.previewExercises[0].confidence, 0.0001)
    }

    // ── reorderExercise ───────────────────────────────────────────────────────

    @Test
    fun `reorderExercise moves exercise from index 0 to index 1`() = runTest(testDispatcher) {
        this.loadTwoExercisePreview()

        val original = viewModel.uiState.value.previewExercises
        assertEquals(benchPress, original[0].matchedExercise)
        assertEquals(squat, original[1].matchedExercise)

        viewModel.reorderExercise(0, 1)

        val reordered = viewModel.uiState.value.previewExercises
        assertEquals(squat, reordered[0].matchedExercise)
        assertEquals(benchPress, reordered[1].matchedExercise)
    }

    @Test
    fun `reorderExercise is no-op for out-of-bounds indices`() = runTest(testDispatcher) {
        this.loadTwoExercisePreview()

        val before = viewModel.uiState.value.previewExercises.toList()
        viewModel.reorderExercise(0, 5)
        assertEquals(before, viewModel.uiState.value.previewExercises)
    }

    // ── replaceExercise ───────────────────────────────────────────────────────

    @Test
    fun `replaceExercise sets matchType MANUAL regardless of prior EXACT match`() = runTest(testDispatcher) {
        this.loadTwoExercisePreview()

        assertEquals(MatchType.EXACT, viewModel.uiState.value.previewExercises[0].matchType)

        viewModel.replaceExercise(0, squat)

        assertEquals(squat, viewModel.uiState.value.previewExercises[0].matchedExercise)
        assertEquals(MatchType.MANUAL, viewModel.uiState.value.previewExercises[0].matchType)
        assertEquals(1.0, viewModel.uiState.value.previewExercises[0].confidence, 0.0001)
    }

    @Test
    fun `replaceExercise always emits synonym prompt`() = runTest(testDispatcher) {
        this.loadTwoExercisePreview()

        // exercise[0] is EXACT matched — normally no prompt on swapExercise
        assertNull(viewModel.synonymPrompt.value)

        viewModel.replaceExercise(0, squat)

        val prompt = viewModel.synonymPrompt.value
        assertNotNull(prompt)
        assertEquals(squat.id, prompt!!.exerciseId)
    }

    // ── setRestTime ───────────────────────────────────────────────────────────

    @Test
    fun `setRestTime updates restSeconds at given index`() = runTest(testDispatcher) {
        this.loadTwoExercisePreview()

        viewModel.setRestTime(0, 90)

        assertEquals(90, viewModel.uiState.value.previewExercises[0].restSeconds)
        assertNull(viewModel.uiState.value.previewExercises[1].restSeconds)
    }

    // ── setExerciseNote ───────────────────────────────────────────────────────

    @Test
    fun `setExerciseNote updates notes at given index`() = runTest(testDispatcher) {
        this.loadTwoExercisePreview()

        viewModel.setExerciseNote(1, "Keep back straight")

        assertNull(viewModel.uiState.value.previewExercises[0].notes)
        assertEquals("Keep back straight", viewModel.uiState.value.previewExercises[1].notes)
    }

    @Test
    fun `setExerciseNote with blank input clears notes`() = runTest(testDispatcher) {
        this.loadTwoExercisePreview()
        viewModel.setExerciseNote(0, "Some note")
        assertEquals("Some note", viewModel.uiState.value.previewExercises[0].notes)

        viewModel.setExerciseNote(0, "   ")

        assertNull(viewModel.uiState.value.previewExercises[0].notes)
    }

    // ── createSuperset ────────────────────────────────────────────────────────

    @Test
    fun `createSuperset assigns shared UUID to 2 consecutive exercises`() = runTest(testDispatcher) {
        this.loadTwoExercisePreview()

        viewModel.createSuperset(setOf(0, 1))

        val preview = viewModel.uiState.value.previewExercises
        assertNotNull(preview[0].supersetGroupId)
        assertEquals(preview[0].supersetGroupId, preview[1].supersetGroupId)
    }

    @Test
    fun `createSuperset applies rest=0 for all but last member`() = runTest(testDispatcher) {
        this.loadThreeExercisePreview()
        viewModel.setRestTime(0, 60)
        viewModel.setRestTime(1, 60)
        viewModel.setRestTime(2, 90)

        viewModel.createSuperset(setOf(0, 1, 2))

        val preview = viewModel.uiState.value.previewExercises
        assertEquals(0, preview[0].restSeconds)
        assertEquals(0, preview[1].restSeconds)
        assertEquals(90, preview[2].restSeconds)
    }

    @Test
    fun `createSuperset ignores non-consecutive indices`() = runTest(testDispatcher) {
        this.loadThreeExercisePreview()

        viewModel.createSuperset(setOf(0, 2))

        val preview = viewModel.uiState.value.previewExercises
        assertNull(preview[0].supersetGroupId)
        assertNull(preview[2].supersetGroupId)
    }

    @Test
    fun `createSuperset ignores groups larger than 4`() = runTest(testDispatcher) {
        this.loadThreeExercisePreview()

        viewModel.createSuperset(setOf(0, 1, 2, 3, 4))

        val preview = viewModel.uiState.value.previewExercises
        assertNull(preview[0].supersetGroupId)
    }

    // ── dissolveSuperset ──────────────────────────────────────────────────────

    @Test
    fun `dissolveSuperset clears supersetGroupId from all group members`() = runTest(testDispatcher) {
        this.loadTwoExercisePreview()
        viewModel.createSuperset(setOf(0, 1))

        val groupId = viewModel.uiState.value.previewExercises[0].supersetGroupId
        assertNotNull(groupId)

        viewModel.dissolveSuperset(0)

        val preview = viewModel.uiState.value.previewExercises
        assertNull(preview[0].supersetGroupId)
        assertNull(preview[1].supersetGroupId)
    }

    @Test
    fun `dissolveSuperset on exercise without superset is no-op`() = runTest(testDispatcher) {
        this.loadTwoExercisePreview()

        viewModel.dissolveSuperset(0)

        val preview = viewModel.uiState.value.previewExercises
        assertNull(preview[0].supersetGroupId)
        assertNull(preview[1].supersetGroupId)
    }

    // ── matchedPlanExercises maps new fields ──────────────────────────────────

    @Test
    fun `startWorkout passes supersetGroupId and notes to createWorkoutFromPlan`() = runTest(testDispatcher) {
        this.loadTwoExercisePreview()
        viewModel.createSuperset(setOf(0, 1))
        viewModel.setExerciseNote(0, "Keep tight")

        val groupId = viewModel.uiState.value.previewExercises[0].supersetGroupId
        val bootstrap = WorkoutBootstrap("w1", emptyMap(), emptyList())
        whenever(workoutRepository.createWorkoutFromPlan(any())).thenReturn(bootstrap)

        viewModel.startWorkout()
        advanceUntilIdle()

        verify(workoutRepository).createWorkoutFromPlan(
            listOf(
                PlanExercise(exerciseId = 1L, sets = 3, reps = 8, weight = null, restSeconds = 0, supersetGroupId = groupId, notes = "Keep tight"),
                PlanExercise(exerciseId = 2L, sets = 4, reps = 5, weight = null, restSeconds = null, supersetGroupId = groupId, notes = null)
            )
        )
    }

    // ── Organize mode ─────────────────────────────────────────────────────────

    @Test
    fun `enterOrganizeMode sets isOrganizeMode true`() = runTest(testDispatcher) {
        viewModel.enterOrganizeMode()
        assertTrue(viewModel.uiState.value.isOrganizeMode)
    }

    @Test
    fun `exitOrganizeMode clears isOrganizeMode`() = runTest(testDispatcher) {
        viewModel.enterOrganizeMode()
        viewModel.exitOrganizeMode()
        assertFalse(viewModel.uiState.value.isOrganizeMode)
    }

    // ── Superset select mode ──────────────────────────────────────────────────

    @Test
    fun `enterSupersetSelectMode sets mode and anchor with anchor in candidates`() = runTest(testDispatcher) {
        viewModel.enterSupersetSelectMode(1)

        val state = viewModel.uiState.value
        assertTrue(state.isSupersetSelectMode)
        assertEquals(1, state.supersetAnchorIndex)
        assertTrue(1 in state.supersetCandidateIndices)
    }

    @Test
    fun `exitSupersetSelectMode clears all superset select state`() = runTest(testDispatcher) {
        viewModel.enterSupersetSelectMode(0)
        viewModel.toggleSupersetCandidate(1)
        viewModel.exitSupersetSelectMode()

        val state = viewModel.uiState.value
        assertFalse(state.isSupersetSelectMode)
        assertNull(state.supersetAnchorIndex)
        assertTrue(state.supersetCandidateIndices.isEmpty())
    }

    @Test
    fun `toggleSupersetCandidate adds and removes index from candidates`() = runTest(testDispatcher) {
        viewModel.enterSupersetSelectMode(0)

        viewModel.toggleSupersetCandidate(1)
        assertTrue(1 in viewModel.uiState.value.supersetCandidateIndices)

        viewModel.toggleSupersetCandidate(1)
        assertFalse(1 in viewModel.uiState.value.supersetCandidateIndices)
    }

    @Test
    fun `toggleSupersetCandidate cannot remove anchor index`() = runTest(testDispatcher) {
        viewModel.enterSupersetSelectMode(0)
        viewModel.toggleSupersetCandidate(0) // toggle anchor
        assertTrue(0 in viewModel.uiState.value.supersetCandidateIndices)
    }

    // ── startWorkout ──────────────────────────────────────────────────────────

    @Test
    fun `startWorkout calls createWorkoutFromPlan with matched exercises`() = runTest(testDispatcher) {
        advanceUntilIdle()

        whenever(workoutParser.parseWorkoutText(any(), any()))
            .thenReturn(ParseResult(listOf(ParsedExercise("Bench Press", 3, 8, weight = 80.0))))
        whenever(exerciseMatcher.matchExercise(any(), any()))
            .thenReturn(MatchResult(benchPress, 1.0, MatchType.EXACT))
        val bootstrap = WorkoutBootstrap("w1", emptyMap(), emptyList())
        whenever(workoutRepository.createWorkoutFromPlan(any())).thenReturn(bootstrap)

        viewModel.updateInputText("3x8 bench")
        viewModel.processTextInput()
        advanceUntilIdle()

        viewModel.startWorkout()
        advanceUntilIdle()

        verify(workoutRepository).createWorkoutFromPlan(
            listOf(PlanExercise(exerciseId = 1L, sets = 3, reps = 8, weight = 80.0, restSeconds = null, supersetGroupId = null, notes = null))
        )
    }

    // ── API_KEY_MISSING sentinel ──────────────────────────────────────────────

    @Test
    fun `processTextInput shows settings CTA when API key is missing`() = runTest(testDispatcher) {
        advanceUntilIdle()

        whenever(workoutParser.parseWorkoutText(any(), any()))
            .thenReturn(ParseResult(emptyList(), "API_KEY_MISSING"))

        viewModel.updateInputText("bench press")
        viewModel.processTextInput()
        advanceUntilIdle()

        assertEquals(AiWorkoutStep.INPUT, viewModel.uiState.value.step)
        assertTrue(viewModel.uiState.value.error?.contains("Settings") == true)
    }

    @Test
    fun `processTextInput with generic error shows raw error message`() = runTest(testDispatcher) {
        advanceUntilIdle()

        whenever(workoutParser.parseWorkoutText(any(), any()))
            .thenReturn(ParseResult(emptyList(), "Network timeout"))

        viewModel.updateInputText("bench press")
        viewModel.processTextInput()
        advanceUntilIdle()

        assertEquals("Network timeout", viewModel.uiState.value.error)
    }

    @Test
    fun `startWorkout emits WorkoutStarted event`() = runTest(testDispatcher) {
        advanceUntilIdle()

        whenever(workoutParser.parseWorkoutText(any(), any()))
            .thenReturn(ParseResult(listOf(ParsedExercise("Bench Press", 3, 8))))
        whenever(exerciseMatcher.matchExercise(any(), any()))
            .thenReturn(MatchResult(benchPress, 1.0, MatchType.EXACT))
        val bootstrap = WorkoutBootstrap("w1", emptyMap(), emptyList())
        whenever(workoutRepository.createWorkoutFromPlan(any())).thenReturn(bootstrap)

        viewModel.updateInputText("bench press")
        viewModel.processTextInput()
        advanceUntilIdle()

        viewModel.startWorkout()
        advanceUntilIdle()

        val event = viewModel.events.value
        assertTrue(event is AiWorkoutEvent.WorkoutStarted)
        assertEquals("w1", (event as AiWorkoutEvent.WorkoutStarted).bootstrap.workoutId)
    }
}
