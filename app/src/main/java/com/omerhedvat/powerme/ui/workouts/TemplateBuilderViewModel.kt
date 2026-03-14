package com.omerhedvat.powerme.ui.workouts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.omerhedvat.powerme.data.database.PowerMeDatabase
import com.omerhedvat.powerme.data.database.Routine
import com.omerhedvat.powerme.data.database.RoutineDao
import com.omerhedvat.powerme.data.database.RoutineExercise
import com.omerhedvat.powerme.data.database.RoutineExerciseDao
import com.omerhedvat.powerme.data.repository.ExerciseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DraftExercise(
    val routineExerciseId: Long = 0,
    val exerciseId: Long,
    val exerciseName: String,
    val muscleGroup: String,
    val sets: Int = 3,
    val order: Int
)

@HiltViewModel
class TemplateBuilderViewModel @Inject constructor(
    private val routineDao: RoutineDao,
    private val routineExerciseDao: RoutineExerciseDao,
    private val exerciseRepository: ExerciseRepository,
    private val database: PowerMeDatabase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val routineId: Long = savedStateHandle.get<Long>("routineId") ?: -1L

    private val _routineName = MutableStateFlow("")
    val routineName: StateFlow<String> = _routineName.asStateFlow()

    private val _draftExercises = MutableStateFlow<List<DraftExercise>>(emptyList())
    val draftExercises: StateFlow<List<DraftExercise>> = _draftExercises.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    init {
        if (routineId > 0L) {
            viewModelScope.launch {
                val routine = routineDao.getRoutineById(routineId)
                if (routine != null) {
                    _routineName.value = routine.name
                }
                val exercises = routineExerciseDao.getExercisesWithNamesForRoutine(routineId)
                _draftExercises.value = exercises.mapIndexed { i, ex ->
                    DraftExercise(
                        exerciseId = ex.exerciseId,
                        exerciseName = ex.exerciseName,
                        muscleGroup = ex.muscleGroup,
                        sets = ex.sets,
                        order = i
                    )
                }
            }
        }
    }

    fun onNameChanged(name: String) {
        _routineName.value = name
    }

    fun addExercises(ids: List<Long>) {
        viewModelScope.launch {
            val existingIds = _draftExercises.value.map { it.exerciseId }.toSet()
            val newIds = ids.filter { it !in existingIds }
            if (newIds.isEmpty()) return@launch
            val exercises = exerciseRepository.getExercisesByIds(newIds)
            val currentSize = _draftExercises.value.size
            val newDrafts = exercises.mapIndexed { i, ex ->
                DraftExercise(
                    exerciseId = ex.id,
                    exerciseName = ex.name,
                    muscleGroup = ex.muscleGroup,
                    sets = 3,
                    order = currentSize + i
                )
            }
            _draftExercises.value = _draftExercises.value + newDrafts
        }
    }

    fun removeExercise(exerciseId: Long) {
        _draftExercises.value = _draftExercises.value
            .filter { it.exerciseId != exerciseId }
            .mapIndexed { i, d -> d.copy(order = i) }
    }

    fun incrementSets(exerciseId: Long) {
        _draftExercises.value = _draftExercises.value.map { d ->
            if (d.exerciseId == exerciseId) d.copy(sets = d.sets + 1) else d
        }
    }

    fun decrementSets(exerciseId: Long) {
        _draftExercises.value = _draftExercises.value.map { d ->
            if (d.exerciseId == exerciseId) d.copy(sets = maxOf(1, d.sets - 1)) else d
        }
    }

    fun reorderDraftExercise(fromIndex: Int, toIndex: Int) {
        _draftExercises.value = _draftExercises.value.toMutableList().apply {
            val item = removeAt(fromIndex)
            add(toIndex, item)
        }.mapIndexed { idx, draft -> draft.copy(order = idx) }
    }

    fun save(onDone: () -> Unit) {
        if (_routineName.value.isBlank()) return
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val drafts = _draftExercises.value
                database.withTransaction {
                    val rid: Long
                    if (routineId > 0L) {
                        val existing = routineDao.getRoutineById(routineId)
                        if (existing != null) {
                            routineDao.updateRoutine(existing.copy(name = _routineName.value.trim()))
                        }
                        rid = routineId
                    } else {
                        rid = routineDao.insertRoutine(
                            Routine(name = _routineName.value.trim(), isCustom = true)
                        )
                    }
                    routineExerciseDao.deleteAllForRoutine(rid)
                    routineExerciseDao.insertAll(
                        drafts.mapIndexed { i, d ->
                            RoutineExercise(
                                routineId = rid,
                                exerciseId = d.exerciseId,
                                sets = d.sets,
                                reps = 10,
                                restTime = 90,
                                order = i
                            )
                        }
                    )
                }
                withContext(Dispatchers.Main) { onDone() }
            } finally {
                _isSaving.value = false
            }
        }
    }
}
