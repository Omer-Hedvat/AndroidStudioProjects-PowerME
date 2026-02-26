package com.omerhedvat.powerme.ui.gyms

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.omerhedvat.powerme.data.database.GymProfile
import com.omerhedvat.powerme.data.repository.GymProfileRepository
import com.omerhedvat.powerme.util.ModelRouter
import com.omerhedvat.powerme.util.SecurePreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private val ALL_PLATES = setOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25, 0.5)

data class GymSetupUiState(
    val gymName: String = "",
    val selectedPlates: Set<Double> = ALL_PLATES,
    val dumbbellMinKg: Float = 5f,
    val dumbbellMaxKg: Float = 40f,
    // Standard equipment toggles
    val hasBarbell: Boolean = true,
    val hasBench: Boolean = true,
    val hasPullUpBar: Boolean = true,
    val hasSquatCage: Boolean = true,
    val hasCableCross: Boolean = true,
    val imageBitmap: Bitmap? = null,
    val detectedEquipment: List<String> = emptyList(),
    val isAnalyzing: Boolean = false,
    // Free text extraction
    val gymDescriptionText: String = "",
    val isExtractingText: Boolean = false,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val savedProfileId: Long? = null,
    val error: String? = null,
    // D3: model readiness
    val isModelsReady: Boolean = true
)

@HiltViewModel
class GymSetupViewModel @Inject constructor(
    private val gymProfileRepository: GymProfileRepository,
    private val securePreferencesManager: SecurePreferencesManager,
    private val modelRouter: ModelRouter,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(GymSetupUiState())
    val uiState: StateFlow<GymSetupUiState> = _uiState.asStateFlow()

    init {
        // D3: if API key exists but no models loaded yet, trigger fetch and disable buttons until ready
        val apiKey = securePreferencesManager.getApiKey()
        if (apiKey != null && modelRouter.availableModelIds.isEmpty()) {
            _uiState.update { it.copy(isModelsReady = false) }
            viewModelScope.launch {
                try {
                    modelRouter.fetchModels(apiKey)
                } catch (_: Exception) { /* silently fallback */ }
                _uiState.update { it.copy(isModelsReady = true) }
            }
        }
    }

    fun updateGymName(name: String) { _uiState.update { it.copy(gymName = name) } }

    fun togglePlate(weight: Double) {
        val current = _uiState.value.selectedPlates
        _uiState.update {
            it.copy(selectedPlates = if (weight in current) current - weight else current + weight)
        }
    }

    fun toggleBarbell() {
        val newValue = !_uiState.value.hasBarbell
        _uiState.update { state ->
            if (newValue) {
                state.copy(hasBarbell = true, hasBench = true, selectedPlates = ALL_PLATES)
            } else {
                state.copy(hasBarbell = false, hasBench = false, selectedPlates = emptySet())
            }
        }
    }

    fun toggleBench() {
        _uiState.update { it.copy(hasBench = !it.hasBench) }
    }

    fun togglePullUpBar() { _uiState.update { it.copy(hasPullUpBar = !it.hasPullUpBar) } }
    fun toggleSquatCage() { _uiState.update { it.copy(hasSquatCage = !it.hasSquatCage) } }
    fun toggleCableCross() { _uiState.update { it.copy(hasCableCross = !it.hasCableCross) } }

    fun updateDumbbellRange(range: ClosedFloatingPointRange<Float>) {
        // Defensive guard: Material3 RangeSlider already enforces min<=max,
        // but we clamp here to be safe against any edge-case inversion.
        val safeMin = range.start.coerceAtMost(range.endInclusive)
        val safeMax = range.endInclusive.coerceAtLeast(range.start)
        _uiState.update { it.copy(dumbbellMinKg = safeMin, dumbbellMaxKg = safeMax) }
    }

    fun addEquipmentManually(item: String) {
        _uiState.update { it.copy(detectedEquipment = (it.detectedEquipment + item).distinct()) }
    }

    fun setImageUri(uri: Uri?) {
        if (uri == null) return
        try {
            @Suppress("DEPRECATION")
            val bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            _uiState.update { it.copy(imageBitmap = bitmap, error = null) }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Failed to load image") }
        }
    }

    fun analyzeImage() {
        val bitmap = _uiState.value.imageBitmap ?: return
        val apiKey = securePreferencesManager.getApiKey() ?: run {
            _uiState.update { it.copy(error = "API key not configured") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, error = null) }
            try {
                val model = GenerativeModel(modelRouter.getBestFlashModel(), apiKey)
                val prompt = content {
                    image(bitmap)
                    text(
                        "List all gym equipment visible in this image. " +
                        "Pay special attention to: Barbell, Bench, Pull-up Bar, Squat Cage, Cable Cross Machine. " +
                        "Return only a comma-separated list of equipment names, nothing else."
                    )
                }
                val response = model.generateContent(prompt)
                val text = response.text ?: ""
                val equipment = text.split(",").map { it.trim() }.filter { it.isNotBlank() }
                // Auto-check standard equipment toggles if detected
                val lowerEquipment = equipment.map { it.lowercase() }
                _uiState.update { state ->
                    state.copy(
                        detectedEquipment = (state.detectedEquipment + equipment).distinct(),
                        isAnalyzing = false,
                        hasBarbell = state.hasBarbell || lowerEquipment.any { it.contains("barbell") },
                        hasBench = state.hasBench || lowerEquipment.any { it.contains("bench") },
                        hasPullUpBar = state.hasPullUpBar || lowerEquipment.any { it.contains("pull") },
                        hasSquatCage = state.hasSquatCage || lowerEquipment.any { it.contains("squat") || it.contains("cage") || it.contains("rack") },
                        hasCableCross = state.hasCableCross || lowerEquipment.any { it.contains("cable") }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isAnalyzing = false, error = "Analysis failed: ${e.message}") }
            }
        }
    }

    fun updateGymDescription(text: String) { _uiState.update { it.copy(gymDescriptionText = text) } }

    fun extractEquipmentFromText() {
        val text = _uiState.value.gymDescriptionText.trim()
        if (text.isBlank()) return
        val apiKey = securePreferencesManager.getApiKey() ?: run {
            _uiState.update { it.copy(error = "API key not configured") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isExtractingText = true, error = null) }
            try {
                val model = GenerativeModel(modelRouter.getBestFlashModel(), apiKey)
                val response = model.generateContent(
                    "Extract a list of gym equipment from this description. " +
                    "Return only a comma-separated list of equipment names, nothing else.\n\n$text"
                )
                val result = response.text ?: ""
                val equipment = result.split(",").map { it.trim() }.filter { it.isNotBlank() }
                val lowerEquipment = equipment.map { it.lowercase() }
                _uiState.update { state ->
                    state.copy(
                        detectedEquipment = (state.detectedEquipment + equipment).distinct(),
                        isExtractingText = false,
                        hasBarbell = state.hasBarbell || lowerEquipment.any { it.contains("barbell") },
                        hasBench = state.hasBench || lowerEquipment.any { it.contains("bench") },
                        hasPullUpBar = state.hasPullUpBar || lowerEquipment.any { it.contains("pull") },
                        hasSquatCage = state.hasSquatCage || lowerEquipment.any { it.contains("squat") || it.contains("cage") || it.contains("rack") },
                        hasCableCross = state.hasCableCross || lowerEquipment.any { it.contains("cable") }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExtractingText = false, error = "Extraction failed: ${e.message}") }
            }
        }
    }

    fun removeEquipmentChip(item: String) {
        _uiState.update { it.copy(detectedEquipment = it.detectedEquipment - item) }
    }

    fun saveGym() {
        val state = _uiState.value
        val name = state.gymName.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(error = "Gym name is required") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val plateEquipment = state.selectedPlates.map { "${it}kg plate" }
            val standardEquipment = buildList {
                if (state.hasBarbell) add("Barbell")
                if (state.hasBench) add("Bench")
                if (state.hasPullUpBar) add("Pull-up Bar")
                if (state.hasSquatCage) add("Squat Cage")
                if (state.hasCableCross) add("Cable Cross Machine")
            }
            val allEquipment = (standardEquipment + plateEquipment + state.detectedEquipment).distinct()
            val profile = GymProfile(
                name = name,
                equipment = allEquipment.joinToString(","),
                isActive = false,
                notes = null,
                dumbbellMinKg = state.dumbbellMinKg,
                dumbbellMaxKg = state.dumbbellMaxKg
            )
            val insertedId = gymProfileRepository.insertProfile(profile)
            _uiState.update { it.copy(isSaving = false, saveSuccess = true, savedProfileId = insertedId) }
        }
    }
}
