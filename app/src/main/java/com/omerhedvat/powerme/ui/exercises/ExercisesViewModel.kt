package com.omerhedvat.powerme.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omerhedvat.powerme.data.database.Exercise
import com.omerhedvat.powerme.data.repository.ExerciseRepository
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
    val selectedMuscle: String? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class ExercisesViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExercisesUiState())
    val uiState: StateFlow<ExercisesUiState> = _uiState.asStateFlow()

    private var allExercises: List<Exercise> = emptyList()

    init {
        loadExercises()
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

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun onMuscleFilterChanged(muscle: String?) {
        _uiState.update { it.copy(selectedMuscle = muscle) }
        applyFilters()
    }

    private fun applyFilters() {
        val query = _uiState.value.searchQuery.trim().lowercase()
        val muscle = _uiState.value.selectedMuscle

        val filtered = allExercises.filter { exercise ->
            val matchesQuery = query.isEmpty() || exercise.name.lowercase().contains(query)
            val matchesMuscle = muscle == null || exercise.muscleGroup.equals(muscle, ignoreCase = true)
            matchesQuery && matchesMuscle
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
