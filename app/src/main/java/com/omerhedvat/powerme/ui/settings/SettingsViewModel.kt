package com.omerhedvat.powerme.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.omerhedvat.powerme.data.AppSettingsDataStore
import com.omerhedvat.powerme.data.database.GymProfile
import com.omerhedvat.powerme.data.database.PowerMeDatabase
import com.omerhedvat.powerme.data.database.UserSettings
import com.omerhedvat.powerme.data.database.UserSettingsDao
import com.omerhedvat.powerme.data.repository.GymProfileRepository
import com.omerhedvat.powerme.util.DatabaseExporter
import com.omerhedvat.powerme.util.GeminiModel
import com.omerhedvat.powerme.util.ModelRouter
import com.omerhedvat.powerme.util.SecurePreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class PlateState(val weight: Double, val isEnabled: Boolean)

data class SettingsUiState(
    val apiKey: String = "",
    val hasApiKey: Boolean = false,
    val showSuccessMessage: Boolean = false,
    val isValidatingKey: Boolean = false,
    val keyValidationError: String? = null,
    val availablePlates: List<PlateState> = listOf(
        PlateState(25.0, true), PlateState(20.0, true), PlateState(15.0, true),
        PlateState(10.0, true), PlateState(5.0, true), PlateState(2.5, true),
        PlateState(1.25, true), PlateState(0.5, true)
    ),
    val restTimerAudioEnabled: Boolean = true,
    val restTimerHapticsEnabled: Boolean = true,
    val isExporting: Boolean = false,
    val exportSuccessMessage: String? = null,
    val exportErrorMessage: String? = null,
    val gymProfiles: List<GymProfile> = emptyList(),
    val activeGym: GymProfile? = null,
    // Model discovery
    val availableModels: List<GeminiModel> = emptyList(),
    val isFetchingModels: Boolean = false,
    val selectedWarRoomModel: String = "gemini-2.0-flash-thinking-exp",
    val selectedEnrichmentModel: String = "gemini-1.5-flash",
    // Language
    val language: String = "Hebrew",
    // Account deletion
    val showDeleteAccountDialog: Boolean = false,
    val isDeletingAccount: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val securePreferencesManager: SecurePreferencesManager,
    private val userSettingsDao: UserSettingsDao,
    private val database: PowerMeDatabase,
    private val gymProfileRepository: GymProfileRepository,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val modelRouter: ModelRouter,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadApiKey()
        loadUserSettings()
        loadGymProfiles()
        loadAppSettings()
    }

    private fun loadAppSettings() {
        viewModelScope.launch {
            appSettingsDataStore.warRoomModel.collect { model ->
                _uiState.update { it.copy(selectedWarRoomModel = model) }
            }
        }
        viewModelScope.launch {
            appSettingsDataStore.enrichmentModel.collect { model ->
                _uiState.update { it.copy(selectedEnrichmentModel = model) }
            }
        }
        viewModelScope.launch {
            appSettingsDataStore.language.collect { lang ->
                _uiState.update { it.copy(language = lang) }
            }
        }
    }

    private fun loadUserSettings() {
        viewModelScope.launch {
            userSettingsDao.getSettings().collect { settings ->
                if (settings != null) {
                    val plateWeights = settings.availablePlates.split(",")
                        .mapNotNull { it.trim().toDoubleOrNull() }.toSet()
                    val plateStates = _uiState.value.availablePlates.map { plate ->
                        plate.copy(isEnabled = plateWeights.contains(plate.weight))
                    }
                    _uiState.update {
                        it.copy(
                            availablePlates = plateStates,
                            restTimerAudioEnabled = settings.restTimerAudioEnabled,
                            restTimerHapticsEnabled = settings.restTimerHapticsEnabled,
                            language = settings.language
                        )
                    }
                }
            }
        }
    }

    private fun loadApiKey() {
        val savedKey = securePreferencesManager.getApiKey()
        _uiState.update {
            it.copy(apiKey = savedKey ?: "", hasApiKey = securePreferencesManager.hasApiKey())
        }
    }

    fun updateApiKey(newKey: String) { _uiState.update { it.copy(apiKey = newKey) } }

    fun saveApiKey() {
        val key = _uiState.value.apiKey.trim()
        if (key.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isValidatingKey = true, keyValidationError = null) }
            val isValid = modelRouter.validateKey(key)
            if (isValid) {
                securePreferencesManager.saveApiKey(key)
                _uiState.update { it.copy(hasApiKey = true, showSuccessMessage = true, isValidatingKey = false) }
                fetchModels(key)
            } else {
                _uiState.update {
                    it.copy(isValidatingKey = false, keyValidationError = "Invalid API key — check your key at aistudio.google.com")
                }
            }
        }
    }

    private suspend fun fetchModels(apiKey: String) {
        _uiState.update { it.copy(isFetchingModels = true) }
        try {
            val models = modelRouter.fetchModels(apiKey)
            _uiState.update { it.copy(availableModels = models, isFetchingModels = false) }
        } catch (e: Exception) {
            _uiState.update { it.copy(isFetchingModels = false) }
        }
    }

    fun fetchModelsIfNeeded() {
        val key = securePreferencesManager.getApiKey() ?: return
        if (_uiState.value.availableModels.isEmpty() && !_uiState.value.isFetchingModels) {
            viewModelScope.launch { fetchModels(key) }
        }
    }

    fun clearApiKey() {
        securePreferencesManager.clearApiKey()
        _uiState.update { it.copy(apiKey = "", hasApiKey = false, showSuccessMessage = false) }
    }

    fun dismissSuccessMessage() { _uiState.update { it.copy(showSuccessMessage = false) } }

    fun setWarRoomModel(modelId: String) {
        viewModelScope.launch {
            appSettingsDataStore.setWarRoomModel(modelId)
            _uiState.update { it.copy(selectedWarRoomModel = modelId) }
        }
    }

    fun setEnrichmentModel(modelId: String) {
        viewModelScope.launch {
            appSettingsDataStore.setEnrichmentModel(modelId)
            _uiState.update { it.copy(selectedEnrichmentModel = modelId) }
        }
    }

    fun updateLanguage(language: String) {
        viewModelScope.launch {
            appSettingsDataStore.setLanguage(language)
            userSettingsDao.updateLanguage(language)
            _uiState.update { it.copy(language = language) }
        }
    }

    fun togglePlate(weight: Double) {
        viewModelScope.launch {
            val updatedPlates = _uiState.value.availablePlates.map { plate ->
                if (plate.weight == weight) plate.copy(isEnabled = !plate.isEnabled) else plate
            }
            _uiState.update { it.copy(availablePlates = updatedPlates) }
            val enabledPlates = updatedPlates.filter { it.isEnabled }.map { it.weight.toString() }.joinToString(",")
            val settings = userSettingsDao.getSettingsOnce() ?: UserSettings()
            userSettingsDao.insertSettings(settings.copy(availablePlates = enabledPlates))
        }
    }

    fun toggleRestTimerAudio() {
        viewModelScope.launch {
            val newValue = !_uiState.value.restTimerAudioEnabled
            _uiState.update { it.copy(restTimerAudioEnabled = newValue) }
            userSettingsDao.updateRestTimerAudio(newValue)
        }
    }

    fun toggleRestTimerHaptics() {
        viewModelScope.launch {
            val newValue = !_uiState.value.restTimerHapticsEnabled
            _uiState.update { it.copy(restTimerHapticsEnabled = newValue) }
            userSettingsDao.updateRestTimerHaptics(newValue)
        }
    }

    fun exportDatabase() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportErrorMessage = null, exportSuccessMessage = null) }
            try {
                val exporter = DatabaseExporter(context, database)
                val result = exporter.exportDatabase()
                if (result.success) {
                    _uiState.update { it.copy(isExporting = false, exportSuccessMessage = "Exported to: ${result.jsonFilePath}") }
                } else {
                    _uiState.update { it.copy(isExporting = false, exportErrorMessage = result.error ?: "Export failed") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isExporting = false, exportErrorMessage = e.message ?: "Unknown error") }
            }
        }
    }

    fun dismissExportMessages() {
        _uiState.update { it.copy(exportSuccessMessage = null, exportErrorMessage = null) }
    }

    private fun loadGymProfiles() {
        viewModelScope.launch {
            gymProfileRepository.getAllProfiles().collect { profiles ->
                _uiState.update { it.copy(gymProfiles = profiles) }
            }
        }
        viewModelScope.launch {
            gymProfileRepository.getActiveProfile().collect { activeGym ->
                _uiState.update { it.copy(activeGym = activeGym) }
            }
        }
    }

    fun switchToGym(gymName: String) {
        viewModelScope.launch {
            val profile = gymProfileRepository.getProfileByName(gymName)
            if (profile != null) gymProfileRepository.setActiveProfile(profile.id)
        }
    }

    fun showDeleteAccountDialog() { _uiState.update { it.copy(showDeleteAccountDialog = true) } }
    fun dismissDeleteAccountDialog() { _uiState.update { it.copy(showDeleteAccountDialog = false) } }

    fun deleteAccount(onComplete: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingAccount = true, showDeleteAccountDialog = false) }
            try {
                database.clearAllTables()
                Firebase.auth.currentUser?.delete()?.await()
                securePreferencesManager.clearApiKey()
                appSettingsDataStore.setLanguage("Hebrew")
            } catch (e: Exception) {
                android.util.Log.e("SettingsViewModel", "Delete account error", e)
            } finally {
                _uiState.update { it.copy(isDeletingAccount = false) }
                onComplete()
            }
        }
    }
}
