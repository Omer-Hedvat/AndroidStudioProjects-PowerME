package com.omerhedvat.powerme.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omerhedvat.powerme.data.database.Exercise
import com.omerhedvat.powerme.data.repository.ExerciseRepository
import com.omerhedvat.powerme.util.ModelRouter
import com.omerhedvat.powerme.util.SecurePreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
data class ExerciseMetadata(
    val muscleGroup: String? = null,
    val equipmentType: String? = null,
    val youtubeVideoId: String? = null,
    val setupNotes: String? = null,
    val restDurationSeconds: Int? = null,
    val error: String? = null
)

sealed class MagicAddUiState {
    object Idle : MagicAddUiState()
    object Loading : MagicAddUiState()
    data class Found(val exercise: Exercise) : MagicAddUiState()
    data class Error(val message: String) : MagicAddUiState()
    object Saved : MagicAddUiState()
}

@HiltViewModel
class MagicAddViewModel @Inject constructor(
    private val securePreferencesManager: SecurePreferencesManager,
    private val exerciseRepository: ExerciseRepository,
    private val modelRouter: ModelRouter
) : ViewModel() {

    private val _uiState = MutableStateFlow<MagicAddUiState>(MagicAddUiState.Idle)
    val uiState: StateFlow<MagicAddUiState> = _uiState.asStateFlow()

    val hasApiKey: Boolean get() = securePreferencesManager.hasApiKey()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Calls Gemini to look up exercise metadata by name.
     * Returns structured JSON with muscle group, equipment type, YouTube video ID, and setup notes.
     *
     * @param name The exercise name typed by the user
     */
    fun searchExercise(name: String) {
        if (name.isBlank()) return

        viewModelScope.launch {
            _uiState.value = MagicAddUiState.Loading

            try {
                if (!securePreferencesManager.hasApiKey()) {
                    _uiState.value = MagicAddUiState.Error("No API key configured. Add it in Settings.")
                    return@launch
                }

                val model = modelRouter.buildModel(
                    modelId = modelRouter.resolveEnrichmentModel(null),
                    temperature = 0.2f,
                    topK = 20,
                    topP = 0.9f
                )

                val prompt = buildPrompt(name)
                val response = model.generateContent(prompt)
                val text = response.text ?: throw Exception("Empty response from AI")

                // Extract JSON block (handles both plain JSON and markdown code blocks)
                val jsonStr = extractJson(text)
                    ?: throw Exception("Could not parse AI response")

                val metadata = json.decodeFromString<ExerciseMetadata>(jsonStr)

                if (metadata.error != null) {
                    _uiState.value = MagicAddUiState.Error("\"${name}\" not recognized as an exercise")
                    return@launch
                }

                val exercise = Exercise(
                    name = name.trim(),
                    muscleGroup = metadata.muscleGroup ?: "General",
                    equipmentType = metadata.equipmentType ?: "Bodyweight",
                    youtubeVideoId = metadata.youtubeVideoId,
                    setupNotes = metadata.setupNotes,
                    restDurationSeconds = metadata.restDurationSeconds ?: 90,
                    isCustom = true,
                    isFavorite = false
                )

                _uiState.value = MagicAddUiState.Found(exercise)

            } catch (e: Exception) {
                _uiState.value = MagicAddUiState.Error(e.message ?: "Search failed")
            }
        }
    }

    /**
     * Saves the found exercise to the database and returns it with its generated ID.
     *
     * @param exercise The exercise to save
     * @param onSaved Callback with the saved exercise (including DB-generated ID)
     */
    fun saveExercise(exercise: Exercise, onSaved: (Exercise) -> Unit) {
        viewModelScope.launch {
            try {
                val id = exerciseRepository.insertExercise(exercise)
                val saved = exercise.copy(id = id)
                onSaved(saved)
                _uiState.value = MagicAddUiState.Saved
            } catch (e: Exception) {
                _uiState.value = MagicAddUiState.Error("Failed to save exercise: ${e.message}")
            }
        }
    }

    /** Resets state back to Idle (e.g. when dialog is reopened) */
    fun reset() {
        _uiState.value = MagicAddUiState.Idle
    }

    private fun buildPrompt(exerciseName: String): String = """
        Exercise: "$exerciseName"

        Return ONLY valid JSON with this exact format, no extra text:
        {
          "muscleGroup": "one of: Legs, Chest, Back, Shoulders, Arms, Core, Cardio, Full Body",
          "equipmentType": "one of: Barbell, Dumbbells, Cable, Machine, Bodyweight, Resistance Bands, Pull-up Bar, Kettlebell",
          "youtubeVideoId": "11-character YouTube video ID from Renaissance Periodization, Jeff Nippard, or Athlean-X — or null if unknown",
          "setupNotes": "3-4 sentence form cue for proper technique and safety. Include lumbar and joint protection cues relevant to this exercise.",
          "restDurationSeconds": 90
        }

        If this is not a real exercise, return: {"error": "not found"}
    """.trimIndent()

    private fun extractJson(text: String): String? {
        // Try markdown code block first
        val codeBlock = "```(?:json)?\\s*([\\s\\S]*?)\\s*```".toRegex()
        codeBlock.find(text)?.groupValues?.get(1)?.trim()?.let { return it }

        // Fall back to plain JSON object
        val jsonObject = "\\{[\\s\\S]*\\}".toRegex()
        return jsonObject.find(text)?.value?.trim()
    }
}
