package com.powerme.app.util

import com.powerme.app.data.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WarmupCalculatorTest {

    // ── equipmentToWarmupParams ───────────────────────────────────────────────

    @Test
    fun `barbell metric - correct params`() {
        val p = WarmupCalculator.equipmentToWarmupParams("Barbell", UnitSystem.METRIC)!!
        assertEquals(20.0, p.startWeight, 0.01)
        assertEquals(20.0, p.maxJump, 0.01)
        assertEquals(2.5, p.rounding, 0.01)
    }

    @Test
    fun `barbell imperial - 45lb bar`() {
        val p = WarmupCalculator.equipmentToWarmupParams("Barbell", UnitSystem.IMPERIAL)!!
        assertEquals(45.0, p.startWeight, 0.01)
    }

    @Test
    fun `dumbbell metric - correct params`() {
        val p = WarmupCalculator.equipmentToWarmupParams("Dumbbell", UnitSystem.METRIC)!!
        assertEquals(4.0, p.startWeight, 0.01)
        assertEquals(5.0, p.maxJump, 0.01)
        assertEquals(1.0, p.rounding, 0.01)
    }

    @Test
    fun `cable metric - correct params`() {
        val p = WarmupCalculator.equipmentToWarmupParams("Cable", UnitSystem.METRIC)!!
        assertEquals(5.0, p.startWeight, 0.01)
        assertEquals(10.0, p.maxJump, 0.01)
    }

    @Test
    fun `machine metric - correct params`() {
        val p = WarmupCalculator.equipmentToWarmupParams("Machine", UnitSystem.METRIC)!!
        assertEquals(10.0, p.startWeight, 0.01)
        assertEquals(15.0, p.maxJump, 0.01)
    }

    @Test
    fun `ez bar metric - correct params`() {
        assertNotNull(WarmupCalculator.equipmentToWarmupParams("EZ Bar", UnitSystem.METRIC))
    }

    @Test
    fun `smith machine metric - correct params`() {
        assertNotNull(WarmupCalculator.equipmentToWarmupParams("Smith Machine", UnitSystem.METRIC))
    }

    @Test
    fun `landmine metric - correct params`() {
        assertNotNull(WarmupCalculator.equipmentToWarmupParams("Landmine", UnitSystem.METRIC))
    }

    @Test
    fun `kettlebell metric - correct params`() {
        assertNotNull(WarmupCalculator.equipmentToWarmupParams("Kettlebell", UnitSystem.METRIC))
    }

    @Test
    fun `bench metric - correct params`() {
        assertNotNull(WarmupCalculator.equipmentToWarmupParams("Bench", UnitSystem.METRIC))
    }

    @Test
    fun `bodyweight - returns null`() {
        assertNull(WarmupCalculator.equipmentToWarmupParams("Bodyweight", UnitSystem.METRIC))
    }

    @Test
    fun `pull-up bar - skip, returns null`() {
        assertNull(WarmupCalculator.equipmentToWarmupParams("Pull-up Bar", UnitSystem.METRIC))
    }

    @Test
    fun `rings - skip, returns null`() {
        assertNull(WarmupCalculator.equipmentToWarmupParams("Rings", UnitSystem.METRIC))
    }

    @Test
    fun `resistance band - skip, returns null`() {
        assertNull(WarmupCalculator.equipmentToWarmupParams("Resistance Band", UnitSystem.METRIC))
    }

    @Test
    fun `jump rope - skip, returns null`() {
        assertNull(WarmupCalculator.equipmentToWarmupParams("Jump Rope", UnitSystem.METRIC))
    }

    @Test
    fun `battle ropes - skip, returns null`() {
        assertNull(WarmupCalculator.equipmentToWarmupParams("Battle Ropes", UnitSystem.METRIC))
    }

    @Test
    fun `sled - skip, returns null`() {
        assertNull(WarmupCalculator.equipmentToWarmupParams("Sled", UnitSystem.METRIC))
    }

    @Test
    fun `ab wheel - skip, returns null`() {
        assertNull(WarmupCalculator.equipmentToWarmupParams("Ab Wheel", UnitSystem.METRIC))
    }

    @Test
    fun `medicine ball - skip, returns null`() {
        assertNull(WarmupCalculator.equipmentToWarmupParams("Medicine Ball", UnitSystem.METRIC))
    }

    @Test
    fun `unknown equipment type - returns null`() {
        assertNull(WarmupCalculator.equipmentToWarmupParams("Trampoline", UnitSystem.METRIC))
    }

    // ── computeWarmupSets — set count ─────────────────────────────────────────

    @Test
    fun `barbell 100kg - 4 warmup sets`() {
        val params = WarmupCalculator.equipmentToWarmupParams("Barbell", UnitSystem.METRIC)!!
        val result = WarmupCalculator.computeWarmupSets(100.0, params)
        // gap = 80, maxJump = 20, ceil(80/20) = 4
        assertEquals(4, result.size)
    }

    @Test
    fun `barbell 40kg - 1 warmup set (gap equals maxJump)`() {
        val params = WarmupCalculator.equipmentToWarmupParams("Barbell", UnitSystem.METRIC)!!
        val result = WarmupCalculator.computeWarmupSets(40.0, params)
        // gap = 20, maxJump = 20, so exactly 1 set
        assertEquals(1, result.size)
    }

    @Test
    fun `barbell 20kg (bar only) - no warmup sets`() {
        val params = WarmupCalculator.equipmentToWarmupParams("Barbell", UnitSystem.METRIC)!!
        val result = WarmupCalculator.computeWarmupSets(20.0, params)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `barbell 15kg - working weight below start, returns empty`() {
        val params = WarmupCalculator.equipmentToWarmupParams("Barbell", UnitSystem.METRIC)!!
        val result = WarmupCalculator.computeWarmupSets(15.0, params)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `barbell 23kg - working weight too close to bar, returns empty`() {
        val params = WarmupCalculator.equipmentToWarmupParams("Barbell", UnitSystem.METRIC)!!
        val result = WarmupCalculator.computeWarmupSets(23.0, params)
        // Only warmup set would be 22.5kg = 98% of working weight — filtered by 90% threshold
        assertTrue(result.isEmpty())
    }

    @Test
    fun `dumbbell 10kg - 2 warmup sets`() {
        val params = WarmupCalculator.equipmentToWarmupParams("Dumbbell", UnitSystem.METRIC)!!
        val result = WarmupCalculator.computeWarmupSets(10.0, params)
        // gap = 6, maxJump = 5, ceil(6/5) = 2
        assertEquals(2, result.size)
    }

    @Test
    fun `dumbbell 8kg - 1 warmup set (gap equals maxJump)`() {
        val params = WarmupCalculator.equipmentToWarmupParams("Dumbbell", UnitSystem.METRIC)!!
        val result = WarmupCalculator.computeWarmupSets(8.0, params)
        // gap = 4, maxJump = 5, gap <= maxJump → 1
        assertEquals(1, result.size)
    }

    @Test
    fun `cable 60kg - 5 warmup sets (capped at 5)`() {
        val params = WarmupCalculator.equipmentToWarmupParams("Cable", UnitSystem.METRIC)!!
        val result = WarmupCalculator.computeWarmupSets(60.0, params)
        // gap = 55, maxJump = 10, ceil(55/10) = 6, capped at 5
        assertEquals(5, result.size)
    }

    @Test
    fun `barbell 200kg - capped at 5 sets`() {
        val params = WarmupCalculator.equipmentToWarmupParams("Barbell", UnitSystem.METRIC)!!
        val result = WarmupCalculator.computeWarmupSets(200.0, params)
        assertEquals(5, result.size)
    }

    @Test
    fun `bodyweight loaded params - 20kg extra - 2 sets`() {
        val params = WarmupCalculator.bodyweightLoadedParams(UnitSystem.METRIC)
        val result = WarmupCalculator.computeWarmupSets(20.0, params)
        // gap = 20, maxJump = 10, ceil(20/10) = 2
        assertEquals(2, result.size)
    }

    // ── computeWarmupSets — weights ───────────────────────────────────────────

    @Test
    fun `barbell 100kg - weights are rounded to nearest 2_5`() {
        val params = WarmupCalculator.equipmentToWarmupParams("Barbell", UnitSystem.METRIC)!!
        val result = WarmupCalculator.computeWarmupSets(100.0, params)
        result.forEach { set ->
            assertTrue("weight ${set.weight} not multiple of 2.5", set.weight % 2.5 < 0.01)
        }
    }

    @Test
    fun `dumbbell 10kg - weights are rounded to nearest 1kg`() {
        val params = WarmupCalculator.equipmentToWarmupParams("Dumbbell", UnitSystem.METRIC)!!
        val result = WarmupCalculator.computeWarmupSets(10.0, params)
        result.forEach { set ->
            val remainder = set.weight % 1.0
            assertTrue("weight ${set.weight} not whole number", remainder < 0.01)
        }
    }

    @Test
    fun `barbell 100kg - weights ascend from start to near working weight`() {
        val params = WarmupCalculator.equipmentToWarmupParams("Barbell", UnitSystem.METRIC)!!
        val result = WarmupCalculator.computeWarmupSets(100.0, params)
        // Weights should be ascending
        for (i in 1 until result.size) {
            assertTrue(result[i].weight > result[i - 1].weight)
        }
        // No warmup weight should equal or exceed working weight
        result.forEach { assertTrue(it.weight < 100.0) }
    }

    // ── computeWarmupSets — reps ──────────────────────────────────────────────

    @Test
    fun `reps descend as weight increases`() {
        val params = WarmupCalculator.equipmentToWarmupParams("Barbell", UnitSystem.METRIC)!!
        val result = WarmupCalculator.computeWarmupSets(100.0, params)
        assertEquals(4, result.size)
        assertEquals(10, result[0].reps)
        assertEquals(8, result[1].reps)
        assertEquals(5, result[2].reps)
        assertEquals(3, result[3].reps)
    }

    @Test
    fun `single warmup set gets 10 reps`() {
        val params = WarmupCalculator.equipmentToWarmupParams("Barbell", UnitSystem.METRIC)!!
        val result = WarmupCalculator.computeWarmupSets(40.0, params)
        assertEquals(1, result.size)
        assertEquals(10, result[0].reps)
    }

    // ── computeWarmupSets — imperial ──────────────────────────────────────────

    @Test
    fun `barbell imperial 225lb - set count based on 45lb bar`() {
        val params = WarmupCalculator.equipmentToWarmupParams("Barbell", UnitSystem.IMPERIAL)!!
        val result = WarmupCalculator.computeWarmupSets(225.0, params)
        // gap = 180, maxJump = 45, ceil(180/45) = 4
        assertEquals(4, result.size)
    }

    @Test
    fun `barbell imperial - weights rounded to nearest 5lb`() {
        val params = WarmupCalculator.equipmentToWarmupParams("Barbell", UnitSystem.IMPERIAL)!!
        val result = WarmupCalculator.computeWarmupSets(225.0, params)
        result.forEach { set ->
            assertTrue("weight ${set.weight} not multiple of 5", set.weight % 5.0 < 0.01)
        }
    }

    // ── roundToNearest ────────────────────────────────────────────────────────

    @Test
    fun `roundToNearest 2_5 - rounds correctly`() {
        assertEquals(2.5, WarmupCalculator.roundToNearest(2.7, 2.5), 0.01)
        assertEquals(5.0, WarmupCalculator.roundToNearest(4.8, 2.5), 0.01)
        assertEquals(0.0, WarmupCalculator.roundToNearest(1.1, 2.5), 0.01)
    }

    @Test
    fun `roundToNearest 1_0 - rounds to whole numbers`() {
        assertEquals(6.0, WarmupCalculator.roundToNearest(5.7, 1.0), 0.01)
        assertEquals(5.0, WarmupCalculator.roundToNearest(5.2, 1.0), 0.01)
    }
}
