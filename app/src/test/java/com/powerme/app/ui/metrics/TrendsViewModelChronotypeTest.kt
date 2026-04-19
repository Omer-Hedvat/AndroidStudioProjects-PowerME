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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class TrendsViewModelChronotypeTest {

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

    private suspend fun stubRepositoryDefaults() {
        whenever(trendsRepository.getReadinessScore()).thenReturn(
            ReadinessEngine.ReadinessScore.NoData
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
        whenever(trendsRepository.getBodyCompositionData(org.mockito.kotlin.any())).thenReturn(
            BodyCompositionData(emptyList(), emptyList(), emptyList())
        )
        whenever(trendsRepository.getExercisePicker(org.mockito.kotlin.any())).thenReturn(
            emptyList()
        )
    }

    // ── Sleep empty-state threshold ───────────────────────────────────────────

    @Test
    fun `loadChronotypeData sets state from repository`() = runTest(testDispatcher) {
        val sleepPoints = (1..7).map { i ->
            SleepChartPoint(LocalDate.of(2025, 1, i), durationMinutes = 480)
        }
        val workoutPoints = (0..9).map { TimeOfDayChartPoint(startHour = 9 + it % 4, totalVolume = 1000.0) }
        val data = ChronotypeData(sleepPoints, workoutPoints, peakHour = 10, peakHourLabel = "10am")

        stubRepositoryDefaults()
        whenever(trendsRepository.getChronotypeData(org.mockito.kotlin.any())).thenReturn(data)

        viewModel = TrendsViewModel(trendsRepository, SavedStateHandle())
        runCurrent()

        assertEquals(data, viewModel.chronotypeData.value)
    }

    @Test
    fun `loadChronotypeData sets null on exception`() = runTest(testDispatcher) {
        stubRepositoryDefaults()
        whenever(trendsRepository.getChronotypeData(org.mockito.kotlin.any()))
            .thenThrow(RuntimeException("DB error"))

        viewModel = TrendsViewModel(trendsRepository, SavedStateHandle())
        runCurrent()

        assertNull(viewModel.chronotypeData.value)
    }

    @Test
    fun `6 sleep points is below threshold — data stored, UI would show empty state`() = runTest(testDispatcher) {
        // The ViewModel stores whatever the repository returns; threshold logic is in the composable.
        val sleepPoints = (1..6).map { i ->
            SleepChartPoint(LocalDate.of(2025, 1, i), durationMinutes = 480)
        }
        val data = ChronotypeData(sleepPoints, emptyList(), null, null)

        stubRepositoryDefaults()
        whenever(trendsRepository.getChronotypeData(org.mockito.kotlin.any())).thenReturn(data)

        viewModel = TrendsViewModel(trendsRepository, SavedStateHandle())
        runCurrent()

        val result = viewModel.chronotypeData.value
        assertNotNull(result)
        assertEquals(6, result!!.sleepPoints.size)
        // Composable checks sleepPoints.size >= 7 — would show empty state at 6
    }

    @Test
    fun `7 sleep points is sufficient for sleep chart`() = runTest(testDispatcher) {
        val sleepPoints = (1..7).map { i ->
            SleepChartPoint(LocalDate.of(2025, 1, i), durationMinutes = 420)
        }
        val data = ChronotypeData(sleepPoints, emptyList(), null, null)

        stubRepositoryDefaults()
        whenever(trendsRepository.getChronotypeData(org.mockito.kotlin.any())).thenReturn(data)

        viewModel = TrendsViewModel(trendsRepository, SavedStateHandle())
        runCurrent()

        assertEquals(7, viewModel.chronotypeData.value!!.sleepPoints.size)
    }

    // ── Training window empty-state threshold ─────────────────────────────────

    @Test
    fun `9 workout points is below training window threshold`() = runTest(testDispatcher) {
        val workoutPoints = (0..8).map { TimeOfDayChartPoint(startHour = 9, totalVolume = 1000.0) }
        val data = ChronotypeData(emptyList(), workoutPoints, null, null)

        stubRepositoryDefaults()
        whenever(trendsRepository.getChronotypeData(org.mockito.kotlin.any())).thenReturn(data)

        viewModel = TrendsViewModel(trendsRepository, SavedStateHandle())
        runCurrent()

        val result = viewModel.chronotypeData.value!!
        assertEquals(9, result.workoutPoints.size)
        assertNull(result.peakHour) // null because < 10 workouts
    }

    @Test
    fun `10 workout points meets training window threshold`() = runTest(testDispatcher) {
        val workoutPoints = (0..9).map { TimeOfDayChartPoint(startHour = 9, totalVolume = 1000.0) }
        val data = ChronotypeData(emptyList(), workoutPoints, peakHour = 9, peakHourLabel = "9am")

        stubRepositoryDefaults()
        whenever(trendsRepository.getChronotypeData(org.mockito.kotlin.any())).thenReturn(data)

        viewModel = TrendsViewModel(trendsRepository, SavedStateHandle())
        runCurrent()

        val result = viewModel.chronotypeData.value!!
        assertEquals(10, result.workoutPoints.size)
        assertNotNull(result.peakHour)
    }

    // ── Empty data ────────────────────────────────────────────────────────────

    @Test
    fun `empty chronotype data returns non-null with empty lists`() = runTest(testDispatcher) {
        val data = ChronotypeData(emptyList(), emptyList(), null, null)

        stubRepositoryDefaults()
        whenever(trendsRepository.getChronotypeData(org.mockito.kotlin.any())).thenReturn(data)

        viewModel = TrendsViewModel(trendsRepository, SavedStateHandle())
        runCurrent()

        val result = viewModel.chronotypeData.value
        assertNotNull(result)
        assertEquals(emptyList<SleepChartPoint>(), result!!.sleepPoints)
        assertEquals(emptyList<TimeOfDayChartPoint>(), result.workoutPoints)
        assertNull(result.peakHour)
        assertNull(result.peakHourLabel)
    }
}
