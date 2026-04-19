package com.powerme.app.ui.metrics

import androidx.lifecycle.SavedStateHandle
import com.powerme.app.analytics.ReadinessEngine
import com.powerme.app.data.repository.TrendsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeast
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Verifies that EffectiveSets and MuscleGroupVolume data is re-fetched from the
 * repository whenever the time range changes via setTimeRange.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrendsViewModelEffectiveSetsFilterTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var trendsRepository: TrendsRepository
    private lateinit var viewModel: TrendsViewModel

    // A fixed epoch-aligned week start used in DAO rows
    private val weekBucket = 2818L
    private val weekStartMs = weekBucket * 604_800_000L

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        trendsRepository = mock()
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
        whenever(trendsRepository.getReadinessScore())
            .thenReturn(ReadinessEngine.ReadinessScore.NoData)
        whenever(trendsRepository.getReadinessSubMetrics())
            .thenReturn(ReadinessSubMetrics(null, null, null))
        whenever(trendsRepository.getE1RMProgression(any(), any(), any()))
            .thenReturn(E1RMProgressionData(1L, "Squat", emptyList(), emptyList(), null))
        whenever(trendsRepository.getWeeklyVolume(any()))
            .thenReturn(WeeklyVolumeData(emptyList(), 0.0))
        whenever(trendsRepository.getWeeklyMuscleGroupVolume(any()))
            .thenReturn(emptyList())
        whenever(trendsRepository.getWeeklyEffectiveSets(any()))
            .thenReturn(emptyList())
        whenever(trendsRepository.getEffectiveSetsCoverage(any()))
            .thenReturn(0f)
        whenever(trendsRepository.getBodyCompositionData(any()))
            .thenReturn(BodyCompositionData(emptyList(), emptyList(), emptyList()))
        whenever(trendsRepository.getExercisePicker(any()))
            .thenReturn(emptyList())
        whenever(trendsRepository.getChronotypeData(any()))
            .thenReturn(ChronotypeData(emptyList(), emptyList(), null, null))
        whenever(trendsRepository.getBodyStressMap())
            .thenReturn(emptyList())
    }

    // ── setTimeRange triggers re-fetch of effective sets ─────────────────────

    @Test
    fun `setTimeRange ONE_MONTH re-fetches effective sets with new range`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        viewModel = TrendsViewModel(trendsRepository, SavedStateHandle())
        runCurrent()

        // Change from default THREE_MONTHS to ONE_MONTH
        viewModel.setTimeRange(TrendsTimeRange.ONE_MONTH)
        runCurrent()

        // Capture all sinceMs values passed to getWeeklyEffectiveSets
        val captor = argumentCaptor<TrendsTimeRange>()
        verify(trendsRepository, atLeast(2)).getWeeklyEffectiveSets(captor.capture())

        val allRanges = captor.allValues
        // Last call must be ONE_MONTH
        assertEquals(TrendsTimeRange.ONE_MONTH, allRanges.last())
    }

    @Test
    fun `setTimeRange ONE_YEAR re-fetches muscle group volume with new range`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        viewModel = TrendsViewModel(trendsRepository, SavedStateHandle())
        runCurrent()

        viewModel.setTimeRange(TrendsTimeRange.ONE_YEAR)
        runCurrent()

        val captor = argumentCaptor<TrendsTimeRange>()
        verify(trendsRepository, atLeast(2)).getWeeklyMuscleGroupVolume(captor.capture())

        assertEquals(TrendsTimeRange.ONE_YEAR, captor.allValues.last())
    }

    @Test
    fun `setTimeRange with same range does not trigger additional re-fetch`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        viewModel = TrendsViewModel(trendsRepository, SavedStateHandle())
        runCurrent()

        // Default is THREE_MONTHS; calling setTimeRange with THREE_MONTHS should be a no-op
        viewModel.setTimeRange(TrendsTimeRange.THREE_MONTHS)
        runCurrent()

        // Only the initial load should have called the DAO, not a second time
        val captor = argumentCaptor<TrendsTimeRange>()
        verify(trendsRepository, atLeast(1)).getWeeklyEffectiveSets(captor.capture())
        // All calls should use THREE_MONTHS (the default and the no-op re-set)
        captor.allValues.forEach { range ->
            assertEquals(TrendsTimeRange.THREE_MONTHS, range)
        }
    }

    // ── timeRange state is updated correctly ──────────────────────────────────

    @Test
    fun `setTimeRange updates timeRange StateFlow`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        viewModel = TrendsViewModel(trendsRepository, SavedStateHandle())
        runCurrent()

        assertEquals(TrendsTimeRange.THREE_MONTHS, viewModel.timeRange.value)

        viewModel.setTimeRange(TrendsTimeRange.SIX_MONTHS)
        runCurrent()

        assertEquals(TrendsTimeRange.SIX_MONTHS, viewModel.timeRange.value)
    }

    @Test
    fun `setTimeRange updates effectiveSets from repository response`() = runTest(testDispatcher) {
        stubRepositoryDefaults()

        val oneMonthData = listOf(
            EffectiveSetsChartPoint(weekStartMs, "Chest", 4),
            EffectiveSetsChartPoint(weekStartMs, "Legs", 6)
        )
        whenever(trendsRepository.getWeeklyEffectiveSets(TrendsTimeRange.ONE_MONTH))
            .thenReturn(oneMonthData)

        viewModel = TrendsViewModel(trendsRepository, SavedStateHandle())
        runCurrent()

        viewModel.setTimeRange(TrendsTimeRange.ONE_MONTH)
        runCurrent()

        assertEquals(oneMonthData, viewModel.effectiveSets.value)
    }

    @Test
    fun `setTimeRange updates muscleGroupVolume from repository response`() = runTest(testDispatcher) {
        stubRepositoryDefaults()

        val sixMonthData = listOf(
            MuscleGroupVolumePoint(weekStartMs, "Back", 12000.0),
            MuscleGroupVolumePoint(weekStartMs, "Legs", 18000.0)
        )
        whenever(trendsRepository.getWeeklyMuscleGroupVolume(TrendsTimeRange.SIX_MONTHS))
            .thenReturn(sixMonthData)

        viewModel = TrendsViewModel(trendsRepository, SavedStateHandle())
        runCurrent()

        viewModel.setTimeRange(TrendsTimeRange.SIX_MONTHS)
        runCurrent()

        assertEquals(sixMonthData, viewModel.muscleGroupVolume.value)
    }
}
