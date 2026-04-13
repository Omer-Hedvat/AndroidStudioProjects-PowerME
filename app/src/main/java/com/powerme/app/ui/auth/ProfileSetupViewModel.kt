package com.powerme.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.UnitSystem
import com.powerme.app.data.database.User
import com.powerme.app.health.HealthConnectManager
import com.powerme.app.util.UserSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileSetupUiState(
    /** 1 = Health Connect offer, 2 = profile form */
    val currentStep: Int = 1,
    // HC state
    val hcAvailable: Boolean = false,
    val hcConnected: Boolean = false,
    val hcPermissionDenied: Boolean = false,
    val hcWeight: Double? = null,
    val hcHeight: Float? = null,
    val hcBodyFat: Double? = null,
    // Google pre-fill
    val googleDisplayName: String? = null,
    // Save state
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val profileSaved: Boolean = false
)

@HiltViewModel
class ProfileSetupViewModel @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
    private val userSessionManager: UserSessionManager,
    private val firebaseAuth: FirebaseAuth,
    private val appSettingsDataStore: AppSettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileSetupUiState())
    val uiState: StateFlow<ProfileSetupUiState> = _uiState.asStateFlow()

    val unitSystem: StateFlow<UnitSystem> = appSettingsDataStore.unitSystem
        .stateIn(viewModelScope, SharingStarted.Eagerly, UnitSystem.METRIC)

    init {
        viewModelScope.launch {
            val available = healthConnectManager.isAvailable()
            val displayName = firebaseAuth.currentUser?.displayName

            if (available && healthConnectManager.checkPermissionsGranted()) {
                // Permissions already granted from a prior interrupted onboarding — auto-read.
                val data = healthConnectManager.readAllData()
                _uiState.update {
                    it.copy(
                        hcAvailable = true,
                        hcConnected = true,
                        hcWeight = data.weight,
                        hcHeight = data.height,
                        hcBodyFat = data.bodyFat,
                        googleDisplayName = displayName,
                        currentStep = 2
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        hcAvailable = available,
                        googleDisplayName = displayName,
                        currentStep = if (available) 1 else 2
                    )
                }
            }
        }
    }

    /**
     * Called after the system HC permission dialog returns.
     * [granted] is the set of permission strings the user actually granted.
     */
    fun onHcPermissionResult(granted: Set<String>) {
        viewModelScope.launch {
            val allGranted = healthConnectManager.checkPermissionsGranted()
            if (allGranted) {
                val data = healthConnectManager.readAllData()
                _uiState.update {
                    it.copy(
                        hcConnected = true,
                        hcPermissionDenied = false,
                        hcWeight = data.weight,
                        hcHeight = data.height,
                        hcBodyFat = data.bodyFat,
                        currentStep = 2
                    )
                }
            } else {
                _uiState.update { it.copy(hcPermissionDenied = true) }
            }
        }
    }

    fun setUnitSystem(unit: UnitSystem) {
        viewModelScope.launch { appSettingsDataStore.setUnitSystem(unit) }
    }

    /** User tapped "Skip" on the HC offer screen. */
    fun skipHc() {
        _uiState.update { it.copy(currentStep = 2) }
    }

    fun saveProfile(
        name: String?,
        dateOfBirth: Long?,
        heightCm: Float?,
        weightKg: Float?,
        bodyFatPercent: Float?,
        occupationType: String?,
        parentalLoad: Int?,
        chronotype: String?,
        averageSleepHours: Float?,
        gender: String?,
        trainingTargets: String?
    ) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isSaving = true, saveError = null) }
                val email = firebaseAuth.currentUser?.email ?: run {
                    _uiState.update { it.copy(isSaving = false, saveError = "Not signed in") }
                    return@launch
                }
                val user = User(
                    email = email,
                    name = name?.takeIf { it.isNotBlank() },
                    dateOfBirth = dateOfBirth,
                    heightCm = heightCm,
                    weightKg = weightKg,
                    bodyFatPercent = bodyFatPercent,
                    occupationType = occupationType,
                    parentalLoad = parentalLoad,
                    chronotype = chronotype,
                    averageSleepHours = averageSleepHours,
                    gender = gender,
                    trainingTargets = trainingTargets
                )
                userSessionManager.saveUser(user)
                _uiState.update { it.copy(isSaving = false, profileSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, saveError = e.message) }
            }
        }
    }
}
