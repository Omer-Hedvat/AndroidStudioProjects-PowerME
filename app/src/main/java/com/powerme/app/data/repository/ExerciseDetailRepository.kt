package com.powerme.app.data.repository

import com.powerme.app.analytics.StatisticalEngine
import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.ExerciseDao
import com.powerme.app.data.database.ExerciseStressVectorDao
import com.powerme.app.data.database.ExerciseWorkoutHistoryRow
import com.powerme.app.data.database.MetricType
import com.powerme.app.data.database.TrendsDao
import com.powerme.app.data.UnitSystem
import com.powerme.app.data.database.WorkoutSetDao
import com.powerme.app.ui.exercises.detail.AlternativeExercise
import com.powerme.app.util.WarmupCalculator
import com.powerme.app.ui.exercises.detail.ExerciseTrendData
import com.powerme.app.ui.exercises.detail.LastPerformedSummary
import com.powerme.app.ui.exercises.detail.OverloadSuggestion
import com.powerme.app.ui.exercises.detail.PersonalRecords
import com.powerme.app.ui.exercises.detail.TrendPoint
import com.powerme.app.ui.exercises.detail.WarmUpSet
import com.powerme.app.ui.metrics.TrendsTimeRange
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

@Singleton
class ExerciseDetailRepository @Inject constructor(
    private val exerciseDao: ExerciseDao,
    private val workoutSetDao: WorkoutSetDao,
    private val trendsDao: TrendsDao,
    private val exerciseStressVectorDao: ExerciseStressVectorDao,
    private val metricLogRepository: MetricLogRepository
) {

    // ── Exercise data ────────────────────────────────────────────────────────

    suspend fun getExercise(exerciseId: Long): Exercise? =
        exerciseDao.getExerciseById(exerciseId)

    suspend fun getAllExercises(): List<Exercise> =
        exerciseDao.getAllExercisesSync()

    suspend fun updateUserNote(exerciseId: Long, note: String) {
        exerciseDao.updateUserNote(exerciseId, note, System.currentTimeMillis())
    }

    // ── Last performed + frequency ───────────────────────────────────────────

    suspend fun getLastPerformed(exerciseId: Long): LastPerformedSummary? {
        val row = trendsDao.getExerciseLastPerformed(exerciseId) ?: return null
        return LastPerformedSummary(
            timestampMs = row.timestamp,
            setCount = row.setCount,
            totalVolume = row.totalVolume
        )
    }

    suspend fun getSessionCount(exerciseId: Long): Int =
        workoutSetDao.getExerciseSessionCount(exerciseId)

    // ── Personal Records ─────────────────────────────────────────────────────

    suspend fun computePersonalRecords(exerciseId: Long): PersonalRecords {
        val sets = workoutSetDao.getAllSetsForExercisePRs(exerciseId)
        if (sets.isEmpty()) return PersonalRecords(null, null, null, null, null, null, null, null, null)

        var bestE1RM = 0.0
        var bestE1RMTs = 0L
        var bestSetVolume = 0.0
        var bestSetWeight = 0.0
        var bestSetReps = 0
        var bestSetTs = 0L

        for (s in sets) {
            val e1rm = StatisticalEngine.calculate1RM(s.weight, s.reps)
            if (e1rm > bestE1RM) { bestE1RM = e1rm; bestE1RMTs = s.timestamp }
            val vol = s.setVolume
            if (vol > bestSetVolume) {
                bestSetVolume = vol; bestSetWeight = s.weight; bestSetReps = s.reps; bestSetTs = s.timestamp
            }
        }

        // Group by workout (timestamp) for session-level PRs
        val bySession = sets.groupBy { it.timestamp }
        var bestSessionVolume = 0.0
        var bestSessionVolumeTs = 0L
        var bestTotalReps = 0
        var bestTotalRepsTs = 0L

        for ((ts, sessionSets) in bySession) {
            val sessionVolume = sessionSets.sumOf { it.setVolume }
            if (sessionVolume > bestSessionVolume) { bestSessionVolume = sessionVolume; bestSessionVolumeTs = ts }
            val totalReps = sessionSets.sumOf { it.reps }
            if (totalReps > bestTotalReps) { bestTotalReps = totalReps; bestTotalRepsTs = ts }
        }

        return PersonalRecords(
            bestE1RM = bestE1RM.takeIf { it > 0 },
            bestE1RMTimestampMs = bestE1RMTs.takeIf { bestE1RM > 0 },
            bestSetWeight = bestSetWeight.takeIf { it > 0 },
            bestSetReps = bestSetReps.takeIf { it > 0 },
            bestSetTimestampMs = bestSetTs.takeIf { bestSetWeight > 0 },
            bestSessionVolume = bestSessionVolume.takeIf { it > 0 },
            bestSessionTimestampMs = bestSessionVolumeTs.takeIf { bestSessionVolume > 0 },
            bestTotalReps = bestTotalReps.takeIf { it > 0 },
            bestTotalRepsTimestampMs = bestTotalRepsTs.takeIf { bestTotalReps > 0 }
        )
    }

    // ── Progressive Overload ─────────────────────────────────────────────────

    suspend fun computeOverloadSuggestion(exerciseId: Long): OverloadSuggestion {
        val sets = workoutSetDao.getPreviousSessionCompletedSets(exerciseId, Long.MAX_VALUE)
        if (sets.isEmpty()) return OverloadSuggestion.NoData

        val avgWeight = sets.map { it.weight }.average()
        val avgReps = sets.map { it.reps }.average().roundToInt()
        val targetSets = sets.size

        return if (avgReps >= 12) {
            // Top of hypertrophy zone → increase weight, reset reps
            val increment = if (sets.first().weight > 0) 2.5 else 0.0
            OverloadSuggestion.IncreaseWeight(
                currentWeight = avgWeight,
                suggestedWeight = roundToNearest(avgWeight + increment, 2.5),
                targetReps = 8,
                targetSets = targetSets
            )
        } else {
            OverloadSuggestion.IncreaseReps(
                currentWeight = avgWeight,
                currentReps = avgReps,
                targetReps = avgReps + 1,
                targetSets = targetSets
            )
        }
    }

    // ── Warm-Up Ramp ─────────────────────────────────────────────────────────

    suspend fun computeWarmUpRamp(
        exerciseId: Long,
        equipmentType: String = "Barbell",
        unitSystem: UnitSystem = UnitSystem.METRIC
    ): List<WarmUpSet> {
        val sets = workoutSetDao.getPreviousSessionCompletedSets(exerciseId, Long.MAX_VALUE)
        val workingWeight = sets.map { it.weight }.average().takeIf { !it.isNaN() && it > 0 }
            ?: return emptyList()

        val params = if (equipmentType == "Bodyweight") {
            if (workingWeight > 0) WarmupCalculator.bodyweightLoadedParams(unitSystem) else return emptyList()
        } else {
            WarmupCalculator.equipmentToWarmupParams(equipmentType, unitSystem) ?: return emptyList()
        }
        return WarmupCalculator.computeWarmupSets(workingWeight, params)
    }

    // ── Trend data ────────────────────────────────────────────────────────────

    suspend fun getTrendData(exerciseId: Long, range: TrendsTimeRange): ExerciseTrendData {
        val sinceMs = range.sinceMs()
        val e1rmRows = trendsDao.getE1RMHistory(exerciseId, sinceMs)
        val volumeRows = workoutSetDao.getExerciseSessionVolume(exerciseId, sinceMs)
        val maxWeightRows = workoutSetDao.getExerciseSessionMaxWeight(exerciseId, sinceMs)
        val bestSetRows = workoutSetDao.getExerciseSessionBestSet(exerciseId, sinceMs)
        val rpeRows = workoutSetDao.getExerciseRpeTrend(exerciseId, sinceMs)

        return ExerciseTrendData(
            e1rmPoints = e1rmRows.map { TrendPoint(it.dateMs, it.bestE1RM) },
            maxWeightPoints = maxWeightRows.map { TrendPoint(it.timestamp, it.maxWeight) },
            volumePoints = volumeRows.map { TrendPoint(it.timestamp, it.totalVolume) },
            bestSetPoints = bestSetRows.map { TrendPoint(it.timestamp, it.volume) },
            rpePoints = rpeRows.map { TrendPoint(it.timestamp, it.avgRpe / 10.0) } // stored as int * 10
        )
    }

    // ── Stress vectors ────────────────────────────────────────────────────────

    suspend fun getStressCoefficients(exerciseId: Long): Map<String, Float> {
        return exerciseStressVectorDao.getForExercise(exerciseId)
            .associate { it.bodyRegion to it.stressCoefficient.toFloat() }
    }

    // ── Workout history (paginated) ───────────────────────────────────────────

    suspend fun getWorkoutHistory(exerciseId: Long, page: Int, pageSize: Int = PAGE_SIZE): List<ExerciseWorkoutHistoryRow> {
        return trendsDao.getExerciseWorkoutHistory(exerciseId, limit = pageSize + 1, offset = page * pageSize)
    }

    // ── Alternatives ──────────────────────────────────────────────────────────

    suspend fun findAlternatives(exercise: Exercise): List<AlternativeExercise> {
        val all = exerciseDao.getAllExercisesSync()
        val now = System.currentTimeMillis()
        val scored = all
            .filter { it.id != exercise.id }
            .map { candidate ->
                var score = 0
                if (exercise.familyId != null && candidate.familyId == exercise.familyId) score += 100
                if (candidate.muscleGroup == exercise.muscleGroup) score += 50
                if (candidate.equipmentType == exercise.equipmentType) score += 20
                if (candidate.exerciseType == exercise.exerciseType) score += 10
                candidate to score
            }
            .filter { it.second >= 50 }
            .sortedByDescending { it.second }
            .take(6)

        return scored.map { (candidate, score) ->
            val userHasHistory = workoutSetDao.getExerciseSessionCount(candidate.id) > 0
            // Estimate starting weight for the candidate using the main exercise as the data source.
            // Only shown when user has no history on the candidate but may have history on the main exercise.
            val estimatedWeight = if (userHasHistory) null
            else estimateStartingWeight(target = candidate, sourceExercise = exercise, now)
            AlternativeExercise(exercise = candidate, score = score, userHasDone = userHasHistory, estimatedStartingWeight = estimatedWeight)
        }
    }

    private suspend fun estimateStartingWeight(
        target: Exercise,
        sourceExercise: Exercise,
        now: Long
    ): Double? {
        val sourceE1RM = workoutSetDao.getHistoricalBestE1RM(sourceExercise.id, now) ?: return null
        if (sourceE1RM <= 0) return null
        val ratio = equipmentTransferRatio(sourceExercise.equipmentType, target.equipmentType)
        return roundToNearest(sourceE1RM * ratio * 0.80, 2.5)
    }

    // ── Bodyweight (for relative strength) ───────────────────────────────────

    suspend fun getLatestBodyWeightKg(): Double? {
        val entry = metricLogRepository.getLatestForType(MetricType.WEIGHT) ?: return null
        return entry.value
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun equipmentTransferRatio(from: String, to: String): Double {
        if (from.equals(to, ignoreCase = true)) return 1.0
        return when {
            from.equals("Barbell", ignoreCase = true) && to.equals("Dumbbell", ignoreCase = true) -> 0.35
            from.equals("Dumbbell", ignoreCase = true) && to.equals("Barbell", ignoreCase = true) -> 1.40
            from.equals("Barbell", ignoreCase = true) && to.equals("Machine", ignoreCase = true) -> 0.85
            from.equals("Machine", ignoreCase = true) && to.equals("Barbell", ignoreCase = true) -> 1.15
            from.equals("Cable", ignoreCase = true) && to.equals("Dumbbell", ignoreCase = true) -> 0.90
            else -> 0.80
        }
    }

    private fun roundToNearest(value: Double, nearest: Double): Double =
        (value / nearest).roundToInt() * nearest

    companion object {
        const val PAGE_SIZE = 20
    }
}
