package com.powerme.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.UnitSystem
import com.powerme.app.analytics.StatisticalEngine
import com.powerme.app.data.database.PRDetectionRow
import com.powerme.app.data.repository.WorkoutRepository
import com.powerme.app.util.UnitConverter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class WorkoutWithExerciseSummary(
    val id: String,
    val routineId: String?,
    val timestamp: Long,
    val durationSeconds: Int,
    val totalVolume: Double,
    val notes: String?,
    val exerciseNames: List<String>,
    val routineName: String?,
    val setCount: Int,
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 0L,
    val hasPR: Boolean = false
) {
    /** Duration in seconds from precise timestamps, or fallback to durationSeconds. */
    val durationMsComputed: Long get() =
        if (startTimeMs > 0L && endTimeMs > 0L) endTimeMs - startTimeMs
        else durationSeconds * 1000L
}

data class HistoryGroup(
    val label: String,
    val workouts: List<WorkoutWithExerciseSummary>
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    workoutRepository: WorkoutRepository,
    appSettingsDataStore: AppSettingsDataStore
) : ViewModel() {

    val unitSystem: StateFlow<UnitSystem> = appSettingsDataStore.unitSystem
        .stateIn(viewModelScope, SharingStarted.Eagerly, UnitSystem.METRIC)

    /**
     * Shared base flow: single Room subscription for the workout listing query.
     * Eagerly started so data is ready when the user navigates to History and stays warm.
     */
    private val baseWorkouts: StateFlow<List<WorkoutWithExerciseSummary>> =
        workoutRepository.getAllCompletedWorkoutsWithExerciseNames()
            .map { rows -> collapseRows(rows) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Set of workout IDs that contain at least one PR, computed with an O(N) Kotlin scan
     * over a lightweight set projection (workoutId, exerciseId, weight, reps, timestamp).
     * This replaces the former O(N²) correlated SQL subquery.
     */
    private val prWorkoutIds: StateFlow<Set<String>> =
        workoutRepository.getAllCompletedSetsForPRDetection()
            .map { sets -> computePRWorkoutIds(sets) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Workouts with hasPR merged in from the separate PR detection flow. */
    val workouts: StateFlow<List<WorkoutWithExerciseSummary>> =
        combine(baseWorkouts, prWorkoutIds) { ws, prIds ->
            ws.map { it.copy(hasPR = it.id in prIds) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val groups: StateFlow<List<HistoryGroup>> =
        workouts
            .map { ws -> groupByMonth(ws) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val insightCards: StateFlow<List<InsightCard>> =
        combine(workouts, appSettingsDataStore.unitSystem) { ws, unit ->
            computeInsightCards(ws, unit)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun collapseRows(rows: List<com.powerme.app.data.database.WorkoutExerciseNameRow>): List<WorkoutWithExerciseSummary> {
        return rows
            .groupBy { it.id }
            .map { (_, group) ->
                val first = group.first()
                val names = group.mapNotNull { it.exerciseName }.distinct()
                WorkoutWithExerciseSummary(
                    id = first.id,
                    routineId = first.routineId,
                    timestamp = first.timestamp,
                    durationSeconds = first.durationSeconds,
                    totalVolume = first.totalVolume,
                    notes = first.notes,
                    exerciseNames = names,
                    routineName = first.routineName,
                    setCount = first.setCount,
                    startTimeMs = first.startTimeMs,
                    endTimeMs = first.endTimeMs,
                    hasPR = false  // merged in later from prWorkoutIds
                )
            }
            .sortedByDescending { it.timestamp }
    }

    private fun computeInsightCards(workouts: List<WorkoutWithExerciseSummary>, unit: UnitSystem = UnitSystem.METRIC): List<InsightCard> {
        val now = System.currentTimeMillis()
        val weekMs = TimeUnit.DAYS.toMillis(7)
        val thisWeekStart = now - weekMs
        val lastWeekStart = now - 2 * weekMs

        val thisWeek = workouts.filter { it.timestamp >= thisWeekStart }
        val lastWeek = workouts.filter { it.timestamp in lastWeekStart until thisWeekStart }

        // Hidden if no data in last 7 days
        if (thisWeek.isEmpty()) return emptyList()

        val cards = mutableListOf<InsightCard>()

        // Card 1: Weekly volume Δ
        val thisVolume = thisWeek.sumOf { it.totalVolume }
        val lastVolume = lastWeek.sumOf { it.totalVolume }
        val volumeDelta = if (lastVolume > 0) (thisVolume - lastVolume) / lastVolume else null
        cards.add(InsightCard(
            title = "Weekly Volume",
            value = UnitConverter.formatWeight(thisVolume, unit),
            delta = volumeDelta,
            subtitle = if (lastVolume > 0) "vs ${UnitConverter.formatWeight(lastVolume, unit)} last week" else null
        ))

        // Card 2: Workout count Δ
        val thisCount = thisWeek.size
        val lastCount = lastWeek.size
        val countDelta = if (lastCount > 0) (thisCount - lastCount).toDouble() / lastCount else null
        cards.add(InsightCard(
            title = "Sessions",
            value = "$thisCount this week",
            delta = countDelta,
            subtitle = if (lastCount > 0) "$lastCount last week" else null
        ))

        // Card 3: Most-trained exercise
        val exerciseFreq = thisWeek.flatMap { it.exerciseNames }.groupingBy { it }.eachCount()
        val topExercise = exerciseFreq.maxByOrNull { it.value }
        if (topExercise != null) {
            cards.add(InsightCard(
                title = "Most Trained",
                value = topExercise.key,
                subtitle = "${topExercise.value}× this week"
            ))
        }

        // Card 4: Top session volume
        val topSession = thisWeek.maxByOrNull { it.totalVolume }
        if (topSession != null) {
            val label = topSession.routineName ?: "Ad-hoc workout"
            cards.add(InsightCard(
                title = "Best Session",
                value = UnitConverter.formatWeight(topSession.totalVolume, unit),
                subtitle = label
            ))
        }

        return cards
    }

    private fun groupByMonth(workouts: List<WorkoutWithExerciseSummary>): List<HistoryGroup> {
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        return workouts
            .groupBy { monthFormat.format(Date(it.timestamp)) }
            .map { (label, items) -> HistoryGroup(label = label, workouts = items) }
            .sortedByDescending { it.workouts.first().timestamp }
    }
}

/**
 * Single-pass O(N) PR detection. Scans sets in chronological order (oldest first),
 * tracking the best e1RM seen per exercise. A workout is marked as containing a PR
 * when any of its sets establishes a new best for that exercise.
 */
fun computePRWorkoutIds(sets: List<PRDetectionRow>): Set<String> {
    val bestE1RM = mutableMapOf<String, Double>()
    val prWorkoutIds = mutableSetOf<String>()
    for (set in sets) {
        val e1rm = StatisticalEngine.calculate1RM(set.weight, set.reps)
        val best = bestE1RM[set.exerciseId]
        if (best == null || e1rm > best) {
            bestE1RM[set.exerciseId] = e1rm
            prWorkoutIds.add(set.workoutId)
        }
    }
    return prWorkoutIds
}
