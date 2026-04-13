package com.powerme.app.ui.workouts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.powerme.app.data.database.PowerMeDatabase
import com.powerme.app.data.database.Routine
import com.powerme.app.data.database.RoutineDao
import com.powerme.app.data.database.RoutineExercise
import com.powerme.app.data.database.RoutineExerciseDao
import com.powerme.app.data.repository.ExerciseRepository
import com.powerme.app.data.sync.FirestoreSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

data class DraftExercise(
    val routineExerciseId: Long = 0,
    val exerciseId: Long,
    val exerciseName: String,
    val muscleGroup: String,
    val sets: Int = 3,
    val order: Int,
    val supersetGroupId: String? = null
)

@HiltViewModel
class TemplateBuilderViewModel @Inject constructor(
    private val routineDao: RoutineDao,
    private val routineExerciseDao: RoutineExerciseDao,
    private val exerciseRepository: ExerciseRepository,
    private val database: PowerMeDatabase,
    private val firestoreSyncManager: FirestoreSyncManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val routineId: String = savedStateHandle.get<String>("routineId") ?: "new"

    private val _routineName = MutableStateFlow("")
    val routineName: StateFlow<String> = _routineName.asStateFlow()

    private val _draftExercises = MutableStateFlow<List<DraftExercise>>(emptyList())
    val draftExercises: StateFlow<List<DraftExercise>> = _draftExercises.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _isOrganizeMode = MutableStateFlow(false)
    val isOrganizeMode: StateFlow<Boolean> = _isOrganizeMode.asStateFlow()

    private val _selectedExerciseIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedExerciseIds: StateFlow<Set<Long>> = _selectedExerciseIds.asStateFlow()

    init {
        if (routineId != "new" && routineId.isNotBlank()) {
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
                        order = i,
                        supersetGroupId = ex.supersetGroupId
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
        val updated = _draftExercises.value
            .filter { it.exerciseId != exerciseId }
            .mapIndexed { i, d -> d.copy(order = i) }
        // Dissolve any superset that now has only 1 member
        val groupCounts = updated.groupBy { it.supersetGroupId }.filterKeys { it != null }
        val soloGroups = groupCounts.filterValues { it.size == 1 }.keys
        _draftExercises.value = if (soloGroups.isEmpty()) updated
            else updated.map { if (it.supersetGroupId in soloGroups) it.copy(supersetGroupId = null) else it }
        // Remove from selection if present
        _selectedExerciseIds.value = _selectedExerciseIds.value - exerciseId
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

    fun enterOrganizeMode() {
        _selectedExerciseIds.value = emptySet()
        _isOrganizeMode.value = true
    }

    fun exitOrganizeMode() {
        _isOrganizeMode.value = false
        _selectedExerciseIds.value = emptySet()
    }

    fun toggleExerciseSelection(exerciseId: Long) {
        val current = _selectedExerciseIds.value
        _selectedExerciseIds.value = if (exerciseId in current) current - exerciseId else current + exerciseId
    }

    fun commitSupersetGroup() {
        val selected = _selectedExerciseIds.value
        if (selected.size < 2) return
        val groupId = UUID.randomUUID().toString()
        _draftExercises.value = _draftExercises.value.map { d ->
            if (d.exerciseId in selected) d.copy(supersetGroupId = groupId) else d
        }
        _selectedExerciseIds.value = emptySet()
        // Stay in organize mode for further grouping
    }

    fun save(onDone: () -> Unit) {
        if (_routineName.value.isBlank()) return
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            _isOrganizeMode.value = false
            _selectedExerciseIds.value = emptySet()
            try {
                val drafts = _draftExercises.value
                val rid: String = database.withTransaction {
                    if (routineId != "new" && routineId.isNotBlank()) {
                        val existing = routineDao.getRoutineById(routineId)
                        if (existing != null) {
                            routineDao.updateRoutine(
                                existing.copy(name = _routineName.value.trim(), updatedAt = System.currentTimeMillis())
                            )
                        }
                        routineId
                    } else {
                        val newId = UUID.randomUUID().toString()
                        val now = System.currentTimeMillis()
                        routineDao.insertRoutine(
                            Routine(id = newId, name = _routineName.value.trim(), isCustom = true, updatedAt = now)
                        )
                        newId
                    }.also { r ->
                        routineExerciseDao.deleteAllForRoutine(r)
                        routineExerciseDao.insertAll(
                            drafts.mapIndexed { i, d ->
                                RoutineExercise(
                                    id = UUID.randomUUID().toString(),
                                    routineId = r,
                                    exerciseId = d.exerciseId,
                                    sets = d.sets,
                                    reps = 10,
                                    restTime = 90,
                                    order = i,
                                    supersetGroupId = d.supersetGroupId
                                )
                            }
                        )
                    }
                }
                firestoreSyncManager.pushRoutine(rid)
                withContext(Dispatchers.Main) { onDone() }
            } finally {
                _isSaving.value = false
            }
        }
    }
}
