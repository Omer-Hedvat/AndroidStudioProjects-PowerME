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
import com.powerme.app.data.database.PowerMeDatabase
import com.powerme.app.data.database.UserSettings
import com.powerme.app.data.database.UserSettingsDao
import com.powerme.app.data.database.WorkoutDao
import com.powerme.app.data.database.WorkoutSetDao
import com.powerme.app.health.HealthConnectManager
import com.powerme.app.health.HealthConnectReadResult
import com.powerme.app.util.DatabaseExporter
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
    // Keep screen on
    val keepScreenOn: Boolean = false,
    // RPE auto-pop
    val useRpeAutoPop: Boolean = false,
    // Get Ready countdown before timed sets
    val timedSetSetupSeconds: Int = 3,
    // Appearance
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    // Units
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    // Cloud Sync
    val isSignedIn: Boolean = false,
    val isRestoringFromCloud: Boolean = false,
    val cloudRestoreMessage: String? = null,
    val isBackingUpToCloud: Boolean = false,
    val backupMessage: String? = null,
    // Health Connect
    val healthConnectChecking: Boolean = true,
    val healthConnectAvailable: Boolean = false,
    val healthConnectPermissionsGranted: Boolean = false,
    val healthConnectPermissionsDenied: Boolean = false,
    val healthConnectSyncing: Boolean = false,
    val healthConnectData: HealthConnectReadResult? = null,
    val healthConnectError: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userSettingsDao: UserSettingsDao,
    private val database: PowerMeDatabase,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val firestoreSyncManager: FirestoreSyncManager,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
    private val healthConnectManager: HealthConnectManager,
    private val workoutDao: WorkoutDao,
    private val workoutSetDao: WorkoutSetDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadUserSettings()
        loadAppSettings()
        _uiState.update { it.copy(isSignedIn = auth.currentUser != null) }
        checkHealthConnectStatus()
    }

    private fun loadAppSettings() {
        viewModelScope.launch {
            appSettingsDataStore.keepScreenOn.collect { value ->
                _uiState.update { it.copy(keepScreenOn = value) }
            }
        }
        viewModelScope.launch {
            appSettingsDataStore.useRpeAutoPop.collect { value ->
                _uiState.update { it.copy(useRpeAutoPop = value) }
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
        viewModelScope.launch {
            appSettingsDataStore.timedSetSetupSeconds.collect { value ->
                _uiState.update { it.copy(timedSetSetupSeconds = value) }
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

    fun toggleKeepScreenOn() {
        viewModelScope.launch {
            val newValue = !_uiState.value.keepScreenOn
            appSettingsDataStore.setKeepScreenOn(newValue)
            _uiState.update { it.copy(keepScreenOn = newValue) }
            firestoreSyncManager.pushAppPreferences()
        }
    }

    fun toggleUseRpeAutoPop() {
        viewModelScope.launch {
            val newValue = !_uiState.value.useRpeAutoPop
            appSettingsDataStore.setUseRpeAutoPop(newValue)
            _uiState.update { it.copy(useRpeAutoPop = newValue) }
            firestoreSyncManager.pushAppPreferences()
        }
    }

    fun setTimedSetSetupSeconds(seconds: Int) {
        viewModelScope.launch {
            appSettingsDataStore.setTimedSetSetupSeconds(seconds)
            _uiState.update { it.copy(timedSetSetupSeconds = seconds) }
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

    fun backupToCloud() {
        if (auth.currentUser == null) {
            _uiState.update { it.copy(backupMessage = "Sign in to back up to cloud") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isBackingUpToCloud = true, backupMessage = null) }
            val result = firestoreSyncManager.pushAllToCloud()
            val message = if (result.success) {
                "Backed up: ${result.workoutsImported} workouts, ${result.routinesImported} routines"
            } else {
                "Backup failed: ${result.error ?: "unknown"}"
            }
            _uiState.update { it.copy(isBackingUpToCloud = false, backupMessage = message) }
        }
    }

    fun dismissBackupMessage() {
        _uiState.update { it.copy(backupMessage = null) }
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
                triggerBackfillIfNeeded()
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

    private fun triggerBackfillIfNeeded() {
        viewModelScope.launch {
            val alreadyDone = appSettingsDataStore.hcWorkoutBackfillDone.first()
            if (!alreadyDone) {
                appSettingsDataStore.setHcWorkoutBackfillDone(true)  // flip BEFORE backfill runs
                healthConnectManager.backfillWorkoutSessions(workoutDao, workoutSetDao)
            }
        }
    }

    fun onHealthConnectPermissionResult(granted: Set<String>) {
        android.util.Log.d("PowerME_HC", "onPermissionResult: granted=${granted.size} perms, expected=${HealthConnectManager.CORE_PERMISSIONS.size}, set=$granted")
        if (granted.containsAll(HealthConnectManager.CORE_PERMISSIONS)) {
            android.util.Log.d("PowerME_HC", "onPermissionResult: allGranted=true")
            _uiState.update {
                it.copy(healthConnectPermissionsGranted = true, healthConnectPermissionsDenied = false)
            }
            syncHealthConnect()
            triggerBackfillIfNeeded()
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
                if (actuallyGranted) {
                    syncHealthConnect()
                    triggerBackfillIfNeeded()
                }
            }
        }
    }

    fun deleteAccount(onComplete: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isDeletingAccount = true, showDeleteAccountDialog = false) }
            try {
                database.clearAllTables()
                Firebase.auth.currentUser?.delete()?.await()
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
