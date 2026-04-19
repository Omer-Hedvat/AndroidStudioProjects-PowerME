package com.powerme.app.ui.exercises.detail

import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.ExerciseWorkoutHistoryRow
import com.powerme.app.data.database.Joint
import com.powerme.app.ui.metrics.TrendsTimeRange

// ── UI State ────────────────────────────────────────────────────────────────

data class ExerciseDetailUiState(
    val exercise: Exercise? = null,
    val affectedJoints: Set<Joint> = emptySet(),
    val lastPerformed: LastPerformedSummary? = null,
    val sessionCount: Int = 0,
    val personalRecords: PersonalRecords? = null,
    val overloadSuggestion: OverloadSuggestion = OverloadSuggestion.NoData,
    val warmUpRamp: List<WarmUpSet> = emptyList(),
    val stressColors: Map<String, Float> = emptyMap(), // bodyRegion name → stress coefficient
    val alternatives: List<AlternativeExercise> = emptyList(),
    val workoutHistory: List<ExerciseWorkoutHistoryRow> = emptyList(),
    val hasMoreHistory: Boolean = false,
    val trendData: ExerciseTrendData? = null,
    val timeRange: TrendsTimeRange = TrendsTimeRange.THREE_MONTHS,
    val userBodyWeightKg: Double? = null, // for relative strength display
    val isLoading: Boolean = true
)

// ── Domain models ───────────────────────────────────────────────────────────

data class LastPerformedSummary(
    val timestampMs: Long,
    val setCount: Int,
    val totalVolume: Double
)

data class PersonalRecords(
    /** Heaviest weight × reps combo (highest Epley e1RM) */
    val bestE1RM: Double?,
    val bestE1RMTimestampMs: Long?,
    /** Single set with the highest weight × reps product */
    val bestSetWeight: Double?,
    val bestSetReps: Int?,
    val bestSetTimestampMs: Long?,
    /** Session with the highest total volume */
    val bestSessionVolume: Double?,
    val bestSessionTimestampMs: Long?,
    /** Session with the most total reps logged */
    val bestTotalReps: Int?,
    val bestTotalRepsTimestampMs: Long?
)

sealed class OverloadSuggestion {
    object NoData : OverloadSuggestion()
    data class IncreaseReps(
        val currentWeight: Double,
        val currentReps: Int,
        val targetReps: Int,
        val targetSets: Int
    ) : OverloadSuggestion()
    data class IncreaseWeight(
        val currentWeight: Double,
        val suggestedWeight: Double,
        val targetReps: Int,
        val targetSets: Int
    ) : OverloadSuggestion()
}

data class WarmUpSet(
    val weight: Double,
    val reps: Int,
    val percentageLabel: String // e.g. "50%"
)

data class AlternativeExercise(
    val exercise: Exercise,
    val score: Int,
    val estimatedStartingWeight: Double? = null // null = user already has history
)

// ── Trend data ──────────────────────────────────────────────────────────────

data class ExerciseTrendData(
    val e1rmPoints: List<TrendPoint>,
    val maxWeightPoints: List<TrendPoint>,
    val volumePoints: List<TrendPoint>,
    val bestSetPoints: List<TrendPoint>,
    val rpePoints: List<TrendPoint>
)

data class TrendPoint(
    val timestampMs: Long,
    val value: Double
)
