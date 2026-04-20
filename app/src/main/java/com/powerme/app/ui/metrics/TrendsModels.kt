package com.powerme.app.ui.metrics

import com.powerme.app.analytics.StressAccumulationEngine
import java.time.LocalDate

/**
 * Shared time range for all Trends tab chart cards.
 * Driven by TrendsViewModel, consumed by TrendsRepository for query parameters.
 */
enum class TrendsTimeRange(val label: String, val days: Int) {
    ONE_MONTH("1M", 30),
    THREE_MONTHS("3M", 90),
    SIX_MONTHS("6M", 180),
    ONE_YEAR("1Y", 365);

    /** Epoch millis for the start of this time window. */
    fun sinceMs(): Long = System.currentTimeMillis() - days.toLong() * 24 * 60 * 60 * 1000
}

/** A single e1RM data point for one workout session. */
data class E1RMProgressionData(
    val exerciseId: Long,
    val exerciseName: String,
    val points: List<E1RMChartPoint>,
    val movingAverage: List<E1RMChartPoint>,
    val percentChange: Double?
)

data class E1RMChartPoint(
    val timestampMs: Long,
    val e1rm: Double
)

/** Weekly volume aggregation with gap-filled empty weeks. */
data class WeeklyVolumeData(
    val points: List<WeeklyVolumeChartPoint>,
    val avgWorkoutsPerWeek: Double
)

data class WeeklyVolumeChartPoint(
    val weekStartMs: Long,
    val totalVolume: Double,
    val workoutCount: Int
)

/** Per-muscle-group weekly volume with primary/secondary credit. */
data class MuscleGroupVolumePoint(
    val weekStartMs: Long,
    val majorGroup: String,
    val volume: Double
)

/** Effective sets (RPE >= 7.0) per muscle group per week. */
data class EffectiveSetsChartPoint(
    val weekStartMs: Long,
    val majorGroup: String,
    val setCount: Int
)

/** Body composition overlay data (weight + body fat over time). */
data class BodyCompositionData(
    val weightPoints: List<TimestampedValue>,
    val bodyFatPoints: List<TimestampedValue>,
    val bmiPoints: List<TimestampedValue>
)

data class TimestampedValue(
    val timestampMs: Long,
    val value: Double
)

/** Workout time-of-day point for chronotype scatter. */
data class TimeOfDayChartPoint(
    val startHour: Int,
    val totalVolume: Double
)

/** Single night's sleep duration for the sleep trend bar chart. */
data class SleepChartPoint(
    val date: LocalDate,
    val durationMinutes: Int
) {
    val durationHours: Double get() = durationMinutes / 60.0
}

/** Combined data for the ChronotypeCard (sleep trend + training window scatter). */
data class ChronotypeData(
    val sleepPoints: List<SleepChartPoint>,
    val workoutPoints: List<TimeOfDayChartPoint>,
    /** Hour bucket (0–23) with highest median volume; null when < 10 workouts. */
    val peakHour: Int?,
    /** Pre-formatted label e.g. "6am", "3pm"; null when peakHour is null. */
    val peakHourLabel: String?
)

/** Body stress map data — one entry per non-zero region, plus the overall max for normalization. */
data class BodyStressMapData(
    val regionDetails: List<StressAccumulationEngine.RegionDetail>,
    val maxStress: Double
) {
    val regionStresses: List<StressAccumulationEngine.RegionStress> =
        regionDetails.map { StressAccumulationEngine.RegionStress(it.region, it.totalStress) }
}

/** Sub-metric deltas displayed below the readiness gauge arc. */
data class ReadinessSubMetrics(
    val hrvDelta: Double?,
    val rhrDelta: Double?,
    val sleepMinutes: Int?
)
