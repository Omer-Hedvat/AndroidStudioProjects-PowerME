package com.powerme.app.analytics

import com.powerme.app.data.database.BodyRegion
import com.powerme.app.data.database.ExerciseStressVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

class StressAccumulationEngineTest {

    private val lambda = ln(2.0) / StressAccumulationEngine.HALF_LIFE_DAYS
    private val nowMs = 1_700_000_000_000L // arbitrary fixed "now"
    private val dayMs = 86_400_000L

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun set(
        exerciseId: Long = 1L,
        weight: Double = 100.0,
        reps: Int = 10,
        daysAgo: Double = 0.0
    ) = StressAccumulationEngine.SetRecord(
        exerciseId = exerciseId,
        weight = weight,
        reps = reps,
        timestampMs = nowMs - (daysAgo * dayMs).toLong()
    )

    private fun vector(
        exerciseId: Long = 1L,
        region: BodyRegion = BodyRegion.KNEE_JOINT,
        coefficient: Double = 1.0
    ) = ExerciseStressVector(exerciseId, region.name, coefficient)

    private fun vectors(vararg v: ExerciseStressVector): Map<Long, List<ExerciseStressVector>> =
        v.toList().groupBy { it.exerciseId }

    private fun assertNear(expected: Double, actual: Double, tol: Double = 0.001) {
        assertTrue(
            "Expected ~$expected but got $actual (tolerance $tol)",
            abs(actual - expected) < tol
        )
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `empty sets list returns empty result`() {
        val result = StressAccumulationEngine.computeRegionStress(emptyList(), emptyMap(), nowMs)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `sets with no matching vectors return empty result`() {
        val result = StressAccumulationEngine.computeRegionStress(
            sets = listOf(set(exerciseId = 99L)),
            vectors = vectors(vector(exerciseId = 1L)),
            nowMs = nowMs
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `single set today produces correct stress`() {
        // At day 0: decay = e^0 = 1.0, so stress = volume * coefficient * 1.0
        val result = StressAccumulationEngine.computeRegionStress(
            sets = listOf(set(weight = 100.0, reps = 10, daysAgo = 0.0)),
            vectors = vectors(vector(coefficient = 0.8)),
            nowMs = nowMs
        )
        assertEquals(1, result.size)
        assertNear(100.0 * 10 * 0.8 * 1.0, result[0].totalStress)
    }

    @Test
    fun `stress at half-life (4 days) is approximately half of today`() {
        val todayStress = StressAccumulationEngine.computeRegionStress(
            sets = listOf(set(daysAgo = 0.0)),
            vectors = vectors(vector(coefficient = 1.0)),
            nowMs = nowMs
        ).first().totalStress

        val halfLifeStress = StressAccumulationEngine.computeRegionStress(
            sets = listOf(set(daysAgo = 4.0)),
            vectors = vectors(vector(coefficient = 1.0)),
            nowMs = nowMs
        ).first().totalStress

        // e^(-lambda*4) = e^(-ln(2)) = 0.5
        assertNear(todayStress * 0.5, halfLifeStress, tol = 1.0)
    }

    @Test
    fun `stress at 3 half-lives (12 days) is approximately 12_5 percent of today`() {
        val todayStress = StressAccumulationEngine.computeRegionStress(
            sets = listOf(set(daysAgo = 0.0)),
            vectors = vectors(vector(coefficient = 1.0)),
            nowMs = nowMs
        ).first().totalStress

        val threeHalfLivesStress = StressAccumulationEngine.computeRegionStress(
            sets = listOf(set(daysAgo = 12.0)),
            vectors = vectors(vector(coefficient = 1.0)),
            nowMs = nowMs
        ).first().totalStress

        // e^(-lambda*12) = (0.5)^3 = 0.125
        assertNear(todayStress * 0.125, threeHalfLivesStress, tol = 1.0)
    }

    @Test
    fun `multiple sets for same region accumulate`() {
        val sets = listOf(
            set(weight = 100.0, reps = 5, daysAgo = 0.0),
            set(weight = 80.0, reps = 8, daysAgo = 0.0)
        )
        val result = StressAccumulationEngine.computeRegionStress(
            sets = sets,
            vectors = vectors(vector(coefficient = 1.0)),
            nowMs = nowMs
        )
        assertEquals(1, result.size)
        // (100*5 + 80*8) * 1.0 * e^0 = (500 + 640) = 1140
        assertNear(1140.0, result[0].totalStress)
    }

    @Test
    fun `multiple regions for same exercise produce separate outputs`() {
        val v = vectors(
            vector(region = BodyRegion.KNEE_JOINT, coefficient = 0.9),
            vector(region = BodyRegion.QUADS, coefficient = 0.8)
        )
        val result = StressAccumulationEngine.computeRegionStress(
            sets = listOf(set(weight = 100.0, reps = 10, daysAgo = 0.0)),
            vectors = v,
            nowMs = nowMs
        )
        assertEquals(2, result.size)
        val kneeStress = result.first { it.region == BodyRegion.KNEE_JOINT }.totalStress
        val quadStress = result.first { it.region == BodyRegion.QUADS }.totalStress
        assertNear(900.0, kneeStress)
        assertNear(800.0, quadStress)
    }

    @Test
    fun `output is sorted by descending stress`() {
        val v = vectors(
            vector(region = BodyRegion.KNEE_JOINT, coefficient = 0.5),
            vector(region = BodyRegion.QUADS, coefficient = 0.9)
        )
        val result = StressAccumulationEngine.computeRegionStress(
            sets = listOf(set()),
            vectors = v,
            nowMs = nowMs
        )
        assertTrue("First entry should have higher stress", result[0].totalStress >= result[1].totalStress)
        assertEquals(BodyRegion.QUADS, result[0].region)
    }

    @Test
    fun `zero volume set contributes nothing`() {
        val result = StressAccumulationEngine.computeRegionStress(
            sets = listOf(set(weight = 0.0, reps = 10)),
            vectors = vectors(vector()),
            nowMs = nowMs
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `sets for multiple exercises with different vectors accumulate correctly`() {
        val sets = listOf(
            set(exerciseId = 1L, weight = 100.0, reps = 5, daysAgo = 0.0),
            set(exerciseId = 2L, weight = 50.0, reps = 10, daysAgo = 0.0)
        )
        val v = vectors(
            ExerciseStressVector(1L, BodyRegion.KNEE_JOINT.name, 0.8),
            ExerciseStressVector(2L, BodyRegion.KNEE_JOINT.name, 0.6)
        )
        val result = StressAccumulationEngine.computeRegionStress(sets, v, nowMs)
        assertEquals(1, result.size)
        // 100*5*0.8 + 50*10*0.6 = 400 + 300 = 700
        assertNear(700.0, result[0].totalStress)
    }

    @Test
    fun `exponential decay formula matches expected value at arbitrary day`() {
        val daysAgo = 7.0
        val expected = 100.0 * 10 * 1.0 * exp(-lambda * daysAgo)
        val result = StressAccumulationEngine.computeRegionStress(
            sets = listOf(set(weight = 100.0, reps = 10, daysAgo = daysAgo)),
            vectors = vectors(vector(coefficient = 1.0)),
            nowMs = nowMs
        )
        assertNear(expected, result[0].totalStress, tol = 0.01)
    }
}
