package com.powerme.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.UnitSystem
import com.powerme.app.data.database.ExperienceLevel
import com.powerme.app.data.database.HealthHistoryEntry
import com.powerme.app.data.database.HealthHistoryType
import com.powerme.app.data.database.HealthHistorySeverity
import com.powerme.app.data.database.MetricType
import com.powerme.app.data.repository.HealthHistoryRepository
import com.powerme.app.data.repository.MetricLogRepository
import com.powerme.app.util.SurgicalValidator
import com.powerme.app.util.UnitConverter
import com.powerme.app.util.UserSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ProfileUiState(
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
    val personalInfoSaveMessage: String? = null,
    // Body Metrics
    val weightInput: String = "",
    val bodyFatInput: String = "",
    val heightInput: String = "",
    val heightFeetInput: String = "",
    val heightInchesInput: String = "",
    val lastWeight: Double? = null,
    val lastBodyFat: Double? = null,
    val lastHeight: Float? = null,
    val isSavingMetrics: Boolean = false,
    // Units (read from DataStore — affects Body Metrics display)
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    // Fitness Level
    val experienceLevel: ExperienceLevel? = null,
    val trainingAgeYears: Int = 0,
    // Health History
    val healthHistoryEntries: List<HealthHistoryEntry> = emptyList(),
    val showHealthHistorySheet: Boolean = false,
    val editingHealthEntry: HealthHistoryEntry? = null,
    // Sheet form fields
    val sheetType: HealthHistoryType = HealthHistoryType.INJURY,
    val sheetTitle: String = "",
    val sheetBodyRegion: String = "",
    val sheetSeverity: HealthHistorySeverity = HealthHistorySeverity.MODERATE,
    val sheetStartDate: Long? = null,
    val sheetResolvedDate: Long? = null,
    val sheetNotes: String = ""
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userSessionManager: UserSessionManager,
    private val metricLogRepository: MetricLogRepository,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val healthHistoryRepository: HealthHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadPersonalInfo()
        loadAppSettings()
        observeMetricLogs()
        observeHealthHistory()
    }

    // ── Personal Info ─────────────────────────────────────────────────────────

    private fun loadPersonalInfo() {
        viewModelScope.launch {
            val user = userSessionManager.getCurrentUser() ?: return@launch
            val targets = user.trainingTargets
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.toSet()
                ?: emptySet()
            val experienceLevel = user.experienceLevel?.let {
                runCatching { ExperienceLevel.valueOf(it) }.getOrNull()
            }
            _uiState.update {
                it.copy(
                    nameInput = user.name ?: "",
                    dateOfBirth = user.dateOfBirth,
                    averageSleepHoursInput = user.averageSleepHours?.toString() ?: "",
                    parentalLoadInput = user.parentalLoad?.toString() ?: "",
                    gender = user.gender ?: "",
                    occupationType = user.occupationType ?: "",
                    chronotype = user.chronotype ?: "",
                    selectedTrainingTargets = targets,
                    experienceLevel = experienceLevel,
                    trainingAgeYears = user.trainingAgeYears ?: 0
                )
            }
            loadUserHeight(user.heightCm)
        }
    }

    private fun loadUserHeight(heightCm: Float?) {
        if (heightCm == null) return
        val unit = _uiState.value.unitSystem
        if (unit == UnitSystem.IMPERIAL) {
            val (feet, inches) = UnitConverter.cmToFeetInches(heightCm.toDouble())
            _uiState.update {
                it.copy(lastHeight = heightCm, heightFeetInput = feet.toString(), heightInchesInput = inches.toString())
            }
        } else {
            _uiState.update { it.copy(lastHeight = heightCm, heightInput = heightCm.toInt().toString()) }
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

    // ── Body Metrics ──────────────────────────────────────────────────────────

    private fun loadAppSettings() {
        viewModelScope.launch {
            appSettingsDataStore.unitSystem.collect { unit ->
                _uiState.update { it.copy(unitSystem = unit) }
            }
        }
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

        val weightDisplay = (weightResult as? SurgicalValidator.ValidationResult.Valid)?.value
        val weightKg = weightDisplay?.let { UnitConverter.inputWeightToKg(it, unit) }
        val bodyFat = (bodyFatResult as? SurgicalValidator.ValidationResult.Valid)?.value

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
            weightKg?.let { metricLogRepository.log(MetricType.WEIGHT, it) }
            bodyFat?.let { metricLogRepository.log(MetricType.BODY_FAT, it) }
            heightCm?.toDouble()?.let { metricLogRepository.log(MetricType.HEIGHT, it) }
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

    // ── Fitness Level ─────────────────────────────────────────────────────────

    fun updateExperienceLevel(level: ExperienceLevel) {
        _uiState.update { it.copy(experienceLevel = level) }
        viewModelScope.launch {
            val current = userSessionManager.getCurrentUser() ?: return@launch
            userSessionManager.saveUser(current.copy(experienceLevel = level.name))
        }
    }

    fun updateTrainingAge(years: Int) {
        _uiState.update { it.copy(trainingAgeYears = years) }
        viewModelScope.launch {
            val current = userSessionManager.getCurrentUser() ?: return@launch
            userSessionManager.saveUser(current.copy(trainingAgeYears = years))
        }
    }

    // ── Health History ────────────────────────────────────────────────────────

    private fun observeHealthHistory() {
        viewModelScope.launch {
            healthHistoryRepository.getActiveEntries().collect { entries ->
                _uiState.update { it.copy(healthHistoryEntries = entries) }
            }
        }
    }

    fun openAddHealthEntry() {
        _uiState.update {
            it.copy(
                showHealthHistorySheet = true,
                editingHealthEntry = null,
                sheetType = HealthHistoryType.INJURY,
                sheetTitle = "",
                sheetBodyRegion = "",
                sheetSeverity = HealthHistorySeverity.MODERATE,
                sheetStartDate = null,
                sheetResolvedDate = null,
                sheetNotes = ""
            )
        }
    }

    fun openEditHealthEntry(entry: HealthHistoryEntry) {
        _uiState.update {
            it.copy(
                showHealthHistorySheet = true,
                editingHealthEntry = entry,
                sheetType = runCatching { HealthHistoryType.valueOf(entry.type) }.getOrElse { HealthHistoryType.INJURY },
                sheetTitle = entry.title,
                sheetBodyRegion = entry.bodyRegion ?: "",
                sheetSeverity = runCatching { HealthHistorySeverity.valueOf(entry.severity) }.getOrElse { HealthHistorySeverity.MODERATE },
                sheetStartDate = entry.startDate,
                sheetResolvedDate = entry.resolvedDate,
                sheetNotes = entry.notes ?: ""
            )
        }
    }

    fun dismissHealthHistorySheet() {
        _uiState.update { it.copy(showHealthHistorySheet = false, editingHealthEntry = null) }
    }

    fun updateSheetType(type: HealthHistoryType) { _uiState.update { it.copy(sheetType = type) } }
    fun updateSheetTitle(value: String) { _uiState.update { it.copy(sheetTitle = value) } }
    fun updateSheetBodyRegion(value: String) { _uiState.update { it.copy(sheetBodyRegion = value) } }
    fun updateSheetSeverity(severity: HealthHistorySeverity) { _uiState.update { it.copy(sheetSeverity = severity) } }
    fun updateSheetStartDate(epochMs: Long?) { _uiState.update { it.copy(sheetStartDate = epochMs) } }
    fun updateSheetResolvedDate(epochMs: Long?) { _uiState.update { it.copy(sheetResolvedDate = epochMs) } }
    fun updateSheetNotes(value: String) { _uiState.update { it.copy(sheetNotes = value) } }

    fun saveHealthEntry() {
        val state = _uiState.value
        val title = state.sheetTitle.trim()
        if (title.isBlank()) return

        val userId = runCatching {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: ""
        }.getOrElse { "" }

        val entry = HealthHistoryEntry(
            id = state.editingHealthEntry?.id ?: UUID.randomUUID().toString(),
            userId = userId,
            type = state.sheetType.name,
            title = title,
            bodyRegion = state.sheetBodyRegion.trim().takeIf { it.isNotBlank() },
            severity = state.sheetSeverity.name,
            startDate = state.sheetStartDate,
            resolvedDate = if (state.sheetSeverity == HealthHistorySeverity.RESOLVED) state.sheetResolvedDate else null,
            notes = state.sheetNotes.trim().takeIf { it.isNotBlank() },
            createdAt = state.editingHealthEntry?.createdAt ?: System.currentTimeMillis()
        )

        viewModelScope.launch {
            healthHistoryRepository.save(entry)
            _uiState.update { it.copy(showHealthHistorySheet = false, editingHealthEntry = null) }
        }
    }

    fun archiveHealthEntry(id: String) {
        viewModelScope.launch {
            healthHistoryRepository.archive(id)
            _uiState.update { it.copy(showHealthHistorySheet = false, editingHealthEntry = null) }
        }
    }
}
