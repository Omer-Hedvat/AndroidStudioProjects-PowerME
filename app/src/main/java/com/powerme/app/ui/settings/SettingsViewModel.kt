package com.powerme.app.ui.settings

import timber.log.Timber
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.secure.SecurePreferencesStore
import com.google.ai.client.generativeai.GenerativeModel
import com.powerme.app.ai.AiCoreAvailability
import com.powerme.app.ai.AiCoreStatus
import com.powerme.app.ai.GeminiKeyResolver
import com.powerme.app.data.sync.FirestoreSyncManager
import com.powerme.app.data.ThemeMode
import com.powerme.app.data.UnitSystem
import com.powerme.app.data.WorkoutStyle
import com.powerme.app.util.TimerSound
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

enum class ApiKeyStatus { UsingUser, UsingDefault, NoKey }

sealed class ApiKeyValidationState {
    object Idle : ApiKeyValidationState()
    object Validating : ApiKeyValidationState()
    object Valid : ApiKeyValidationState()
    object QuotaExceeded : ApiKeyValidationState()
    data class Invalid(val message: String) : ApiKeyValidationState()
}

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
    // Timer sound
    val timerSound: TimerSound = TimerSound.BEEP,
    // Notifications (watch & lock screen)
    val notificationsEnabled: Boolean = true,
    // Appearance
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    // Units
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    // Workout style
    val workoutStyle: WorkoutStyle = WorkoutStyle.HYBRID,
    // Cloud Sync
    val isSignedIn: Boolean = false,
    val isRestoringFromCloud: Boolean = false,
    val cloudRestoreMessage: String? = null,
    val isBackingUpToCloud: Boolean = false,
    val backupMessage: String? = null,
    // AI — API key (device-local, never synced to Firestore)
    val hasUserApiKey: Boolean = false,
    val userApiKeyInput: String = "",
    val apiKeyStatus: ApiKeyStatus = ApiKeyStatus.UsingDefault,
    val apiKeyValidation: ApiKeyValidationState = ApiKeyValidationState.Idle,
    val onDeviceAiStatus: AiCoreStatus = AiCoreStatus.NotSupported,
    // Health Connect
    val healthConnectChecking: Boolean = true,
    val healthConnectAvailable: Boolean = false,
    val healthConnectPermissionsGranted: Boolean = false,
    val healthConnectPermissionsDenied: Boolean = false,
    val healthConnectSyncing: Boolean = false,
    val healthConnectData: HealthConnectReadResult? = null,
    val healthConnectError: String? = null,
    // DEBUG — nuke HC data button
    val nukeHcInProgress: Boolean = false,
    val nukeHcResult: String? = null
)

@HiltViewModel
open class SettingsViewModel @Inject constructor(
    private val userSettingsDao: UserSettingsDao,
    private val database: PowerMeDatabase,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val firestoreSyncManager: FirestoreSyncManager,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
    private val healthConnectManager: HealthConnectManager,
    private val workoutDao: WorkoutDao,
    private val workoutSetDao: WorkoutSetDao,
    private val securePreferencesStore: SecurePreferencesStore,
    private val keyResolver: GeminiKeyResolver,
    private val aiCoreAvailability: AiCoreAvailability
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // DEBUG: set to true when nuke button triggers permission request, so the result callback
    // knows to auto-fire nukeHcData() instead of the normal connect flow.
    private var _nukePermissionPending = false

    init {
        loadUserSettings()
        loadAppSettings()
        loadApiKeyStatus()
        _uiState.update { it.copy(isSignedIn = auth.currentUser != null) }
        viewModelScope.launch {
            _uiState.update { it.copy(onDeviceAiStatus = aiCoreAvailability.check()) }
        }
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
        viewModelScope.launch {
            appSettingsDataStore.timerSound.collect { value ->
                _uiState.update { it.copy(timerSound = value) }
            }
        }
        viewModelScope.launch {
            appSettingsDataStore.notificationsEnabled.collect { value ->
                _uiState.update { it.copy(notificationsEnabled = value) }
            }
        }
        viewModelScope.launch {
            appSettingsDataStore.workoutStyle.collect { value ->
                _uiState.update { it.copy(workoutStyle = value) }
            }
        }
    }

    private fun loadApiKeyStatus() {
        val hasKey = securePreferencesStore.hasUserGeminiApiKey()
        val status = when {
            hasKey -> ApiKeyStatus.UsingUser
            keyResolver.resolve() is com.powerme.app.ai.KeyResolution.NoKey -> ApiKeyStatus.NoKey
            else -> ApiKeyStatus.UsingDefault
        }
        _uiState.update { it.copy(hasUserApiKey = hasKey, apiKeyStatus = status) }
    }

    fun updateApiKeyInput(text: String) {
        _uiState.update { it.copy(userApiKeyInput = text, apiKeyValidation = ApiKeyValidationState.Idle) }
    }

    fun saveUserApiKey() {
        val trimmed = _uiState.value.userApiKeyInput.trim()
        if (trimmed.isBlank()) return
        securePreferencesStore.setUserGeminiApiKey(trimmed)
        // Do NOT call firestoreSyncManager.pushAppPreferences() — API key is device-local
        _uiState.update {
            it.copy(
                userApiKeyInput = "",
                hasUserApiKey = true,
                apiKeyStatus = ApiKeyStatus.UsingUser,
                apiKeyValidation = ApiKeyValidationState.Validating
            )
        }
        viewModelScope.launch {
            _uiState.update { it.copy(apiKeyValidation = validateApiKey(trimmed)) }
        }
    }

    fun clearUserApiKey() {
        securePreferencesStore.clearUserGeminiApiKey()
        // Do NOT call firestoreSyncManager.pushAppPreferences() — API key is device-local
        val newStatus = if (keyResolver.resolve() is com.powerme.app.ai.KeyResolution.NoKey) {
            ApiKeyStatus.NoKey
        } else {
            ApiKeyStatus.UsingDefault
        }
        _uiState.update { it.copy(hasUserApiKey = false, apiKeyStatus = newStatus, apiKeyValidation = ApiKeyValidationState.Idle) }
    }

    open fun createGenerativeModel(key: String): GenerativeModel =
        GenerativeModel(modelName = "gemini-2.0-flash", apiKey = key)

    protected open suspend fun callGeminiForValidation(key: String) {
        createGenerativeModel(key).generateContent("hi")
    }

    private suspend fun validateApiKey(key: String): ApiKeyValidationState {
        return try {
            callGeminiForValidation(key)
            ApiKeyValidationState.Valid
        } catch (e: Throwable) {
            val msg = e.message?.lowercase() ?: ""
            when {
                "api key not valid" in msg || "api_key_invalid" in msg || "invalid api key" in msg ->
                    ApiKeyValidationState.Invalid("API key is not valid")
                "quota" in msg || "resource_exhausted" in msg || "429" in msg ->
                    ApiKeyValidationState.QuotaExceeded
                else -> {
                    Timber.w(e, "API key validation: unexpected error")
                    ApiKeyValidationState.Invalid(e.message ?: "Could not verify key")
                }
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

    fun setWorkoutStyle(style: WorkoutStyle) {
        viewModelScope.launch {
            appSettingsDataStore.setWorkoutStyle(style)
            _uiState.update { it.copy(workoutStyle = style) }
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

    fun setTimerSound(sound: TimerSound) {
        viewModelScope.launch {
            appSettingsDataStore.setTimerSound(sound)
            _uiState.update { it.copy(timerSound = sound) }
            firestoreSyncManager.pushAppPreferences()
        }
    }

    fun toggleNotificationsEnabled() {
        viewModelScope.launch {
            val newValue = !_uiState.value.notificationsEnabled
            appSettingsDataStore.setNotificationsEnabled(newValue)
            _uiState.update { it.copy(notificationsEnabled = newValue) }
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
            Timber.d("recheckOnResume: granted=$granted")
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
            Timber.d("checkHealthConnectStatus: available=$available")
            if (!available) {
                _uiState.update { it.copy(healthConnectChecking = false, healthConnectAvailable = false) }
                return@launch
            }
            val granted = healthConnectManager.checkPermissionsGranted()
            Timber.d("checkHealthConnectStatus: permissionsGranted=$granted")
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
            Timber.d("syncHealthConnect: starting")
            _uiState.update { it.copy(healthConnectSyncing = true, healthConnectError = null) }
            try {
                val data = healthConnectManager.syncAndRead()
                Timber.d("syncHealthConnect: success, data=$data")
                _uiState.update {
                    it.copy(
                        healthConnectSyncing = false,
                        healthConnectData = data,
                        healthConnectPermissionsGranted = true
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "syncHealthConnect: error")
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
        Timber.d("onPermissionResult: granted=${granted.size} perms, expected=${HealthConnectManager.CORE_PERMISSIONS.size}, set=$granted")

        // DEBUG: if this result came from the nuke permission request, auto-fire nuke and return.
        val writeExercise = androidx.health.connect.client.permission.HealthPermission
            .getWritePermission(androidx.health.connect.client.records.ExerciseSessionRecord::class)
        if (_nukePermissionPending && writeExercise in granted) {
            _nukePermissionPending = false
            nukeHcData()
            return
        }
        _nukePermissionPending = false

        if (granted.containsAll(HealthConnectManager.CORE_PERMISSIONS)) {
            Timber.d("onPermissionResult: allGranted=true")
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
                Timber.d("onPermissionResult: re-query granted=$actuallyGranted")
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
                securePreferencesStore.clearUserGeminiApiKey()
                Firebase.auth.currentUser?.delete()?.await()
                appSettingsDataStore.setLanguage("Hebrew")
            } catch (e: Exception) {
                Timber.e(e, "Delete account error")
            } finally {
                _uiState.update { it.copy(isDeletingAccount = false) }
                onComplete()
            }
        }
    }

    // ── DEBUG: nuke all PowerME-written HC records ────────────────────────────
    /** Called by the nuke button before launching the HC permission dialog. */
    fun prepareNukePermissionRequest() { _nukePermissionPending = true }

    fun nukeHcData() {
        if (!healthConnectManager.isAvailable()) {
            _uiState.update { it.copy(nukeHcResult = "Health Connect unavailable") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(nukeHcInProgress = true, nukeHcResult = null) }
            try {
                val client = androidx.health.connect.client.HealthConnectClient.getOrCreate(context)
                // Fetch all workout IDs ever recorded — clientRecordId = workout.id in HC.
                // Using a 2-year window to catch every record PowerME may have written.
                val sinceMs = System.currentTimeMillis() - 2L * 365 * 24 * 60 * 60 * 1000
                val ids = workoutDao.getCompletedWorkoutsSince(sinceMs).map { it.id }
                healthConnectManager.nukePowerMEData(client, ids)
                _uiState.update { it.copy(nukeHcInProgress = false, nukeHcResult = "Nuke complete (${ids.size} IDs) — check Logcat HealthConnect-Nuke") }
            } catch (e: Exception) {
                _uiState.update { it.copy(nukeHcInProgress = false, nukeHcResult = "Nuke FAILED: ${e.message}") }
            }
        }
    }
}
