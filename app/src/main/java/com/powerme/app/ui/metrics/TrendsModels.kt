package com.powerme.app.ui.metrics

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

/** Sub-metric deltas displayed below the readiness gauge arc. */
data class ReadinessSubMetrics(
    val hrvDelta: Double?,
    val rhrDelta: Double?,
    val sleepMinutes: Int?
)
