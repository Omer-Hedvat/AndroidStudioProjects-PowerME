package com.powerme.app.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powerme.app.analytics.StatisticalEngine
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.UnitSystem
import com.powerme.app.data.sync.FirestoreSyncManager
import com.powerme.app.util.SurgicalValidator
import com.powerme.app.util.UnitConverter
import com.powerme.app.data.database.PowerMeDatabase
import com.powerme.app.data.database.Workout
import com.powerme.app.data.database.WorkoutDao
import com.powerme.app.data.database.WorkoutSetDao
import com.powerme.app.data.database.WorkoutSetWithExercise
import dagger.hilt.android.lifecycle.HiltViewModel
import androidx.room.withTransaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetDisplayRow(
    val id: String,
    val setOrder: Int,
    val setType: com.powerme.app.data.database.SetType,
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
    val exerciseType: com.powerme.app.data.database.ExerciseType,
    val sets: List<SetDisplayRow>
)

data class PendingEdit(val weight: String, val reps: String)

data class WorkoutDetailUiState(
    val workout: Workout? = null,
    val exerciseGroups: List<ExerciseGroup> = emptyList(),
    val expandedExerciseIds: Set<Long> = emptySet(),
    val isLoading: Boolean = true,
    val pendingEdits: Map<String, PendingEdit> = emptyMap(),
    val isSaving: Boolean = false,
    val savedSuccessfully: Boolean = false
)

@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutDao: WorkoutDao,
    private val workoutSetDao: WorkoutSetDao,
    private val database: PowerMeDatabase,
    private val firestoreSyncManager: FirestoreSyncManager,
    private val appSettingsDataStore: AppSettingsDataStore
) : ViewModel() {

    private val workoutId: String = checkNotNull(savedStateHandle["workoutId"])

    private val _uiState = MutableStateFlow(WorkoutDetailUiState())
    val uiState: StateFlow<WorkoutDetailUiState> = _uiState.asStateFlow()

    val unitSystem: StateFlow<UnitSystem> = appSettingsDataStore.unitSystem
        .stateIn(viewModelScope, SharingStarted.Eagerly, UnitSystem.METRIC)

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val workout = workoutDao.getWorkoutById(workoutId)
            val sets = workoutSetDao.getSetsWithExerciseForWorkout(workoutId)
            val groups = buildGroups(sets)
            val initialEdits = groups.flatMap { it.sets }.associate { set ->
                val weightStr = UnitConverter.formatWeightRaw(set.weight, unitSystem.value)
                set.id to PendingEdit(weight = weightStr, reps = set.reps.toString())
            }
            _uiState.value = WorkoutDetailUiState(
                workout = workout,
                exerciseGroups = groups,
                expandedExerciseIds = groups.map { it.exerciseId }.toSet(),
                isLoading = false,
                pendingEdits = initialEdits
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

    fun hasUnsavedChanges(): Boolean {
        val state = _uiState.value
        if (state.pendingEdits.isEmpty()) return false
        val originalSets = state.exerciseGroups.flatMap { it.sets }.associateBy { it.id }
        return state.pendingEdits.any { (setId, edit) ->
            val original = originalSets[setId] ?: return@any false
            val origWeight = UnitConverter.formatWeightRaw(original.weight, unitSystem.value)
            edit.weight != origWeight || edit.reps != original.reps.toString()
        }
    }

    fun updatePendingWeight(setId: String, weight: String) =
        updatePendingField(setId) { it.copy(weight = weight) }

    fun updatePendingReps(setId: String, reps: String) =
        updatePendingField(setId) { it.copy(reps = reps) }

    private fun updatePendingField(setId: String, transform: (PendingEdit) -> PendingEdit) {
        val edits = _uiState.value.pendingEdits.toMutableMap()
        edits[setId] = transform(edits[setId] ?: PendingEdit("", ""))
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
            val workout = workoutDao.getWorkoutById(workoutId)
            if (workout != null) {
                workoutDao.updateWorkout(workout.copy(updatedAt = System.currentTimeMillis()))
                firestoreSyncManager.pushWorkout(workoutId)
            }
            _uiState.value = _uiState.value.copy(isSaving = false, savedSuccessfully = true)
        }
    }

    fun deleteSession(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val workout = workoutDao.getWorkoutById(workoutId) ?: run { onDeleted(); return@launch }
            val now = System.currentTimeMillis()
            workoutDao.updateWorkout(workout.copy(isArchived = true, updatedAt = now))
            firestoreSyncManager.pushWorkout(workoutId)
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
