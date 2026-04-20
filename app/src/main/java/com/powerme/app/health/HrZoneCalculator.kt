package com.powerme.app.health

import androidx.health.connect.client.records.HeartRateRecord

data class HrZoneDistribution(
    val z1Pct: Float,
    val z2Pct: Float,
    val z3Pct: Float,
    val z4Pct: Float,
    val z5Pct: Float
)

object HrZoneCalculator {

    fun maxHr(age: Int): Int = 220 - age

    /**
     * Returns HR zone 1–5 for a given bpm against maxHr.
     * Z1 (<50%): Recovery
     * Z2 (50–60%): Fat Burn
     * Z3 (60–70%): Aerobic
     * Z4 (70–85%): Threshold
     * Z5 (>85%): Anaerobic
     */
    fun zoneForBpm(bpm: Long, maxHr: Int): Int {
        val pct = bpm.toDouble() / maxHr
        return when {
            pct >= 0.85 -> 5
            pct >= 0.70 -> 4
            pct >= 0.60 -> 3
            pct >= 0.50 -> 2
            else -> 1
        }
    }

    fun aggregateZones(samples: List<HeartRateRecord.Sample>, maxHr: Int): HrZoneDistribution {
        if (samples.isEmpty()) return HrZoneDistribution(0f, 0f, 0f, 0f, 0f)
        val counts = IntArray(6)
        for (s in samples) counts[zoneForBpm(s.beatsPerMinute, maxHr)]++
        val total = samples.size.toFloat()
        return HrZoneDistribution(
            z1Pct = counts[1] / total,
            z2Pct = counts[2] / total,
            z3Pct = counts[3] / total,
            z4Pct = counts[4] / total,
            z5Pct = counts[5] / total
        )
    }
}
