package com.powerme.app.ui.metrics

import androidx.lifecycle.SavedStateHandle
import com.powerme.app.data.AppSettingsDataStore
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.flow.flowOf
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class TrendsViewModelBodyCompositionTest {

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
        // Give it time to finish before resetMain() unsets Dispatchers.Main.
        Thread.sleep(100)
        Dispatchers.resetMain()
    }

    private suspend fun stubRepositoryDefaults() {
        whenever(trendsRepository.getReadinessScore()).thenReturn(
            com.powerme.app.analytics.ReadinessEngine.ReadinessScore.NoData
        )
        whenever(trendsRepository.getReadinessSubMetrics()).thenReturn(
            ReadinessSubMetrics(null, null, null)
        )
        whenever(trendsRepository.getE1RMProgression(
            org.mockito.kotlin.any(), org.mockito.kotlin.any(), org.mockito.kotlin.any()
        )).thenReturn(
            E1RMProgressionData(1L, "Squat", emptyList(), emptyList(), null)
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
        whenever(trendsRepository.getChronotypeData(org.mockito.kotlin.any())).thenReturn(
            ChronotypeData(emptyList(), emptyList(), null, null)
        )
        whenever(trendsRepository.getExercisePicker(org.mockito.kotlin.any())).thenReturn(
            emptyList()
        )
    }

    @Test
    fun `loadBodyComposition sets state from repository`() = runTest(testDispatcher) {
        val weightTs = 1_700_000_000_000L
        val bodyFatTs = 1_700_100_000_000L
        val data = BodyCompositionData(
            weightPoints = listOf(
                TimestampedValue(weightTs, 80.0),
                TimestampedValue(weightTs + 604_800_000L, 79.5)
            ),
            bodyFatPoints = listOf(
                TimestampedValue(bodyFatTs, 18.0),
                TimestampedValue(bodyFatTs + 604_800_000L, 17.5)
            ),
            bmiPoints = emptyList()
        )

        stubRepositoryDefaults()
        whenever(trendsRepository.getBodyCompositionData(org.mockito.kotlin.any())).thenReturn(data)

        viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()

        assertEquals(data, viewModel.bodyComposition.value)
    }

    @Test
    fun `loadBodyComposition sets null on exception`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        whenever(trendsRepository.getBodyCompositionData(org.mockito.kotlin.any()))
            .thenThrow(RuntimeException("DB error"))

        viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()

        assertNull(viewModel.bodyComposition.value)
    }

    @Test
    fun `loadBodyComposition stores weight and bodyFat points`() = runTest(testDispatcher) {
        val ts1 = 1_700_000_000_000L
        val data = BodyCompositionData(
            weightPoints = listOf(TimestampedValue(ts1, 80.0), TimestampedValue(ts1 + 604_800_000L, 79.0)),
            bodyFatPoints = listOf(TimestampedValue(ts1, 18.0)),
            bmiPoints = emptyList()
        )

        stubRepositoryDefaults()
        whenever(trendsRepository.getBodyCompositionData(org.mockito.kotlin.any())).thenReturn(data)

        viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()

        val result = viewModel.bodyComposition.value
        assertNotNull(result)
        assertEquals(2, result!!.weightPoints.size)
        assertEquals(1, result.bodyFatPoints.size)
    }

    @Test
    fun `loadBodyComposition handles empty data`() = runTest(testDispatcher) {
        val data = BodyCompositionData(emptyList(), emptyList(), emptyList())

        stubRepositoryDefaults()
        whenever(trendsRepository.getBodyCompositionData(org.mockito.kotlin.any())).thenReturn(data)

        viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()

        val result = viewModel.bodyComposition.value
        assertNotNull(result)
        assertEquals(emptyList<TimestampedValue>(), result!!.weightPoints)
        assertEquals(emptyList<TimestampedValue>(), result.bodyFatPoints)
    }

    @Test
    fun `loadBodyComposition handles weight-only data`() = runTest(testDispatcher) {
        val data = BodyCompositionData(
            weightPoints = listOf(TimestampedValue(1_700_000_000_000L, 80.0)),
            bodyFatPoints = emptyList(),
            bmiPoints = emptyList()
        )

        stubRepositoryDefaults()
        whenever(trendsRepository.getBodyCompositionData(org.mockito.kotlin.any())).thenReturn(data)

        viewModel = TrendsViewModel(trendsRepository, appSettings, SavedStateHandle())
        runCurrent()

        val result = viewModel.bodyComposition.value
        assertNotNull(result)
        assertEquals(1, result!!.weightPoints.size)
        assertEquals(emptyList<TimestampedValue>(), result.bodyFatPoints)
    }
}
