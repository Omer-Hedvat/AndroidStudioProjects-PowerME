package com.powerme.app.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.sync.FirestoreSyncManager
import com.powerme.app.data.ThemeMode
import com.powerme.app.data.UnitSystem
import com.powerme.app.util.UnitConverter
import com.powerme.app.data.database.PowerMeDatabase
import com.powerme.app.data.database.UserSettings
import com.powerme.app.data.database.UserSettingsDao
import com.powerme.app.data.database.MetricType
import com.powerme.app.data.repository.MetricLogRepository
import com.powerme.app.health.HealthConnectManager
import com.powerme.app.health.HealthConnectReadResult
import com.powerme.app.util.DatabaseExporter
import com.powerme.app.util.SecurePreferencesManager
import com.powerme.app.util.SurgicalValidator
import com.powerme.app.util.UserSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class PlateState(val weight: Double, val isEnabled: Boolean)

data class SettingsUiState(
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
    // For imperial height input (feet + inches as separate fields)
    val heightFeetInput: String = "",
    val heightInchesInput: String = "",
    // Keep screen on
    val keepScreenOn: Boolean = false,
    // Appearance
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    // Units
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    // Cloud Sync
    val isSignedIn: Boolean = false,
    val isRestoringFromCloud: Boolean = false,
    val cloudRestoreMessage: String? = null,
    // Health Connect
    val healthConnectChecking: Boolean = true,
    val healthConnectAvailable: Boolean = false,
    val healthConnectPermissionsGranted: Boolean = false,
    val healthConnectPermissionsDenied: Boolean = false,
    val healthConnectSyncing: Boolean = false,
    val healthConnectData: HealthConnectReadResult? = null,
    val healthConnectError: String? = null,
    // Personal Info
    val nameInput: String = "",
    val dateOfBirth: Long? = null,
    val averageSleepHoursInput: String = "",
    val parentalLoadInput: String = "",
    val gender: String = "",
    val occupationType: String = "",
    val chronotype: String = "",
    val selectedTrainingTargets: Set<String> = emptySet(),
    val isSavingPersonalInfo: Boolean = false,
    val personalInfoSaveMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val securePreferencesManager: SecurePreferencesManager,
    private val userSettingsDao: UserSettingsDao,
    private val database: PowerMeDatabase,
    private val metricLogRepository: MetricLogRepository,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val userSessionManager: UserSessionManager,
    private val firestoreSyncManager: FirestoreSyncManager,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
    private val healthConnectManager: HealthConnectManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadUserSettings()
        loadAppSettings()
        observeMetricLogs()
        loadUserHeight()
        loadPersonalInfo()
        _uiState.update { it.copy(isSignedIn = auth.currentUser != null) }
        checkHealthConnectStatus()
    }

    private fun loadUserHeight() {
        viewModelScope.launch {
            val user = userSessionManager.getCurrentUser()
            val h = user?.heightCm
            if (h != null) {
                val unit = _uiState.value.unitSystem
                if (unit == UnitSystem.IMPERIAL) {
                    val (feet, inches) = UnitConverter.cmToFeetInches(h.toDouble())
                    _uiState.update {
                        it.copy(lastHeight = h, heightFeetInput = feet.toString(), heightInchesInput = inches.toString())
                    }
                } else {
                    _uiState.update { it.copy(lastHeight = h, heightInput = h.toInt().toString()) }
                }
            }
        }
    }

    private fun loadPersonalInfo() {
        viewModelScope.launch {
            val user = userSessionManager.getCurrentUser() ?: return@launch
            val targets = user.trainingTargets
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet()
            _uiState.update {
                it.copy(
                    nameInput = user.name ?: "",
                    dateOfBirth = user.dateOfBirth,
                    averageSleepHoursInput = user.averageSleepHours?.toString() ?: "",
                    parentalLoadInput = user.parentalLoad?.toString() ?: "",
                    gender = user.gender ?: "",
                    occupationType = user.occupationType ?: "",
                    chronotype = user.chronotype ?: "",
                    selectedTrainingTargets = targets
                )
            }
        }
    }

    fun updateNameInput(value: String) { _uiState.update { it.copy(nameInput = value) } }
    fun updateDateOfBirth(epochMs: Long?) { _uiState.update { it.copy(dateOfBirth = epochMs) } }
    fun updateSleepHoursInput(value: String) { _uiState.update { it.copy(averageSleepHoursInput = value) } }
    fun updateParentalLoadInput(value: String) { _uiState.update { it.copy(parentalLoadInput = value) } }
    fun updateGender(value: String) { _uiState.update { it.copy(gender = if (it.gender == value) "" else value) } }
    fun updateOccupationType(value: String) { _uiState.update { it.copy(occupationType = value) } }
    fun updateChronotype(value: String) { _uiState.update { it.copy(chronotype = value) } }
    fun toggleTrainingTarget(target: String) {
        _uiState.update {
            val updated = if (target in it.selectedTrainingTargets)
                it.selectedTrainingTargets - target
            else
                it.selectedTrainingTargets + target
            it.copy(selectedTrainingTargets = updated)
        }
    }

    fun savePersonalInfo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingPersonalInfo = true, personalInfoSaveMessage = null) }
            try {
                val current = userSessionManager.getCurrentUser() ?: return@launch
                val targets = _uiState.value.selectedTrainingTargets
                    .joinToString(",")
                    .takeIf { it.isNotBlank() }
                val updated = current.copy(
                    name = _uiState.value.nameInput.trim().takeIf { it.isNotBlank() },
                    dateOfBirth = _uiState.value.dateOfBirth,
                    averageSleepHours = _uiState.value.averageSleepHoursInput.toFloatOrNull(),
                    parentalLoad = _uiState.value.parentalLoadInput.toIntOrNull(),
                    gender = _uiState.value.gender.takeIf { it.isNotBlank() },
                    occupationType = _uiState.value.occupationType.takeIf { it.isNotBlank() },
                    chronotype = _uiState.value.chronotype.takeIf { it.isNotBlank() },
                    trainingTargets = targets
                )
                userSessionManager.saveUser(updated)
                _uiState.update { it.copy(isSavingPersonalInfo = false, personalInfoSaveMessage = "Saved") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSavingPersonalInfo = false, personalInfoSaveMessage = "Save failed: ${e.message}") }
            }
        }
    }

    fun dismissPersonalInfoSaveMessage() { _uiState.update { it.copy(personalInfoSaveMessage = null) } }

    private fun loadAppSettings() {
        viewModelScope.launch {
            appSettingsDataStore.keepScreenOn.collect { value ->
                _uiState.update { it.copy(keepScreenOn = value) }
            }
        }
        viewModelScope.launch {
            appSettingsDataStore.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            appSettingsDataStore.unitSystem.collect { unit ->
                _uiState.update { it.copy(unitSystem = unit) }
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            appSettingsDataStore.setThemeMode(mode)
            _uiState.update { it.copy(themeMode = mode) }
            firestoreSyncManager.pushAppPreferences()
        }
    }

    fun setUnitSystem(unit: UnitSystem) {
        viewModelScope.launch {
            appSettingsDataStore.setUnitSystem(unit)
            _uiState.update { it.copy(unitSystem = unit) }
            firestoreSyncManager.pushAppPreferences()
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
                            restTimerHapticsEnabled = settings.restTimerHapticsEnabled
                        )
                    }
                }
            }
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
            userSettingsDao.insertSettings(settings.copy(availablePlates = enabledPlates, updatedAt = System.currentTimeMillis()))
            firestoreSyncManager.pushSettings()
        }
    }

    fun toggleRestTimerAudio() {
        viewModelScope.launch {
            val newValue = !_uiState.value.restTimerAudioEnabled
            _uiState.update { it.copy(restTimerAudioEnabled = newValue) }
            val settings = userSettingsDao.getSettingsOnce() ?: UserSettings()
            userSettingsDao.insertSettings(settings.copy(restTimerAudioEnabled = newValue, updatedAt = System.currentTimeMillis()))
            firestoreSyncManager.pushSettings()
        }
    }

    fun toggleRestTimerHaptics() {
        viewModelScope.launch {
            val newValue = !_uiState.value.restTimerHapticsEnabled
            _uiState.update { it.copy(restTimerHapticsEnabled = newValue) }
            val settings = userSettingsDao.getSettingsOnce() ?: UserSettings()
            userSettingsDao.insertSettings(settings.copy(restTimerHapticsEnabled = newValue, updatedAt = System.currentTimeMillis()))
            firestoreSyncManager.pushSettings()
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

    private fun observeMetricLogs() {
        viewModelScope.launch {
            metricLogRepository.getByType(MetricType.WEIGHT).collect { entries ->
                val latestKg = entries.lastOrNull()?.value
                _uiState.update { state ->
                    val displayWeight = latestKg?.let { UnitConverter.displayWeight(it, state.unitSystem) }
                    state.copy(
                        lastWeight = latestKg,
                        weightInput = if (state.weightInput.isEmpty() && displayWeight != null)
                            "%.1f".format(displayWeight) else state.weightInput
                    )
                }
            }
        }
        viewModelScope.launch {
            metricLogRepository.getByType(MetricType.BODY_FAT).collect { entries ->
                val latest = entries.lastOrNull()?.value
                _uiState.update { state ->
                    state.copy(
                        lastBodyFat = latest,
                        bodyFatInput = if (state.bodyFatInput.isEmpty() && latest != null)
                            "%.1f".format(latest) else state.bodyFatInput
                    )
                }
            }
        }
    }

    fun updateWeightInput(value: String) { _uiState.update { it.copy(weightInput = value) } }
    fun updateBodyFatInput(value: String) { _uiState.update { it.copy(bodyFatInput = value) } }
    fun updateHeightInput(value: String) {
        val result = SurgicalValidator.parseDecimal(value)
        if (result !is SurgicalValidator.ValidationResult.Invalid) {
            _uiState.update { it.copy(heightInput = value) }
        }
    }
    fun updateHeightFeetInput(value: String) { _uiState.update { it.copy(heightFeetInput = value) } }
    fun updateHeightInchesInput(value: String) { _uiState.update { it.copy(heightInchesInput = value) } }

    fun saveBodyMetrics() {
        val unit = _uiState.value.unitSystem
        val weightResult = SurgicalValidator.parseDecimal(_uiState.value.weightInput.trim())
        val bodyFatResult = SurgicalValidator.parseDecimal(_uiState.value.bodyFatInput.trim())

        // Convert display weight → kg for storage
        val weightDisplay = (weightResult as? SurgicalValidator.ValidationResult.Valid)?.value
        val weightKg = weightDisplay?.let { UnitConverter.inputWeightToKg(it, unit) }
        val bodyFat = (bodyFatResult as? SurgicalValidator.ValidationResult.Valid)?.value

        // Resolve height → cm for storage
        val heightCm: Float? = if (unit == UnitSystem.IMPERIAL) {
            val feet = _uiState.value.heightFeetInput.trim().toIntOrNull()
            val inches = _uiState.value.heightInchesInput.trim().toIntOrNull() ?: 0
            if (feet != null) UnitConverter.feetInchesToCm(feet, inches).toFloat() else null
        } else {
            val heightResult = SurgicalValidator.parseDecimal(_uiState.value.heightInput.trim())
            (heightResult as? SurgicalValidator.ValidationResult.Valid)?.value?.toFloat()
        }

        if (weightKg == null && bodyFat == null && heightCm == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingMetrics = true) }
            // MetricLog sink — always store in metric
            weightKg?.let { metricLogRepository.log(MetricType.WEIGHT, it) }
            bodyFat?.let { metricLogRepository.log(MetricType.BODY_FAT, it) }
            heightCm?.toDouble()?.let { metricLogRepository.log(MetricType.HEIGHT, it) }
            // User entity sink
            val currentUser = userSessionManager.getCurrentUser()
            if (currentUser != null) {
                val updated = currentUser.copy(
                    weightKg = weightKg?.toFloat() ?: currentUser.weightKg,
                    bodyFatPercent = bodyFat?.toFloat() ?: currentUser.bodyFatPercent,
                    heightCm = heightCm ?: currentUser.heightCm
                )
                userSessionManager.saveUser(updated)
            }
            _uiState.update {
                val displayWeight = weightKg?.let { w -> UnitConverter.displayWeight(w, unit) }
                it.copy(
                    isSavingMetrics = false,
                    weightInput = displayWeight?.let { w -> "%.1f".format(w) } ?: it.weightInput,
                    bodyFatInput = bodyFat?.let { bf -> "%.1f".format(bf) } ?: it.bodyFatInput,
                    heightInput = if (unit == UnitSystem.METRIC) heightCm?.toInt()?.toString() ?: it.heightInput else it.heightInput,
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
            firestoreSyncManager.pushAppPreferences()
        }
    }

    fun showDeleteAccountDialog() { _uiState.update { it.copy(showDeleteAccountDialog = true) } }
    fun dismissDeleteAccountDialog() { _uiState.update { it.copy(showDeleteAccountDialog = false) } }

    fun restoreFromCloud() {
        if (auth.currentUser == null) {
            _uiState.update { it.copy(cloudRestoreMessage = "Sign in to restore from cloud") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRestoringFromCloud = true, cloudRestoreMessage = null) }
            val result = firestoreSyncManager.pullFromCloud()
            _uiState.update { it.copy(isRestoringFromCloud = false, cloudRestoreMessage = result.toUserMessage()) }
        }
    }

    fun dismissCloudRestoreMessage() {
        _uiState.update { it.copy(cloudRestoreMessage = null) }
    }

    fun recheckHealthConnectPermissions() {
        if (!_uiState.value.healthConnectAvailable) return
        viewModelScope.launch {
            val granted = healthConnectManager.checkPermissionsGranted()
            android.util.Log.d("PowerME_HC", "recheckOnResume: granted=$granted")
            if (granted) {
                _uiState.update {
                    it.copy(
                        healthConnectPermissionsGranted = true,
                        healthConnectPermissionsDenied = false
                    )
                }
                syncHealthConnect()
            }
        }
    }

    private fun checkHealthConnectStatus() {
        viewModelScope.launch {
            val available = healthConnectManager.isAvailable()
            android.util.Log.d("PowerME_HC", "checkHealthConnectStatus: available=$available")
            if (!available) {
                _uiState.update { it.copy(healthConnectChecking = false, healthConnectAvailable = false) }
                return@launch
            }
            val granted = healthConnectManager.checkPermissionsGranted()
            android.util.Log.d("PowerME_HC", "checkHealthConnectStatus: permissionsGranted=$granted")
            _uiState.update {
                it.copy(
                    healthConnectChecking = false,
                    healthConnectAvailable = true,
                    healthConnectPermissionsGranted = granted
                )
            }
        }
    }

    fun syncHealthConnect() {
        viewModelScope.launch {
            android.util.Log.d("PowerME_HC", "syncHealthConnect: starting")
            _uiState.update { it.copy(healthConnectSyncing = true, healthConnectError = null) }
            try {
                val data = healthConnectManager.syncAndRead()
                android.util.Log.d("PowerME_HC", "syncHealthConnect: success, data=$data")
                _uiState.update {
                    it.copy(
                        healthConnectSyncing = false,
                        healthConnectData = data,
                        healthConnectPermissionsGranted = true
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("PowerME_HC", "syncHealthConnect: error", e)
                _uiState.update {
                    it.copy(
                        healthConnectSyncing = false,
                        healthConnectError = e.message ?: "Sync failed"
                    )
                }
            }
        }
    }

    fun onHealthConnectPermissionResult(granted: Set<String>) {
        android.util.Log.d("PowerME_HC", "onPermissionResult: granted=${granted.size} perms, expected=${HealthConnectManager.ALL_PERMISSIONS.size}, set=$granted")
        if (granted.containsAll(HealthConnectManager.ALL_PERMISSIONS)) {
            android.util.Log.d("PowerME_HC", "onPermissionResult: allGranted=true")
            _uiState.update {
                it.copy(healthConnectPermissionsGranted = true, healthConnectPermissionsDenied = false)
            }
            syncHealthConnect()
        } else {
            // Callback returned fewer than all permissions. This can mean:
            // (a) user denied in the dialog, or
            // (b) the dialog was skipped because permissions were already granted at OS level
            //     (getSynchronousResult path). Re-query to distinguish the two cases.
            viewModelScope.launch {
                val actuallyGranted = healthConnectManager.checkPermissionsGranted()
                android.util.Log.d("PowerME_HC", "onPermissionResult: re-query granted=$actuallyGranted")
                _uiState.update {
                    it.copy(
                        healthConnectPermissionsGranted = actuallyGranted,
                        healthConnectPermissionsDenied = !actuallyGranted
                    )
                }
                if (actuallyGranted) syncHealthConnect()
            }
        }
    }

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
