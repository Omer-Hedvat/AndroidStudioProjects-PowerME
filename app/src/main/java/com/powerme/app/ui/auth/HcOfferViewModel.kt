package com.powerme.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.health.HealthConnectManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HcOfferUiState(
    val hcAvailable: Boolean = true,
    val hcPermissionDenied: Boolean = false,
    val isDone: Boolean = false
)

@HiltViewModel
class HcOfferViewModel @Inject constructor(
    private val healthConnectManager: HealthConnectManager,
    private val appSettingsDataStore: AppSettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(HcOfferUiState())
    val uiState: StateFlow<HcOfferUiState> = _uiState.asStateFlow()

    init {
        if (!healthConnectManager.isAvailable()) {
            _uiState.update { it.copy(hcAvailable = false, isDone = true) }
        }
    }

    fun onHcPermissionResult(granted: Set<String>) {
        viewModelScope.launch {
            val allGranted = healthConnectManager.checkPermissionsGranted()
            if (allGranted) {
                appSettingsDataStore.setHcOfferDismissed(true)
                _uiState.update { it.copy(isDone = true) }
            } else {
                _uiState.update { it.copy(hcPermissionDenied = true) }
            }
        }
    }

    fun skipHc() {
        viewModelScope.launch {
            appSettingsDataStore.setHcOfferDismissed(true)
            _uiState.update { it.copy(isDone = true) }
        }
    }
}
