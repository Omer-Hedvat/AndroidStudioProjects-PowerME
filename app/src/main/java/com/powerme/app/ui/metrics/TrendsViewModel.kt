package com.powerme.app.ui.metrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powerme.app.analytics.ReadinessEngine
import com.powerme.app.data.database.ExerciseWithHistory
import com.powerme.app.data.repository.TrendsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrendsViewModel @Inject constructor(
    private val trendsRepository: TrendsRepository
) : ViewModel() {

    private val _timeRange = MutableStateFlow(TrendsTimeRange.THREE_MONTHS)
    val timeRange: StateFlow<TrendsTimeRange> = _timeRange.asStateFlow()

    private val _readinessScore = MutableStateFlow<ReadinessEngine.ReadinessScore>(ReadinessEngine.ReadinessScore.NoData)
    val readinessScore: StateFlow<ReadinessEngine.ReadinessScore> = _readinessScore.asStateFlow()

    private val _selectedExerciseId = MutableStateFlow<Long?>(null)
    val selectedExerciseId: StateFlow<Long?> = _selectedExerciseId.asStateFlow()

    private val _e1rmData = MutableStateFlow<E1RMProgressionData?>(null)
    val e1rmData: StateFlow<E1RMProgressionData?> = _e1rmData.asStateFlow()

    private val _weeklyVolume = MutableStateFlow<WeeklyVolumeData?>(null)
    val weeklyVolume: StateFlow<WeeklyVolumeData?> = _weeklyVolume.asStateFlow()

    private val _muscleGroupVolume = MutableStateFlow<List<MuscleGroupVolumePoint>>(emptyList())
    val muscleGroupVolume: StateFlow<List<MuscleGroupVolumePoint>> = _muscleGroupVolume.asStateFlow()

    private val _effectiveSets = MutableStateFlow<List<EffectiveSetsChartPoint>>(emptyList())
    val effectiveSets: StateFlow<List<EffectiveSetsChartPoint>> = _effectiveSets.asStateFlow()

    private val _bodyComposition = MutableStateFlow<BodyCompositionData?>(null)
    val bodyComposition: StateFlow<BodyCompositionData?> = _bodyComposition.asStateFlow()

    private val _chronotypeData = MutableStateFlow<List<TimeOfDayChartPoint>>(emptyList())
    val chronotypeData: StateFlow<List<TimeOfDayChartPoint>> = _chronotypeData.asStateFlow()

    private val _exercisePickerItems = MutableStateFlow<List<ExerciseWithHistory>>(emptyList())
    val exercisePickerItems: StateFlow<List<ExerciseWithHistory>> = _exercisePickerItems.asStateFlow()

    private val _readinessSubMetrics = MutableStateFlow(ReadinessSubMetrics(null, null, null))
    val readinessSubMetrics: StateFlow<ReadinessSubMetrics> = _readinessSubMetrics.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadAll()
    }

    fun setTimeRange(range: TrendsTimeRange) {
        if (_timeRange.value == range) return
        _timeRange.value = range
        loadAll()
    }

    fun selectExercise(exerciseId: Long, exerciseName: String) {
        _selectedExerciseId.value = exerciseId
        viewModelScope.launch {
            try {
                _e1rmData.value = trendsRepository.getE1RMProgression(
                    exerciseId, exerciseName, _timeRange.value
                )
            } catch (e: Exception) {
                // Silently handle — chart shows empty state
            }
        }
    }

    private fun loadAll() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                coroutineScope {
                    launch { loadReadiness() }
                    launch { loadExercisePicker() }
                    launch { loadWeeklyVolume() }
                    launch { loadMuscleGroupVolume() }
                    launch { loadEffectiveSets() }
                    launch { loadBodyComposition() }
                    launch { loadChronotypeData() }
                    launch { loadE1RM() }
                }
            } catch (_: Exception) {
                // Individual loads handle their own errors
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadReadiness() {
        try {
            _readinessScore.value = trendsRepository.getReadinessScore()
            _readinessSubMetrics.value = trendsRepository.getReadinessSubMetrics()
        } catch (_: Exception) {
            _readinessScore.value = ReadinessEngine.ReadinessScore.NoData
        }
    }

    private suspend fun loadExercisePicker() {
        try {
            val items = trendsRepository.getExercisePicker(_timeRange.value)
            _exercisePickerItems.value = items
            // Auto-select first exercise if none selected
            if (_selectedExerciseId.value == null && items.isNotEmpty()) {
                _selectedExerciseId.value = items.first().id
            }
        } catch (_: Exception) {
            // Keep existing list
        }
    }

    private suspend fun loadE1RM() {
        try {
            val exerciseId = _selectedExerciseId.value ?: return
            val exerciseName = _exercisePickerItems.value
                .firstOrNull { it.id == exerciseId }?.name ?: return
            _e1rmData.value = trendsRepository.getE1RMProgression(
                exerciseId, exerciseName, _timeRange.value
            )
        } catch (_: Exception) {
            _e1rmData.value = null
        }
    }

    private suspend fun loadWeeklyVolume() {
        try {
            _weeklyVolume.value = trendsRepository.getWeeklyVolume(_timeRange.value)
        } catch (_: Exception) {
            _weeklyVolume.value = null
        }
    }

    private suspend fun loadMuscleGroupVolume() {
        try {
            _muscleGroupVolume.value = trendsRepository.getWeeklyMuscleGroupVolume(_timeRange.value)
        } catch (_: Exception) {
            _muscleGroupVolume.value = emptyList()
        }
    }

    private suspend fun loadEffectiveSets() {
        try {
            _effectiveSets.value = trendsRepository.getWeeklyEffectiveSets(_timeRange.value)
        } catch (_: Exception) {
            _effectiveSets.value = emptyList()
        }
    }

    private suspend fun loadBodyComposition() {
        try {
            _bodyComposition.value = trendsRepository.getBodyCompositionData(_timeRange.value)
        } catch (_: Exception) {
            _bodyComposition.value = null
        }
    }

    private suspend fun loadChronotypeData() {
        try {
            _chronotypeData.value = trendsRepository.getWorkoutsByTimeOfDay(_timeRange.value)
        } catch (_: Exception) {
            _chronotypeData.value = emptyList()
        }
    }
}
