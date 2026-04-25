package com.powerme.app.ui.workouts.ai

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powerme.app.ai.AiCoreDownloadManager
import com.powerme.app.ai.DownloadState
import com.powerme.app.ai.TextRecognitionService
import com.powerme.app.ai.WorkoutTextParser
import com.powerme.app.analytics.AnalyticsTracker
import com.powerme.app.data.database.Exercise
import com.powerme.app.data.repository.ExerciseRepository
import com.powerme.app.data.repository.PlanExercise
import com.powerme.app.data.repository.RoutineRepository
import com.powerme.app.data.repository.UserSynonymRepository
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
import java.util.UUID
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
    val restSeconds: Int?,
    val supersetGroupId: String? = null,
    val notes: String? = null
)

data class AiWorkoutUiState(
    val step: AiWorkoutStep = AiWorkoutStep.INPUT,
    val inputText: String = "",
    val inputMode: InputMode = InputMode.TEXT,
    val isProcessing: Boolean = false,
    val ocrText: String? = null,
    val error: String? = null,
    val previewExercises: List<PreviewExercise> = emptyList(),
    // Organize / management modes
    val isOrganizeMode: Boolean = false,
    val managementSheetIndex: Int? = null,
    val isSupersetSelectMode: Boolean = false,
    val supersetAnchorIndex: Int? = null,
    val supersetCandidateIndices: Set<Int> = emptySet(),
    val restTimeDialogIndex: Int? = null,
    val notesDialogIndex: Int? = null
)

/** Emitted once when the workout is created and ready for navigation. */
sealed class AiWorkoutEvent {
    data class WorkoutStarted(val bootstrap: WorkoutBootstrap) : AiWorkoutEvent()
    data class RoutineSaved(val routineId: String) : AiWorkoutEvent()
}

/** Emitted once when the user manually swaps an UNMATCHED or FUZZY exercise, offering to save the alias. */
data class SynonymSavePrompt(
    val rawName: String,
    val exerciseId: Long,
    val exerciseName: String
)

@HiltViewModel
class AiWorkoutViewModel @Inject constructor(
    private val workoutParser: WorkoutTextParser,
    private val exerciseMatcher: ExerciseMatcher,
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val routineRepository: RoutineRepository,
    private val textRecognitionService: TextRecognitionService,
    private val userSynonymRepository: UserSynonymRepository,
    private val analyticsTracker: AnalyticsTracker,
    private val downloadManager: AiCoreDownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiWorkoutUiState())
    val uiState: StateFlow<AiWorkoutUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<AiWorkoutEvent?>(null)
    val events: StateFlow<AiWorkoutEvent?> = _events.asStateFlow()

    private val _synonymPrompt = MutableStateFlow<SynonymSavePrompt?>(null)
    val synonymPrompt: StateFlow<SynonymSavePrompt?> = _synonymPrompt.asStateFlow()

    val downloadState: StateFlow<DownloadState> = downloadManager.downloadState

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

            val parseResult = workoutParser.parseWorkoutText(input, exerciseNames)
            if (parseResult.error != null && parseResult.exercises.isEmpty()) {
                val userError = if (parseResult.error == "API_KEY_MISSING") {
                    "No Gemini API key configured. Add your own key in Settings → AI."
                } else {
                    parseResult.error
                }
                _uiState.update { it.copy(isProcessing = false, error = userError) }
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
        val pre = applyExerciseSwap(index, newExercise, MatchType.EXACT) ?: return
        if (pre.matchType == MatchType.UNMATCHED || pre.matchType == MatchType.FUZZY) {
            _synonymPrompt.value = SynonymSavePrompt(pre.originalName, newExercise.id, newExercise.name)
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

    // ── Reorder ───────────────────────────────────────────────────────────────

    fun reorderExercise(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val list = state.previewExercises.toMutableList()
            if (fromIndex !in list.indices || toIndex !in list.indices) return@update state
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            state.copy(previewExercises = list)
        }
    }

    // ── Organize mode ─────────────────────────────────────────────────────────

    fun enterOrganizeMode() = _uiState.update { it.copy(isOrganizeMode = true) }
    fun exitOrganizeMode() = _uiState.update { it.copy(isOrganizeMode = false) }

    // ── Management sheet ──────────────────────────────────────────────────────

    fun openManagementSheet(index: Int) = _uiState.update { it.copy(managementSheetIndex = index) }
    fun closeManagementSheet() = _uiState.update { it.copy(managementSheetIndex = null) }

    // ── Rest time & notes ─────────────────────────────────────────────────────

    fun openRestTimeDialog(index: Int) = _uiState.update { it.copy(restTimeDialogIndex = index) }
    fun closeRestTimeDialog() = _uiState.update { it.copy(restTimeDialogIndex = null) }

    fun openNotesDialog(index: Int) = _uiState.update { it.copy(notesDialogIndex = index) }
    fun closeNotesDialog() = _uiState.update { it.copy(notesDialogIndex = null) }

    fun setRestTime(index: Int, seconds: Int) = updatePreviewExercise(index) { it.copy(restSeconds = seconds) }

    fun setExerciseNote(index: Int, note: String) =
        updatePreviewExercise(index) { it.copy(notes = note.takeIf { n -> n.isNotBlank() }) }

    // ── Replace (all rows) ────────────────────────────────────────────────────

    /** Like swapExercise but sets matchType = MANUAL and always offers synonym prompt. */
    fun replaceExercise(index: Int, newExercise: Exercise) {
        val pre = applyExerciseSwap(index, newExercise, MatchType.MANUAL) ?: return
        _synonymPrompt.value = SynonymSavePrompt(pre.originalName, newExercise.id, newExercise.name)
    }

    fun replaceExerciseById(index: Int, exerciseId: Long) {
        viewModelScope.launch {
            val exercise = exerciseRepository.getExerciseById(exerciseId) ?: return@launch
            replaceExercise(index, exercise)
        }
    }

    private fun applyExerciseSwap(index: Int, newExercise: Exercise, matchType: MatchType): PreviewExercise? {
        var original: PreviewExercise? = null
        _uiState.update { state ->
            original = state.previewExercises.getOrNull(index)
            val updated = state.previewExercises.toMutableList()
            if (index in updated.indices) {
                updated[index] = updated[index].copy(
                    matchedExercise = newExercise,
                    matchType = matchType,
                    confidence = 1.0
                )
            }
            state.copy(previewExercises = updated)
        }
        return original
    }

    // ── Superset ──────────────────────────────────────────────────────────────

    fun enterSupersetSelectMode(anchorIndex: Int) {
        _uiState.update {
            it.copy(
                isSupersetSelectMode = true,
                supersetAnchorIndex = anchorIndex,
                supersetCandidateIndices = setOf(anchorIndex)
            )
        }
    }

    fun exitSupersetSelectMode() {
        _uiState.update {
            it.copy(
                isSupersetSelectMode = false,
                supersetAnchorIndex = null,
                supersetCandidateIndices = emptySet()
            )
        }
    }

    fun toggleSupersetCandidate(index: Int) {
        _uiState.update { state ->
            val anchor = state.supersetAnchorIndex ?: return@update state
            val current = state.supersetCandidateIndices
            val updated = if (index == anchor) {
                // anchor is always included; toggle is a no-op on anchor
                current
            } else if (index in current) {
                current - index
            } else if (current.size < 4) {
                current + index
            } else {
                current // already at max 4
            }
            state.copy(supersetCandidateIndices = updated)
        }
    }

    fun commitSupersetSelection() {
        val state = _uiState.value
        val anchor = state.supersetAnchorIndex ?: return
        val candidates = state.supersetCandidateIndices
        if (candidates.size < 2) return
        createSuperset(candidates)
        exitSupersetSelectMode()
    }

    /**
     * Groups the exercises at [indices] into a superset.
     * Validates 2–4 consecutive indices. Assigns shared UUID.
     * Applies sync defaults: restSeconds = 0 for all except the last index.
     */
    fun createSuperset(indices: Set<Int>) {
        val sorted = indices.sorted()
        if (sorted.size < 2 || sorted.size > 4) return
        _uiState.update { state ->
            for (i in 1 until sorted.size) {
                if (sorted[i] != sorted[i - 1] + 1) return@update state
            }
            val groupId = UUID.randomUUID().toString()
            val lastIdx = sorted.last()
            val list = state.previewExercises.toMutableList()
            sorted.forEach { idx ->
                if (idx in list.indices) {
                    list[idx] = list[idx].copy(
                        supersetGroupId = groupId,
                        restSeconds = if (idx == lastIdx) list[idx].restSeconds else 0
                    )
                }
            }
            state.copy(previewExercises = list)
        }
    }

    /** Clears supersetGroupId from all members of the group that [anchorIndex] belongs to. */
    fun dissolveSuperset(anchorIndex: Int) {
        val groupId = _uiState.value.previewExercises.getOrNull(anchorIndex)?.supersetGroupId
            ?: return
        _uiState.update { state ->
            val list = state.previewExercises.toMutableList()
            list.forEachIndexed { idx, pe ->
                if (pe.supersetGroupId == groupId) {
                    list[idx] = pe.copy(supersetGroupId = null)
                }
            }
            state.copy(previewExercises = list)
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

    fun saveSynonym() {
        val prompt = _synonymPrompt.value ?: return
        _synonymPrompt.value = null
        viewModelScope.launch {
            userSynonymRepository.saveSynonym(prompt.rawName, prompt.exerciseId)
            analyticsTracker.logSynonymSaved(prompt.rawName, prompt.exerciseName)
        }
    }

    fun dismissSynonymPrompt() {
        _synonymPrompt.value = null
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
                    restSeconds = pe.restSeconds,
                    supersetGroupId = pe.supersetGroupId,
                    notes = pe.notes
                )
            }
            .takeIf { it.isNotEmpty() }
    }
}
