package com.powerme.app.ui.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powerme.app.data.database.Routine
import com.powerme.app.data.database.RoutineBlock
import com.powerme.app.data.database.RoutineBlockDao
import com.powerme.app.data.database.RoutineDao
import com.powerme.app.data.database.RoutineExerciseDao
import com.powerme.app.data.database.RoutineExerciseNameRow
import com.powerme.app.data.database.RoutineExerciseWithName
import com.powerme.app.data.repository.RoutineRepository
import com.powerme.app.data.sync.FirestoreSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class RoutineWithSummary(
    val routine: Routine,
    val exerciseNames: List<String>,
    val daysSincePerformed: Int?
)

@HiltViewModel
class WorkoutsViewModel @Inject constructor(
    private val routineDao: RoutineDao,
    private val routineExerciseDao: RoutineExerciseDao,
    private val routineBlockDao: RoutineBlockDao,
    private val routineRepository: RoutineRepository,
    private val firestoreSyncManager: FirestoreSyncManager
) : ViewModel() {

    private val _routineDetails = MutableStateFlow<List<RoutineExerciseWithName>>(emptyList())
    val routineDetails: StateFlow<List<RoutineExerciseWithName>> = _routineDetails.asStateFlow()

    private val _routineBlocks = MutableStateFlow<List<RoutineBlock>>(emptyList())
    val routineBlocks: StateFlow<List<RoutineBlock>> = _routineBlocks.asStateFlow()

    fun loadRoutineDetails(routineId: String) {
        viewModelScope.launch {
            _routineDetails.value = routineExerciseDao.getExercisesWithNamesForRoutine(routineId)
            _routineBlocks.value = routineBlockDao.getBlocksForRoutineOnce(routineId)
        }
    }

    fun clearRoutineDetails() {
        _routineDetails.value = emptyList()
        _routineBlocks.value = emptyList()
    }

    private val _showArchived = MutableStateFlow(false)
    val showArchived: StateFlow<Boolean> = _showArchived.asStateFlow()

    fun toggleShowArchived() {
        _showArchived.value = !_showArchived.value
    }

    val visibleRoutines: StateFlow<List<RoutineWithSummary>> =
        _showArchived
            .flatMapLatest { showArch ->
                if (showArch) routineDao.getAllArchivedRoutinesWithExerciseNames()
                else routineDao.getAllActiveRoutinesWithExerciseNames()
            }
            .map { rows -> collapseRows(rows) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    private fun collapseRows(rows: List<RoutineExerciseNameRow>): List<RoutineWithSummary> {
        val now = System.currentTimeMillis()
        return rows.groupBy { it.id }.map { (_, group) ->
            val first = group.first()
            val routine = Routine(
                id = first.id,
                name = first.name,
                lastPerformed = first.lastPerformed,
                isCustom = first.isCustom,
                isArchived = first.isArchived,
                updatedAt = 0L
            )
            val exerciseNames = group.mapNotNull { it.exerciseName }
            val daysSince = first.lastPerformed?.let { ts ->
                TimeUnit.MILLISECONDS.toDays(now - ts).toInt()
            }
            RoutineWithSummary(routine, exerciseNames, daysSince)
        }
    }

    fun archiveRoutine(routine: Routine) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            routineDao.updateRoutine(routine.copy(isArchived = true, updatedAt = now))
            firestoreSyncManager.pushRoutine(routine.id)
        }
    }

    fun unarchiveRoutine(routine: Routine) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            routineDao.updateRoutine(routine.copy(isArchived = false, updatedAt = now))
            firestoreSyncManager.pushRoutine(routine.id)
        }
    }

    fun deleteRoutine(routine: Routine) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            routineDao.updateRoutine(routine.copy(isArchived = true, updatedAt = now))
            firestoreSyncManager.pushRoutine(routine.id)
        }
    }

    fun renameRoutine(routine: Routine, newName: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            routineDao.updateRoutine(routine.copy(name = newName, updatedAt = now))
            firestoreSyncManager.pushRoutine(routine.id)
        }
    }

    fun createEmptyRoutine(name: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            routineDao.insertRoutine(Routine(id = id, name = name, isCustom = true, updatedAt = now))
            firestoreSyncManager.pushRoutine(id)
        }
    }

    fun duplicateRoutine(routine: Routine) {
        viewModelScope.launch {
            val newId = routineRepository.duplicateRoutine(routine.id)
            if (newId.isNotBlank()) firestoreSyncManager.pushRoutine(newId)
        }
    }

    fun createExpressRoutine(routine: Routine) {
        viewModelScope.launch {
            val newId = routineRepository.createExpressRoutine(routine.id)
            if (newId.isNotBlank()) firestoreSyncManager.pushRoutine(newId)
        }
    }
}
