package com.omerhedvat.powerme.analytics

import com.omerhedvat.powerme.data.database.HealthStats
import com.omerhedvat.powerme.data.database.Workout
import com.omerhedvat.powerme.data.database.WorkoutSet
import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit

@Serializable
data class VolumeLoadAnomaly(
    val workoutId: Long,
    val timestamp: Long,
    val volumeLoad: Double,
    val zScore: Double,
    val type: String, // "Positive Outlier", "Negative Outlier", or "Normal"
    val healthContext: String? = null
)

@Serializable
data class ProgressionAnomaly(
    val exerciseName: String,
    val currentE1RM: Double,
    val previousE1RM: Double,
    val rateOfChange: Double,
    val flag: String // "Rapid Gain - Verify Technique", "Plateau Detected", "Normal"
)

@Serializable
data class HealthPerformanceCorrelation(
    val correlationCoefficient: Double,
    val interpretation: String, // "Strong", "Moderate", "Weak", "Decoupled"
    val recommendation: String
)

@Serializable
data class WeeklyInsights(
    val weekStartDate: Long,
    val weekEndDate: Long,
    val status: String, // "Stable", "Anomalous"
    val volumeLoadAnomalies: List<VolumeLoadAnomaly>,
    val progressionAnomalies: List<ProgressionAnomaly>,
    val healthPerformanceCorrelation: HealthPerformanceCorrelation?,
    val summary: String,
    val recommendations: List<String>
)

class WeeklyInsightsAnalyzer {

    fun analyzeWeeklyPerformance(
        allWorkouts: List<Workout>,
        allWorkoutSets: Map<Long, List<WorkoutSet>>,
        exerciseNames: Map<Long, String>,
        healthStats: List<HealthStats>
    ): WeeklyInsights {
        val now = System.currentTimeMillis()
        val weekAgo = now - TimeUnit.DAYS.toMillis(7)
        val fourWeeksAgo = now - TimeUnit.DAYS.toMillis(28)

        // Filter workouts
        val lastWeekWorkouts = allWorkouts.filter { it.timestamp >= weekAgo }
        val lastFourWeeksWorkouts = allWorkouts.filter { it.timestamp >= fourWeeksAgo }

        // Analyze volume load anomalies
        val volumeAnomalies = analyzeVolumeLoadAnomalies(
            lastWeekWorkouts,
            lastFourWeeksWorkouts,
            allWorkoutSets,
            healthStats
        )

        // Analyze progression anomalies
        val progressionAnomalies = analyzeProgressionAnomalies(
            lastWeekWorkouts,
            lastFourWeeksWorkouts,
            allWorkoutSets,
            exerciseNames
        )

        // Analyze health-performance correlation
        val healthCorrelation = analyzeHealthPerformanceCorrelation(
            lastFourWeeksWorkouts,
            healthStats
        )

        // Determine status
        val status = if (volumeAnomalies.any { it.type != "Normal" } ||
                        progressionAnomalies.any { it.flag != "Normal" }) {
            "Anomalous"
        } else {
            "Stable"
        }

        // Generate summary
        val summary = generateSummary(volumeAnomalies, progressionAnomalies, healthCorrelation)

        // Generate recommendations
        val recommendations = generateRecommendations(
            volumeAnomalies,
            progressionAnomalies,
            healthCorrelation
        )

        return WeeklyInsights(
            weekStartDate = weekAgo,
            weekEndDate = now,
            status = status,
            volumeLoadAnomalies = volumeAnomalies,
            progressionAnomalies = progressionAnomalies,
            healthPerformanceCorrelation = healthCorrelation,
            summary = summary,
            recommendations = recommendations
        )
    }

    private fun analyzeVolumeLoadAnomalies(
        lastWeekWorkouts: List<Workout>,
        lastFourWeeksWorkouts: List<Workout>,
        allWorkoutSets: Map<Long, List<WorkoutSet>>,
        healthStats: List<HealthStats>
    ): List<VolumeLoadAnomaly> {
        if (lastFourWeeksWorkouts.size < 4) return emptyList()

        // Calculate volume loads for all workouts in the last 4 weeks
        val volumeLoads = lastFourWeeksWorkouts.map { workout ->
            val sets = allWorkoutSets[workout.id] ?: emptyList()
            workout.timestamp to sets.sumOf { it.weight * it.reps }
        }

        val values = volumeLoads.map { it.second }
        val mean = StatisticalEngine.mean(values)
        val stdDev = StatisticalEngine.standardDeviation(values)

        // Analyze last week's workouts for outliers
        return lastWeekWorkouts.mapNotNull { workout ->
            val sets = allWorkoutSets[workout.id] ?: emptyList()
            val volumeLoad = sets.sumOf { it.weight * it.reps }
            val z = StatisticalEngine.zScore(volumeLoad, mean, stdDev)

            when {
                z > 2.0 -> {
                    VolumeLoadAnomaly(
                        workoutId = workout.id,
                        timestamp = workout.timestamp,
                        volumeLoad = volumeLoad,
                        zScore = z,
                        type = "Positive Outlier",
                        healthContext = "Peak Performance - Boris demands new baseline"
                    )
                }
                z < -2.0 -> {
                    val healthContext = getHealthContext(workout.timestamp, healthStats)
                    VolumeLoadAnomaly(
                        workoutId = workout.id,
                        timestamp = workout.timestamp,
                        volumeLoad = volumeLoad,
                        zScore = z,
                        type = "Negative Outlier",
                        healthContext = healthContext
                    )
                }
                else -> null
            }
        }
    }

    private fun analyzeProgressionAnomalies(
        lastWeekWorkouts: List<Workout>,
        lastFourWeeksWorkouts: List<Workout>,
        allWorkoutSets: Map<Long, List<WorkoutSet>>,
        exerciseNames: Map<Long, String>
    ): List<ProgressionAnomaly> {
        val anomalies = mutableListOf<ProgressionAnomaly>()

        // Group sets by exercise
        val exerciseProgressionMap = mutableMapOf<Long, MutableList<Pair<Long, Double>>>()

        lastFourWeeksWorkouts.forEach { workout ->
            val sets = allWorkoutSets[workout.id] ?: emptyList()
            sets.forEach { set ->
                val e1rm = StatisticalEngine.calculate1RM(set.weight, set.reps)
                exerciseProgressionMap.getOrPut(set.exerciseId) { mutableListOf() }
                    .add(workout.timestamp to e1rm)
            }
        }

        // Check compound lifts for rapid progression
        val compoundLifts = listOf("Bench Press", "Squat", "Deadlift", "Press")

        exerciseProgressionMap.forEach { (exerciseId, progressionData) ->
            val exerciseName = exerciseNames[exerciseId] ?: return@forEach

            // Only check compound lifts
            if (!compoundLifts.any { exerciseName.contains(it, ignoreCase = true) }) return@forEach

            if (progressionData.size < 2) return@forEach

            // Sort by timestamp
            val sorted = progressionData.sortedBy { it.first }
            val latest = sorted.last().second
            val previous = sorted[sorted.size - 2].second

            val roc = StatisticalEngine.rateOfChange(latest, previous)

            when {
                roc > 0.15 -> {
                    anomalies.add(
                        ProgressionAnomaly(
                            exerciseName = exerciseName,
                            currentE1RM = latest,
                            previousE1RM = previous,
                            rateOfChange = roc,
                            flag = "Rapid Gain - Verify Technique"
                        )
                    )
                }
                roc < -0.10 -> {
                    anomalies.add(
                        ProgressionAnomaly(
                            exerciseName = exerciseName,
                            currentE1RM = latest,
                            previousE1RM = previous,
                            rateOfChange = roc,
                            flag = "Performance Drop Detected"
                        )
                    )
                }
                sorted.takeLast(3).all { it.second == previous } -> {
                    anomalies.add(
                        ProgressionAnomaly(
                            exerciseName = exerciseName,
                            currentE1RM = latest,
                            previousE1RM = previous,
                            rateOfChange = 0.0,
                            flag = "Plateau Detected"
                        )
                    )
                }
            }
        }

        return anomalies
    }

    private fun analyzeHealthPerformanceCorrelation(
        lastFourWeeksWorkouts: List<Workout>,
        healthStats: List<HealthStats>
    ): HealthPerformanceCorrelation? {
        if (lastFourWeeksWorkouts.size < 3 || healthStats.size < 3) return null

        // Match workouts with health data
        val matchedData = mutableListOf<Pair<Double, Double>>()

        lastFourWeeksWorkouts.forEach { workout ->
            val closestHealthStat = healthStats
                .minByOrNull { kotlin.math.abs(it.date - workout.timestamp) }

            if (closestHealthStat?.sleepDurationMinutes != null) {
                val sleepHours = closestHealthStat.sleepDurationMinutes / 60.0
                val intensity = workout.totalVolume / workout.durationSeconds.toDouble()
                matchedData.add(sleepHours to intensity)
            }
        }

        if (matchedData.size < 3) return null

        val sleepData = matchedData.map { it.first }
        val intensityData = matchedData.map { it.second }

        val r = StatisticalEngine.pearsonCorrelation(sleepData, intensityData)

        val interpretation = when {
            kotlin.math.abs(r) > 0.7 -> "Strong"
            kotlin.math.abs(r) > 0.5 -> "Moderate"
            kotlin.math.abs(r) > 0.3 -> "Weak"
            else -> "Decoupled"
        }

        val recommendation = when {
            r < 0.3 -> "Performance is decoupled from sleep. Coach Carter suggests investigating external stressors or over-reaching."
            r < 0 -> "Negative correlation detected. Poor sleep may be causing increased training intensity (ego lifting)."
            else -> "Healthy correlation between sleep and performance. Continue current recovery protocol."
        }

        return HealthPerformanceCorrelation(r, interpretation, recommendation)
    }

    private fun getHealthContext(timestamp: Long, healthStats: List<HealthStats>): String {
        val closestHealthStat = healthStats
            .minByOrNull { kotlin.math.abs(it.date - timestamp) }
            ?: return "No health data available for context"

        val sleepHours = (closestHealthStat.sleepDurationMinutes ?: 0) / 60.0
        val hrv = closestHealthStat.heartRateVariability

        return when {
            sleepHours < 6 -> "Valid Fatigue - Poor sleep (${"%.1f".format(sleepHours)}h) detected"
            hrv != null && hrv < 40 -> "Valid Fatigue - Low HRV (${"%.1f".format(hrv)}ms) detected"
            else -> "Performance Drop - Health metrics normal, potential injury concern"
        }
    }

    private fun generateSummary(
        volumeAnomalies: List<VolumeLoadAnomaly>,
        progressionAnomalies: List<ProgressionAnomaly>,
        healthCorrelation: HealthPerformanceCorrelation?
    ): String {
        val parts = mutableListOf<String>()

        if (volumeAnomalies.isNotEmpty()) {
            val positive = volumeAnomalies.count { it.type == "Positive Outlier" }
            val negative = volumeAnomalies.count { it.type == "Negative Outlier" }
            if (positive > 0) parts.add("$positive peak performance session(s)")
            if (negative > 0) parts.add("$negative underperformance session(s)")
        }

        if (progressionAnomalies.isNotEmpty()) {
            val rapid = progressionAnomalies.count { it.flag.contains("Rapid") }
            val plateau = progressionAnomalies.count { it.flag.contains("Plateau") }
            if (rapid > 0) parts.add("$rapid exercise(s) with rapid progression")
            if (plateau > 0) parts.add("$plateau exercise(s) plateaued")
        }

        if (healthCorrelation != null && healthCorrelation.interpretation == "Decoupled") {
            parts.add("sleep-performance decoupling detected")
        }

        return if (parts.isEmpty()) {
            "All metrics within normal range. Training progressing as expected."
        } else {
            "Detected: ${parts.joinToString(", ")}."
        }
    }

    private fun generateRecommendations(
        volumeAnomalies: List<VolumeLoadAnomaly>,
        progressionAnomalies: List<ProgressionAnomaly>,
        healthCorrelation: HealthPerformanceCorrelation?
    ): List<String> {
        val recommendations = mutableListOf<String>()

        volumeAnomalies.forEach { anomaly ->
            when (anomaly.type) {
                "Positive Outlier" -> {
                    recommendations.add("Boris: Increase baseline weight by 2.5kg for next session based on peak performance")
                }
                "Negative Outlier" -> {
                    if (anomaly.healthContext?.contains("Valid Fatigue") == true) {
                        recommendations.add("Coach Carter: Reduce RPE by 1-2 points due to poor recovery metrics")
                    } else {
                        recommendations.add("Noa: Monitor for injury - performance drop without clear fatigue markers")
                    }
                }
            }
        }

        progressionAnomalies.forEach { anomaly ->
            when {
                anomaly.flag.contains("Rapid") -> {
                    recommendations.add("Noa: Verify technique on ${anomaly.exerciseName} - ${(anomaly.rateOfChange * 100).toInt()}% gain suggests possible form compromise")
                }
                anomaly.flag.contains("Plateau") -> {
                    recommendations.add("Boris: Implement top-set strategy or increase volume on ${anomaly.exerciseName}")
                }
            }
        }

        if (healthCorrelation?.interpretation == "Decoupled") {
            recommendations.add("Coach Carter: ${healthCorrelation.recommendation}")
        }

        return recommendations
    }
}
