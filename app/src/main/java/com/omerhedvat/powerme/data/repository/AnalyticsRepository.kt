package com.omerhedvat.powerme.data.repository

import com.omerhedvat.powerme.analytics.StatisticalEngine
import com.omerhedvat.powerme.analytics.WeeklyInsights
import com.omerhedvat.powerme.analytics.WeeklyInsightsAnalyzer
import com.omerhedvat.powerme.data.database.HealthStatsDao
import com.omerhedvat.powerme.data.database.WorkoutDao
import com.omerhedvat.powerme.data.database.WorkoutSetDao
import com.omerhedvat.powerme.data.database.ExerciseDao
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val workoutSetDao: WorkoutSetDao,
    private val exerciseDao: ExerciseDao,
    private val healthStatsDao: HealthStatsDao
) {
    private val analyzer = WeeklyInsightsAnalyzer()

    suspend fun generateWeeklyInsights(): WeeklyInsights {
        // 1. Data Retrieval: Get everything in parallel or optimized flows
        val allWorkouts = workoutDao.getAllWorkouts().first()

        // Optimized: Pull all sets for the relevant workouts in one go if possible,
        // otherwise, maintain the map but use the Bayesian smoothing logic
        val workoutSetsMap = allWorkouts.associate { workout ->
            workout.id to workoutSetDao.getSetsForWorkout(workout.id).first()
        }

        val allExercises = exerciseDao.getAllExercises().first()
        val exerciseNamesMap = allExercises.associate { it.id to it.name }

        // 2. Health Siphon: 28-day window
        val fourWeeksAgo = System.currentTimeMillis() - (28L * 24 * 60 * 60 * 1000)
        val healthStats = healthStatsDao.getHealthStatsSince(fourWeeksAgo).first()

        // 3. Compute Bayesian 1RMs per exercise from all available sets
        val weekAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
        val allSetsByExercise = workoutSetsMap.values.flatten().groupBy { it.exerciseId }
        val recentWorkoutIds = allWorkouts.filter { it.timestamp >= weekAgo }.map { it.id }.toSet()

        val bayesian1RMs: Map<Long, Double> = buildMap {
            allSetsByExercise.forEach { (exerciseId, sets) ->
                val validSets = sets.filter { it.weight > 0 && it.reps > 0 }
                if (validSets.isEmpty()) return@forEach

                val priorMean = StatisticalEngine.mean(
                    validSets.map { StatisticalEngine.calculate1RM(it.weight, it.reps) }
                )
                val recentSets = validSets.filter { it.workoutId in recentWorkoutIds }
                if (recentSets.isEmpty()) return@forEach

                val sampleMean = StatisticalEngine.mean(
                    recentSets.map { StatisticalEngine.calculate1RM(it.weight, it.reps) }
                )
                put(
                    exerciseId,
                    StatisticalEngine.calculateBayesian1RM(
                        sampleMean = sampleMean,
                        sampleSize = recentSets.size,
                        priorMean = priorMean
                    )
                )
            }
        }

        // 4. Analysis Handoff
        return analyzer.analyzeWeeklyPerformance(
            allWorkouts = allWorkouts,
            allWorkoutSets = workoutSetsMap,
            exerciseNames = exerciseNamesMap,
            healthStats = healthStats,
            bayesian1RMs = bayesian1RMs
        )
    }
}
