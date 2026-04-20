package com.powerme.app.data.repository

import com.powerme.app.data.database.EffectiveSetsRow
import com.powerme.app.data.database.ExerciseStressVectorDao
import com.powerme.app.data.database.HealthConnectSyncDao
import com.powerme.app.data.database.MuscleGroupVolumeRow
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
 * Tests that weekly grouping and time range filtering work correctly for
 * MuscleGroupVolume and EffectiveSets queries.
 *
 * Bug fixed: weekStartMs was previously MIN(w.timestamp) per (weekBucket, majorGroup),
 * causing different muscle groups trained on different days of the same week to each
 * get a distinct weekStartMs. This made the chart render one bar per workout instead
 * of one bar per week.
 *
 * Fix: weekStartMs is now (weekBucket * 604800000) — the epoch-aligned week start —
 * so all muscle groups within the same week always share the same weekStartMs.
 */
class TrendsRepositoryWeeklyGroupingTest {

    private lateinit var trendsDao: TrendsDao
    private lateinit var hcSyncDao: HealthConnectSyncDao
    private lateinit var metricLogRepository: MetricLogRepository
    private lateinit var stressVectorDao: ExerciseStressVectorDao
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var repository: TrendsRepository

    // A fixed epoch-aligned week start: week bucket N * 604800000
    // Using a concrete value: bucket 2818 → weekStartMs = 2818 * 604800000 = 1_703_664_000_000
    private val weekBucket = 2818L
    private val weekStartMs = weekBucket * 604_800_000L  // = 1_703_664_000_000L

    // Monday of that week (weekStartMs) and Thursday of that week (weekStartMs + 3 days)
    private val mondayMs = weekStartMs
    private val thursdayMs = weekStartMs + 3 * 86_400_000L

    @Before
    fun setUp() {
        trendsDao = mock()
        hcSyncDao = mock()
        metricLogRepository = mock()
        stressVectorDao = mock()
        healthConnectManager = mock()
        repository = TrendsRepository(trendsDao, hcSyncDao, metricLogRepository, stressVectorDao, healthConnectManager)
    }

    // ── MuscleGroupVolume: correct weekly grouping ────────────────────────────

    @Test
    fun `getWeeklyMuscleGroupVolume - same weekStartMs for all muscle groups in same week`() = runTest {
        // The fixed SQL returns the epoch-aligned weekStartMs for every row.
        // Chest trained Monday, Legs trained Thursday — both get weekStartMs = weekStartMs.
        val daoRows = listOf(
            MuscleGroupVolumeRow(weekBucket, weekStartMs, "Chest", 5000.0),
            MuscleGroupVolumeRow(weekBucket, weekStartMs, "Legs",  8000.0)
        )
        whenever(trendsDao.getWeeklyMuscleGroupVolume(any())).thenReturn(daoRows)

        val result = repository.getWeeklyMuscleGroupVolume(TrendsTimeRange.THREE_MONTHS)

        assertEquals(2, result.size)
        // Both points share the same weekStartMs → chart renders one bar per week
        assertEquals(weekStartMs, result[0].weekStartMs)
        assertEquals(weekStartMs, result[1].weekStartMs)
        assertEquals("Chest", result[0].majorGroup)
        assertEquals(5000.0, result[0].volume, 0.001)
        assertEquals("Legs", result[1].majorGroup)
        assertEquals(8000.0, result[1].volume, 0.001)
    }

    @Test
    fun `getWeeklyMuscleGroupVolume - two distinct weeks produce two distinct weekStartMs`() = runTest {
        val weekBucket2 = weekBucket + 1L
        val weekStartMs2 = weekBucket2 * 604_800_000L

        val daoRows = listOf(
            MuscleGroupVolumeRow(weekBucket,  weekStartMs,  "Chest", 5000.0),
            MuscleGroupVolumeRow(weekBucket2, weekStartMs2, "Chest", 4000.0)
        )
        whenever(trendsDao.getWeeklyMuscleGroupVolume(any())).thenReturn(daoRows)

        val result = repository.getWeeklyMuscleGroupVolume(TrendsTimeRange.THREE_MONTHS)

        assertEquals(2, result.size)
        assertEquals(weekStartMs,  result[0].weekStartMs)
        assertEquals(weekStartMs2, result[1].weekStartMs)
    }

    @Test
    fun `getWeeklyMuscleGroupVolume - empty result returns empty list`() = runTest {
        whenever(trendsDao.getWeeklyMuscleGroupVolume(any())).thenReturn(emptyList())

        val result = repository.getWeeklyMuscleGroupVolume(TrendsTimeRange.ONE_MONTH)

        assertEquals(emptyList<Any>(), result)
    }

    // ── EffectiveSets: correct weekly grouping ────────────────────────────────

    @Test
    fun `getWeeklyEffectiveSets - same weekStartMs for all muscle groups in same week`() = runTest {
        val daoRows = listOf(
            EffectiveSetsRow(weekBucket, weekStartMs, "Chest", 3),
            EffectiveSetsRow(weekBucket, weekStartMs, "Legs",  5)
        )
        whenever(trendsDao.getWeeklyEffectiveSets(any())).thenReturn(daoRows)

        val result = repository.getWeeklyEffectiveSets(TrendsTimeRange.THREE_MONTHS)

        assertEquals(2, result.size)
        assertEquals(weekStartMs, result[0].weekStartMs)
        assertEquals(weekStartMs, result[1].weekStartMs)
        assertEquals("Chest", result[0].majorGroup)
        assertEquals(3, result[0].setCount)
        assertEquals("Legs", result[1].majorGroup)
        assertEquals(5, result[1].setCount)
    }

    @Test
    fun `getWeeklyEffectiveSets - two distinct weeks produce two distinct weekStartMs`() = runTest {
        val weekBucket2 = weekBucket + 1L
        val weekStartMs2 = weekBucket2 * 604_800_000L

        val daoRows = listOf(
            EffectiveSetsRow(weekBucket,  weekStartMs,  "Back", 4),
            EffectiveSetsRow(weekBucket2, weekStartMs2, "Back", 2)
        )
        whenever(trendsDao.getWeeklyEffectiveSets(any())).thenReturn(daoRows)

        val result = repository.getWeeklyEffectiveSets(TrendsTimeRange.THREE_MONTHS)

        assertEquals(2, result.size)
        assertEquals(weekStartMs,  result[0].weekStartMs)
        assertEquals(weekStartMs2, result[1].weekStartMs)
    }

    // ── Time range filter: correct sinceMs passed to DAO ─────────────────────

    @Test
    fun `getWeeklyMuscleGroupVolume passes ONE_MONTH sinceMs to DAO`() = runTest {
        whenever(trendsDao.getWeeklyMuscleGroupVolume(any())).thenReturn(emptyList())

        val before = System.currentTimeMillis() - TrendsTimeRange.ONE_MONTH.days.toLong() * 86_400_000L
        repository.getWeeklyMuscleGroupVolume(TrendsTimeRange.ONE_MONTH)
        val after = System.currentTimeMillis() - TrendsTimeRange.ONE_MONTH.days.toLong() * 86_400_000L

        val captor = argumentCaptor<Long>()
        verify(trendsDao).getWeeklyMuscleGroupVolume(captor.capture())
        val sinceMs = captor.firstValue
        // sinceMs should be within [before, after] — current-time minus 30 days
        assert(sinceMs in before..after) {
            "sinceMs=$sinceMs was not in expected 1M window [$before, $after]"
        }
    }

    @Test
    fun `getWeeklyEffectiveSets passes ONE_YEAR sinceMs to DAO`() = runTest {
        whenever(trendsDao.getWeeklyEffectiveSets(any())).thenReturn(emptyList())

        val before = System.currentTimeMillis() - TrendsTimeRange.ONE_YEAR.days.toLong() * 86_400_000L
        repository.getWeeklyEffectiveSets(TrendsTimeRange.ONE_YEAR)
        val after = System.currentTimeMillis() - TrendsTimeRange.ONE_YEAR.days.toLong() * 86_400_000L

        val captor = argumentCaptor<Long>()
        verify(trendsDao).getWeeklyEffectiveSets(captor.capture())
        val sinceMs = captor.firstValue
        assert(sinceMs in before..after) {
            "sinceMs=$sinceMs was not in expected 1Y window [$before, $after]"
        }
    }

    @Test
    fun `ONE_MONTH sinceMs is more recent than THREE_MONTHS sinceMs`() {
        val oneMonth = TrendsTimeRange.ONE_MONTH.sinceMs()
        val threeMonths = TrendsTimeRange.THREE_MONTHS.sinceMs()
        assert(oneMonth > threeMonths) {
            "ONE_MONTH sinceMs ($oneMonth) should be greater than THREE_MONTHS sinceMs ($threeMonths)"
        }
    }

    @Test
    fun `THREE_MONTHS sinceMs is more recent than ONE_YEAR sinceMs`() {
        val threeMonths = TrendsTimeRange.THREE_MONTHS.sinceMs()
        val oneYear = TrendsTimeRange.ONE_YEAR.sinceMs()
        assert(threeMonths > oneYear) {
            "THREE_MONTHS sinceMs ($threeMonths) should be greater than ONE_YEAR sinceMs ($oneYear)"
        }
    }
}
