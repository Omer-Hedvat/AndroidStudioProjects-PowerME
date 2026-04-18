package com.powerme.app.ui.workouts.ai

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powerme.app.ai.GeminiWorkoutParser
import com.powerme.app.ai.TextRecognitionService
import com.powerme.app.data.database.Exercise
import com.powerme.app.data.repository.ExerciseRepository
import com.powerme.app.data.repository.PlanExercise
import com.powerme.app.data.repository.RoutineRepository
import com.powerme.app.data.repository.WorkoutBootstrap
import com.powerme.app.data.repository.WorkoutRepository
import com.powerme.app.util.ExerciseMatcher
import com.powerme.app.util.MatchType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AiWorkoutStep { INPUT, PREVIEW }
enum class InputMode { TEXT, PHOTO }

data class PreviewExercise(
    val originalName: String,
    val matchedExercise: Exercise?,
    val matchType: MatchType,
    val confidence: Double,
    val sets: Int,
    val reps: Int,
    val weight: Double?,
    val restSeconds: Int?
)

data class AiWorkoutUiState(
    val step: AiWorkoutStep = AiWorkoutStep.INPUT,
    val inputText: String = "",
    val inputMode: InputMode = InputMode.TEXT,
    val isProcessing: Boolean = false,
    val ocrText: String? = null,
    val error: String? = null,
    val previewExercises: List<PreviewExercise> = emptyList()
)

/** Emitted once when the workout is created and ready for navigation. */
sealed class AiWorkoutEvent {
    data class WorkoutStarted(val bootstrap: WorkoutBootstrap) : AiWorkoutEvent()
    data class RoutineSaved(val routineId: String) : AiWorkoutEvent()
}

@HiltViewModel
class AiWorkoutViewModel @Inject constructor(
    private val geminiParser: GeminiWorkoutParser,
    private val exerciseMatcher: ExerciseMatcher,
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val routineRepository: RoutineRepository,
    private val textRecognitionService: TextRecognitionService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiWorkoutUiState())
    val uiState: StateFlow<AiWorkoutUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<AiWorkoutEvent?>(null)
    val events: StateFlow<AiWorkoutEvent?> = _events.asStateFlow()

    private var cachedLibrary: List<Exercise> = emptyList()

    init {
        viewModelScope.launch {
            cachedLibrary = exerciseRepository.getAllExercises().first()
        }
    }

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text, error = null) }
    }

    fun setInputMode(mode: InputMode) {
        _uiState.update { it.copy(inputMode = mode, error = null) }
    }

    fun updateOcrText(text: String) {
        _uiState.update { it.copy(ocrText = text, inputText = text, error = null) }
    }

    fun processPhoto(uri: Uri, context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }
            val text = textRecognitionService.recognizeText(uri, context)
            if (text.isBlank()) {
                _uiState.update { it.copy(isProcessing = false, error = "Could not extract text from photo. Try a clearer image.") }
            } else {
                _uiState.update { it.copy(isProcessing = false, ocrText = text, inputText = text) }
            }
        }
    }

    fun processTextInput() {
        val input = _uiState.value.inputText.trim()
        if (input.isBlank()) {
            _uiState.update { it.copy(error = "Please enter a workout description.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, error = null) }

            if (cachedLibrary.isEmpty()) {
                cachedLibrary = exerciseRepository.getAllExercises().first()
            }
            val exerciseNames = cachedLibrary.map { it.name }

            val parseResult = geminiParser.parseWorkoutText(input, exerciseNames)
            if (parseResult.error != null && parseResult.exercises.isEmpty()) {
                _uiState.update { it.copy(isProcessing = false, error = parseResult.error) }
                return@launch
            }

            val preview = parseResult.exercises.map { parsed ->
                val match = exerciseMatcher.matchExercise(parsed.name, cachedLibrary)
                PreviewExercise(
                    originalName = parsed.name,
                    matchedExercise = match.exercise,
                    matchType = match.matchType,
                    confidence = match.confidence,
                    sets = parsed.sets,
                    reps = parsed.reps,
                    weight = parsed.weight,
                    restSeconds = parsed.restSeconds
                )
            }

            _uiState.update {
                it.copy(isProcessing = false, step = AiWorkoutStep.PREVIEW, previewExercises = preview)
            }
        }
    }

    fun swapExercise(index: Int, newExercise: Exercise) {
        _uiState.update { state ->
            val updated = state.previewExercises.toMutableList()
            if (index in updated.indices) {
                updated[index] = updated[index].copy(
                    matchedExercise = newExercise,
                    matchType = MatchType.EXACT,
                    confidence = 1.0
                )
            }
            state.copy(previewExercises = updated)
        }
    }

    fun swapExerciseById(index: Int, exerciseId: Long) {
        viewModelScope.launch {
            val exercise = exerciseRepository.getExerciseById(exerciseId) ?: return@launch
            swapExercise(index, exercise)
        }
    }

    fun updateSets(index: Int, sets: Int) = updatePreviewExercise(index) { it.copy(sets = sets) }
    fun updateReps(index: Int, reps: Int) = updatePreviewExercise(index) { it.copy(reps = reps) }
    fun updateWeight(index: Int, weight: Double?) = updatePreviewExercise(index) { it.copy(weight = weight) }

    fun removeExercise(index: Int) {
        _uiState.update { state ->
            val updated = state.previewExercises.toMutableList().also { it.removeAt(index) }
            state.copy(previewExercises = updated)
        }
    }

    private fun updatePreviewExercise(index: Int, transform: (PreviewExercise) -> PreviewExercise) {
        _uiState.update { state ->
            val updated = state.previewExercises.toMutableList()
            if (index in updated.indices) updated[index] = transform(updated[index])
            state.copy(previewExercises = updated)
        }
    }

    fun goBackToInput() {
        _uiState.update { it.copy(step = AiWorkoutStep.INPUT) }
    }

    fun startWorkout() {
        val exercises = matchedPlanExercises() ?: return
        viewModelScope.launch {
            val bootstrap = workoutRepository.createWorkoutFromPlan(exercises)
            _events.value = AiWorkoutEvent.WorkoutStarted(bootstrap)
        }
    }

    fun saveAsRoutine(name: String) {
        val exercises = matchedPlanExercises() ?: return
        viewModelScope.launch {
            val routineId = routineRepository.createRoutineFromPlan(name, exercises)
            _events.value = AiWorkoutEvent.RoutineSaved(routineId)
        }
    }

    fun consumeEvent() {
        _events.value = null
    }

    private fun matchedPlanExercises(): List<PlanExercise>? {
        val preview = _uiState.value.previewExercises
        return preview
            .filter { it.matchedExercise != null }
            .map { pe ->
                PlanExercise(
                    exerciseId = pe.matchedExercise!!.id,
                    sets = pe.sets,
                    reps = pe.reps,
                    weight = pe.weight,
                    restSeconds = pe.restSeconds
                )
            }
            .takeIf { it.isNotEmpty() }
    }
}
