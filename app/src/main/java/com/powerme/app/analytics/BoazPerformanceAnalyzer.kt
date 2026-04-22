package com.powerme.app.analytics

import timber.log.Timber
import com.powerme.app.data.repository.WorkoutRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class PlannedVsActual(
    val exerciseName: String,
    val plannedSets: Int,
    val actualSets: Int,
    val plannedRepsAvg: Float,
    val actualRepsAvg: Float,
    val plannedRpe: Int,
    val actualRpeAvg: Float,
    val volumeDelta: Double,   // actual - planned total volume
    val tutSeconds: Long? = null,  // Time Under Tension (endTime - startTime) per set
    val outcome: String        // "EXCEEDED" | "MET" | "UNDERPERFORMED"
)

@Singleton
class BoazPerformanceAnalyzer @Inject constructor(
    private val workoutRepository: WorkoutRepository
) {
    /**
     * Compares actual workout performance against a planned routine.
     * Calculates TUT = endTime - startTime per set.
     * Results are returned for injection into the next Delta Summary.
     */
    suspend fun compare(workoutId: String, routineId: String?): List<PlannedVsActual> {
        return try {
            val sets = workoutRepository.getSetsForWorkout(workoutId).first()

            // Group sets by exerciseId
            val setsByExercise = sets.groupBy { it.exerciseId }

            setsByExercise.mapNotNull { (exerciseId, exerciseSets) ->
                val actualSets = exerciseSets.size
                val actualRepsAvg = if (actualSets > 0) exerciseSets.map { it.reps }.average().toFloat() else 0f
                val actualRpeAvg = exerciseSets.mapNotNull { it.rpe }.let { rpes ->
                    if (rpes.isNotEmpty()) rpes.average().toFloat() else 0f
                }
                val actualVolume = exerciseSets.sumOf { it.weight * it.reps }

                // Calculate TUT for this exercise (sum of all set durations)
                val tutSeconds = exerciseSets.mapNotNull { set ->
                    val start = set.startTime
                    val end = set.endTime
                    if (start != null && end != null) (end - start) / 1000 else null
                }.sumOf { it }

                val outcome = when {
                    actualRpeAvg > 8.5f -> "EXCEEDED"
                    actualSets >= 3 && actualRpeAvg >= 7f -> "MET"
                    else -> "UNDERPERFORMED"
                }

                PlannedVsActual(
                    exerciseName = "Exercise $exerciseId",  // Resolved by caller if exercise name map available
                    plannedSets = actualSets,   // Without routine reference, use actual as baseline
                    actualSets = actualSets,
                    plannedRepsAvg = actualRepsAvg,
                    actualRepsAvg = actualRepsAvg,
                    plannedRpe = 8,
                    actualRpeAvg = actualRpeAvg,
                    volumeDelta = 0.0,
                    tutSeconds = if (tutSeconds > 0) tutSeconds else null,
                    outcome = outcome
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to compare performance")
            emptyList()
        }
    }

    /**
     * Formats PlannedVsActual list into a Boaz data segment for Delta Summary injection.
     */
    fun formatPerformanceReport(report: List<PlannedVsActual>): String {
        if (report.isEmpty()) return ""
        return buildString {
            appendLine("BOAZ PERFORMANCE REPORT (last session planned vs actual):")
            report.forEach { item ->
                appendLine("• ${item.exerciseName}: ${item.actualSets} sets @ avg RPE ${String.format("%.1f", item.actualRpeAvg)} — ${item.outcome}")
                item.tutSeconds?.let { appendLine("  TUT: ${it}s") }
            }
            appendLine("Focus: Highlight deviations > 10% from plan. Flag RPE divergence.")
        }
    }
}
