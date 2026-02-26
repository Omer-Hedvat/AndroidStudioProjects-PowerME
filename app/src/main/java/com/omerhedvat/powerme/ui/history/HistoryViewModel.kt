package com.omerhedvat.powerme.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omerhedvat.powerme.data.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class WorkoutWithExerciseSummary(
    val id: Long,
    val routineId: Long?,
    val timestamp: Long,
    val durationSeconds: Int,
    val totalVolume: Double,
    val notes: String?,
    val exerciseNames: List<String>
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    workoutRepository: WorkoutRepository
) : ViewModel() {

    val workouts: StateFlow<List<WorkoutWithExerciseSummary>> =
        workoutRepository.getAllCompletedWorkoutsWithExerciseNames()
            .map { rows -> collapseRows(rows) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    private fun collapseRows(rows: List<com.omerhedvat.powerme.data.database.WorkoutExerciseNameRow>): List<WorkoutWithExerciseSummary> {
        return rows
            .groupBy { it.id }
            .map { (_, group) ->
                val first = group.first()
                val names = group.mapNotNull { it.exerciseName }.distinct().take(4)
                WorkoutWithExerciseSummary(
                    id = first.id,
                    routineId = first.routineId,
                    timestamp = first.timestamp,
                    durationSeconds = first.durationSeconds,
                    totalVolume = first.totalVolume,
                    notes = first.notes,
                    exerciseNames = names
                )
            }
            .sortedByDescending { it.timestamp }
    }
}
