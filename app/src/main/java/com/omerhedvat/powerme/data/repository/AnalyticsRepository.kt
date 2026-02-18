package com.omerhedvat.powerme.data.repository

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
        // Get all workouts
        val allWorkouts = workoutDao.getAllWorkouts().first()

        // Get workout sets for each workout
        val workoutSetsMap = mutableMapOf<Long, List<com.omerhedvat.powerme.data.database.WorkoutSet>>()
        allWorkouts.forEach { workout ->
            val sets = workoutSetDao.getSetsForWorkout(workout.id).first()
            workoutSetsMap[workout.id] = sets
        }

        // Get exercise names
        val allExercises = exerciseDao.getAllExercises().first()
        val exerciseNamesMap = allExercises.associate { it.id to it.name }

        // Get health stats from last 28 days
        val fourWeeksAgo = System.currentTimeMillis() - (28L * 24 * 60 * 60 * 1000)
        val healthStats = healthStatsDao.getHealthStatsSince(fourWeeksAgo).first()

        return analyzer.analyzeWeeklyPerformance(
            allWorkouts = allWorkouts,
            allWorkoutSets = workoutSetsMap,
            exerciseNames = exerciseNamesMap,
            healthStats = healthStats
        )
    }
}
