package com.powerme.app.data.repository

import com.powerme.app.analytics.ReadinessEngine
import com.powerme.app.data.database.HealthConnectSyncDao
import com.powerme.app.data.database.MetricType
import com.powerme.app.data.database.TrendsDao
import com.powerme.app.ui.metrics.BodyCompositionData
import com.powerme.app.ui.metrics.E1RMChartPoint
import com.powerme.app.ui.metrics.E1RMProgressionData
import com.powerme.app.ui.metrics.EffectiveSetsChartPoint
import com.powerme.app.ui.metrics.MuscleGroupVolumePoint
import com.powerme.app.ui.metrics.TimeOfDayChartPoint
import com.powerme.app.ui.metrics.TimestampedValue
import com.powerme.app.ui.metrics.ReadinessSubMetrics
import com.powerme.app.ui.metrics.TrendsTimeRange
import com.powerme.app.ui.metrics.WeeklyVolumeChartPoint
import com.powerme.app.ui.metrics.WeeklyVolumeData
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrendsRepository @Inject constructor(
    private val trendsDao: TrendsDao,
    private val healthConnectSyncDao: HealthConnectSyncDao,
    private val metricLogRepository: MetricLogRepository
) {

    suspend fun getReadinessScore(): ReadinessEngine.ReadinessScore {
        // Use getLatestSync first to check if any data exists at all
        val latest = healthConnectSyncDao.getLatestSync()
            ?: return ReadinessEngine.ReadinessScore.NoData
        val syncs = healthConnectSyncDao.getRecentSyncs().first()
        return ReadinessEngine.compute(syncs)
    }

    suspend fun getReadinessSubMetrics(): ReadinessSubMetrics {
        val syncs = healthConnectSyncDao.getRecentSyncs().first()
        if (syncs.size < 2) {
            val today = syncs.firstOrNull()
            return ReadinessSubMetrics(
                hrvDelta = null,
                rhrDelta = null,
                sleepMinutes = today?.sleepDurationMinutes
            )
        }
        val today = syncs[0]
        val yesterday = syncs[1]
        val hrvDelta = if (today.hrv != null && yesterday.hrv != null) {
            today.hrv - yesterday.hrv
        } else null
        val rhrDelta = if (today.rhr != null && yesterday.rhr != null) {
            (today.rhr - yesterday.rhr).toDouble()
        } else null
        return ReadinessSubMetrics(
            hrvDelta = hrvDelta,
            rhrDelta = rhrDelta,
            sleepMinutes = today.sleepDurationMinutes
        )
    }

    suspend fun getE1RMProgression(
        exerciseId: Long,
        exerciseName: String,
        range: TrendsTimeRange
    ): E1RMProgressionData {
        val raw = trendsDao.getE1RMHistory(exerciseId, range.sinceMs())
        val points = raw.map { E1RMChartPoint(it.dateMs, it.bestE1RM) }
        val ma = computeMovingAverage(points, windowSize = 3)
        val pctChange = if (points.size >= 2) {
            val first = points.first().e1rm
            val last = points.last().e1rm
            if (first > 0) (last - first) / first * 100.0 else null
        } else null

        return E1RMProgressionData(
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            points = points,
            movingAverage = ma,
            percentChange = pctChange
        )
    }

    suspend fun getWeeklyVolume(range: TrendsTimeRange): WeeklyVolumeData {
        val raw = trendsDao.getWeeklyVolume(range.sinceMs())
        val points = raw.map {
            WeeklyVolumeChartPoint(it.weekStartMs, it.totalVolume, it.workoutCount)
        }
        val gapFilled = gapFillWeeklyPoints(points, range)
        val avgWorkouts = if (gapFilled.isNotEmpty()) {
            gapFilled.sumOf { it.workoutCount }.toDouble() / gapFilled.size
        } else 0.0

        return WeeklyVolumeData(points = gapFilled, avgWorkoutsPerWeek = avgWorkouts)
    }

    suspend fun getWeeklyMuscleGroupVolume(range: TrendsTimeRange): List<MuscleGroupVolumePoint> {
        return trendsDao.getWeeklyMuscleGroupVolume(range.sinceMs()).map {
            MuscleGroupVolumePoint(it.weekStartMs, it.majorGroup, it.volume)
        }
    }

    suspend fun getWeeklyEffectiveSets(range: TrendsTimeRange): List<EffectiveSetsChartPoint> {
        return trendsDao.getWeeklyEffectiveSets(range.sinceMs()).map {
            EffectiveSetsChartPoint(it.weekStartMs, it.majorGroup, it.effectiveSets)
        }
    }

    suspend fun getWorkoutsByTimeOfDay(range: TrendsTimeRange): List<TimeOfDayChartPoint> {
        return trendsDao.getWorkoutsByTimeOfDay(range.sinceMs()).map {
            TimeOfDayChartPoint(it.startHour, it.totalVolume)
        }
    }

    suspend fun getExercisePicker(range: TrendsTimeRange): List<com.powerme.app.data.database.ExerciseWithHistory> {
        return trendsDao.getExercisesWithHistory(range.sinceMs())
    }

    suspend fun getBodyCompositionData(range: TrendsTimeRange): BodyCompositionData {
        val sinceMs = range.sinceMs()
        val weightEntries = metricLogRepository.getByType(MetricType.WEIGHT).first()
            .filter { it.timestamp >= sinceMs }
            .map { TimestampedValue(it.timestamp, it.value) }

        val bodyFatEntries = metricLogRepository.getByType(MetricType.BODY_FAT).first()
            .filter { it.timestamp >= sinceMs }
            .map { TimestampedValue(it.timestamp, it.value) }

        // Compute BMI points from weight entries (need height)
        val heightEntry = metricLogRepository.getLatestForType(MetricType.HEIGHT)
        val heightCm = heightEntry?.value
        val bmiPoints = if (heightCm != null && heightCm > 0) {
            val heightM = heightCm / 100.0
            weightEntries.map { TimestampedValue(it.timestampMs, it.value / (heightM * heightM)) }
        } else emptyList()

        return BodyCompositionData(
            weightPoints = weightEntries,
            bodyFatPoints = bodyFatEntries,
            bmiPoints = bmiPoints
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun computeMovingAverage(
        points: List<E1RMChartPoint>,
        windowSize: Int
    ): List<E1RMChartPoint> {
        if (points.size < windowSize) return emptyList()
        return points.mapIndexed { i, point ->
            val windowStart = maxOf(0, i - windowSize + 1)
            val window = points.subList(windowStart, i + 1)
            val avg = window.sumOf { it.e1rm } / window.size
            E1RMChartPoint(point.timestampMs, avg)
        }
    }

    /**
     * Fill in missing weeks with zero-volume entries so charts have no gaps.
     */
    private fun gapFillWeeklyPoints(
        points: List<WeeklyVolumeChartPoint>,
        range: TrendsTimeRange
    ): List<WeeklyVolumeChartPoint> {
        if (points.isEmpty()) return emptyList()

        val weekMs = 604_800_000L
        val startBucket = range.sinceMs() / weekMs
        val endBucket = System.currentTimeMillis() / weekMs
        val existingByBucket = points.associateBy { it.weekStartMs / weekMs }

        return (startBucket..endBucket).map { bucket ->
            existingByBucket[bucket] ?: WeeklyVolumeChartPoint(
                weekStartMs = bucket * weekMs,
                totalVolume = 0.0,
                workoutCount = 0
            )
        }
    }
}
