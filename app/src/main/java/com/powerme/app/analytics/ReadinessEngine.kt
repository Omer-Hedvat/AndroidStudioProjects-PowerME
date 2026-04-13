package com.powerme.app.analytics

import com.powerme.app.data.database.HealthConnectSync
import kotlin.math.roundToInt

/**
 * Computes a personal readiness score from Health Connect sync history.
 *
 * Algorithm: z-score normalization against 30-day personal baselines,
 * weighted composite (HRV 0.45, Sleep 0.35, RHR 0.20), mapped to 0-100
 * via linear clamping. See TRENDS_SPEC.md §6.
 */
object ReadinessEngine {

    private const val W_HRV = 0.45
    private const val W_SLEEP = 0.35
    private const val W_RHR = 0.20
    private const val MIN_CALIBRATION_DAYS = 5

    sealed class ReadinessScore {
        /** No HC sync data at all for today. */
        data object NoData : ReadinessScore()

        /** Fewer than [MIN_CALIBRATION_DAYS] days of history for any metric. */
        data class Calibrating(
            val daysCollected: Int,
            val daysRequired: Int = MIN_CALIBRATION_DAYS
        ) : ReadinessScore()

        /** Computed readiness score 0-100 with tier classification. */
        data class Score(val value: Int, val tier: Tier) : ReadinessScore()
    }

    enum class Tier { FATIGUED, MODERATE, RECOVERED }

    /**
     * Compute readiness from the most recent sync entry against the full history.
     *
     * @param syncs Most recent 30 days of HC sync data, ordered date DESC.
     *              Typically from [HealthConnectSyncDao.getRecentSyncs].
     * @return [ReadinessScore] — one of NoData, Calibrating, or Score.
     */
    fun compute(syncs: List<HealthConnectSync>): ReadinessScore {
        if (syncs.isEmpty()) return ReadinessScore.NoData

        val today = syncs.first()

        // Check if today has any usable metric at all
        if (today.hrv == null && today.sleepDurationMinutes == null && today.rhr == null) {
            return ReadinessScore.NoData
        }

        // Extract non-null histories for each metric
        val hrvHistory = syncs.mapNotNull { it.hrv }
        val sleepHistory = syncs.mapNotNull { it.sleepDurationMinutes?.toDouble() }
        val rhrHistory = syncs.mapNotNull { it.rhr?.toDouble() }

        // Find the minimum history length across metrics that have today's value
        val availableHistoryLengths = buildList {
            if (today.hrv != null) add(hrvHistory.size)
            if (today.sleepDurationMinutes != null) add(sleepHistory.size)
            if (today.rhr != null) add(rhrHistory.size)
        }

        val minHistory = availableHistoryLengths.minOrNull() ?: return ReadinessScore.NoData
        if (minHistory < MIN_CALIBRATION_DAYS) {
            return ReadinessScore.Calibrating(
                daysCollected = minHistory,
                daysRequired = MIN_CALIBRATION_DAYS
            )
        }

        // Compute baselines
        val hrvMean = StatisticalEngine.mean(hrvHistory)
        val hrvStdDev = StatisticalEngine.standardDeviation(hrvHistory)
        val sleepMean = StatisticalEngine.mean(sleepHistory)
        val sleepStdDev = StatisticalEngine.standardDeviation(sleepHistory)
        val rhrMean = StatisticalEngine.mean(rhrHistory)
        val rhrStdDev = StatisticalEngine.standardDeviation(rhrHistory)

        // Compute z-scores for today's values + build weighted sum
        var weightedSum = 0.0
        var totalWeight = 0.0

        if (today.hrv != null) {
            val z = StatisticalEngine.zScore(today.hrv, hrvMean, hrvStdDev)
            weightedSum += W_HRV * z
            totalWeight += W_HRV
        }

        if (today.sleepDurationMinutes != null) {
            val z = StatisticalEngine.zScore(today.sleepDurationMinutes.toDouble(), sleepMean, sleepStdDev)
            weightedSum += W_SLEEP * z
            totalWeight += W_SLEEP
        }

        if (today.rhr != null) {
            // Negate: lower RHR = better recovery = positive contribution
            val z = StatisticalEngine.zScore(today.rhr.toDouble(), rhrMean, rhrStdDev)
            weightedSum += W_RHR * (-z)
            totalWeight += W_RHR
        }

        // Re-normalize to account for missing metrics
        val rawScore = if (totalWeight > 0.0) weightedSum / totalWeight else 0.0

        // Map raw z-score to 0-100 via linear clamping:
        // rawScore of -2.0 → 0, rawScore of +2.0 → 100, 0.0 → 50
        val score = ((rawScore + 2.0) / 4.0).coerceIn(0.0, 1.0) * 100.0
        val scoreInt = score.roundToInt()

        val tier = when {
            scoreInt >= 70 -> Tier.RECOVERED
            scoreInt >= 40 -> Tier.MODERATE
            else -> Tier.FATIGUED
        }

        return ReadinessScore.Score(value = scoreInt, tier = tier)
    }
}
