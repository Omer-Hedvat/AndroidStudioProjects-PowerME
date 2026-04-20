package com.powerme.app.analytics

import com.powerme.app.data.database.BodyRegion
import com.powerme.app.data.database.ExerciseStressVector
import kotlin.math.exp
import kotlin.math.ln

/**
 * Computes cumulative per-region training stress from workout history.
 *
 * Algorithm:
 *   stress_region = Σ (volume × coefficient × e^(−λ × daysSince))
 *
 * Where:
 *   - volume = weight × reps per set
 *   - coefficient = ExerciseStressVector.stressCoefficient for the (exercise, region) pair
 *   - λ = ln(2) / HALF_LIFE_DAYS — exponential decay constant
 *   - daysSince = (nowMs − set.timestampMs) / 86_400_000
 *
 * Half-life is set to 4 days based on connective tissue recovery research
 * (Schoenfeld / NSCA guidelines). Adjust via [HALF_LIFE_DAYS] if needed.
 *
 * This is a pure stateless object — no DAO calls, easy to unit test.
 */
object StressAccumulationEngine {

    const val HALF_LIFE_DAYS = 4.0
    private val LAMBDA = ln(2.0) / HALF_LIFE_DAYS

    /** Threshold: if recent-48h stress exceeds this fraction of total, region is FATIGUED. */
    private const val FATIGUE_THRESHOLD = 0.40

    /**
     * A single completed working set (WARMUP/DROP already excluded by the DAO query).
     */
    data class SetRecord(
        val exerciseId: Long,
        val weight: Double,
        val reps: Int,
        val timestampMs: Long
    )

    /**
     * Accumulated stress for one body region, decay-weighted over the lookback window.
     */
    data class RegionStress(
        val region: BodyRegion,
        val totalStress: Double
    )

    /** Exercise that contributed to a region's stress, with its share. */
    data class ExerciseContribution(
        val exerciseId: Long,
        val exerciseName: String,
        val stress: Double
    )

    /** User-facing recovery state for a body region. */
    enum class RecoveryStatus { READY, RECOVERING, FATIGUED }

    /** User-facing intensity tier relative to the user's own historical max. */
    enum class IntensityTier { LOW, MODERATE, HIGH, VERY_HIGH }

    /**
     * Full context for one body region: stress, contributing exercises, and recovery status.
     * Used by the redesigned heatmap card to display tier labels and drill-down detail.
     */
    data class RegionDetail(
        val region: BodyRegion,
        val totalStress: Double,
        val topExercises: List<ExerciseContribution>,
        val recoveryStatus: RecoveryStatus
    )

    /**
     * Computes region stress from pre-fetched workout history.
     *
     * @param sets     Completed working sets in the lookback window (excl. WARMUP/DROP).
     * @param vectors  Stress vectors indexed by exerciseId.
     * @param nowMs    Current time epoch ms (used for decay calculation).
     * @return         Non-zero regions sorted by descending stress.
     */
    fun computeRegionStress(
        sets: List<SetRecord>,
        vectors: Map<Long, List<ExerciseStressVector>>,
        nowMs: Long
    ): List<RegionStress> =
        computeRegionDetails(sets, vectors, emptyMap(), nowMs)
            .map { RegionStress(it.region, it.totalStress) }

    /**
     * Extended computation that returns full [RegionDetail] for each stressed region.
     * Includes exercise attribution (top-3 contributors) and recovery status.
     *
     * @param sets           Completed working sets in the lookback window.
     * @param vectors        Stress vectors indexed by exerciseId.
     * @param exerciseNames  Map of exerciseId → display name for attribution labels.
     * @param nowMs          Current time epoch ms.
     * @return               Non-zero regions sorted by descending stress.
     */
    fun computeRegionDetails(
        sets: List<SetRecord>,
        vectors: Map<Long, List<ExerciseStressVector>>,
        exerciseNames: Map<Long, String>,
        nowMs: Long
    ): List<RegionDetail> {
        // region → total stress
        val stressMap = mutableMapOf<BodyRegion, Double>()
        // region → (exerciseId → stress contribution)
        val exerciseContribMap = mutableMapOf<BodyRegion, MutableMap<Long, Double>>()
        // region → stress from sets within the last 48 hours
        val recent48hMap = mutableMapOf<BodyRegion, Double>()

        for (set in sets) {
            val exerciseVectors = vectors[set.exerciseId] ?: continue
            val volume = set.weight * set.reps
            if (volume <= 0.0) continue

            val daysSince = (nowMs - set.timestampMs) / 86_400_000.0
            val decay = exp(-LAMBDA * daysSince)
            val isRecent = daysSince < 2.0

            for (vector in exerciseVectors) {
                val region = BodyRegion.valueOf(vector.bodyRegion)
                val stress = volume * vector.stressCoefficient * decay

                stressMap[region] = (stressMap[region] ?: 0.0) + stress

                val exMap = exerciseContribMap.getOrPut(region) { mutableMapOf() }
                exMap[set.exerciseId] = (exMap[set.exerciseId] ?: 0.0) + stress

                if (isRecent) {
                    recent48hMap[region] = (recent48hMap[region] ?: 0.0) + stress
                }
            }
        }

        return stressMap
            .filter { it.value > 0.0 }
            .map { (region, total) ->
                val top3 = exerciseContribMap[region]
                    ?.entries
                    ?.sortedByDescending { it.value }
                    ?.take(3)
                    ?.map { (id, s) ->
                        ExerciseContribution(id, exerciseNames[id] ?: "Unknown", s)
                    }
                    ?: emptyList()

                val recent48h = recent48hMap[region] ?: 0.0
                val recovery = if (recent48h / total > FATIGUE_THRESHOLD) {
                    RecoveryStatus.FATIGUED
                } else {
                    RecoveryStatus.RECOVERING
                }

                RegionDetail(region, total, top3, recovery)
            }
            .sortedByDescending { it.totalStress }
    }

    /**
     * Classifies a stress value into a user-visible intensity tier relative to
     * the user's own historical maximum. Uses quartile boundaries so the tier
     * label adapts as the user's baseline grows over time.
     *
     * @param stress      Stress for the region to classify.
     * @param maxStress   Maximum stress in the current dataset (user's baseline peak).
     */
    fun classifyIntensity(stress: Double, maxStress: Double): IntensityTier {
        if (maxStress <= 0.0) return IntensityTier.LOW
        return when ((stress / maxStress).coerceIn(0.0, 1.0)) {
            in 0.75..1.0   -> IntensityTier.VERY_HIGH
            in 0.50..<0.75 -> IntensityTier.HIGH
            in 0.25..<0.50 -> IntensityTier.MODERATE
            else            -> IntensityTier.LOW
        }
    }
}
