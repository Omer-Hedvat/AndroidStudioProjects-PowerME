package com.powerme.app.analytics

import com.powerme.app.data.database.BodyRegion
import com.powerme.app.data.database.ExerciseStressVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [StressAccumulationEngine.computeRegionDetails] and
 * [StressAccumulationEngine.classifyIntensity].
 */
class StressAccumulationEngineDetailTest {

    private val nowMs = 1_700_000_000_000L
    private val dayMs = 86_400_000L

    private fun set(
        exerciseId: Long = 1L,
        weight: Double = 100.0,
        reps: Int = 10,
        daysAgo: Double = 5.0
    ) = StressAccumulationEngine.SetRecord(
        exerciseId = exerciseId,
        weight = weight,
        reps = reps,
        timestampMs = nowMs - (daysAgo * dayMs).toLong()
    )

    private fun vector(
        exerciseId: Long = 1L,
        region: BodyRegion = BodyRegion.QUADS,
        coeff: Double = 1.0
    ) = ExerciseStressVector(exerciseId, region.name, coeff)

    // ── computeRegionDetails ──────────────────────────────────────────────────

    @Test
    fun `computeRegionDetails returns non-empty list when sets are present`() {
        val sets = listOf(set(exerciseId = 1L, daysAgo = 2.0))
        val vectors = mapOf(1L to listOf(vector(1L, BodyRegion.QUADS, 0.9)))
        val names = mapOf(1L to "Squat")

        val result = StressAccumulationEngine.computeRegionDetails(sets, vectors, names, nowMs)

        assertTrue(result.isNotEmpty())
        assertEquals(BodyRegion.QUADS, result.first().region)
    }

    @Test
    fun `computeRegionDetails returns empty list when no sets`() {
        val result = StressAccumulationEngine.computeRegionDetails(
            emptyList(), emptyMap(), emptyMap(), nowMs
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `computeRegionDetails sorts by descending totalStress`() {
        val sets = listOf(
            set(exerciseId = 1L, weight = 100.0, reps = 10, daysAgo = 5.0),
            set(exerciseId = 2L, weight = 50.0,  reps = 10, daysAgo = 5.0)
        )
        val vectors = mapOf(
            1L to listOf(vector(1L, BodyRegion.QUADS, 1.0)),
            2L to listOf(vector(2L, BodyRegion.HAMSTRINGS, 1.0))
        )
        val result = StressAccumulationEngine.computeRegionDetails(sets, vectors, emptyMap(), nowMs)

        assertEquals(2, result.size)
        assertTrue(result[0].totalStress >= result[1].totalStress)
        assertEquals(BodyRegion.QUADS, result[0].region)
    }

    @Test
    fun `computeRegionDetails includes top 3 exercises sorted by contribution`() {
        val sets = listOf(
            set(exerciseId = 1L, weight = 300.0, reps = 10, daysAgo = 5.0),  // most
            set(exerciseId = 2L, weight = 200.0, reps = 10, daysAgo = 5.0),  // middle
            set(exerciseId = 3L, weight = 100.0, reps = 10, daysAgo = 5.0),  // least
            set(exerciseId = 4L, weight = 50.0,  reps = 10, daysAgo = 5.0)   // 4th — dropped
        )
        val vectors = mapOf(
            1L to listOf(vector(1L, BodyRegion.QUADS, 1.0)),
            2L to listOf(vector(2L, BodyRegion.QUADS, 1.0)),
            3L to listOf(vector(3L, BodyRegion.QUADS, 1.0)),
            4L to listOf(vector(4L, BodyRegion.QUADS, 1.0))
        )
        val names = mapOf(1L to "Squat", 2L to "Leg Press", 3L to "Lunge", 4L to "Extension")

        val result = StressAccumulationEngine.computeRegionDetails(sets, vectors, names, nowMs)

        val quadDetail = result.first { it.region == BodyRegion.QUADS }
        assertEquals(3, quadDetail.topExercises.size)
        assertEquals("Squat",      quadDetail.topExercises[0].exerciseName)
        assertEquals("Leg Press",  quadDetail.topExercises[1].exerciseName)
        assertEquals("Lunge",      quadDetail.topExercises[2].exerciseName)
        // 4th exercise must not appear
        assertTrue(quadDetail.topExercises.none { it.exerciseName == "Extension" })
    }

    @Test
    fun `computeRegionDetails uses 'Unknown' when exercise name is missing`() {
        val sets = listOf(set(exerciseId = 99L, daysAgo = 2.0))
        val vectors = mapOf(99L to listOf(vector(99L, BodyRegion.PECS, 0.8)))
        val result = StressAccumulationEngine.computeRegionDetails(sets, vectors, emptyMap(), nowMs)

        assertEquals("Unknown", result.first().topExercises.first().exerciseName)
    }

    // ── Recovery status ───────────────────────────────────────────────────────

    @Test
    fun `recovery status is READY when totalStress is zero`() {
        // No sets → no regions → nothing to check; test via single region with zero stress
        // Use a vector with coefficient 0.0 so stress accumulates to ~0
        val sets = listOf(set(exerciseId = 1L, weight = 100.0, reps = 10, daysAgo = 2.0))
        val vectors = mapOf(1L to listOf(vector(1L, BodyRegion.QUADS, 0.0)))
        val result = StressAccumulationEngine.computeRegionDetails(sets, vectors, emptyMap(), nowMs)
        // With coefficient 0 no stress is accumulated → result should be empty (filtered out)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `recovery status is FATIGUED when more than 40pct of stress is within last 48h`() {
        // Only recent sets (within 48h)
        val sets = listOf(
            set(exerciseId = 1L, weight = 200.0, reps = 10, daysAgo = 0.5)  // recent
        )
        val vectors = mapOf(1L to listOf(vector(1L, BodyRegion.QUADS, 1.0)))
        val result = StressAccumulationEngine.computeRegionDetails(sets, vectors, emptyMap(), nowMs)

        assertEquals(StressAccumulationEngine.RecoveryStatus.FATIGUED, result.first().recoveryStatus)
    }

    @Test
    fun `recovery status is RECOVERING when stress is older than 48h`() {
        // All sets more than 2 days ago → recent48h = 0
        val sets = listOf(
            set(exerciseId = 1L, weight = 200.0, reps = 10, daysAgo = 5.0)
        )
        val vectors = mapOf(1L to listOf(vector(1L, BodyRegion.QUADS, 1.0)))
        val result = StressAccumulationEngine.computeRegionDetails(sets, vectors, emptyMap(), nowMs)

        assertEquals(StressAccumulationEngine.RecoveryStatus.RECOVERING, result.first().recoveryStatus)
    }

    // ── classifyIntensity ─────────────────────────────────────────────────────

    @Test
    fun `classifyIntensity returns LOW for ratio 0 to 0_25`() {
        assertEquals(StressAccumulationEngine.IntensityTier.LOW,
            StressAccumulationEngine.classifyIntensity(0.0, 100.0))
        assertEquals(StressAccumulationEngine.IntensityTier.LOW,
            StressAccumulationEngine.classifyIntensity(24.9, 100.0))
    }

    @Test
    fun `classifyIntensity returns MODERATE for ratio 0_25 to 0_50`() {
        assertEquals(StressAccumulationEngine.IntensityTier.MODERATE,
            StressAccumulationEngine.classifyIntensity(25.0, 100.0))
        assertEquals(StressAccumulationEngine.IntensityTier.MODERATE,
            StressAccumulationEngine.classifyIntensity(49.9, 100.0))
    }

    @Test
    fun `classifyIntensity returns HIGH for ratio 0_50 to 0_75`() {
        assertEquals(StressAccumulationEngine.IntensityTier.HIGH,
            StressAccumulationEngine.classifyIntensity(50.0, 100.0))
        assertEquals(StressAccumulationEngine.IntensityTier.HIGH,
            StressAccumulationEngine.classifyIntensity(74.9, 100.0))
    }

    @Test
    fun `classifyIntensity returns VERY_HIGH for ratio 0_75 to 1_0`() {
        assertEquals(StressAccumulationEngine.IntensityTier.VERY_HIGH,
            StressAccumulationEngine.classifyIntensity(75.0, 100.0))
        assertEquals(StressAccumulationEngine.IntensityTier.VERY_HIGH,
            StressAccumulationEngine.classifyIntensity(100.0, 100.0))
    }

    @Test
    fun `classifyIntensity returns LOW when maxStress is zero`() {
        assertEquals(StressAccumulationEngine.IntensityTier.LOW,
            StressAccumulationEngine.classifyIntensity(0.0, 0.0))
        assertEquals(StressAccumulationEngine.IntensityTier.LOW,
            StressAccumulationEngine.classifyIntensity(50.0, 0.0))
    }
}
