package com.powerme.app.ui.metrics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.powerme.app.analytics.ReadinessEngine
import com.powerme.app.data.database.BodyRegion
import com.powerme.app.data.database.ExerciseWithHistory
import com.powerme.app.data.repository.TrendsRepository
import com.powerme.app.ui.metrics.charts.VicoChartHelpers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TrendsViewModel @Inject constructor(
    private val trendsRepository: TrendsRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _timeRange = MutableStateFlow(TrendsTimeRange.THREE_MONTHS)
    val timeRange: StateFlow<TrendsTimeRange> = _timeRange.asStateFlow()

    private val _readinessScore = MutableStateFlow<ReadinessEngine.ReadinessScore>(ReadinessEngine.ReadinessScore.NoData)
    val readinessScore: StateFlow<ReadinessEngine.ReadinessScore> = _readinessScore.asStateFlow()

    private val _selectedExerciseId = MutableStateFlow<Long?>(null)
    val selectedExerciseId: StateFlow<Long?> = _selectedExerciseId.asStateFlow()

    private val _e1rmData = MutableStateFlow<E1RMProgressionData?>(null)
    val e1rmData: StateFlow<E1RMProgressionData?> = _e1rmData.asStateFlow()

    private val _weeklyVolume = MutableStateFlow<WeeklyVolumeData?>(null)
    val weeklyVolume: StateFlow<WeeklyVolumeData?> = _weeklyVolume.asStateFlow()

    private val _muscleGroupVolume = MutableStateFlow<List<MuscleGroupVolumePoint>>(emptyList())
    val muscleGroupVolume: StateFlow<List<MuscleGroupVolumePoint>> = _muscleGroupVolume.asStateFlow()

    private val _effectiveSets = MutableStateFlow<List<EffectiveSetsChartPoint>>(emptyList())
    val effectiveSets: StateFlow<List<EffectiveSetsChartPoint>> = _effectiveSets.asStateFlow()

    private val _effectiveSetsCoverage = MutableStateFlow(0f)
    val effectiveSetsCoverage: StateFlow<Float> = _effectiveSetsCoverage.asStateFlow()

    private val _bodyComposition = MutableStateFlow<BodyCompositionData?>(null)
    val bodyComposition: StateFlow<BodyCompositionData?> = _bodyComposition.asStateFlow()

    private val _chronotypeData = MutableStateFlow<ChronotypeData?>(null)
    val chronotypeData: StateFlow<ChronotypeData?> = _chronotypeData.asStateFlow()

    private val _bodyStressMap = MutableStateFlow<BodyStressMapData?>(null)
    val bodyStressMap: StateFlow<BodyStressMapData?> = _bodyStressMap.asStateFlow()

    private val _selectedBodyRegion = MutableStateFlow<BodyRegion?>(null)
    val selectedBodyRegion: StateFlow<BodyRegion?> = _selectedBodyRegion.asStateFlow()

    fun selectBodyRegion(region: BodyRegion?) {
        _selectedBodyRegion.value = region
    }

    private val _exercisePickerItems = MutableStateFlow<List<ExerciseWithHistory>>(emptyList())
    val exercisePickerItems: StateFlow<List<ExerciseWithHistory>> = _exercisePickerItems.asStateFlow()

    private val _readinessSubMetrics = MutableStateFlow(ReadinessSubMetrics(null, null, null))
    val readinessSubMetrics: StateFlow<ReadinessSubMetrics> = _readinessSubMetrics.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // ── Vico model producers — ViewModel-scoped so they survive tab navigation
    // and LazyColumn recycling. CartesianChartHost in each card attaches to these
    // directly; data is pushed here rather than in LaunchedEffect to avoid the
    // race between recomposition removing the host and a pending runTransaction.
    val volumeModelProducer = CartesianChartModelProducer()
    val e1rmModelProducer = CartesianChartModelProducer()
    val muscleGroupModelProducer = CartesianChartModelProducer()
    val effectiveSetsModelProducer = CartesianChartModelProducer()
    val bodyCompositionModelProducer = CartesianChartModelProducer()
    val sleepModelProducer = CartesianChartModelProducer()

    // Tracks the current load coroutine so we can cancel it before starting a new one,
    // preventing concurrent runTransaction calls on the same producer.
    private var loadJob: Job? = null

    // True when the screen was opened via a deep-link with exerciseId — triggers auto-scroll.
    // Consumed (set to false) by MetricsScreen after the scroll completes.
    private val _deepLinkPending = MutableStateFlow(false)
    val deepLinkPending: StateFlow<Boolean> = _deepLinkPending.asStateFlow()

    fun consumeDeepLink() { _deepLinkPending.value = false }

    init {
        // Pre-select exercise from deep-link nav arg before loadAll() runs,
        // so loadExercisePicker()'s auto-select is skipped for this exercise.
        val deepLinkExerciseId = savedStateHandle.get<Long>("exerciseId")
        if (deepLinkExerciseId != null && deepLinkExerciseId > 0L) {
            _selectedExerciseId.value = deepLinkExerciseId
            _deepLinkPending.value = true
        }
        loadAll()
    }

    /** Reload readiness data (call on ON_RESUME to pick up fresh HC syncs). */
    fun refreshReadiness() {
        viewModelScope.launch {
            loadReadiness()
        }
    }

    fun setTimeRange(range: TrendsTimeRange) {
        if (_timeRange.value == range) return
        _timeRange.value = range
        loadAll()
    }

    fun selectExercise(exerciseId: Long, exerciseName: String) {
        _selectedExerciseId.value = exerciseId
        viewModelScope.launch {
            try {
                val data = trendsRepository.getE1RMProgression(
                    exerciseId, exerciseName, _timeRange.value
                )
                _e1rmData.value = data
                pushE1rmToProducer(data)
            } catch (_: Exception) {
                // Silently handle — chart shows empty state
            }
        }
    }

    private fun loadAll() {
        // Cancel any in-flight load before starting a new one. Without this,
        // clicking a time-range chip while init's loadAll() is still running
        // produces two concurrent runTransaction calls on the same producer.
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                coroutineScope {
                    launch { loadReadiness() }
                    // loadE1RM runs after loadExercisePicker so the exercise name
                    // lookup (used by getE1RMProgression) always finds its item.
                    launch { loadExercisePicker(); loadE1RM() }
                    launch { loadWeeklyVolume() }
                    launch { loadMuscleGroupVolume() }
                    launch { loadEffectiveSets() }
                    launch { loadBodyComposition() }
                    launch { loadChronotypeData() }
                    launch { loadBodyStressMap() }
                }
            } catch (_: Exception) {
                // Individual loads handle their own errors
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadReadiness() {
        try {
            _readinessScore.value = trendsRepository.getReadinessScore()
            _readinessSubMetrics.value = trendsRepository.getReadinessSubMetrics()
        } catch (_: Exception) {
            _readinessScore.value = ReadinessEngine.ReadinessScore.NoData
        }
    }

    private suspend fun loadExercisePicker() {
        try {
            val items = trendsRepository.getExercisePicker(_timeRange.value)
            _exercisePickerItems.value = items
            // Auto-select first exercise if none selected
            if (_selectedExerciseId.value == null && items.isNotEmpty()) {
                _selectedExerciseId.value = items.first().id
            }
        } catch (_: Exception) {
            // Keep existing list
        }
    }

    private suspend fun loadE1RM() {
        try {
            val exerciseId = _selectedExerciseId.value ?: return
            val exerciseName = _exercisePickerItems.value
                .firstOrNull { it.id == exerciseId }?.name ?: return
            val data = trendsRepository.getE1RMProgression(
                exerciseId, exerciseName, _timeRange.value
            )
            _e1rmData.value = data
            pushE1rmToProducer(data)
        } catch (_: Exception) {
            _e1rmData.value = null
        }
    }

    private fun pushE1rmToProducer(data: E1RMProgressionData?) {
        viewModelScope.launch {
            val rawPts = data?.points.orEmpty()
            val maPts = data?.movingAverage.orEmpty()
            if (rawPts.size >= 2) {
                e1rmModelProducer.runTransaction {
                    lineSeries {
                        series(rawPts.map { it.e1rm })
                        if (maPts.isNotEmpty()) series(maPts.map { it.e1rm })
                    }
                }
            } else {
                // Dummy data matching the single rawLine layer — overlay hides it visually.
                e1rmModelProducer.runTransaction {
                    lineSeries { series(listOf(0.0, 0.0)) }
                }
            }
        }
    }

    private suspend fun loadWeeklyVolume() {
        try {
            val data = trendsRepository.getWeeklyVolume(_timeRange.value)
            _weeklyVolume.value = data
            pushVolumeToProducer(data)
        } catch (_: Exception) {
            _weeklyVolume.value = null
        }
    }

    private fun pushVolumeToProducer(data: WeeklyVolumeData?) {
        viewModelScope.launch {
            val pts = data?.points.orEmpty()
            if (pts.size >= 2) {
                volumeModelProducer.runTransaction {
                    columnSeries { series(pts.map { it.totalVolume }) }
                    lineSeries { series(computeVolumeMa4Week(pts)) }
                }
            } else {
                // Dummy data matching column + line layers — overlay hides it visually.
                volumeModelProducer.runTransaction {
                    columnSeries { series(listOf(0.0, 0.0)) }
                    lineSeries { series(listOf(0.0, 0.0)) }
                }
            }
        }
    }

    private suspend fun loadMuscleGroupVolume() {
        try {
            val data = trendsRepository.getWeeklyMuscleGroupVolume(_timeRange.value)
            _muscleGroupVolume.value = data
            pushMuscleGroupToProducer(data)
        } catch (_: Exception) {
            _muscleGroupVolume.value = emptyList()
        }
    }

    private fun pushMuscleGroupToProducer(points: List<MuscleGroupVolumePoint>) {
        viewModelScope.launch {
            val weeks = points.map { it.weekStartMs }.distinct().sorted()
            if (weeks.isEmpty()) {
                // Dummy — empty state overlay hides this visually. Series count must stay fixed at 8.
                muscleGroupModelProducer.runTransaction {
                    columnSeries {
                        repeat(VicoChartHelpers.muscleGroupOrder.size) { series(listOf(0.0, 0.0)) }
                    }
                }
                return@launch
            }
            val weekIndex = weeks.withIndex().associate { (i, ms) -> ms to i }
            muscleGroupModelProducer.runTransaction {
                columnSeries {
                    VicoChartHelpers.muscleGroupOrder.forEach { group ->
                        val volumeByWeek = DoubleArray(weeks.size)
                        points.filter { it.majorGroup == group }.forEach { p ->
                            weekIndex[p.weekStartMs]?.let { idx -> volumeByWeek[idx] = p.volume }
                        }
                        series(volumeByWeek.toList())
                    }
                }
            }
        }
    }

    private suspend fun loadEffectiveSets() {
        try {
            val data = trendsRepository.getWeeklyEffectiveSets(_timeRange.value)
            val coverage = trendsRepository.getEffectiveSetsCoverage(_timeRange.value)
            _effectiveSets.value = data
            _effectiveSetsCoverage.value = coverage
            pushEffectiveSetsToProducer(data)
        } catch (_: Exception) {
            _effectiveSets.value = emptyList()
            _effectiveSetsCoverage.value = 0f
        }
    }

    private fun pushEffectiveSetsToProducer(points: List<EffectiveSetsChartPoint>) {
        viewModelScope.launch {
            val weeks = points.map { it.weekStartMs }.distinct().sorted()
            if (weeks.isEmpty()) {
                effectiveSetsModelProducer.runTransaction {
                    columnSeries {
                        repeat(VicoChartHelpers.muscleGroupOrder.size) { series(listOf(0.0, 0.0)) }
                    }
                }
                return@launch
            }
            val weekIndex = weeks.withIndex().associate { (i, ms) -> ms to i }
            effectiveSetsModelProducer.runTransaction {
                columnSeries {
                    VicoChartHelpers.muscleGroupOrder.forEach { group ->
                        val countsByWeek = DoubleArray(weeks.size)
                        points.filter { it.majorGroup == group }.forEach { p ->
                            weekIndex[p.weekStartMs]?.let { idx ->
                                countsByWeek[idx] = p.setCount.toDouble()
                            }
                        }
                        series(countsByWeek.toList())
                    }
                }
            }
        }
    }

    private suspend fun loadBodyComposition() {
        try {
            val data = trendsRepository.getBodyCompositionData(_timeRange.value)
            _bodyComposition.value = data
            pushBodyCompositionToProducer(data)
        } catch (_: Exception) {
            _bodyComposition.value = null
        }
    }

    private fun pushBodyCompositionToProducer(data: BodyCompositionData?) {
        viewModelScope.launch {
            val weightPts = data?.weightPoints.orEmpty()
            val bodyFatPts = data?.bodyFatPoints.orEmpty()
            val allTimestamps = (weightPts.map { it.timestampMs } + bodyFatPts.map { it.timestampMs })
                .distinct().sorted()

            if (allTimestamps.size < 2) {
                // Always push exactly 2 series so the layer count stays stable;
                // Vico crashes on series-count changes. Empty-state overlay hides it.
                bodyCompositionModelProducer.runTransaction {
                    lineSeries {
                        series(listOf(0.0, 0.0))
                        series(listOf(0.0, 0.0))
                    }
                }
                return@launch
            }

            val weightByTs = weightPts.associate { it.timestampMs to it.value }
            val bodyFatByTs = bodyFatPts.associate { it.timestampMs to it.value }
            val weightSeries = allTimestamps.map { ts -> weightByTs[ts] ?: 0.0 }
            val bodyFatSeries = allTimestamps.map { ts -> bodyFatByTs[ts] ?: 0.0 }

            bodyCompositionModelProducer.runTransaction {
                lineSeries {
                    series(weightSeries)
                    series(bodyFatSeries)
                }
            }
        }
    }

    private suspend fun loadChronotypeData() {
        try {
            val data = trendsRepository.getChronotypeData(_timeRange.value)
            _chronotypeData.value = data
            pushSleepToProducer(data.sleepPoints)
        } catch (_: Exception) {
            _chronotypeData.value = null
        }
    }

    private suspend fun loadBodyStressMap() {
        try {
            val stresses = trendsRepository.getBodyStressMap()
            val maxStress = stresses.maxOfOrNull { it.totalStress } ?: 0.0
            _bodyStressMap.value = BodyStressMapData(stresses, maxStress)
        } catch (_: Exception) {
            _bodyStressMap.value = null
        }
    }

    private fun pushSleepToProducer(points: List<SleepChartPoint>) {
        viewModelScope.launch {
            try {
                val hasSufficientData = points.size >= 7
                val greenSeries = if (hasSufficientData) {
                    points.map { if (it.durationMinutes >= 420) it.durationHours else 0.0 }
                } else listOf(0.0, 0.0)
                val redSeries = if (hasSufficientData) {
                    points.map { if (it.durationMinutes < 420) it.durationHours else 0.0 }
                } else listOf(0.0, 0.0)
                sleepModelProducer.runTransaction {
                    columnSeries {
                        series(greenSeries)
                        series(redSeries)
                    }
                }
            } catch (_: Exception) {
                // Producer push is best-effort; empty state overlay handles missing data.
            }
        }
    }
}

/**
 * 4-week rolling average of weekly volume (raw kg, before any unit conversion).
 * Early weeks use a partial window (e.g. the first week averages just itself).
 */
internal fun computeVolumeMa4Week(points: List<WeeklyVolumeChartPoint>): List<Double> {
    return points.mapIndexed { i, _ ->
        val windowStart = maxOf(0, i - 3)
        val window = points.subList(windowStart, i + 1)
        window.sumOf { it.totalVolume } / window.size
    }
}
