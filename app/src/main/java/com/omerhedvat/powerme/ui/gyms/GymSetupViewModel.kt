package com.omerhedvat.powerme.ui.gyms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omerhedvat.powerme.data.database.GymProfile
import com.omerhedvat.powerme.data.repository.GymProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val detectedEquipment: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val savedProfileId: Long? = null,
    val error: String? = null
)

@HiltViewModel
class GymSetupViewModel @Inject constructor(
    private val gymProfileRepository: GymProfileRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GymSetupUiState())
    val uiState: StateFlow<GymSetupUiState> = _uiState.asStateFlow()

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
