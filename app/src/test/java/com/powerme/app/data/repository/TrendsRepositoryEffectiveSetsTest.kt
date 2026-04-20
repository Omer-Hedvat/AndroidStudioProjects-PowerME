package com.powerme.app.data.repository

import com.powerme.app.data.database.EffectiveSetsRow
import com.powerme.app.data.database.ExerciseStressVectorDao
import com.powerme.app.data.database.HealthConnectSyncDao
import com.powerme.app.data.database.TrendsDao
import com.powerme.app.health.HealthConnectManager
import com.powerme.app.ui.metrics.TrendsTimeRange
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Verifies that getWeeklyEffectiveSets and getEffectiveSetsCoverage correctly pass
 * the time-range sinceMs to the DAO, and that data mapping is correct.
 *
 * These tests complement TrendsRepositoryWeeklyGroupingTest (which already covers the
 * grouping fix and ONE_YEAR sinceMs) by adding the remaining per-range sinceMs tests
 * and full coverage of the coverage percentage path.
 */
class TrendsRepositoryEffectiveSetsTest {

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

    // ── getWeeklyEffectiveSets: sinceMs per time range ────────────────────────

    @Test
    fun `getWeeklyEffectiveSets passes ONE_MONTH sinceMs to DAO`() = runTest {
        whenever(trendsDao.getWeeklyEffectiveSets(any())).thenReturn(emptyList())

        val before = System.currentTimeMillis() - TrendsTimeRange.ONE_MONTH.days.toLong() * 86_400_000L
        repository.getWeeklyEffectiveSets(TrendsTimeRange.ONE_MONTH)
        val after = System.currentTimeMillis() - TrendsTimeRange.ONE_MONTH.days.toLong() * 86_400_000L

        val captor = argumentCaptor<Long>()
        verify(trendsDao).getWeeklyEffectiveSets(captor.capture())
        val sinceMs = captor.firstValue
        assert(sinceMs in before..after) {
            "sinceMs=$sinceMs was not in expected 1M window [$before, $after]"
        }
    }

    @Test
    fun `getWeeklyEffectiveSets passes THREE_MONTHS sinceMs to DAO`() = runTest {
        whenever(trendsDao.getWeeklyEffectiveSets(any())).thenReturn(emptyList())

        val before = System.currentTimeMillis() - TrendsTimeRange.THREE_MONTHS.days.toLong() * 86_400_000L
        repository.getWeeklyEffectiveSets(TrendsTimeRange.THREE_MONTHS)
        val after = System.currentTimeMillis() - TrendsTimeRange.THREE_MONTHS.days.toLong() * 86_400_000L

        val captor = argumentCaptor<Long>()
        verify(trendsDao).getWeeklyEffectiveSets(captor.capture())
        val sinceMs = captor.firstValue
        assert(sinceMs in before..after) {
            "sinceMs=$sinceMs was not in expected 3M window [$before, $after]"
        }
    }

    @Test
    fun `getWeeklyEffectiveSets passes SIX_MONTHS sinceMs to DAO`() = runTest {
        whenever(trendsDao.getWeeklyEffectiveSets(any())).thenReturn(emptyList())

        val before = System.currentTimeMillis() - TrendsTimeRange.SIX_MONTHS.days.toLong() * 86_400_000L
        repository.getWeeklyEffectiveSets(TrendsTimeRange.SIX_MONTHS)
        val after = System.currentTimeMillis() - TrendsTimeRange.SIX_MONTHS.days.toLong() * 86_400_000L

        val captor = argumentCaptor<Long>()
        verify(trendsDao).getWeeklyEffectiveSets(captor.capture())
        val sinceMs = captor.firstValue
        assert(sinceMs in before..after) {
            "sinceMs=$sinceMs was not in expected 6M window [$before, $after]"
        }
    }

    @Test
    fun `getWeeklyEffectiveSets sinceMs ordering - 1M greater than 3M greater than 6M greater than 1Y`() {
        val oneMonth = TrendsTimeRange.ONE_MONTH.sinceMs()
        val threeMonths = TrendsTimeRange.THREE_MONTHS.sinceMs()
        val sixMonths = TrendsTimeRange.SIX_MONTHS.sinceMs()
        val oneYear = TrendsTimeRange.ONE_YEAR.sinceMs()
        assert(oneMonth > threeMonths) { "1M sinceMs ($oneMonth) must be more recent than 3M ($threeMonths)" }
        assert(threeMonths > sixMonths) { "3M sinceMs ($threeMonths) must be more recent than 6M ($sixMonths)" }
        assert(sixMonths > oneYear) { "6M sinceMs ($sixMonths) must be more recent than 1Y ($oneYear)" }
    }

    @Test
    fun `getWeeklyEffectiveSets maps EffectiveSetsRow to EffectiveSetsChartPoint preserving all fields`() = runTest {
        val weekStartMs = 2818L * 604_800_000L
        val daoRows = listOf(
            EffectiveSetsRow(weekBucket = 2818L, weekStartMs = weekStartMs, majorGroup = "Chest", effectiveSets = 5),
            EffectiveSetsRow(weekBucket = 2818L, weekStartMs = weekStartMs, majorGroup = "Legs", effectiveSets = 8)
        )
        whenever(trendsDao.getWeeklyEffectiveSets(any())).thenReturn(daoRows)

        val result = repository.getWeeklyEffectiveSets(TrendsTimeRange.ONE_MONTH)

        assertEquals(2, result.size)
        assertEquals(weekStartMs, result[0].weekStartMs)
        assertEquals("Chest", result[0].majorGroup)
        assertEquals(5, result[0].setCount)
        assertEquals(weekStartMs, result[1].weekStartMs)
        assertEquals("Legs", result[1].majorGroup)
        assertEquals(8, result[1].setCount)
    }

    @Test
    fun `getWeeklyEffectiveSets returns empty list when DAO returns empty`() = runTest {
        whenever(trendsDao.getWeeklyEffectiveSets(any())).thenReturn(emptyList())

        val result = repository.getWeeklyEffectiveSets(TrendsTimeRange.THREE_MONTHS)

        assertEquals(0, result.size)
    }

    // ── getEffectiveSetsCoverage: sinceMs and computation ────────────────────

    @Test
    fun `getEffectiveSetsCoverage passes ONE_MONTH sinceMs to both count DAOs`() = runTest {
        whenever(trendsDao.getTotalSetsCount(any())).thenReturn(0)
        whenever(trendsDao.getRpeCoveredSetsCount(any())).thenReturn(0)

        val before = System.currentTimeMillis() - TrendsTimeRange.ONE_MONTH.days.toLong() * 86_400_000L
        repository.getEffectiveSetsCoverage(TrendsTimeRange.ONE_MONTH)
        val after = System.currentTimeMillis() - TrendsTimeRange.ONE_MONTH.days.toLong() * 86_400_000L

        val totalCaptor = argumentCaptor<Long>()
        val coveredCaptor = argumentCaptor<Long>()
        verify(trendsDao).getTotalSetsCount(totalCaptor.capture())
        verify(trendsDao).getRpeCoveredSetsCount(coveredCaptor.capture())

        assert(totalCaptor.firstValue in before..after) {
            "getTotalSetsCount sinceMs=${totalCaptor.firstValue} not in 1M window"
        }
        assert(coveredCaptor.firstValue in before..after) {
            "getRpeCoveredSetsCount sinceMs=${coveredCaptor.firstValue} not in 1M window"
        }
    }

    @Test
    fun `getEffectiveSetsCoverage passes ONE_YEAR sinceMs to both count DAOs`() = runTest {
        whenever(trendsDao.getTotalSetsCount(any())).thenReturn(0)
        whenever(trendsDao.getRpeCoveredSetsCount(any())).thenReturn(0)

        val before = System.currentTimeMillis() - TrendsTimeRange.ONE_YEAR.days.toLong() * 86_400_000L
        repository.getEffectiveSetsCoverage(TrendsTimeRange.ONE_YEAR)
        val after = System.currentTimeMillis() - TrendsTimeRange.ONE_YEAR.days.toLong() * 86_400_000L

        val totalCaptor = argumentCaptor<Long>()
        val coveredCaptor = argumentCaptor<Long>()
        verify(trendsDao).getTotalSetsCount(totalCaptor.capture())
        verify(trendsDao).getRpeCoveredSetsCount(coveredCaptor.capture())

        assert(totalCaptor.firstValue in before..after) {
            "getTotalSetsCount sinceMs=${totalCaptor.firstValue} not in 1Y window"
        }
        assert(coveredCaptor.firstValue in before..after) {
            "getRpeCoveredSetsCount sinceMs=${coveredCaptor.firstValue} not in 1Y window"
        }
    }

    @Test
    fun `getEffectiveSetsCoverage returns 0f when total is 0`() = runTest {
        whenever(trendsDao.getTotalSetsCount(any())).thenReturn(0)
        whenever(trendsDao.getRpeCoveredSetsCount(any())).thenReturn(0)

        val result = repository.getEffectiveSetsCoverage(TrendsTimeRange.THREE_MONTHS)

        assertEquals(0f, result, 0.001f)
    }

    @Test
    fun `getEffectiveSetsCoverage computes percentage correctly`() = runTest {
        whenever(trendsDao.getTotalSetsCount(any())).thenReturn(200)
        whenever(trendsDao.getRpeCoveredSetsCount(any())).thenReturn(50)

        val result = repository.getEffectiveSetsCoverage(TrendsTimeRange.THREE_MONTHS)

        assertEquals(25f, result, 0.001f)
    }
}
