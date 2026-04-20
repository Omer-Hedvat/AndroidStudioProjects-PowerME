package com.powerme.app.health

import androidx.health.connect.client.records.HeartRateRecord
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class HrZoneCalculatorTest {

    private fun makeSample(bpm: Long): HeartRateRecord.Sample =
        HeartRateRecord.Sample(Instant.ofEpochSecond(0), bpm)

    // ── maxHr ─────────────────────────────────────────────────────────────────

    @Test
    fun `maxHr returns 220 minus age`() {
        assertEquals(190, HrZoneCalculator.maxHr(30))
        assertEquals(180, HrZoneCalculator.maxHr(40))
        assertEquals(170, HrZoneCalculator.maxHr(50))
    }

    // ── zoneForBpm ────────────────────────────────────────────────────────────

    @Test
    fun `zoneForBpm - below 50 pct is Zone 1`() {
        // maxHr=200, 50% = 100bpm → 99 is Z1
        assertEquals(1, HrZoneCalculator.zoneForBpm(99, 200))
    }

    @Test
    fun `zoneForBpm - 50 pct threshold is Zone 2`() {
        // maxHr=200, 50% = 100bpm → 100 is Z2
        assertEquals(2, HrZoneCalculator.zoneForBpm(100, 200))
    }

    @Test
    fun `zoneForBpm - 60 pct threshold is Zone 3`() {
        // maxHr=200, 60% = 120bpm → 120 is Z3
        assertEquals(3, HrZoneCalculator.zoneForBpm(120, 200))
    }

    @Test
    fun `zoneForBpm - 70 pct threshold is Zone 4`() {
        // maxHr=200, 70% = 140bpm → 140 is Z4
        assertEquals(4, HrZoneCalculator.zoneForBpm(140, 200))
    }

    @Test
    fun `zoneForBpm - 85 pct threshold is Zone 5`() {
        // maxHr=200, 85% = 170bpm → 170 is Z5
        assertEquals(5, HrZoneCalculator.zoneForBpm(170, 200))
    }

    @Test
    fun `zoneForBpm - 169 is Zone 4 not 5`() {
        assertEquals(4, HrZoneCalculator.zoneForBpm(169, 200))
    }

    // ── aggregateZones ────────────────────────────────────────────────────────

    @Test
    fun `aggregateZones returns all zeros for empty samples`() {
        val result = HrZoneCalculator.aggregateZones(emptyList(), 190)
        assertEquals(0f, result.z1Pct, 0.001f)
        assertEquals(0f, result.z2Pct, 0.001f)
        assertEquals(0f, result.z3Pct, 0.001f)
        assertEquals(0f, result.z4Pct, 0.001f)
        assertEquals(0f, result.z5Pct, 0.001f)
    }

    @Test
    fun `aggregateZones - all samples in Z1`() {
        val maxHr = 200
        val samples = listOf(makeSample(50), makeSample(60), makeSample(70), makeSample(80))
        val result = HrZoneCalculator.aggregateZones(samples, maxHr)
        assertEquals(1.0f, result.z1Pct, 0.001f)
        assertEquals(0f, result.z2Pct, 0.001f)
        assertEquals(0f, result.z3Pct, 0.001f)
    }

    @Test
    fun `aggregateZones - all samples in Z5`() {
        val maxHr = 200
        val samples = listOf(makeSample(172), makeSample(180), makeSample(190))
        val result = HrZoneCalculator.aggregateZones(samples, maxHr)
        assertEquals(1.0f, result.z5Pct, 0.001f)
        assertEquals(0f, result.z1Pct, 0.001f)
    }

    @Test
    fun `aggregateZones - zone percentages sum to 1`() {
        val maxHr = 190
        val samples = listOf(
            makeSample(80),   // Z1
            makeSample(100),  // Z2
            makeSample(115),  // Z3
            makeSample(140),  // Z4
            makeSample(165)   // Z5
        )
        val result = HrZoneCalculator.aggregateZones(samples, maxHr)
        val sum = result.z1Pct + result.z2Pct + result.z3Pct + result.z4Pct + result.z5Pct
        assertEquals(1.0f, sum, 0.001f)
    }

    @Test
    fun `aggregateZones distributes 4 samples across 2 zones correctly`() {
        val maxHr = 200  // 50%=100, 85%=170
        // 2 samples Z1 (< 100), 2 samples Z5 (>= 170)
        val samples = listOf(makeSample(80), makeSample(90), makeSample(175), makeSample(180))
        val result = HrZoneCalculator.aggregateZones(samples, maxHr)
        assertEquals(0.5f, result.z1Pct, 0.001f)
        assertEquals(0.5f, result.z5Pct, 0.001f)
        assertEquals(0f, result.z2Pct, 0.001f)
    }
}
