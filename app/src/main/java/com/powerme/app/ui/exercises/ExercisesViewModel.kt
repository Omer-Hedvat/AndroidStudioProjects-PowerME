package com.powerme.app.ui.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.ExerciseType
import com.powerme.app.data.database.Joint
import com.powerme.app.data.database.matchesSearchTokens
import com.powerme.app.data.database.toAffectedJoints
import com.powerme.app.data.database.toSearchTokens
import com.powerme.app.data.repository.ExerciseRepository
import com.powerme.app.data.repository.HealthHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExercisesUiState(
    val exercises: List<Exercise> = emptyList(),
    val searchQuery: String = "",
    val selectedMuscles: Set<String> = emptySet(),
    val selectedEquipment: Set<String> = emptySet(),
    val selectedTypes: Set<ExerciseType> = emptySet(),
    val functionalFilter: Boolean = false,
    val favoritesOnly: Boolean = false,
    val showFilterDialog: Boolean = false,
    val isLoading: Boolean = false
) {
    val activeFilterCount: Int
        get() = selectedTypes.size + selectedMuscles.size + selectedEquipment.size +
                if (functionalFilter) 1 else 0
}

@HiltViewModel
class ExercisesViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val healthHistoryRepository: HealthHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExercisesUiState())
    val uiState: StateFlow<ExercisesUiState> = _uiState.asStateFlow()

    private val _muscleGroupFilters = MutableStateFlow<List<String>>(emptyList())
    val muscleGroupFilters: StateFlow<List<String>> = _muscleGroupFilters.asStateFlow()

    private val _equipmentFilters = MutableStateFlow<List<String>>(emptyList())
    val equipmentFilters: StateFlow<List<String>> = _equipmentFilters.asStateFlow()

    /** Set of joints the user has active MODERATE/SEVERE health history entries for. */
    val affectedJoints: StateFlow<Set<Joint>> = healthHistoryRepository.getActiveEntries()
        .map { entries -> entries.flatMap { it.toAffectedJoints() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

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
            val musclesDeferred = async { exerciseRepository.getDistinctMuscleGroups() }
            val equipmentDeferred = async { exerciseRepository.getDistinctEquipmentTypes() }
            _muscleGroupFilters.value = musclesDeferred.await()
            _equipmentFilters.value = sortEquipmentTypes(equipmentDeferred.await())
        }
    }

    private fun sortEquipmentTypes(types: List<String>): List<String> {
        val prioritized = PRIORITY_EQUIPMENT.filter { p -> types.any { it.equals(p, ignoreCase = true) } }
        val remaining = types.filter { t -> PRIORITY_EQUIPMENT.none { it.equals(t, ignoreCase = true) } }
        return prioritized + remaining.sorted()
    }

    companion object {
        internal val PRIORITY_EQUIPMENT = listOf("Barbell", "Dumbbell", "Kettlebell", "Bench", "Bodyweight", "Cable")
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        applyFilters()
    }

    fun onMuscleFilterToggled(muscle: String) {
        val current = _uiState.value.selectedMuscles
        val updated = if (muscle in current) current - muscle else current + muscle
        _uiState.update { it.copy(selectedMuscles = updated) }
        applyFilters()
    }

    fun onEquipmentFilterToggled(equipment: String) {
        val current = _uiState.value.selectedEquipment
        val updated = if (equipment in current) current - equipment else current + equipment
        _uiState.update { it.copy(selectedEquipment = updated) }
        applyFilters()
    }

    fun onTypeFilterToggled(type: ExerciseType) {
        val current = _uiState.value.selectedTypes
        val updated = if (type in current) current - type else current + type
        _uiState.update { it.copy(selectedTypes = updated) }
        applyFilters()
    }

    fun onFunctionalFilterToggled() {
        _uiState.update { it.copy(functionalFilter = !it.functionalFilter) }
        applyFilters()
    }

    fun onFavoritesFilterToggled() {
        _uiState.update { it.copy(favoritesOnly = !it.favoritesOnly) }
        applyFilters()
    }

    fun onSelectAllMuscles() {
        _uiState.update { it.copy(selectedMuscles = _muscleGroupFilters.value.toSet()) }
        applyFilters()
    }

    fun onDeselectAllMuscles() {
        _uiState.update { it.copy(selectedMuscles = emptySet()) }
        applyFilters()
    }

    fun onSelectAllEquipment() {
        _uiState.update { it.copy(selectedEquipment = _equipmentFilters.value.toSet()) }
        applyFilters()
    }

    fun onDeselectAllEquipment() {
        _uiState.update { it.copy(selectedEquipment = emptySet()) }
        applyFilters()
    }

    fun onSelectAllTypes() {
        _uiState.update { it.copy(selectedTypes = ExerciseType.entries.toSet(), functionalFilter = true) }
        applyFilters()
    }

    fun onDeselectAllTypes() {
        _uiState.update { it.copy(selectedTypes = emptySet(), functionalFilter = false) }
        applyFilters()
    }

    fun applyInitialTypeFilters(types: Set<ExerciseType>) {
        if (types.isNotEmpty() && _uiState.value.selectedTypes.isEmpty()) {
            _uiState.update { it.copy(selectedTypes = types) }
            applyFilters()
        }
    }

    fun onClearAllFilters() {
        _uiState.update {
            it.copy(
                selectedMuscles = emptySet(),
                selectedEquipment = emptySet(),
                selectedTypes = emptySet(),
                functionalFilter = false,
                favoritesOnly = false
            )
        }
        applyFilters()
    }

    fun onFilterDialogToggled() {
        _uiState.update { it.copy(showFilterDialog = !it.showFilterDialog) }
    }

    private fun applyFilters() {
        val tokens = _uiState.value.searchQuery.toSearchTokens()
        val muscles = _uiState.value.selectedMuscles
        val equipment = _uiState.value.selectedEquipment
        val types = _uiState.value.selectedTypes
        val functional = _uiState.value.functionalFilter
        val favoritesOnly = _uiState.value.favoritesOnly

        val filtered = allExercises.filter { exercise ->
            val matchesQuery = exercise.matchesSearchTokens(tokens)
            val matchesMuscle = muscles.isEmpty() ||
                muscles.any { it.equals(exercise.muscleGroup, ignoreCase = true) }
            val matchesEquipment = equipment.isEmpty() ||
                equipment.any { it.equals(exercise.equipmentType.trim(), ignoreCase = true) }
            val matchesType = types.isEmpty() || exercise.exerciseType in types
            val matchesFunctional = !functional || exercise.tags.contains("\"functional\"")
            val matchesFavorites = !favoritesOnly || exercise.isFavorite
            matchesQuery && matchesMuscle && matchesEquipment && matchesType && matchesFunctional && matchesFavorites
        }
        _uiState.update { it.copy(exercises = filtered) }
    }

    fun toggleFavorite(exercise: Exercise) {
        viewModelScope.launch {
            exerciseRepository.toggleFavorite(exercise)
        }
    }

    fun toggleFunctionalTag(exercise: Exercise) {
        viewModelScope.launch {
            exerciseRepository.toggleFunctionalTag(exercise)
        }
    }

    fun deleteCustomExercise(exercise: Exercise) {
        if (!exercise.isCustom) return
        viewModelScope.launch {
            exerciseRepository.deleteExercise(exercise)
        }
    }
}
