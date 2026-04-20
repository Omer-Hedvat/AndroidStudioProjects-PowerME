package com.powerme.app.ui.metrics

import androidx.lifecycle.SavedStateHandle
import com.powerme.app.analytics.ReadinessEngine
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.repository.TrendsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for the 24-hour recompute gate in [TrendsViewModel.refreshBodyStressMap].
 *
 * Gate logic: refresh is skipped when `now - lastStressComputedAt < 24h`.
 * Initial [TrendsViewModel.loadAll] always calls [TrendsRepository.getBodyStressMap]
 * regardless of the timestamp, because it's the unconditional startup path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StressRecomputeGateTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var trendsRepository: TrendsRepository
    private lateinit var appSettings: AppSettingsDataStore

    private val twentyFiveHoursAgoMs = System.currentTimeMillis() - 25 * 3600 * 1000L
    private val twentyThreeHoursAgoMs = System.currentTimeMillis() - 23 * 3600 * 1000L

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        trendsRepository = mock()
        appSettings = mock()
    }

    @After
    fun tearDown() {
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

    @Test
    fun `initial loadAll always calls getBodyStressMap regardless of timestamp`() = runTest(testDispatcher) {
        // Even with a very recent timestamp, startup loadAll() must always run
        whenever(appSettings.lastStressComputedAt).thenReturn(flowOf(twentyThreeHoursAgoMs))
        stubRepositoryDefaults()

        TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()

        verify(trendsRepository, times(1)).getBodyStressMap()
    }

    @Test
    fun `refreshBodyStressMap runs when timestamp is 0 (never computed)`() = runTest(testDispatcher) {
        whenever(appSettings.lastStressComputedAt).thenReturn(flowOf(0L))
        stubRepositoryDefaults()

        val viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()

        // Reset: now only count calls from refreshBodyStressMap
        whenever(trendsRepository.getBodyStressMap()).thenReturn(emptyList())

        viewModel.refreshBodyStressMap()
        runCurrent()

        // 2 total: 1 from loadAll + 1 from refreshBodyStressMap
        verify(trendsRepository, times(2)).getBodyStressMap()
    }

    @Test
    fun `refreshBodyStressMap runs when more than 24h have elapsed`() = runTest(testDispatcher) {
        whenever(appSettings.lastStressComputedAt).thenReturn(flowOf(twentyFiveHoursAgoMs))
        stubRepositoryDefaults()

        val viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()

        viewModel.refreshBodyStressMap()
        runCurrent()

        // 2 total: loadAll + refreshBodyStressMap (gate open)
        verify(trendsRepository, times(2)).getBodyStressMap()
    }

    @Test
    fun `refreshBodyStressMap is skipped when less than 24h have elapsed`() = runTest(testDispatcher) {
        whenever(appSettings.lastStressComputedAt).thenReturn(flowOf(twentyThreeHoursAgoMs))
        stubRepositoryDefaults()

        val viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()

        viewModel.refreshBodyStressMap()
        runCurrent()

        // Only 1 call: loadAll — refreshBodyStressMap was skipped by the gate
        verify(trendsRepository, times(1)).getBodyStressMap()
    }

    @Test
    fun `setLastStressComputedAt is called after successful computation`() = runTest(testDispatcher) {
        whenever(appSettings.lastStressComputedAt).thenReturn(flowOf(0L))
        stubRepositoryDefaults()

        TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()

        // loadAll triggered loadBodyStressMap which succeeded → timestamp must be stamped
        verify(appSettings, times(1)).setLastStressComputedAt(any())
    }

    @Test
    fun `setLastStressComputedAt is NOT called when repository throws`() = runTest(testDispatcher) {
        whenever(appSettings.lastStressComputedAt).thenReturn(flowOf(0L))
        stubRepositoryDefaults()
        whenever(trendsRepository.getBodyStressMap()).thenThrow(RuntimeException("DB error"))

        TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()

        verify(appSettings, never()).setLastStressComputedAt(any())
    }
}
