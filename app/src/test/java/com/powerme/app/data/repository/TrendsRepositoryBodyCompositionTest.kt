package com.powerme.app.data.repository

import com.powerme.app.data.database.ExerciseStressVectorDao
import com.powerme.app.data.database.HealthConnectSyncDao
import com.powerme.app.data.database.MetricLog
import com.powerme.app.data.database.MetricType
import com.powerme.app.data.database.TrendsDao
import com.powerme.app.health.HealthConnectManager
import com.powerme.app.ui.metrics.TrendsTimeRange
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests that getBodyCompositionData() merges metric_log entries with Health Connect history,
 * with metric_log taking precedence for same-day conflicts.
 *
 * This covers BUG_body_composition_ignores_hc: BodyCompositionCard was always showing the
 * empty state because only metric_log was queried (never HC history).
 */
class TrendsRepositoryBodyCompositionTest {

    private lateinit var trendsDao: TrendsDao
    private lateinit var hcSyncDao: HealthConnectSyncDao
    private lateinit var metricLogRepository: MetricLogRepository
    private lateinit var stressVectorDao: ExerciseStressVectorDao
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var repository: TrendsRepository

    // UTC-day-aligned timestamps within the ONE_MONTH window.
    // Aligning to day boundaries avoids edge-case failures in the same-day conflict test.
    private val todayDay = System.currentTimeMillis() / 86_400_000L
    private val dayAMs = (todayDay - 20) * 86_400_000L   // start of UTC day, 20 days ago
    private val dayBMs = (todayDay - 10) * 86_400_000L   // start of UTC day, 10 days ago
    private val dayCMs = (todayDay - 5) * 86_400_000L    // start of UTC day, 5 days ago

    @Before
    fun setUp() {
        trendsDao = mock()
        hcSyncDao = mock()
        metricLogRepository = mock()
        stressVectorDao = mock()
        healthConnectManager = mock()
        repository = TrendsRepository(trendsDao, hcSyncDao, metricLogRepository, stressVectorDao, healthConnectManager)
    }

    private suspend fun stubDefaults(
        weightLogs: List<MetricLog> = emptyList(),
        bodyFatLogs: List<MetricLog> = emptyList(),
        heightLog: MetricLog? = null,
        hcWeight: List<Pair<Long, Double>> = emptyList(),
        hcBodyFat: List<Pair<Long, Double>> = emptyList()
    ) {
        whenever(metricLogRepository.getByType(MetricType.WEIGHT)).thenReturn(flowOf(weightLogs))
        whenever(metricLogRepository.getByType(MetricType.BODY_FAT)).thenReturn(flowOf(bodyFatLogs))
        whenever(metricLogRepository.getByType(MetricType.HEIGHT)).thenReturn(flowOf(emptyList()))
        whenever(metricLogRepository.getLatestForType(MetricType.HEIGHT)).thenReturn(heightLog)
        whenever(healthConnectManager.getWeightHistory(any())).thenReturn(hcWeight)
        whenever(healthConnectManager.getBodyFatHistory(any())).thenReturn(hcBodyFat)
    }

    @Test
    fun `returns metric_log data when HC returns empty`() = runTest {
        val logEntries = listOf(
            MetricLog(timestamp = dayAMs, type = MetricType.WEIGHT, value = 80.0),
            MetricLog(timestamp = dayBMs, type = MetricType.WEIGHT, value = 79.5)
        )
        stubDefaults(weightLogs = logEntries, hcWeight = emptyList())

        val result = repository.getBodyCompositionData(TrendsTimeRange.ONE_MONTH)

        assertEquals(2, result.weightPoints.size)
        assertEquals(80.0, result.weightPoints[0].value, 0.001)
        assertEquals(79.5, result.weightPoints[1].value, 0.001)
    }

    @Test
    fun `returns HC data when metric_log is empty — the main bug scenario`() = runTest {
        val hcEntries = listOf(
            dayAMs to 82.0,
            dayBMs to 81.5,
            dayCMs to 81.0
        )
        stubDefaults(weightLogs = emptyList(), hcWeight = hcEntries)

        val result = repository.getBodyCompositionData(TrendsTimeRange.ONE_MONTH)

        assertEquals(3, result.weightPoints.size)
        assertEquals(82.0, result.weightPoints[0].value, 0.001)
        assertEquals(81.5, result.weightPoints[1].value, 0.001)
        assertEquals(81.0, result.weightPoints[2].value, 0.001)
    }

    @Test
    fun `metric_log wins same-day conflict with HC`() = runTest {
        // Log has 80 kg on day A; HC also has a reading on day A at 79 kg — log must win
        val logEntries = listOf(
            MetricLog(timestamp = dayAMs, type = MetricType.WEIGHT, value = 80.0)
        )
        val hcEntries = listOf(dayAMs + 1_000L to 79.0)  // same UTC day, 1 second later
        stubDefaults(weightLogs = logEntries, hcWeight = hcEntries)

        val result = repository.getBodyCompositionData(TrendsTimeRange.ONE_MONTH)

        assertEquals(1, result.weightPoints.size)
        assertEquals(80.0, result.weightPoints[0].value, 0.001)
    }

    @Test
    fun `merges entries from different days into sorted list`() = runTest {
        // Log has day A; HC has day B — both should appear, sorted chronologically
        val logEntries = listOf(
            MetricLog(timestamp = dayAMs, type = MetricType.WEIGHT, value = 80.0)
        )
        val hcEntries = listOf(dayBMs to 79.5)
        stubDefaults(weightLogs = logEntries, hcWeight = hcEntries)

        val result = repository.getBodyCompositionData(TrendsTimeRange.ONE_MONTH)

        assertEquals(2, result.weightPoints.size)
        // Should be sorted by timestamp: day A first, then day B
        assertTrue(result.weightPoints[0].timestampMs < result.weightPoints[1].timestampMs)
        assertEquals(80.0, result.weightPoints[0].value, 0.001)
        assertEquals(79.5, result.weightPoints[1].value, 0.001)
    }

    @Test
    fun `HC exception falls back gracefully to metric_log only`() = runTest {
        val logEntries = listOf(
            MetricLog(timestamp = dayAMs, type = MetricType.WEIGHT, value = 80.0),
            MetricLog(timestamp = dayBMs, type = MetricType.WEIGHT, value = 79.5)
        )
        whenever(metricLogRepository.getByType(MetricType.WEIGHT)).thenReturn(flowOf(logEntries))
        whenever(metricLogRepository.getByType(MetricType.BODY_FAT)).thenReturn(flowOf(emptyList()))
        whenever(metricLogRepository.getLatestForType(MetricType.HEIGHT)).thenReturn(null)
        // HC returns empty (simulates catch block behaviour in HealthConnectManager on exception)
        whenever(healthConnectManager.getWeightHistory(any())).thenReturn(emptyList())
        whenever(healthConnectManager.getBodyFatHistory(any())).thenReturn(emptyList())

        val result = repository.getBodyCompositionData(TrendsTimeRange.ONE_MONTH)

        assertEquals(2, result.weightPoints.size)
        assertTrue(result.bodyFatPoints.isEmpty())
    }

    @Test
    fun `body fat merged from HC alongside weight`() = runTest {
        val hcWeight = listOf(dayAMs to 80.0, dayBMs to 79.5)
        val hcBodyFat = listOf(dayAMs to 18.0, dayBMs to 17.8)
        stubDefaults(hcWeight = hcWeight, hcBodyFat = hcBodyFat)

        val result = repository.getBodyCompositionData(TrendsTimeRange.ONE_MONTH)

        assertEquals(2, result.weightPoints.size)
        assertEquals(2, result.bodyFatPoints.size)
        assertEquals(18.0, result.bodyFatPoints[0].value, 0.001)
        assertEquals(17.8, result.bodyFatPoints[1].value, 0.001)
    }
}
