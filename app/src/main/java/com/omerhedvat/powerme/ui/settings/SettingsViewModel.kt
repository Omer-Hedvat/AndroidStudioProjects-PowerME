package com.omerhedvat.powerme.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.omerhedvat.powerme.data.AppSettingsDataStore
import com.omerhedvat.powerme.data.database.GymProfile
import com.omerhedvat.powerme.health.HealthConnectManager
import com.omerhedvat.powerme.data.database.PowerMeDatabase
import com.omerhedvat.powerme.data.database.UserSettings
import com.omerhedvat.powerme.data.database.UserSettingsDao
import com.omerhedvat.powerme.data.database.MetricType
import com.omerhedvat.powerme.data.repository.GymProfileRepository
import com.omerhedvat.powerme.data.repository.MetricLogRepository
import com.omerhedvat.powerme.util.DatabaseExporter
import com.omerhedvat.powerme.util.GeminiModel
import com.omerhedvat.powerme.util.ModelRouter
import com.omerhedvat.powerme.util.SecurePreferencesManager
import com.omerhedvat.powerme.util.SurgicalValidator
import com.omerhedvat.powerme.util.UserSessionManager
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
    val selectedWarRoomModel: String = "gemini-2.0-flash",
    val selectedEnrichmentModel: String = "gemini-1.5-flash",
    // Language
    val language: String = "Hebrew",
    // Account deletion
    val showDeleteAccountDialog: Boolean = false,
    val isDeletingAccount: Boolean = false,
    // Body metrics
    val weightInput: String = "",
    val bodyFatInput: String = "",
    val heightInput: String = "",
    val lastWeight: Double? = null,
    val lastBodyFat: Double? = null,
    val lastHeight: Float? = null,
    val isSavingMetrics: Boolean = false,
    // Keep screen on
    val keepScreenOn: Boolean = false,
    // Health Connect
    val bodyMeasurementsFromHC: Boolean = false,
    val isSyncingFromHC: Boolean = false,
    val hcSyncError: String? = null,
    val hcPermissionsMissing: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val securePreferencesManager: SecurePreferencesManager,
    private val userSettingsDao: UserSettingsDao,
    private val database: PowerMeDatabase,
    private val gymProfileRepository: GymProfileRepository,
    private val metricLogRepository: MetricLogRepository,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val modelRouter: ModelRouter,
    private val healthConnectManager: HealthConnectManager,
    private val userSessionManager: UserSessionManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadApiKey()
        loadUserSettings()
        loadGymProfiles()
        loadAppSettings()
        observeMetricLogs()
        loadUserHeight()
    }

    private fun loadUserHeight() {
        viewModelScope.launch {
            val user = userSessionManager.getCurrentUser()
            val h = user?.heightCm
            if (h != null) {
                _uiState.update { it.copy(lastHeight = h, heightInput = h.toInt().toString()) }
            }
        }
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
        viewModelScope.launch {
            appSettingsDataStore.keepScreenOn.collect { value ->
                _uiState.update { it.copy(keepScreenOn = value) }
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
        if (!_uiState.value.isFetchingModels) {
            viewModelScope.launch {
                val lastFetched = appSettingsDataStore.modelsLastFetched.first()
                val stale = lastFetched == 0L ||
                    System.currentTimeMillis() - lastFetched > 24 * 60 * 60 * 1000L
                if (_uiState.value.availableModels.isEmpty() || stale) {
                    fetchModels(key)
                    appSettingsDataStore.setModelsLastFetched(System.currentTimeMillis())
                }
            }
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

    private fun observeMetricLogs() {
        viewModelScope.launch {
            metricLogRepository.getByType(MetricType.WEIGHT).collect { entries ->
                _uiState.update { it.copy(lastWeight = entries.lastOrNull()?.value) }
            }
        }
        viewModelScope.launch {
            metricLogRepository.getByType(MetricType.BODY_FAT).collect { entries ->
                _uiState.update { it.copy(lastBodyFat = entries.lastOrNull()?.value) }
            }
        }
    }

    fun updateWeightInput(value: String) { _uiState.update { it.copy(weightInput = value, bodyMeasurementsFromHC = false) } }
    fun updateBodyFatInput(value: String) { _uiState.update { it.copy(bodyFatInput = value, bodyMeasurementsFromHC = false) } }
    fun updateHeightInput(value: String) {
        val result = SurgicalValidator.parseDecimal(value)
        if (result !is SurgicalValidator.ValidationResult.Invalid) {
            _uiState.update { it.copy(heightInput = value, bodyMeasurementsFromHC = false) }
        }
    }

    fun syncFromHealthConnect() {
        if (!healthConnectManager.isAvailable()) {
            _uiState.update { it.copy(hcSyncError = "Health Connect is not available on this device") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncingFromHC = true, hcSyncError = null, hcPermissionsMissing = false) }
            val permissionsGranted = healthConnectManager.checkPermissionsGranted()
            if (!permissionsGranted) {
                _uiState.update { it.copy(isSyncingFromHC = false, hcPermissionsMissing = true) }
                return@launch
            }
            val weight = healthConnectManager.getLatestWeight()
            val bodyFat = healthConnectManager.getLatestBodyFat()
            val height = healthConnectManager.getLatestHeight()
            _uiState.update {
                it.copy(
                    isSyncingFromHC = false,
                    weightInput = weight?.let { w -> "%.1f".format(w) } ?: it.weightInput,
                    bodyFatInput = bodyFat?.let { bf -> "%.1f".format(bf) } ?: it.bodyFatInput,
                    heightInput = height?.let { h -> h.toInt().toString() } ?: it.heightInput,
                    bodyMeasurementsFromHC = weight != null || bodyFat != null || height != null,
                    hcSyncError = if (weight == null && bodyFat == null && height == null) "No recent data found in Health Connect" else null
                )
            }
        }
    }

    fun saveBodyMetrics() {
        val weightResult = SurgicalValidator.parseDecimal(_uiState.value.weightInput.trim())
        val bodyFatResult = SurgicalValidator.parseDecimal(_uiState.value.bodyFatInput.trim())
        val heightResult = SurgicalValidator.parseDecimal(_uiState.value.heightInput.trim())
        val weight = (weightResult as? SurgicalValidator.ValidationResult.Valid)?.value
        val bodyFat = (bodyFatResult as? SurgicalValidator.ValidationResult.Valid)?.value
        val heightCm = (heightResult as? SurgicalValidator.ValidationResult.Valid)?.value?.toFloat()
        if (weight == null && bodyFat == null && heightCm == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingMetrics = true) }
            // MetricLog sink
            weight?.let { metricLogRepository.log(MetricType.WEIGHT, it) }
            bodyFat?.let { metricLogRepository.log(MetricType.BODY_FAT, it) }
            heightCm?.toDouble()?.let { metricLogRepository.log(MetricType.HEIGHT, it) }
            // User entity sink
            val currentUser = userSessionManager.getCurrentUser()
            if (currentUser != null) {
                val updated = currentUser.copy(
                    weightKg = weight?.toFloat() ?: currentUser.weightKg,
                    bodyFatPercent = bodyFat?.toFloat() ?: currentUser.bodyFatPercent,
                    heightCm = heightCm ?: currentUser.heightCm
                )
                userSessionManager.saveUser(updated)
            }
            _uiState.update {
                it.copy(
                    isSavingMetrics = false,
                    weightInput = "",
                    bodyFatInput = "",
                    heightInput = "",
                    lastHeight = heightCm ?: it.lastHeight
                )
            }
        }
    }

    fun toggleKeepScreenOn() {
        viewModelScope.launch {
            val newValue = !_uiState.value.keepScreenOn
            appSettingsDataStore.setKeepScreenOn(newValue)
            _uiState.update { it.copy(keepScreenOn = newValue) }
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
