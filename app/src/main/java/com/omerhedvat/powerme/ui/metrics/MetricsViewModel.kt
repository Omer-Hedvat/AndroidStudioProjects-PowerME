package com.omerhedvat.powerme.ui.metrics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omerhedvat.powerme.analytics.WeeklyInsights
import com.omerhedvat.powerme.data.database.MetricLog
import com.omerhedvat.powerme.data.database.MetricType
import com.omerhedvat.powerme.data.repository.AnalyticsRepository
import com.omerhedvat.powerme.data.repository.MetricLogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MetricsUiState(
    val weeklyInsights: WeeklyInsights? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val weightEntries: List<MetricLog> = emptyList(),
    val bodyFatEntries: List<MetricLog> = emptyList(),
    val weightInput: String = "",
    val bodyFatInput: String = "",
    val isSavingMetrics: Boolean = false,
    val metricSaveMessage: String? = null
)

@HiltViewModel
class MetricsViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository,
    private val metricLogRepository: MetricLogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MetricsUiState())
    val uiState: StateFlow<MetricsUiState> = _uiState.asStateFlow()

    init {
        loadInsights()
        observeMetricLogs()
    }

    fun loadInsights() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                val insights = analyticsRepository.generateWeeklyInsights()
                _uiState.update { it.copy(weeklyInsights = insights, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = "Failed to load insights: ${e.message}") }
            }
        }
    }

    private fun observeMetricLogs() {
        viewModelScope.launch {
            metricLogRepository.getByType(MetricType.WEIGHT).collect { entries ->
                _uiState.update { it.copy(weightEntries = entries) }
            }
        }
        viewModelScope.launch {
            metricLogRepository.getByType(MetricType.BODY_FAT).collect { entries ->
                _uiState.update { it.copy(bodyFatEntries = entries) }
            }
        }
    }

    fun saveMetrics() {
        val weightStr = _uiState.value.weightInput.trim()
        val bodyFatStr = _uiState.value.bodyFatInput.trim()
        val weight = weightStr.toDoubleOrNull()
        val bodyFat = bodyFatStr.toDoubleOrNull()

        if (weight == null && bodyFat == null) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSavingMetrics = true) }
            weight?.let { metricLogRepository.log(MetricType.WEIGHT, it) }
            bodyFat?.let { metricLogRepository.log(MetricType.BODY_FAT, it) }
            _uiState.update {
                it.copy(
                    isSavingMetrics = false,
                    weightInput = "",
                    bodyFatInput = "",
                    metricSaveMessage = "Logged successfully"
                )
            }
        }
    }

    fun deleteMetric(entry: MetricLog) {
        viewModelScope.launch {
            metricLogRepository.delete(entry)
        }
    }

    fun dismissMetricSaveMessage() {
        _uiState.update { it.copy(metricSaveMessage = null) }
    }
}
