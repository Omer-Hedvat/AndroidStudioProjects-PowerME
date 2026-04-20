package com.powerme.app.data.repository

import com.powerme.app.data.database.ExerciseStressVectorDao
import com.powerme.app.data.database.HealthConnectSyncDao
import com.powerme.app.data.database.SleepDurationRow
import com.powerme.app.data.database.TrendsDao
import com.powerme.app.data.database.WorkoutTimeRow
import com.powerme.app.health.HealthConnectManager
import com.powerme.app.ui.metrics.TrendsTimeRange
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate

class TrendsRepositoryChronotypeTest {

    private lateinit var trendsDao: TrendsDao
    private lateinit var hcSyncDao: HealthConnectSyncDao
    private lateinit var metricLogRepository: MetricLogRepository
    private lateinit var stressVectorDao: ExerciseStressVectorDao
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var repository: TrendsRepository

    @Before
    fun setUp() {
        trendsDao = mock()
        hcSyncDao = mock()
        metricLogRepository = mock()
        stressVectorDao = mock()
        healthConnectManager = mock()
        repository = TrendsRepository(trendsDao, hcSyncDao, metricLogRepository, stressVectorDao, healthConnectManager)
    }

    // ── formatHour (internal) ─────────────────────────────────────────────────

    @Test
    fun `formatHour midnight returns 12am`() {
        assertEquals("12am", repository.formatHour(0))
    }

    @Test
    fun `formatHour 1am returns 1am`() {
        assertEquals("1am", repository.formatHour(1))
    }

    @Test
    fun `formatHour 11am returns 11am`() {
        assertEquals("11am", repository.formatHour(11))
    }

    @Test
    fun `formatHour noon returns 12pm`() {
        assertEquals("12pm", repository.formatHour(12))
    }

    @Test
    fun `formatHour 1pm returns 1pm`() {
        assertEquals("1pm", repository.formatHour(13))
    }

    @Test
    fun `formatHour 11pm returns 11pm`() {
        assertEquals("11pm", repository.formatHour(23))
    }

    // ── computePeakHour (via getChronotypeData) ───────────────────────────────

    @Test
    fun `peakHour is null when fewer than 10 workouts`() = runTest {
        whenever(hcSyncDao.getSleepHistory()).thenReturn(emptyList())
        whenever(trendsDao.getWorkoutsByTimeOfDay(any())).thenReturn(
            (0..8).map { WorkoutTimeRow("id$it", 1_700_000_000_000L, 1000.0, 9) }
        )

        val result = repository.getChronotypeData(TrendsTimeRange.THREE_MONTHS)
        assertNull(result.peakHour)
        assertNull(result.peakHourLabel)
    }

    @Test
    fun `peakHour is identified correctly with exactly 10 workouts`() = runTest {
        whenever(hcSyncDao.getSleepHistory()).thenReturn(emptyList())
        // 6 workouts at 9am (volume 2000) and 4 at 17pm (volume 1000)
        val rows = (0..5).map { WorkoutTimeRow("a$it", 1_700_000_000_000L, 2000.0, 9) } +
                (0..3).map { WorkoutTimeRow("b$it", 1_700_000_000_000L, 1000.0, 17) }
        whenever(trendsDao.getWorkoutsByTimeOfDay(any())).thenReturn(rows)

        val result = repository.getChronotypeData(TrendsTimeRange.THREE_MONTHS)
        assertEquals(9, result.peakHour)
        assertEquals("9am", result.peakHourLabel)
    }

    @Test
    fun `peakHour uses median volume not total — high outlier does not skew result`() = runTest {
        whenever(hcSyncDao.getSleepHistory()).thenReturn(emptyList())
        // 8 workouts at 7am with volume 500 except one massive outlier at 100000
        // 2 workouts at 17pm with volume 2000 each — lower total but higher median
        val rows = listOf(
            WorkoutTimeRow("a0", 1_700_000_000_000L, 100_000.0, 7), // outlier
            WorkoutTimeRow("a1", 1_700_000_000_000L, 500.0, 7),
            WorkoutTimeRow("a2", 1_700_000_000_000L, 500.0, 7),
            WorkoutTimeRow("a3", 1_700_000_000_000L, 500.0, 7),
            WorkoutTimeRow("a4", 1_700_000_000_000L, 500.0, 7),
            WorkoutTimeRow("a5", 1_700_000_000_000L, 500.0, 7),
            WorkoutTimeRow("a6", 1_700_000_000_000L, 500.0, 7),
            WorkoutTimeRow("a7", 1_700_000_000_000L, 500.0, 7),
            WorkoutTimeRow("b0", 1_700_000_000_000L, 3000.0, 17),
            WorkoutTimeRow("b1", 1_700_000_000_000L, 3000.0, 17)
        )
        whenever(trendsDao.getWorkoutsByTimeOfDay(any())).thenReturn(rows)

        // 7am sorted volumes: [500, 500, 500, 500, 500, 500, 500, 100000] → median idx=4 → 500
        // 17pm sorted volumes: [3000, 3000] → median idx=1 → 3000
        // 17pm should win
        val result = repository.getChronotypeData(TrendsTimeRange.THREE_MONTHS)
        assertEquals(17, result.peakHour)
        assertEquals("5pm", result.peakHourLabel)
    }

    // ── Sleep data mapping ────────────────────────────────────────────────────

    @Test
    fun `sleep points are mapped from DAO rows`() = runTest {
        val sleepRows = listOf(
            SleepDurationRow(LocalDate.of(2025, 1, 1), 420),
            SleepDurationRow(LocalDate.of(2025, 1, 2), 360)
        )
        whenever(hcSyncDao.getSleepHistory()).thenReturn(sleepRows)
        whenever(trendsDao.getWorkoutsByTimeOfDay(any())).thenReturn(emptyList())

        val result = repository.getChronotypeData(TrendsTimeRange.ONE_MONTH)
        assertEquals(2, result.sleepPoints.size)
        assertEquals(LocalDate.of(2025, 1, 1), result.sleepPoints[0].date)
        assertEquals(420, result.sleepPoints[0].durationMinutes)
        assertEquals(7.0, result.sleepPoints[0].durationHours, 0.001)
    }
}
