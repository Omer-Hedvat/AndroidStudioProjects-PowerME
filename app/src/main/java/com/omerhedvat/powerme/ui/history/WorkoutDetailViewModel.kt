package com.omerhedvat.powerme.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omerhedvat.powerme.analytics.StatisticalEngine
import com.omerhedvat.powerme.util.SurgicalValidator
import com.omerhedvat.powerme.data.database.PowerMeDatabase
import com.omerhedvat.powerme.data.database.Workout
import com.omerhedvat.powerme.data.database.WorkoutDao
import com.omerhedvat.powerme.data.database.WorkoutSetDao
import com.omerhedvat.powerme.data.database.WorkoutSetWithExercise
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.room.withTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetDisplayRow(
    val id: Long,
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

data class PendingEdit(val weight: String, val reps: String)

data class WorkoutDetailUiState(
    val workout: Workout? = null,
    val exerciseGroups: List<ExerciseGroup> = emptyList(),
    val expandedExerciseIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val isEditMode: Boolean = false,
    val pendingEdits: Map<Long, PendingEdit> = emptyMap(),
    val isSaving: Boolean = false
)

@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutDao: WorkoutDao,
    private val workoutSetDao: WorkoutSetDao,
    private val database: PowerMeDatabase
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
                expandedExerciseIds = groups.map { it.exerciseId }.toSet(),
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

    fun startEditMode() {
        val initialEdits = _uiState.value.exerciseGroups
            .flatMap { it.sets }
            .associate { set ->
                val weightStr = if (set.weight == set.weight.toLong().toDouble())
                    set.weight.toLong().toString() else "%.1f".format(set.weight)
                set.id to PendingEdit(weight = weightStr, reps = set.reps.toString())
            }
        _uiState.value = _uiState.value.copy(isEditMode = true, pendingEdits = initialEdits)
    }

    fun cancelEditMode() {
        _uiState.value = _uiState.value.copy(isEditMode = false, pendingEdits = emptyMap())
    }

    fun updatePendingWeight(setId: Long, weight: String) {
        val edits = _uiState.value.pendingEdits.toMutableMap()
        edits[setId] = (edits[setId] ?: PendingEdit("", "")).copy(weight = weight)
        _uiState.value = _uiState.value.copy(pendingEdits = edits)
    }

    fun updatePendingReps(setId: Long, reps: String) {
        val edits = _uiState.value.pendingEdits.toMutableMap()
        edits[setId] = (edits[setId] ?: PendingEdit("", "")).copy(reps = reps)
        _uiState.value = _uiState.value.copy(pendingEdits = edits)
    }

    fun saveEdits() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            database.withTransaction {
                _uiState.value.pendingEdits.forEach { (setId, edit) ->
                    val weight = when (val r = SurgicalValidator.parseDecimal(edit.weight)) {
                        is SurgicalValidator.ValidationResult.Valid -> r.value
                        else -> return@forEach
                    }
                    val reps = when (val r = SurgicalValidator.parseReps(edit.reps)) {
                        is SurgicalValidator.ValidationResult.Valid -> r.value.toInt()
                        else -> return@forEach
                    }
                    workoutSetDao.updateWeightReps(setId, weight, reps)
                }
            }
            load()
            _uiState.value = _uiState.value.copy(isEditMode = false, pendingEdits = emptyMap(), isSaving = false)
        }
    }

    fun deleteSession(onDeleted: () -> Unit) {
        viewModelScope.launch {
            workoutDao.deleteWorkoutById(workoutId)
            onDeleted()
        }
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
                            id = ws.id,
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
