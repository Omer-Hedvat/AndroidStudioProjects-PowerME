package com.powerme.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powerme.app.data.repository.WorkoutRepository
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
    val id: Long,
    val routineId: Long?,
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
    workoutRepository: WorkoutRepository
) : ViewModel() {

    val groups: StateFlow<List<HistoryGroup>> =
        workoutRepository.getAllCompletedWorkoutsWithExerciseNames()
            .map { rows -> groupByMonth(collapseRows(rows)) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val insightCards: StateFlow<List<InsightCard>> =
        workoutRepository.getAllCompletedWorkoutsWithExerciseNames()
            .map { rows -> computeInsightCards(collapseRows(rows)) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    // Kept for backward-compat with existing tests
    val workouts: StateFlow<List<WorkoutWithExerciseSummary>> =
        workoutRepository.getAllCompletedWorkoutsWithExerciseNames()
            .map { rows -> collapseRows(rows) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

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
                    hasPR = first.hasPR != 0
                )
            }
            .sortedByDescending { it.timestamp }
    }

    private fun computeInsightCards(workouts: List<WorkoutWithExerciseSummary>): List<InsightCard> {
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
            value = "${thisVolume.toInt()} kg",
            delta = volumeDelta,
            subtitle = if (lastVolume > 0) "vs ${lastVolume.toInt()} kg last week" else null
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
                value = "${topSession.totalVolume.toInt()} kg",
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
