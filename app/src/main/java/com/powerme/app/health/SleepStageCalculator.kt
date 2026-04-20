package com.powerme.app.health

import androidx.health.connect.client.records.SleepSessionRecord
import kotlin.math.roundToInt

object SleepStageCalculator {

    data class SleepStageBreakdown(
        val deepMinutes: Int?,
        val remMinutes: Int?,
        val lightMinutes: Int?,
        val awakeMinutes: Int?
    )

    fun extractStages(stages: List<SleepSessionRecord.Stage>): SleepStageBreakdown {
        if (stages.isEmpty()) return SleepStageBreakdown(null, null, null, null)

        var deep = 0; var rem = 0; var light = 0; var awake = 0
        for (stage in stages) {
            val mins = java.time.temporal.ChronoUnit.MINUTES.between(stage.startTime, stage.endTime).toInt()
            when (stage.stage) {
                SleepSessionRecord.STAGE_TYPE_DEEP -> deep += mins
                SleepSessionRecord.STAGE_TYPE_REM -> rem += mins
                SleepSessionRecord.STAGE_TYPE_LIGHT -> light += mins
                SleepSessionRecord.STAGE_TYPE_AWAKE,
                SleepSessionRecord.STAGE_TYPE_OUT_OF_BED -> awake += mins
                else -> Unit
            }
        }
        return SleepStageBreakdown(
            deepMinutes = deep.takeIf { it > 0 },
            remMinutes = rem.takeIf { it > 0 },
            lightMinutes = light.takeIf { it > 0 },
            awakeMinutes = awake.takeIf { it > 0 }
        )
    }

    fun computeEfficiency(totalMinutes: Int, awakeMinutes: Int): Float {
        if (totalMinutes <= 0) return 0f
        return ((totalMinutes - awakeMinutes).toFloat() / totalMinutes).coerceIn(0f, 1f)
    }

    /**
     * Computes a 0-100 sleep score from total duration and stage breakdown.
     * Formula: 0.35*durationScore + 0.30*deepPctScore + 0.20*remPctScore + 0.15*efficiencyScore
     * Returns null if no stage data available.
     */
    fun computeSleepScore(totalMinutes: Int, breakdown: SleepStageBreakdown): Int? {
        if (breakdown.deepMinutes == null && breakdown.remMinutes == null && breakdown.lightMinutes == null) {
            return null
        }
        val deep = breakdown.deepMinutes ?: 0
        val rem = breakdown.remMinutes ?: 0
        val awake = breakdown.awakeMinutes ?: 0

        // Duration score: 7-9h = 100, linear outside, 0 at <6h or >10h
        val durationScore = when {
            totalMinutes in 420..540 -> 100.0  // 7-9h
            totalMinutes < 360 -> 0.0           // <6h
            totalMinutes > 600 -> 0.0           // >10h
            totalMinutes < 420 -> (totalMinutes - 360).toDouble() / 60.0 * 100.0  // 6-7h linear
            else -> (600 - totalMinutes).toDouble() / 60.0 * 100.0                // 9-10h linear
        }

        // Deep sleep % score: 15-25% = 100, <10% = 0, linear between
        val deepPct = if (totalMinutes > 0) deep.toDouble() / totalMinutes else 0.0
        val deepPctScore = when {
            deepPct >= 0.25 -> 100.0
            deepPct in 0.15..0.25 -> 100.0
            deepPct < 0.10 -> 0.0
            else -> (deepPct - 0.10) / 0.05 * 100.0  // 10-15% linear
        }

        // REM % score: 20-25% = 100, <15% = 0, linear between
        val remPct = if (totalMinutes > 0) rem.toDouble() / totalMinutes else 0.0
        val remPctScore = when {
            remPct >= 0.20 -> 100.0
            remPct < 0.15 -> 0.0
            else -> (remPct - 0.15) / 0.05 * 100.0  // 15-20% linear
        }

        // Efficiency score: >=95% = 100, <80% = 0, linear between
        val efficiency = computeEfficiency(totalMinutes, awake)
        val efficiencyScore = when {
            efficiency >= 0.95f -> 100.0
            efficiency < 0.80f -> 0.0
            else -> (efficiency - 0.80f) / 0.15f * 100.0
        }

        val score = 0.35 * durationScore + 0.30 * deepPctScore + 0.20 * remPctScore + 0.15 * efficiencyScore
        return score.coerceIn(0.0, 100.0).roundToInt()
    }
}
