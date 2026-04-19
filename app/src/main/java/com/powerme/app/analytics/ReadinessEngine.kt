package com.powerme.app.analytics

import com.powerme.app.data.database.HealthConnectSync
import kotlin.math.roundToInt

/**
 * Computes a personal readiness score from Health Connect sync history.
 *
 * Algorithm: z-score normalization against 30-day personal baselines,
 * weighted composite (HRV 0.40, Sleep 0.30, RHR 0.15, Deep sleep 0.10, Resp rate 0.05),
 * mapped to 0-100 via linear clamping, then Phase B caps/bonuses applied.
 * Null optional signals redistribute weight proportionally (graceful degradation).
 * See TRENDS_SPEC.md §6 and HEALTH_CONNECT_EXTENDED_READS_SPEC.md §6.
 */
object ReadinessEngine {

    private const val W_HRV = 0.40
    private const val W_SLEEP = 0.30
    private const val W_RHR = 0.15
    private const val W_DEEP_SLEEP = 0.10
    private const val W_RESP_RATE = 0.05
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
        val deepSleepHistory = syncs.mapNotNull { it.deepSleepMinutes?.toDouble() }
        val respRateHistory = syncs.mapNotNull { it.sleepRespiratoryRate }

        // Find the minimum history length across core metrics that have today's value
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

        // Pre-compute baselines to avoid computing mean twice inside standardDeviation
        val hrvMean = StatisticalEngine.mean(hrvHistory)
        val hrvStdDev = StatisticalEngine.standardDeviation(hrvHistory)
        val sleepMean = StatisticalEngine.mean(sleepHistory)
        val sleepStdDev = StatisticalEngine.standardDeviation(sleepHistory)
        val rhrMean = StatisticalEngine.mean(rhrHistory)
        val rhrStdDev = StatisticalEngine.standardDeviation(rhrHistory)
        val deepSleepMean = if (deepSleepHistory.size >= MIN_CALIBRATION_DAYS) StatisticalEngine.mean(deepSleepHistory) else null
        val deepSleepStdDev = if (deepSleepHistory.size >= MIN_CALIBRATION_DAYS) StatisticalEngine.standardDeviation(deepSleepHistory) else null
        val respRateMean = if (respRateHistory.size >= MIN_CALIBRATION_DAYS) StatisticalEngine.mean(respRateHistory) else null
        val respRateStdDev = if (respRateHistory.size >= MIN_CALIBRATION_DAYS) StatisticalEngine.standardDeviation(respRateHistory) else null

        var weightedSum = 0.0
        var totalWeight = 0.0

        if (today.hrv != null) {
            val z = StatisticalEngine.zScore(today.hrv, hrvMean, hrvStdDev)
            weightedSum += W_HRV * z
            totalWeight += W_HRV
        }

        if (today.sleepDurationMinutes != null) {
            // Prefer sleepScore (0-100 mapped to z-score space) over raw duration when available
            val z = if (today.sleepScore != null) {
                (today.sleepScore.toDouble() - 50.0) / 25.0  // center 50 = z 0, 100 = z +2, 0 = z -2
            } else {
                StatisticalEngine.zScore(today.sleepDurationMinutes.toDouble(), sleepMean, sleepStdDev)
            }
            weightedSum += W_SLEEP * z
            totalWeight += W_SLEEP
        }

        if (today.rhr != null) {
            // Negate: lower RHR = better recovery = positive contribution
            val z = StatisticalEngine.zScore(today.rhr.toDouble(), rhrMean, rhrStdDev)
            weightedSum += W_RHR * (-z)
            totalWeight += W_RHR
        }

        // Deep sleep — optional signal
        if (today.deepSleepMinutes != null && deepSleepMean != null && deepSleepStdDev != null) {
            val z = StatisticalEngine.zScore(today.deepSleepMinutes.toDouble(), deepSleepMean, deepSleepStdDev)
            weightedSum += W_DEEP_SLEEP * z
            totalWeight += W_DEEP_SLEEP
        }

        // Respiratory rate — optional signal, inverted (lower is better)
        if (today.sleepRespiratoryRate != null && respRateMean != null && respRateStdDev != null) {
            val z = StatisticalEngine.zScore(today.sleepRespiratoryRate, respRateMean, respRateStdDev)
            weightedSum += W_RESP_RATE * (-z)
            totalWeight += W_RESP_RATE
        }

        // Re-normalize to account for missing metrics
        val rawScore = if (totalWeight > 0.0) weightedSum / totalWeight else 0.0

        // Map raw z-score to 0-100 via linear clamping:
        // rawScore of -2.0 → 0, rawScore of +2.0 → 100, 0.0 → 50
        var scoreInt = (((rawScore + 2.0) / 4.0).coerceIn(0.0, 1.0) * 100.0).roundToInt()

        // Phase B: caps and bonuses
        // SpO2 < 92 caps readiness at 40
        if (today.spo2Percent != null && today.spo2Percent < 92.0) {
            scoreInt = scoreInt.coerceAtMost(40)
        }
        // Elevated respiratory rate penalty
        if (today.elevatedRespiratoryRateFlag) {
            scoreInt -= 10
        }
        // VO2 Max trend bonus/penalty (compare today vs ~30 days ago)
        val todayVo2 = today.vo2MaxMlKgMin
        if (todayVo2 != null && syncs.size >= 2) {
            val oldVo2 = syncs.lastOrNull { it.vo2MaxMlKgMin != null }?.vo2MaxMlKgMin
            if (oldVo2 != null) {
                val delta = todayVo2 - oldVo2
                scoreInt += when {
                    delta >= 2.0 -> 3
                    delta <= -3.0 -> -2
                    else -> 0
                }
            }
        }

        scoreInt = scoreInt.coerceIn(0, 100)

        val tier = when {
            scoreInt >= 70 -> Tier.RECOVERED
            scoreInt >= 40 -> Tier.MODERATE
            else -> Tier.FATIGUED
        }

        return ReadinessScore.Score(value = scoreInt, tier = tier)
    }
}
