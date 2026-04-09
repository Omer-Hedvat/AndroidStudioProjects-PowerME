package com.powerme.app.analytics

import com.powerme.app.data.database.HealthStats
import com.powerme.app.data.database.Workout
import com.powerme.app.data.database.WorkoutSet
import kotlinx.serialization.Serializable
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@Serializable
data class VolumeLoadAnomaly(
    val workoutId: String,
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
        allWorkoutSets: Map<String, List<WorkoutSet>>,
        exerciseNames: Map<Long, String>,
        healthStats: List<HealthStats>,
        bayesian1RMs: Map<Long, Double>
    ): WeeklyInsights {
        val now = System.currentTimeMillis()
        val weekAgo = now - TimeUnit.DAYS.toMillis(7)
        val fourWeeksAgo = now - TimeUnit.DAYS.toMillis(28)

        val lastWeekWorkouts = allWorkouts.filter { it.timestamp >= weekAgo }
        val lastFourWeeksWorkouts = allWorkouts.filter { it.timestamp >= fourWeeksAgo }

        val volumeAnomalies = analyzeVolumeLoadAnomalies(
            lastWeekWorkouts,
            lastFourWeeksWorkouts,
            allWorkoutSets,
            healthStats
        )

        val progressionAnomalies = analyzeProgressionAnomalies(
            lastWeekWorkouts,
            lastFourWeeksWorkouts,
            allWorkoutSets,
            exerciseNames,
            bayesian1RMs
        )

        val healthCorrelation = analyzeHealthPerformanceCorrelation(
            lastFourWeeksWorkouts,
            healthStats
        )

        val status = if (volumeAnomalies.any { it.type != "Normal" } ||
            progressionAnomalies.any { it.flag != "Normal" }) {
            "Anomalous"
        } else {
            "Stable"
        }

        val summary = generateSummary(volumeAnomalies, progressionAnomalies, healthCorrelation)
        val recommendations = generateRecommendations(volumeAnomalies, progressionAnomalies, healthCorrelation)

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
        allWorkoutSets: Map<String, List<WorkoutSet>>,
        healthStats: List<HealthStats>
    ): List<VolumeLoadAnomaly> {
        // Compute volume load (weight × reps) for every workout in the 4-week baseline
        val volumeLoads = lastFourWeeksWorkouts.map { workout ->
            val sets = allWorkoutSets[workout.id] ?: emptyList()
            val volume = sets.filter { it.weight > 0 && it.reps > 0 }
                .sumOf { it.weight * it.reps }
            workout to volume
        }
        if (volumeLoads.isEmpty()) return emptyList()

        val volumes = volumeLoads.map { it.second }
        val mean = StatisticalEngine.mean(volumes)
        val stdDev = StatisticalEngine.standardDeviation(volumes)

        val lastWeekIds = lastWeekWorkouts.map { it.id }.toSet()
        return volumeLoads.filter { it.first.id in lastWeekIds }.map { (workout, volume) ->
            val z = StatisticalEngine.zScore(volume, mean, stdDev)
            val type = when {
                z > 2.0  -> "Positive Outlier"
                z < -2.0 -> "Negative Outlier"
                else     -> "Normal"
            }
            val healthContext = if (type != "Normal") {
                val workoutDay = workout.timestamp / DAY_MILLIS
                healthStats.firstOrNull { it.date / DAY_MILLIS == workoutDay }
                    ?.let { buildHealthContext(it) }
            } else null

            VolumeLoadAnomaly(
                workoutId = workout.id,
                timestamp = workout.timestamp,
                volumeLoad = volume,
                zScore = z,
                type = type,
                healthContext = healthContext
            )
        }
    }

    private fun buildHealthContext(stats: HealthStats): String? {
        val parts = mutableListOf<String>()
        stats.heartRateVariability?.let { parts.add("HRV: ${it.toInt()} ms") }
        stats.restingHeartRate?.let { parts.add("RHR: $it bpm") }
        stats.sleepDurationMinutes?.let { mins -> parts.add("Sleep: ${mins / 60}h ${mins % 60}m") }
        return if (parts.isEmpty()) null else parts.joinToString(", ")
    }

    private fun analyzeProgressionAnomalies(
        lastWeekWorkouts: List<Workout>,
        lastFourWeeksWorkouts: List<Workout>,
        allWorkoutSets: Map<String, List<WorkoutSet>>,
        exerciseNames: Map<Long, String>,
        bayesian1RMs: Map<Long, Double>
    ): List<ProgressionAnomaly> {
        val anomalies = mutableListOf<ProgressionAnomaly>()
        val compoundLifts = listOf("Bench Press", "Squat", "Deadlift", "Press")

        val lastWeekIds = lastWeekWorkouts.map { it.id }.toSet()
        val historicalWorkoutIds = lastFourWeeksWorkouts
            .filter { it.id !in lastWeekIds }
            .map { it.id }
            .toSet()

        bayesian1RMs.forEach { (exerciseId, latestBayesian1RM) ->
            val exerciseName = exerciseNames[exerciseId] ?: return@forEach
            if (!compoundLifts.any { exerciseName.contains(it, ignoreCase = true) }) return@forEach

            // Historical average e1RM from the 3 weeks before the current week
            val historicalSets = historicalWorkoutIds.flatMap { workoutId ->
                (allWorkoutSets[workoutId] ?: emptyList())
                    .filter { it.exerciseId == exerciseId && it.weight > 0 && it.reps > 0 }
            }
            if (historicalSets.isEmpty()) return@forEach

            val historicalAvg = StatisticalEngine.mean(
                historicalSets.map { StatisticalEngine.calculate1RM(it.weight, it.reps) }
            )
            if (historicalAvg == 0.0) return@forEach

            val rateOfChange = StatisticalEngine.rateOfChange(latestBayesian1RM, historicalAvg)
            val flag = when {
                rateOfChange > 0.15       -> "Rapid Gain - Verify Technique"
                abs(rateOfChange) < 0.02  -> "Plateau Detected"
                else                      -> "Normal"
            }

            anomalies.add(
                ProgressionAnomaly(
                    exerciseName = exerciseName,
                    currentE1RM = latestBayesian1RM,
                    previousE1RM = historicalAvg,
                    rateOfChange = rateOfChange,
                    flag = flag
                )
            )
        }

        return anomalies
    }

    private fun analyzeHealthPerformanceCorrelation(
        lastFourWeeksWorkouts: List<Workout>,
        healthStats: List<HealthStats>
    ): HealthPerformanceCorrelation? {
        if (lastFourWeeksWorkouts.isEmpty() || healthStats.isEmpty()) return null

        // Pair each workout's total volume with the HRV recorded on the same day
        val pairedData = lastFourWeeksWorkouts.mapNotNull { workout ->
            val workoutDay = workout.timestamp / DAY_MILLIS
            healthStats.firstOrNull { it.date / DAY_MILLIS == workoutDay }
                ?.heartRateVariability
                ?.let { hrv -> workout.totalVolume to hrv }
        }
        if (pairedData.size < 3) return null

        val correlation = StatisticalEngine.pearsonCorrelation(
            pairedData.map { it.first },
            pairedData.map { it.second }
        )
        val absCorr = abs(correlation)

        val interpretation = when {
            absCorr >= 0.7 -> "Strong"
            absCorr >= 0.4 -> "Moderate"
            absCorr >= 0.2 -> "Weak"
            else           -> "Decoupled"
        }

        val recommendation = when {
            correlation >=  0.7 -> "Your performance closely tracks your HRV. Prioritize recovery on low-HRV days."
            correlation >=  0.4 -> "HRV moderately predicts your training output. Monitor trends weekly."
            correlation <= -0.4 -> "High training volume may be suppressing your HRV. Consider a deload."
            else                -> "HRV and performance appear independent. Maintain current training balance."
        }

        return HealthPerformanceCorrelation(
            correlationCoefficient = correlation,
            interpretation = interpretation,
            recommendation = recommendation
        )
    }

    private fun generateSummary(
        volumeAnomalies: List<VolumeLoadAnomaly>,
        progressionAnomalies: List<ProgressionAnomaly>,
        healthCorrelation: HealthPerformanceCorrelation?
    ): String {
        val parts = mutableListOf<String>()

        val positiveVolume = volumeAnomalies.count { it.type == "Positive Outlier" }
        val negativeVolume = volumeAnomalies.count { it.type == "Negative Outlier" }
        val rapidGains = progressionAnomalies.count { it.flag == "Rapid Gain - Verify Technique" }
        val plateaus   = progressionAnomalies.count { it.flag == "Plateau Detected" }

        parts.add(
            when {
                positiveVolume > 0 && negativeVolume == 0 ->
                    "Training volume was notably high this week ($positiveVolume spike${if (positiveVolume > 1) "s" else ""} detected)."
                negativeVolume > 0 && positiveVolume == 0 ->
                    "Training volume was below your normal baseline this week."
                positiveVolume > 0 && negativeVolume > 0 ->
                    "Volume was inconsistent this week with both high and low sessions."
                else ->
                    "Training volume was stable this week."
            }
        )

        if (rapidGains > 0)
            parts.add("$rapidGains compound lift${if (rapidGains > 1) "s" else ""} showed unusually rapid strength gains — check form.")
        if (plateaus > 0)
            parts.add("$plateaus exercise${if (plateaus > 1) "s" else ""} appear${if (plateaus == 1) "s" else ""} to be plateauing.")

        healthCorrelation?.let {
            parts.add("Health-performance correlation: ${it.interpretation.lowercase()} (r=${"%.2f".format(it.correlationCoefficient)}).")
        }

        return parts.joinToString(" ")
    }

    private fun generateRecommendations(
        volumeAnomalies: List<VolumeLoadAnomaly>,
        progressionAnomalies: List<ProgressionAnomaly>,
        healthCorrelation: HealthPerformanceCorrelation?
    ): List<String> {
        val recommendations = mutableListOf<String>()

        if (volumeAnomalies.any { it.type == "Positive Outlier" })
            recommendations.add("You had a high-volume session this week. Prioritize sleep and protein intake for recovery.")
        if (volumeAnomalies.any { it.type == "Negative Outlier" })
            recommendations.add("Low volume detected. If planned, great — if not, check for fatigue or scheduling gaps.")

        progressionAnomalies.filter { it.flag == "Rapid Gain - Verify Technique" }.forEach {
            recommendations.add(
                "${it.exerciseName}: e1RM jumped ${"%.0f".format(it.rateOfChange * 100)}% — record a set to verify technique is sound."
            )
        }
        progressionAnomalies.filter { it.flag == "Plateau Detected" }.forEach {
            recommendations.add("${it.exerciseName}: strength has stalled. Consider varying rep ranges, adding volume, or a deload.")
        }

        healthCorrelation?.let { recommendations.add(it.recommendation) }

        if (recommendations.isEmpty())
            recommendations.add("Training looks consistent. Keep up the steady progress!")

        return recommendations
    }

    companion object {
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}
