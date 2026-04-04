package com.powerme.app.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.toSearchName
import com.powerme.app.data.repository.ExerciseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExercisesUiState(
    val exercises: List<Exercise> = emptyList(),
    val searchQuery: String = "",
    val selectedMuscles: Set<String> = emptySet(),
    val selectedEquipment: Set<String> = emptySet(),
    val isLoading: Boolean = false
)

@HiltViewModel
class ExercisesViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExercisesUiState())
    val uiState: StateFlow<ExercisesUiState> = _uiState.asStateFlow()

    private val _muscleGroupFilters = MutableStateFlow<List<String>>(listOf("All"))
    val muscleGroupFilters: StateFlow<List<String>> = _muscleGroupFilters.asStateFlow()

    private val _equipmentFilters = MutableStateFlow<List<String>>(listOf("All"))
    val equipmentFilters: StateFlow<List<String>> = _equipmentFilters.asStateFlow()

    private var allExercises: List<Exercise> = emptyList()

    init {
        loadExercises()
        loadFilterOptions()
    }

    private fun loadExercises() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            exerciseRepository.getAllExercises().collect { exercises ->
                allExercises = exercises
                applyFilters()
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private fun loadFilterOptions() {
        viewModelScope.launch {
            val muscles = exerciseRepository.getDistinctMuscleGroups()
            _muscleGroupFilters.value = listOf("All") + muscles

            val equipment = exerciseRepository.getDistinctEquipmentTypes()
            _equipmentFilters.value = listOf("All") + equipment
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun onMuscleFilterToggled(muscle: String) {
        val current = _uiState.value.selectedMuscles
        val updated = if (muscle == "All") emptySet()
        else if (muscle in current) current - muscle else current + muscle
        _uiState.update { it.copy(selectedMuscles = updated) }
        applyFilters()
    }

    fun onEquipmentFilterToggled(equipment: String) {
        val current = _uiState.value.selectedEquipment
        val updated = if (equipment == "All") emptySet()
        else if (equipment in current) current - equipment else current + equipment
        _uiState.update { it.copy(selectedEquipment = updated) }
        applyFilters()
    }

    private fun applyFilters() {
        val query = _uiState.value.searchQuery.trim().toSearchName()
        val muscles = _uiState.value.selectedMuscles
        val equipment = _uiState.value.selectedEquipment

        val filtered = allExercises.filter { exercise ->
            val matchesQuery = query.isEmpty() || exercise.searchName.contains(query)
            val matchesMuscle = muscles.isEmpty() ||
                muscles.any { it.equals(exercise.muscleGroup, ignoreCase = true) }
            val matchesEquipment = equipment.isEmpty() ||
                equipment.any { it.equals(exercise.equipmentType.trim(), ignoreCase = true) }
            matchesQuery && matchesMuscle && matchesEquipment
        }
        _uiState.update { it.copy(exercises = filtered) }
    }

    fun deleteCustomExercise(exercise: Exercise) {
        if (!exercise.isCustom) return
        viewModelScope.launch {
            exerciseRepository.deleteExercise(exercise)
        }
    }
}
