package com.powerme.app.ui.metrics

import androidx.lifecycle.SavedStateHandle
import com.powerme.app.analytics.ReadinessEngine
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.database.ExerciseWithHistory
import com.powerme.app.data.repository.TrendsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.flow.flowOf
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate

/**
 * Unit tests for the six hasXData boolean StateFlows in TrendsViewModel.
 * Each flag is derived from existing data flows — no new DB queries.
 * Tests cover: false when empty/null, true when non-empty, false when exception.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrendsViewModelDataFlagsTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var trendsRepository: TrendsRepository
    private lateinit var appSettings: AppSettingsDataStore
    private lateinit var viewModel: TrendsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        trendsRepository = mock()
        appSettings = mock()
        whenever(appSettings.lastStressComputedAt).thenReturn(flowOf(0L))
    }

    @After
    fun tearDown() {
        // Vico's CartesianChartModelProducer.runTransaction uses a real background thread.
        // Give it time to finish before resetMain() unsets Dispatchers.Main, which would
        // cause a concurrent-read exception in TestMainDispatcher.
        Thread.sleep(100)
        Dispatchers.resetMain()
    }

    private suspend fun stubRepositoryDefaults() {
        whenever(trendsRepository.getReadinessScore()).thenReturn(
            ReadinessEngine.ReadinessScore.NoData
        )
        whenever(trendsRepository.getReadinessSubMetrics()).thenReturn(
            ReadinessSubMetrics(null, null, null)
        )
        whenever(trendsRepository.getE1RMProgression(any(), any(), any())).thenReturn(
            E1RMProgressionData(1L, "Squat", emptyList(), emptyList(), null)
        )
        whenever(trendsRepository.getWeeklyVolume(any())).thenReturn(
            WeeklyVolumeData(emptyList(), 0.0)
        )
        whenever(trendsRepository.getWeeklyMuscleGroupVolume(any())).thenReturn(emptyList())
        whenever(trendsRepository.getWeeklyEffectiveSets(any())).thenReturn(emptyList())
        whenever(trendsRepository.getEffectiveSetsCoverage(any())).thenReturn(0f)
        whenever(trendsRepository.getBodyCompositionData(any())).thenReturn(
            BodyCompositionData(emptyList(), emptyList(), emptyList())
        )
        whenever(trendsRepository.getExercisePicker(any())).thenReturn(emptyList())
        whenever(trendsRepository.getChronotypeData(any())).thenReturn(
            ChronotypeData(emptyList(), emptyList(), null, null)
        )
        whenever(trendsRepository.getBodyStressMap()).thenReturn(emptyList())
    }

    // ── hasVolumeData ─────────────────────────────────────────────────────────

    @Test
    fun `hasVolumeData is false when weeklyVolume has empty points`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        whenever(trendsRepository.getWeeklyVolume(any())).thenReturn(
            WeeklyVolumeData(emptyList(), 0.0)
        )
        viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()
        assertFalse(viewModel.hasVolumeData.value)
    }

    @Test
    fun `hasVolumeData is true when weeklyVolume has points`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        val pts = listOf(WeeklyVolumeChartPoint(weekStartMs = 1_000L, totalVolume = 500.0, workoutCount = 2))
        whenever(trendsRepository.getWeeklyVolume(any())).thenReturn(
            WeeklyVolumeData(pts, 1.0)
        )
        viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()
        assertTrue(viewModel.hasVolumeData.value)
    }

    @Test
    fun `hasVolumeData is false when getWeeklyVolume throws`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        whenever(trendsRepository.getWeeklyVolume(any())).thenThrow(RuntimeException("DB error"))
        viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()
        assertFalse(viewModel.hasVolumeData.value)
    }

    // ── hasE1rmData ───────────────────────────────────────────────────────────

    @Test
    fun `hasE1rmData is false when exercisePickerItems is empty`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        whenever(trendsRepository.getExercisePicker(any())).thenReturn(emptyList())
        viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()
        assertFalse(viewModel.hasE1rmData.value)
    }

    @Test
    fun `hasE1rmData is true when exercisePickerItems has entries`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        val exercise = ExerciseWithHistory(id = 1L, name = "Bench Press")
        whenever(trendsRepository.getExercisePicker(any())).thenReturn(listOf(exercise))
        whenever(trendsRepository.getE1RMProgression(any(), any(), any())).thenReturn(
            E1RMProgressionData(1L, "Bench Press", emptyList(), emptyList(), null)
        )
        viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()
        assertTrue(viewModel.hasE1rmData.value)
    }

    @Test
    fun `hasE1rmData is false when getExercisePicker throws`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        whenever(trendsRepository.getExercisePicker(any())).thenThrow(RuntimeException("DB error"))
        viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()
        assertFalse(viewModel.hasE1rmData.value)
    }

    // ── hasMuscleGroupData ────────────────────────────────────────────────────

    @Test
    fun `hasMuscleGroupData is false when muscleGroupVolume is empty`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        whenever(trendsRepository.getWeeklyMuscleGroupVolume(any())).thenReturn(emptyList())
        viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()
        assertFalse(viewModel.hasMuscleGroupData.value)
    }

    @Test
    fun `hasMuscleGroupData is true when muscleGroupVolume has entries`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        val pts = listOf(MuscleGroupVolumePoint(weekStartMs = 1_000L, majorGroup = "Legs", volume = 200.0))
        whenever(trendsRepository.getWeeklyMuscleGroupVolume(any())).thenReturn(pts)
        viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()
        assertTrue(viewModel.hasMuscleGroupData.value)
    }

    @Test
    fun `hasMuscleGroupData is false when getWeeklyMuscleGroupVolume throws`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        whenever(trendsRepository.getWeeklyMuscleGroupVolume(any()))
            .thenThrow(RuntimeException("DB error"))
        viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()
        assertFalse(viewModel.hasMuscleGroupData.value)
    }

    // ── hasEffectiveSetsData ──────────────────────────────────────────────────

    @Test
    fun `hasEffectiveSetsData is false when effectiveSets is empty`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        whenever(trendsRepository.getWeeklyEffectiveSets(any())).thenReturn(emptyList())
        viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()
        assertFalse(viewModel.hasEffectiveSetsData.value)
    }

    @Test
    fun `hasEffectiveSetsData is true when effectiveSets has entries`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        val pts = listOf(EffectiveSetsChartPoint(weekStartMs = 1_000L, majorGroup = "Back", setCount = 3))
        whenever(trendsRepository.getWeeklyEffectiveSets(any())).thenReturn(pts)
        viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()
        assertTrue(viewModel.hasEffectiveSetsData.value)
    }

    @Test
    fun `hasEffectiveSetsData is false when getWeeklyEffectiveSets throws`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        whenever(trendsRepository.getWeeklyEffectiveSets(any()))
            .thenThrow(RuntimeException("DB error"))
        viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()
        assertFalse(viewModel.hasEffectiveSetsData.value)
    }

    // ── hasBodyCompositionData ────────────────────────────────────────────────

    @Test
    fun `hasBodyCompositionData is false when both weight and bodyFat are empty`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        whenever(trendsRepository.getBodyCompositionData(any())).thenReturn(
            BodyCompositionData(emptyList(), emptyList(), emptyList())
        )
        viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()
        assertFalse(viewModel.hasBodyCompositionData.value)
    }

    @Test
    fun `hasBodyCompositionData is true when weight points exist`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        val weightPts = listOf(TimestampedValue(timestampMs = 1_000L, value = 75.0))
        whenever(trendsRepository.getBodyCompositionData(any())).thenReturn(
            BodyCompositionData(weightPts, emptyList(), emptyList())
        )
        viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()
        assertTrue(viewModel.hasBodyCompositionData.value)
    }

    @Test
    fun `hasBodyCompositionData is true when bodyFat points exist`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        val bodyFatPts = listOf(TimestampedValue(timestampMs = 1_000L, value = 18.5))
        whenever(trendsRepository.getBodyCompositionData(any())).thenReturn(
            BodyCompositionData(emptyList(), bodyFatPts, emptyList())
        )
        viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()
        assertTrue(viewModel.hasBodyCompositionData.value)
    }

    @Test
    fun `hasBodyCompositionData is false when getBodyCompositionData throws`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        whenever(trendsRepository.getBodyCompositionData(any()))
            .thenThrow(RuntimeException("DB error"))
        viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()
        assertFalse(viewModel.hasBodyCompositionData.value)
    }

    // ── hasChronotypeData ─────────────────────────────────────────────────────

    @Test
    fun `hasChronotypeData is false when both sleep and workout points are empty`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        whenever(trendsRepository.getChronotypeData(any())).thenReturn(
            ChronotypeData(emptyList(), emptyList(), null, null)
        )
        viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()
        assertFalse(viewModel.hasChronotypeData.value)
    }

    @Test
    fun `hasChronotypeData is true when sleep points exist`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        val sleepPts = listOf(SleepChartPoint(LocalDate.of(2025, 1, 1), durationMinutes = 480))
        whenever(trendsRepository.getChronotypeData(any())).thenReturn(
            ChronotypeData(sleepPts, emptyList(), null, null)
        )
        viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()
        assertTrue(viewModel.hasChronotypeData.value)
    }

    @Test
    fun `hasChronotypeData is true when workout points exist`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        val workoutPts = listOf(TimeOfDayChartPoint(startHour = 9, totalVolume = 1000.0))
        whenever(trendsRepository.getChronotypeData(any())).thenReturn(
            ChronotypeData(emptyList(), workoutPts, null, null)
        )
        viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()
        assertTrue(viewModel.hasChronotypeData.value)
    }

    @Test
    fun `hasChronotypeData is true when both sleep and workout points exist`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        val sleepPts = (1..7).map { i -> SleepChartPoint(LocalDate.of(2025, 1, i), durationMinutes = 480) }
        val workoutPts = (0..9).map { TimeOfDayChartPoint(startHour = 9, totalVolume = 1000.0) }
        whenever(trendsRepository.getChronotypeData(any())).thenReturn(
            ChronotypeData(sleepPts, workoutPts, peakHour = 9, peakHourLabel = "9am")
        )
        viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()
        assertTrue(viewModel.hasChronotypeData.value)
    }

    @Test
    fun `hasChronotypeData is false when getChronotypeData throws`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        whenever(trendsRepository.getChronotypeData(any())).thenThrow(RuntimeException("DB error"))
        viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()
        assertFalse(viewModel.hasChronotypeData.value)
    }
}
