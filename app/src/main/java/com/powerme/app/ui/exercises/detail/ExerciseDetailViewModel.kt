package com.powerme.app.ui.exercises.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.powerme.app.data.database.Joint
import com.powerme.app.data.database.toAffectedJoints
import com.powerme.app.data.repository.ExerciseDetailRepository
import com.powerme.app.data.repository.ExerciseRepository
import com.powerme.app.data.repository.HealthHistoryRepository
import com.powerme.app.ui.metrics.TrendsTimeRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ExerciseDetailRepository,
    private val exerciseRepository: ExerciseRepository,
    private val healthHistoryRepository: HealthHistoryRepository
) : ViewModel() {

    val exerciseId: Long = checkNotNull(savedStateHandle["exerciseId"])

    private val _uiState = MutableStateFlow(ExerciseDetailUiState())
    val uiState: StateFlow<ExerciseDetailUiState> = _uiState.asStateFlow()

    // Chart model producers — owned by ViewModel so they survive recompositions
    val e1rmProducer = CartesianChartModelProducer()
    val maxWeightProducer = CartesianChartModelProducer()
    val volumeProducer = CartesianChartModelProducer()
    val bestSetProducer = CartesianChartModelProducer()
    val rpeProducer = CartesianChartModelProducer()

    /** Set of joints the user has active MODERATE/SEVERE health history entries for. */
    val affectedJoints: StateFlow<Set<Joint>> = healthHistoryRepository.getActiveEntries()
        .map { entries -> entries.flatMap { it.toAffectedJoints() }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private var workoutHistoryPage = 0
    private var noteDebounceJob: Job? = null

    init {
        loadAll()
    }

    private fun loadAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val exercise = repository.getExercise(exerciseId)
            if (exercise == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            // Launch all data loads concurrently
            val lastPerformedJob = launch { loadLastPerformed() }
            val sessionCountJob = launch { loadSessionCount() }
            val prsJob = launch { loadPersonalRecords() }
            val overloadJob = launch { loadOverloadSuggestion() }
            val warmupJob = launch { loadWarmUpRamp() }
            val stressJob = launch { loadStressColors() }
            val alternativesJob = launch { loadAlternatives(exercise) }
            val bodyWeightJob = launch { loadBodyWeight() }
            val historyJob = launch { loadWorkoutHistory(reset = true) }
            val trendsJob = launch { loadTrends() }

            _uiState.update { it.copy(exercise = exercise, isLoading = false) }
        }
    }

    private suspend fun loadLastPerformed() {
        val summary = repository.getLastPerformed(exerciseId)
        _uiState.update { it.copy(lastPerformed = summary) }
    }

    private suspend fun loadSessionCount() {
        val count = repository.getSessionCount(exerciseId)
        _uiState.update { it.copy(sessionCount = count) }
    }

    private suspend fun loadPersonalRecords() {
        val prs = repository.computePersonalRecords(exerciseId)
        _uiState.update { it.copy(personalRecords = prs) }
    }

    private suspend fun loadOverloadSuggestion() {
        val suggestion = repository.computeOverloadSuggestion(exerciseId)
        _uiState.update { it.copy(overloadSuggestion = suggestion) }
    }

    private suspend fun loadWarmUpRamp() {
        val exercise = _uiState.value.exercise
        val ramp = repository.computeWarmUpRamp(
            exerciseId = exerciseId,
            equipmentType = exercise?.equipmentType ?: "Barbell"
        )
        _uiState.update { it.copy(warmUpRamp = ramp) }
    }

    private suspend fun loadStressColors() {
        val colors = repository.getStressCoefficients(exerciseId)
        _uiState.update { it.copy(stressColors = colors) }
    }

    private suspend fun loadAlternatives(exercise: com.powerme.app.data.database.Exercise) {
        val alts = repository.findAlternatives(exercise)
        _uiState.update { it.copy(alternatives = alts) }
    }

    private suspend fun loadBodyWeight() {
        val kg = repository.getLatestBodyWeightKg()
        _uiState.update { it.copy(userBodyWeightKg = kg) }
    }

    private suspend fun loadWorkoutHistory(reset: Boolean) {
        if (reset) workoutHistoryPage = 0
        val rows = repository.getWorkoutHistory(exerciseId, workoutHistoryPage)
        val hasMore = rows.size > ExerciseDetailRepository.PAGE_SIZE
        val trimmed = if (hasMore) rows.dropLast(1) else rows
        _uiState.update { state ->
            val combined = if (reset) trimmed else state.workoutHistory + trimmed
            state.copy(workoutHistory = combined, hasMoreHistory = hasMore)
        }
    }

    private suspend fun loadTrends() {
        val range = _uiState.value.timeRange
        val data = repository.getTrendData(exerciseId, range)
        _uiState.update { it.copy(trendData = data) }
        pushTrendDataToProducers(data)
    }

    private fun pushTrendDataToProducers(data: ExerciseTrendData?) {
        viewModelScope.launch {
            val dummy = listOf(0.0, 0.0)
            e1rmProducer.runTransaction {
                val pts = data?.e1rmPoints?.map { it.value }?.takeIf { it.size >= 2 } ?: dummy
                lineSeries { series(pts) }
            }
            maxWeightProducer.runTransaction {
                val pts = data?.maxWeightPoints?.map { it.value }?.takeIf { it.size >= 2 } ?: dummy
                lineSeries { series(pts) }
            }
            volumeProducer.runTransaction {
                val pts = data?.volumePoints?.map { it.value }?.takeIf { it.size >= 2 } ?: dummy
                lineSeries { series(pts) }
            }
            bestSetProducer.runTransaction {
                val pts = data?.bestSetPoints?.map { it.value }?.takeIf { it.size >= 2 } ?: dummy
                lineSeries { series(pts) }
            }
            rpeProducer.runTransaction {
                val pts = data?.rpePoints?.map { it.value }?.takeIf { it.size >= 2 } ?: dummy
                lineSeries { series(pts) }
            }
        }
    }

    // ── Public actions ────────────────────────────────────────────────────────

    fun onTimeRangeChanged(range: TrendsTimeRange) {
        _uiState.update { it.copy(timeRange = range) }
        viewModelScope.launch { loadTrends() }
    }

    fun onUserNoteChanged(note: String) {
        _uiState.update { state ->
            val exercise = state.exercise ?: return@update state
            state.copy(exercise = exercise.copy(userNote = note))
        }
        noteDebounceJob?.cancel()
        noteDebounceJob = viewModelScope.launch {
            delay(500)
            repository.updateUserNote(exerciseId, note)
        }
    }

    fun toggleFavorite() {
        val exercise = _uiState.value.exercise ?: return
        viewModelScope.launch {
            exerciseRepository.toggleFavorite(exercise)
            val updated = repository.getExercise(exerciseId)
            _uiState.update { it.copy(exercise = updated) }
        }
    }

    fun loadMoreHistory() {
        if (!_uiState.value.hasMoreHistory) return
        workoutHistoryPage++
        viewModelScope.launch { loadWorkoutHistory(reset = false) }
    }
}
