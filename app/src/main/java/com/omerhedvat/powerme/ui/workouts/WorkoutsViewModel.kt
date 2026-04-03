package com.omerhedvat.powerme.ui.workouts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omerhedvat.powerme.data.database.Routine
import com.omerhedvat.powerme.data.database.RoutineDao
import com.omerhedvat.powerme.data.database.RoutineExerciseDao
import com.omerhedvat.powerme.data.database.RoutineExerciseNameRow
import com.omerhedvat.powerme.data.database.RoutineExerciseWithName
import com.omerhedvat.powerme.data.repository.RoutineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    private val routineRepository: RoutineRepository
) : ViewModel() {

    private val _routineDetails = MutableStateFlow<List<RoutineExerciseWithName>>(emptyList())
    val routineDetails: StateFlow<List<RoutineExerciseWithName>> = _routineDetails.asStateFlow()

    fun loadRoutineDetails(routineId: Long) {
        viewModelScope.launch {
            _routineDetails.value = routineExerciseDao.getExercisesWithNamesForRoutine(routineId)
        }
    }

    fun clearRoutineDetails() {
        _routineDetails.value = emptyList()
    }

    val activeRoutines: StateFlow<List<RoutineWithSummary>> =
        routineDao.getAllActiveRoutinesWithExerciseNames()
            .map { rows -> collapseRows(rows) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val archivedRoutines: StateFlow<List<RoutineWithSummary>> =
        routineDao.getAllArchivedRoutinesWithExerciseNames()
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
                isArchived = first.isArchived
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
            routineDao.updateRoutine(routine.copy(isArchived = true))
        }
    }

    fun unarchiveRoutine(routine: Routine) {
        viewModelScope.launch {
            routineDao.updateRoutine(routine.copy(isArchived = false))
        }
    }

    fun deleteRoutine(routine: Routine) {
        viewModelScope.launch {
            routineDao.deleteRoutine(routine)
        }
    }

    fun renameRoutine(routine: Routine, newName: String) {
        viewModelScope.launch {
            routineDao.updateRoutine(routine.copy(name = newName))
        }
    }

    fun createEmptyRoutine(name: String) {
        viewModelScope.launch {
            routineDao.insertRoutine(Routine(name = name, isCustom = true))
        }
    }

    fun duplicateRoutine(routine: Routine) {
        viewModelScope.launch {
            routineRepository.duplicateRoutine(routine.id)
        }
    }

    fun createExpressRoutine(routine: Routine) {
        viewModelScope.launch {
            routineRepository.createExpressRoutine(routine.id)
        }
    }
}
