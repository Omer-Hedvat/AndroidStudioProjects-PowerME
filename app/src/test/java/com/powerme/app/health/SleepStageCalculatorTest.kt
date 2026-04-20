package com.powerme.app.health

import androidx.health.connect.client.records.SleepSessionRecord
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class SleepStageCalculatorTest {

    private fun makeStage(stage: Int, durationMinutes: Int): SleepSessionRecord.Stage {
        val start = Instant.ofEpochSecond(0)
        val end = start.plusSeconds(durationMinutes * 60L)
        return SleepSessionRecord.Stage(start, end, stage)
    }

    // ── extractStages ─────────────────────────────────────────────────────────

    @Test
    fun `extractStages returns all-null for empty list`() {
        val result = SleepStageCalculator.extractStages(emptyList())
        assertNull(result.deepMinutes)
        assertNull(result.remMinutes)
        assertNull(result.lightMinutes)
        assertNull(result.awakeMinutes)
    }

    @Test
    fun `extractStages sums minutes correctly for each stage type`() {
        val stages = listOf(
            makeStage(SleepSessionRecord.STAGE_TYPE_DEEP, 90),
            makeStage(SleepSessionRecord.STAGE_TYPE_DEEP, 10),   // 2 deep = 100 min
            makeStage(SleepSessionRecord.STAGE_TYPE_REM, 60),
            makeStage(SleepSessionRecord.STAGE_TYPE_LIGHT, 200),
            makeStage(SleepSessionRecord.STAGE_TYPE_AWAKE, 30)
        )
        val result = SleepStageCalculator.extractStages(stages)
        assertEquals(100, result.deepMinutes)
        assertEquals(60, result.remMinutes)
        assertEquals(200, result.lightMinutes)
        assertEquals(30, result.awakeMinutes)
    }

    @Test
    fun `extractStages treats OUT_OF_BED as awake`() {
        val stages = listOf(
            makeStage(SleepSessionRecord.STAGE_TYPE_OUT_OF_BED, 15),
            makeStage(SleepSessionRecord.STAGE_TYPE_AWAKE, 10)
        )
        val result = SleepStageCalculator.extractStages(stages)
        assertEquals(25, result.awakeMinutes)
    }

    // ── computeEfficiency ─────────────────────────────────────────────────────

    @Test
    fun `computeEfficiency returns 1 when no awake time`() {
        assertEquals(1.0f, SleepStageCalculator.computeEfficiency(480, 0), 0.001f)
    }

    @Test
    fun `computeEfficiency returns correct ratio`() {
        // 480 total, 48 awake → 90% efficiency
        assertEquals(0.90f, SleepStageCalculator.computeEfficiency(480, 48), 0.001f)
    }

    @Test
    fun `computeEfficiency clamps to 0 when awake exceeds total`() {
        assertEquals(0.0f, SleepStageCalculator.computeEfficiency(100, 200), 0.001f)
    }

    @Test
    fun `computeEfficiency returns 0 for zero total`() {
        assertEquals(0.0f, SleepStageCalculator.computeEfficiency(0, 0), 0.001f)
    }

    // ── computeSleepScore ─────────────────────────────────────────────────────

    @Test
    fun `computeSleepScore returns null when all stage fields are null`() {
        val breakdown = SleepStageCalculator.SleepStageBreakdown(null, null, null, null)
        assertNull(SleepStageCalculator.computeSleepScore(480, breakdown))
    }

    @Test
    fun `computeSleepScore in range 0-100`() {
        // Typical 8h sleep: 20% deep=96min, 22% REM=106min, 50% light=240min, 8% awake=38min
        val breakdown = SleepStageCalculator.SleepStageBreakdown(
            deepMinutes = 96, remMinutes = 106, lightMinutes = 240, awakeMinutes = 38
        )
        val score = SleepStageCalculator.computeSleepScore(480, breakdown)
        assertNotNull(score)
        assertTrue("Score should be 0-100, got $score", score!! in 0..100)
    }

    @Test
    fun `computeSleepScore near 100 for ideal sleep`() {
        // 8h duration (in 7-9h range), 20% deep=96, 22% REM=106, 5% awake=24 → very high score
        val breakdown = SleepStageCalculator.SleepStageBreakdown(
            deepMinutes = 96, remMinutes = 106, lightMinutes = 254, awakeMinutes = 24
        )
        val score = SleepStageCalculator.computeSleepScore(480, breakdown)
        assertNotNull(score)
        assertTrue("Ideal sleep should score >= 80, got $score", score!! >= 80)
    }

    @Test
    fun `computeSleepScore near 0 for terrible sleep`() {
        // <6h, 0% deep, 0% REM, 50% awake
        val breakdown = SleepStageCalculator.SleepStageBreakdown(
            deepMinutes = 0, remMinutes = 0, lightMinutes = 180, awakeMinutes = 180
        )
        val score = SleepStageCalculator.computeSleepScore(360, breakdown)
        assertNotNull(score)
        assertTrue("Terrible sleep should score <= 20, got $score", score!! <= 20)
    }

    @Test
    fun `computeSleepScore clamped at 100 maximum`() {
        // Unrealistically perfect params — score must not exceed 100
        val breakdown = SleepStageCalculator.SleepStageBreakdown(
            deepMinutes = 200, remMinutes = 200, lightMinutes = 100, awakeMinutes = 0
        )
        val score = SleepStageCalculator.computeSleepScore(500, breakdown)
        assertNotNull(score)
        assertTrue("Score must not exceed 100, got $score", score!! <= 100)
    }
}
