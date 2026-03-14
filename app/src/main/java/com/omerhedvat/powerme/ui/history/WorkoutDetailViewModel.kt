package com.omerhedvat.powerme.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omerhedvat.powerme.analytics.StatisticalEngine
import com.omerhedvat.powerme.data.database.Workout
import com.omerhedvat.powerme.data.database.WorkoutDao
import com.omerhedvat.powerme.data.database.WorkoutSetDao
import com.omerhedvat.powerme.data.database.WorkoutSetWithExercise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetDisplayRow(
    val setOrder: Int,
    val setType: com.omerhedvat.powerme.data.database.SetType,
    val weight: Double,
    val reps: Int,
    val rpe: Int?,
    val e1RM: Double,
    val setNotes: String?,
    val supersetGroupId: String?,
    val distance: Double?,
    val timeSeconds: Int?
)

data class ExerciseGroup(
    val exerciseId: Long,
    val exerciseName: String,
    val muscleGroup: String?,
    val exerciseType: com.omerhedvat.powerme.data.database.ExerciseType,
    val sets: List<SetDisplayRow>
)

data class WorkoutDetailUiState(
    val workout: Workout? = null,
    val exerciseGroups: List<ExerciseGroup> = emptyList(),
    val expandedExerciseIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true
)

@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutDao: WorkoutDao,
    private val workoutSetDao: WorkoutSetDao
) : ViewModel() {

    private val workoutId: Long = checkNotNull(savedStateHandle["workoutId"])

    private val _uiState = MutableStateFlow(WorkoutDetailUiState())
    val uiState: StateFlow<WorkoutDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val workout = workoutDao.getWorkoutById(workoutId)
            val sets = workoutSetDao.getSetsWithExerciseForWorkout(workoutId)
            val groups = buildGroups(sets)
            _uiState.value = WorkoutDetailUiState(
                workout = workout,
                exerciseGroups = groups,
                isLoading = false
            )
        }
    }

    fun toggleExerciseExpansion(exerciseId: Long) {
        val currentExpanded = _uiState.value.expandedExerciseIds
        val newExpanded = if (currentExpanded.contains(exerciseId)) {
            currentExpanded - exerciseId
        } else {
            currentExpanded + exerciseId
        }
        _uiState.value = _uiState.value.copy(expandedExerciseIds = newExpanded)
    }

    private fun buildGroups(sets: List<WorkoutSetWithExercise>): List<ExerciseGroup> {
        return sets
            .groupBy { it.exerciseId }
            .entries
            .sortedBy { (_, exSets) -> exSets.minOf { it.setOrder } }
            .map { (exerciseId, exSets) ->
                val first = exSets.first()
                ExerciseGroup(
                    exerciseId = exerciseId,
                    exerciseName = first.exerciseName,
                    muscleGroup = first.muscleGroup,
                    exerciseType = first.exerciseType,
                    sets = exSets.sortedBy { it.setOrder }.map { ws ->
                        SetDisplayRow(
                            setOrder = ws.setOrder,
                            setType = ws.setType,
                            weight = ws.weight,
                            reps = ws.reps,
                            rpe = ws.rpe,
                            e1RM = StatisticalEngine.calculate1RM(ws.weight, ws.reps),
                            setNotes = ws.setNotes,
                            supersetGroupId = ws.supersetGroupId,
                            distance = ws.distance,
                            timeSeconds = ws.timeSeconds
                        )
                    }
                )
            }
    }
}
