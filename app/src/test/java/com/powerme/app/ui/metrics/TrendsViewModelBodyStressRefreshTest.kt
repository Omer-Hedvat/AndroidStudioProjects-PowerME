package com.powerme.app.ui.metrics

import androidx.lifecycle.SavedStateHandle
import com.powerme.app.analytics.ReadinessEngine
import com.powerme.app.analytics.StressAccumulationEngine
import com.powerme.app.data.database.BodyRegion
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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TrendsViewModelBodyStressRefreshTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var trendsRepository: TrendsRepository
    private lateinit var viewModel: TrendsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        trendsRepository = mock()
    }

    @After
    fun tearDown() {
        // Vico's CartesianChartModelProducer.runTransaction uses a real background thread.
        // Give it time to finish before resetMain() unsets Dispatchers.Main.
        Thread.sleep(100)
        Dispatchers.resetMain()
    }

    private suspend fun stubRepositoryDefaults(
        stressResult: List<StressAccumulationEngine.RegionStress> = emptyList()
    ) {
        whenever(trendsRepository.getReadinessScore()).thenReturn(
            ReadinessEngine.ReadinessScore.NoData
        )
        whenever(trendsRepository.getReadinessSubMetrics()).thenReturn(
            ReadinessSubMetrics(null, null, null)
        )
        whenever(trendsRepository.getWeeklyVolume(org.mockito.kotlin.any())).thenReturn(
            WeeklyVolumeData(emptyList(), 0.0)
        )
        whenever(trendsRepository.getWeeklyMuscleGroupVolume(org.mockito.kotlin.any())).thenReturn(
            emptyList()
        )
        whenever(trendsRepository.getWeeklyEffectiveSets(org.mockito.kotlin.any())).thenReturn(
            emptyList()
        )
        whenever(trendsRepository.getEffectiveSetsCoverage(org.mockito.kotlin.any())).thenReturn(0f)
        whenever(trendsRepository.getBodyCompositionData(org.mockito.kotlin.any())).thenReturn(
            BodyCompositionData(emptyList(), emptyList(), emptyList())
        )
        whenever(trendsRepository.getExercisePicker(org.mockito.kotlin.any())).thenReturn(
            emptyList()
        )
        whenever(trendsRepository.getChronotypeData(org.mockito.kotlin.any())).thenReturn(
            ChronotypeData(emptyList(), emptyList(), null, null)
        )
        whenever(trendsRepository.getBodyStressMap()).thenReturn(stressResult)
    }

    @Test
    fun `refreshBodyStressMap triggers additional getBodyStressMap call`() = runTest(testDispatcher) {
        stubRepositoryDefaults()

        viewModel = TrendsViewModel(trendsRepository, SavedStateHandle())
        runCurrent()

        // init's loadAll() already called getBodyStressMap once — call refreshBodyStressMap() now
        viewModel.refreshBodyStressMap()
        runCurrent()

        // Should have been called twice: once in loadAll() and once via refreshBodyStressMap()
        verify(trendsRepository, times(2)).getBodyStressMap()
    }

    @Test
    fun `refreshBodyStressMap updates bodyStressMap state with latest data`() = runTest(testDispatcher) {
        val initialStress = listOf(
            StressAccumulationEngine.RegionStress(BodyRegion.PECS, 100.0)
        )
        val refreshedStress = listOf(
            StressAccumulationEngine.RegionStress(BodyRegion.PECS, 70.0) // decayed
        )

        stubRepositoryDefaults(initialStress)
        viewModel = TrendsViewModel(trendsRepository, SavedStateHandle())
        runCurrent()

        // Verify initial state from loadAll()
        val initialState = viewModel.bodyStressMap.value
        assertNotNull(initialState)
        assertEquals(100.0, initialState!!.regionStresses.first().totalStress, 0.001)

        // Simulate time passing: repository now returns lower stress (decay applied)
        whenever(trendsRepository.getBodyStressMap()).thenReturn(refreshedStress)

        viewModel.refreshBodyStressMap()
        runCurrent()

        val refreshedState = viewModel.bodyStressMap.value
        assertNotNull(refreshedState)
        assertEquals(70.0, refreshedState!!.regionStresses.first().totalStress, 0.001)
    }

    @Test
    fun `refreshBodyStressMap handles repository exception gracefully`() = runTest(testDispatcher) {
        val initialStress = listOf(
            StressAccumulationEngine.RegionStress(BodyRegion.UPPER_BACK, 50.0)
        )
        stubRepositoryDefaults(initialStress)
        viewModel = TrendsViewModel(trendsRepository, SavedStateHandle())
        runCurrent()

        // Repository throws on the refresh call
        whenever(trendsRepository.getBodyStressMap())
            .thenThrow(RuntimeException("DB error"))

        viewModel.refreshBodyStressMap()
        runCurrent()

        // State should be set to null (exception path in loadBodyStressMap)
        assertEquals(null, viewModel.bodyStressMap.value)
    }

    @Test
    fun `refreshBodyStressMap updates maxStress derived field`() = runTest(testDispatcher) {
        stubRepositoryDefaults(emptyList())
        viewModel = TrendsViewModel(trendsRepository, SavedStateHandle())
        runCurrent()

        val multiRegionStress = listOf(
            StressAccumulationEngine.RegionStress(BodyRegion.PECS, 80.0),
            StressAccumulationEngine.RegionStress(BodyRegion.ANTERIOR_DELTOID, 120.0),
            StressAccumulationEngine.RegionStress(BodyRegion.UPPER_BACK, 60.0)
        )
        whenever(trendsRepository.getBodyStressMap()).thenReturn(multiRegionStress)

        viewModel.refreshBodyStressMap()
        runCurrent()

        val state = viewModel.bodyStressMap.value
        assertNotNull(state)
        assertEquals(120.0, state!!.maxStress, 0.001)
    }
}
