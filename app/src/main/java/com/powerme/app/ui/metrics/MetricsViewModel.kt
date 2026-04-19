package com.powerme.app.ui.metrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.UnitSystem
import com.powerme.app.data.database.HealthConnectSyncDao
import com.powerme.app.data.database.MetricLog
import com.powerme.app.data.database.MetricType
import com.powerme.app.data.database.ageYears
import com.powerme.app.data.repository.MetricLogRepository
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
import kotlin.math.abs
import javax.inject.Inject

data class MetricsUiState(
    val weightEntries: List<MetricLog> = emptyList(),
    val bodyFatEntries: List<MetricLog> = emptyList(),
    val weightInput: String = "",
    val bodyFatInput: String = "",
    val isSavingMetrics: Boolean = false,
    val metricSaveMessage: String? = null,
    val bodyVitals: BodyVitalsState = BodyVitalsState()
)

@HiltViewModel
class MetricsViewModel @Inject constructor(
    private val metricLogRepository: MetricLogRepository,
    private val healthConnectManager: HealthConnectManager,
    private val userSessionManager: UserSessionManager,
    private val healthConnectSyncDao: HealthConnectSyncDao,
    private val appSettingsDataStore: AppSettingsDataStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(MetricsUiState())
    val uiState: StateFlow<MetricsUiState> = _uiState.asStateFlow()

    val unitSystem: StateFlow<UnitSystem> = appSettingsDataStore.unitSystem
        .stateIn(viewModelScope, SharingStarted.Eagerly, UnitSystem.METRIC)

    init {
        observeMetricLogs()
        loadBodyVitals()
    }

    private fun observeMetricLogs() {
        viewModelScope.launch {
            metricLogRepository.getByType(MetricType.WEIGHT).collect { entries ->
                _uiState.update { it.copy(weightEntries = entries) }
                recomputeDeltas()
            }
        }
        viewModelScope.launch {
            metricLogRepository.getByType(MetricType.BODY_FAT).collect { entries ->
                _uiState.update { it.copy(bodyFatEntries = entries) }
                recomputeDeltas()
            }
        }
    }

    private fun recomputeDeltas() {
        val state = _uiState.value
        val latestWeight = state.weightEntries.lastOrNull()?.value
        val latestBodyFat = state.bodyFatEntries.lastOrNull()?.value
        val newWeightKg = latestWeight ?: state.bodyVitals.weightKg
        val newBmi = computeBmi(newWeightKg, state.bodyVitals.heightCm)
        _uiState.update {
            it.copy(bodyVitals = it.bodyVitals.copy(
                weightKg = newWeightKg ?: it.bodyVitals.weightKg,
                bodyFatPct = latestBodyFat ?: it.bodyVitals.bodyFatPct,
                bmi = newBmi ?: it.bodyVitals.bmi,
                weightDelta7d = compute7dDelta(state.weightEntries),
                bodyFatDelta7d = compute7dDelta(state.bodyFatEntries)
            ))
        }
    }

    fun loadBodyVitals() {
        viewModelScope.launch {
            _uiState.update { it.copy(bodyVitals = it.bodyVitals.copy(hcAvailability = HcAvailability.CHECKING)) }
            doLoadBodyVitals()
        }
    }

    private suspend fun doLoadBodyVitals() {
        val available = healthConnectManager.isAvailable()
        if (!available) {
            _uiState.update { it.copy(bodyVitals = it.bodyVitals.copy(hcAvailability = HcAvailability.UNAVAILABLE)) }
            return
        }
        val granted = healthConnectManager.checkPermissionsGranted()
        if (!granted) {
            _uiState.update { it.copy(bodyVitals = it.bodyVitals.copy(hcAvailability = HcAvailability.AVAILABLE_NOT_GRANTED)) }
            return
        }

        val user = userSessionManager.getCurrentUser()
        val latestSync = healthConnectSyncDao.getLatestSync()
        val weightKg = _uiState.value.weightEntries.lastOrNull()?.value ?: user?.weightKg?.toDouble()
        val bodyFatPct = _uiState.value.bodyFatEntries.lastOrNull()?.value ?: user?.bodyFatPercent?.toDouble()
        val heightCm = user?.heightCm?.toDouble()
        val bmi = computeBmi(weightKg, heightCm)

        _uiState.update {
            it.copy(bodyVitals = it.bodyVitals.copy(
                hcAvailability = HcAvailability.AVAILABLE_GRANTED,
                age = user?.ageYears,
                weightKg = weightKg,
                bodyFatPct = bodyFatPct,
                heightCm = heightCm,
                bmi = bmi,
                sleepMinutes = latestSync?.sleepDurationMinutes ?: it.bodyVitals.sleepMinutes,
                hrvMs = latestSync?.hrv ?: it.bodyVitals.hrvMs,
                rhrBpm = latestSync?.rhr ?: it.bodyVitals.rhrBpm,
                stepsToday = latestSync?.steps ?: it.bodyVitals.stepsToday,
                lastSyncTimestamp = latestSync?.syncTimestamp ?: it.bodyVitals.lastSyncTimestamp,
                weightDelta7d = compute7dDelta(_uiState.value.weightEntries),
                bodyFatDelta7d = compute7dDelta(_uiState.value.bodyFatEntries),
                sleepScore = latestSync?.sleepScore ?: it.bodyVitals.sleepScore,
                avgHeartRateBpm = latestSync?.avgHeartRateBpm ?: it.bodyVitals.avgHeartRateBpm,
                vo2MaxMlKgMin = latestSync?.vo2MaxMlKgMin ?: it.bodyVitals.vo2MaxMlKgMin,
                spo2Percent = latestSync?.spo2Percent ?: it.bodyVitals.spo2Percent,
                lowSpO2Flag = latestSync?.lowSpO2Flag ?: it.bodyVitals.lowSpO2Flag,
                activeCaloriesKcal = latestSync?.activeCaloriesKcal ?: it.bodyVitals.activeCaloriesKcal,
                distanceMetres = latestSync?.distanceMetres ?: it.bodyVitals.distanceMetres
            ))
        }
    }

    fun syncHealthConnect() {
        viewModelScope.launch {
            _uiState.update { it.copy(bodyVitals = it.bodyVitals.copy(isSyncing = true, syncError = null)) }
            try {
                val result = healthConnectManager.syncAndRead()
                // Apply HC result directly so data shows even if the DB write failed silently
                _uiState.update {
                    it.copy(bodyVitals = it.bodyVitals.copy(
                        hcAvailability = HcAvailability.AVAILABLE_GRANTED,
                        sleepMinutes = result.sleepMinutes,
                        hrvMs = result.hrv,
                        rhrBpm = result.rhr,
                        stepsToday = result.steps,
                        lastSyncTimestamp = System.currentTimeMillis(),
                        sleepScore = result.sleepScore,
                        avgHeartRateBpm = result.avgHeartRateBpm,
                        vo2MaxMlKgMin = result.vo2MaxMlKgMin,
                        spo2Percent = result.spo2Percent,
                        lowSpO2Flag = result.lowSpO2Flag,
                        activeCaloriesKcal = result.activeCaloriesKcal,
                        distanceMetres = result.distanceMetres
                    ))
                }
                // Reload from DB for weight/bodyFat deltas and user profile — null-coalesces with live values above
                doLoadBodyVitals()
            } catch (e: Exception) {
                _uiState.update { it.copy(bodyVitals = it.bodyVitals.copy(syncError = e.message ?: "Sync failed")) }
            } finally {
                _uiState.update { it.copy(bodyVitals = it.bodyVitals.copy(isSyncing = false)) }
            }
        }
    }

    fun saveMetrics() {
        val weight = _uiState.value.weightInput.trim().toDoubleOrNull()
        val bodyFat = _uiState.value.bodyFatInput.trim().toDoubleOrNull()
        if (weight == null && bodyFat == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingMetrics = true) }
            weight?.let { metricLogRepository.log(MetricType.WEIGHT, it) }
            bodyFat?.let { metricLogRepository.log(MetricType.BODY_FAT, it) }
            _uiState.update {
                it.copy(isSavingMetrics = false, weightInput = "", bodyFatInput = "", metricSaveMessage = "Logged successfully")
            }
        }
    }

    fun deleteMetric(entry: MetricLog) {
        viewModelScope.launch { metricLogRepository.delete(entry) }
    }

    fun dismissMetricSaveMessage() {
        _uiState.update { it.copy(metricSaveMessage = null) }
    }

    private fun compute7dDelta(entries: List<MetricLog>): Double? {
        val latest = entries.lastOrNull() ?: return null
        val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        val tenDaysAgo = sevenDaysAgo - 3L * 24 * 60 * 60 * 1000
        val reference = entries
            .filter { it.timestamp in tenDaysAgo..sevenDaysAgo }
            .minByOrNull { abs(it.timestamp - sevenDaysAgo) }
            ?: return null
        return latest.value - reference.value
    }

    private fun computeBmi(weightKg: Double?, heightCm: Double?): Double? {
        if (weightKg == null || heightCm == null || heightCm == 0.0) return null
        val heightM = heightCm / 100.0
        return weightKg / (heightM * heightM)
    }
}
