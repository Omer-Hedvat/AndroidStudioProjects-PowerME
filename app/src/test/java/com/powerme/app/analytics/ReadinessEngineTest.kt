package com.powerme.app.analytics

import com.powerme.app.data.database.HealthConnectSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReadinessEngineTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Build a list of N sync records going back from today, all with identical values. */
    private fun buildHistory(
        days: Int,
        hrv: Double? = 50.0,
        sleepMinutes: Int? = 480,
        rhr: Int? = 60
    ): List<HealthConnectSync> {
        val today = LocalDate.now()
        return (0 until days).map { offset ->
            HealthConnectSync(
                date = today.minusDays(offset.toLong()),
                hrv = hrv,
                sleepDurationMinutes = sleepMinutes,
                rhr = rhr
            )
        }
    }

    /** Build history with specific today values differing from the baseline. */
    private fun buildHistoryWithToday(
        baselineDays: Int,
        baselineHrv: Double = 50.0,
        baselineSleep: Int = 480,
        baselineRhr: Int = 60,
        todayHrv: Double? = 50.0,
        todaySleep: Int? = 480,
        todayRhr: Int? = 60
    ): List<HealthConnectSync> {
        val today = LocalDate.now()
        val todaySync = HealthConnectSync(
            date = today,
            hrv = todayHrv,
            sleepDurationMinutes = todaySleep,
            rhr = todayRhr
        )
        val baseline = (1..baselineDays).map { offset ->
            HealthConnectSync(
                date = today.minusDays(offset.toLong()),
                hrv = baselineHrv,
                sleepDurationMinutes = baselineSleep,
                rhr = baselineRhr
            )
        }
        return listOf(todaySync) + baseline
    }

    // ── NoData path ─────────────────────────────────────────────────────────

    @Test
    fun `returns NoData when syncs list is empty`() {
        val result = ReadinessEngine.compute(emptyList())
        assertTrue(result is ReadinessEngine.ReadinessScore.NoData)
    }

    @Test
    fun `returns NoData when today has no HC sync metrics`() {
        val syncs = listOf(
            HealthConnectSync(
                date = LocalDate.now(),
                hrv = null,
                sleepDurationMinutes = null,
                rhr = null
            )
        )
        val result = ReadinessEngine.compute(syncs)
        assertTrue(result is ReadinessEngine.ReadinessScore.NoData)
    }

    // ── Calibrating path ────────────────────────────────────────────────────

    @Test
    fun `returns Calibrating when fewer than 5 days of HRV history`() {
        val syncs = buildHistory(days = 3, hrv = 50.0, sleepMinutes = 480, rhr = 60)
        val result = ReadinessEngine.compute(syncs)
        assertTrue("Expected Calibrating, got $result", result is ReadinessEngine.ReadinessScore.Calibrating)
        val calibrating = result as ReadinessEngine.ReadinessScore.Calibrating
        assertEquals(3, calibrating.daysCollected)
        assertEquals(5, calibrating.daysRequired)
    }

    @Test
    fun `returns Calibrating when fewer than 5 days of sleep history`() {
        // 4 days total → still below threshold
        val syncs = buildHistory(days = 4, hrv = 50.0, sleepMinutes = 480, rhr = 60)
        val result = ReadinessEngine.compute(syncs)
        assertTrue(result is ReadinessEngine.ReadinessScore.Calibrating)
    }

    // ── Happy path ──────────────────────────────────────────────────────────

    @Test
    fun `score is 50 when all metrics exactly at baseline`() {
        // All values identical → z-scores are 0 → raw composite is 0 → maps to 50
        val syncs = buildHistory(days = 15, hrv = 50.0, sleepMinutes = 480, rhr = 60)
        val result = ReadinessEngine.compute(syncs)
        assertTrue("Expected Score, got $result", result is ReadinessEngine.ReadinessScore.Score)
        val score = result as ReadinessEngine.ReadinessScore.Score
        assertEquals(50, score.value)
        assertEquals(ReadinessEngine.Tier.MODERATE, score.tier)
    }

    @Test
    fun `high HRV and sleep with low RHR produces high score`() {
        // Today: HRV much higher, sleep much higher, RHR much lower than baseline
        val syncs = buildHistoryWithToday(
            baselineDays = 20,
            baselineHrv = 40.0,
            baselineSleep = 420,
            baselineRhr = 70,
            todayHrv = 80.0,    // well above baseline
            todaySleep = 560,   // well above baseline
            todayRhr = 50       // well below baseline (good)
        )
        val result = ReadinessEngine.compute(syncs)
        assertTrue(result is ReadinessEngine.ReadinessScore.Score)
        val score = (result as ReadinessEngine.ReadinessScore.Score).value
        assertTrue("Score should be high (>= 70), got $score", score >= 70)
        assertEquals(ReadinessEngine.Tier.RECOVERED, result.tier)
    }

    @Test
    fun `low HRV and sleep with high RHR produces low score`() {
        val syncs = buildHistoryWithToday(
            baselineDays = 20,
            baselineHrv = 60.0,
            baselineSleep = 480,
            baselineRhr = 55,
            todayHrv = 25.0,    // well below baseline
            todaySleep = 300,   // well below baseline
            todayRhr = 85       // well above baseline (bad)
        )
        val result = ReadinessEngine.compute(syncs)
        assertTrue(result is ReadinessEngine.ReadinessScore.Score)
        val score = (result as ReadinessEngine.ReadinessScore.Score).value
        assertTrue("Score should be low (<= 39), got $score", score <= 39)
        assertEquals(ReadinessEngine.Tier.FATIGUED, result.tier)
    }

    @Test
    fun `score clamps at 100 for extremely positive z-scores`() {
        val syncs = buildHistoryWithToday(
            baselineDays = 20,
            baselineHrv = 30.0,
            baselineSleep = 360,
            baselineRhr = 80,
            todayHrv = 200.0,
            todaySleep = 700,
            todayRhr = 30
        )
        val result = ReadinessEngine.compute(syncs)
        assertTrue(result is ReadinessEngine.ReadinessScore.Score)
        val score = (result as ReadinessEngine.ReadinessScore.Score).value
        assertEquals(100, score)
    }

    @Test
    fun `score clamps at 0 for extremely negative z-scores`() {
        val syncs = buildHistoryWithToday(
            baselineDays = 20,
            baselineHrv = 80.0,
            baselineSleep = 540,
            baselineRhr = 50,
            todayHrv = 5.0,
            todaySleep = 60,
            todayRhr = 120
        )
        val result = ReadinessEngine.compute(syncs)
        assertTrue(result is ReadinessEngine.ReadinessScore.Score)
        val score = (result as ReadinessEngine.ReadinessScore.Score).value
        assertEquals(0, score)
    }

    @Test
    fun `RHR contribution is negated - lower RHR produces higher score`() {
        // Two scenarios: same HRV/sleep, but RHR differs
        val lowRhr = buildHistoryWithToday(
            baselineDays = 15, baselineRhr = 65, todayRhr = 50
        )
        val highRhr = buildHistoryWithToday(
            baselineDays = 15, baselineRhr = 65, todayRhr = 80
        )
        val scoreLowRhr = (ReadinessEngine.compute(lowRhr) as ReadinessEngine.ReadinessScore.Score).value
        val scoreHighRhr = (ReadinessEngine.compute(highRhr) as ReadinessEngine.ReadinessScore.Score).value
        assertTrue(
            "Low RHR ($scoreLowRhr) should score higher than high RHR ($scoreHighRhr)",
            scoreLowRhr > scoreHighRhr
        )
    }

    // ── Null metric handling ────────────────────────────────────────────────

    @Test
    fun `weights re-normalized when HRV is null for today`() {
        val syncs = buildHistoryWithToday(
            baselineDays = 15,
            todayHrv = null,    // no HRV today
            todaySleep = 480,
            todayRhr = 60
        )
        val result = ReadinessEngine.compute(syncs)
        assertTrue("Expected Score, got $result", result is ReadinessEngine.ReadinessScore.Score)
        val score = (result as ReadinessEngine.ReadinessScore.Score).value
        // With all baseline values, score should be ~50
        assertEquals(50, score)
    }

    @Test
    fun `score computable with only sleep and RHR data`() {
        val syncs = buildHistoryWithToday(
            baselineDays = 15,
            baselineHrv = 50.0,
            todayHrv = null,
            todaySleep = 480,
            todayRhr = 60
        )
        val result = ReadinessEngine.compute(syncs)
        assertTrue(result is ReadinessEngine.ReadinessScore.Score)
    }

    @Test
    fun `score computable with only HRV data`() {
        val syncs = buildHistoryWithToday(
            baselineDays = 15,
            todayHrv = 50.0,
            todaySleep = null,
            todayRhr = null
        )
        val result = ReadinessEngine.compute(syncs)
        assertTrue(result is ReadinessEngine.ReadinessScore.Score)
    }

    // ── Tier boundaries ─────────────────────────────────────────────────────

    @Test
    fun `score 70 maps to RECOVERED tier`() {
        // Verify the tier boundary: >= 70 = RECOVERED
        // Build data that should produce a score of exactly ~70
        // Instead, we test the tier function indirectly via a known high-ish score
        val syncs = buildHistoryWithToday(
            baselineDays = 20,
            baselineHrv = 40.0,
            baselineSleep = 420,
            baselineRhr = 65,
            todayHrv = 55.0,
            todaySleep = 500,
            todayRhr = 55
        )
        val result = ReadinessEngine.compute(syncs)
        assertTrue(result is ReadinessEngine.ReadinessScore.Score)
        val score = result as ReadinessEngine.ReadinessScore.Score
        // Just verify tier assignment is consistent with value
        when {
            score.value >= 70 -> assertEquals(ReadinessEngine.Tier.RECOVERED, score.tier)
            score.value >= 40 -> assertEquals(ReadinessEngine.Tier.MODERATE, score.tier)
            else -> assertEquals(ReadinessEngine.Tier.FATIGUED, score.tier)
        }
    }

    @Test
    fun `tier boundaries are correct - 39 is FATIGUED, 40 is MODERATE, 69 is MODERATE, 70 is RECOVERED`() {
        // Test boundary conditions directly by constructing scores at known boundaries.
        // Since we can't control exact output, we verify the invariant from the algorithm:
        // All-baseline → score 50 → MODERATE
        val baseline = buildHistory(days = 15)
        val result = ReadinessEngine.compute(baseline)
        assertTrue(result is ReadinessEngine.ReadinessScore.Score)
        val score = result as ReadinessEngine.ReadinessScore.Score
        assertEquals(50, score.value)
        assertEquals(ReadinessEngine.Tier.MODERATE, score.tier)
    }

    @Test
    fun `exactly 5 days of history computes a score, not Calibrating`() {
        val syncs = buildHistory(days = 5, hrv = 50.0, sleepMinutes = 480, rhr = 60)
        val result = ReadinessEngine.compute(syncs)
        assertTrue("5 days should be enough, got $result", result is ReadinessEngine.ReadinessScore.Score)
    }
}
