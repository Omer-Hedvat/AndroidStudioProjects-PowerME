package com.powerme.app.ui.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powerme.app.analytics.StatisticalEngine
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.UnitSystem
import com.powerme.app.data.database.ExerciseType
import com.powerme.app.data.database.SetType
import com.powerme.app.data.database.Workout
import com.powerme.app.data.database.WorkoutDao
import com.powerme.app.data.database.WorkoutSetDao
import com.powerme.app.data.database.WorkoutSetWithExercise
import com.powerme.app.data.sync.FirestoreSyncManager
import com.powerme.app.ui.workout.RoutineSyncType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExerciseSummaryCard(
    val exerciseId: Long,
    val exerciseName: String,
    val muscleGroup: String?,
    val exerciseType: ExerciseType,
    val setCount: Int,
    val bestSetWeight: Double,        // kg (storage units)
    val bestSetReps: Int,
    val e1RM: Double,                 // Epley estimate from best set
    val volumeDeltaPercent: Double?,  // null = no previous session data
    val avgRpe: Double?,              // null = fewer than 50% of sets have RPE logged
    val isGoldenZone: Boolean,        // avg RPE in [8.0, 9.0]
    val isPR: Boolean,
    val supersetGroupId: String?
)

data class MuscleGroupBar(
    val group: String,
    val volume: Double,
    val fraction: Float               // 0.0–1.0, relative to max-volume group
)

data class WorkoutSummaryUiState(
    val workout: Workout? = null,
    val exerciseCards: List<ExerciseSummaryCard> = emptyList(),
    val muscleGroupBars: List<MuscleGroupBar> = emptyList(),
    val totalSets: Int = 0,
    val prCount: Int = 0,
    val isPostWorkout: Boolean = false,
    val pendingRoutineSync: RoutineSyncType? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class WorkoutSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutDao: WorkoutDao,
    private val workoutSetDao: WorkoutSetDao,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val firestoreSyncManager: FirestoreSyncManager
) : ViewModel() {

    private val workoutId: String = checkNotNull(savedStateHandle["workoutId"])

    private val _uiState = MutableStateFlow(WorkoutSummaryUiState())
    val uiState: StateFlow<WorkoutSummaryUiState> = _uiState.asStateFlow()

    val unitSystem: StateFlow<UnitSystem> = appSettingsDataStore.unitSystem
        .stateIn(viewModelScope, SharingStarted.Eagerly, UnitSystem.METRIC)

    init {
        val isPostWorkout = savedStateHandle.get<Boolean>("isPostWorkout") ?: false
        val syncTypeName = savedStateHandle.get<String>("syncType") ?: "NONE"
        val pendingSync = if (syncTypeName == "NONE") null
        else runCatching { RoutineSyncType.valueOf(syncTypeName) }.getOrNull()

        _uiState.update { it.copy(isPostWorkout = isPostWorkout, pendingRoutineSync = pendingSync) }
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            val workout = workoutDao.getWorkoutById(workoutId)
            if (workout == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            val allSets = workoutSetDao.getSetsWithExerciseForWorkout(workoutId)

            // Group sets by exercise, preserving the order first seen
            val exerciseOrder = mutableListOf<Long>()
            val groupedSets = mutableMapOf<Long, MutableList<WorkoutSetWithExercise>>()
            for (set in allSets) {
                if (!groupedSets.containsKey(set.exerciseId)) {
                    exerciseOrder.add(set.exerciseId)
                    groupedSets[set.exerciseId] = mutableListOf()
                }
                groupedSets[set.exerciseId]!!.add(set)
            }

            val cards = exerciseOrder.mapNotNull { exerciseId ->
                val sets = groupedSets[exerciseId] ?: return@mapNotNull null
                buildExerciseCard(exerciseId, sets, workout.timestamp)
            }

            val muscleGroupBars = buildMuscleGroupBars(allSets)
            val totalSets = cards.sumOf { it.setCount }
            val prCount = cards.count { it.isPR }

            _uiState.update {
                it.copy(
                    workout = workout,
                    exerciseCards = cards,
                    muscleGroupBars = muscleGroupBars,
                    totalSets = totalSets,
                    prCount = prCount,
                    isLoading = false
                )
            }
        }
    }

    private suspend fun buildExerciseCard(
        exerciseId: Long,
        sets: List<WorkoutSetWithExercise>,
        workoutTimestamp: Long
    ): ExerciseSummaryCard? {
        val workingSets = sets.filter { it.setType != SetType.WARMUP }
        if (workingSets.isEmpty()) return null

        val firstSet = workingSets.first()

        // Best set: highest Epley e1RM (weight * (1 + reps/30))
        val bestSet = workingSets.maxByOrNull {
            StatisticalEngine.calculate1RM(it.weight, it.reps)
        } ?: return null
        val e1RM = StatisticalEngine.calculate1RM(bestSet.weight, bestSet.reps)

        // Volume delta vs previous session
        val prevSets = workoutSetDao.getPreviousSessionCompletedSets(exerciseId, workoutTimestamp)
        val currentVolume = workingSets.sumOf { it.weight * it.reps }
        val prevVolume = prevSets.sumOf { it.weight * it.reps }
        val volumeDeltaPercent = if (prevSets.isNotEmpty() && prevVolume > 0.0) {
            ((currentVolume - prevVolume) / prevVolume) * 100.0
        } else null

        // Avg RPE — only shown if at least 50% of working sets have RPE logged
        val rpeValues = workingSets.mapNotNull { it.rpe }
        val avgRpe = if (rpeValues.size >= (workingSets.size * 0.5).coerceAtLeast(1.0)) {
            rpeValues.average()
        } else null

        val isGoldenZone = avgRpe != null && avgRpe >= 8.0 && avgRpe <= 9.0

        // PR: current e1RM > all-time best for this exercise before this workout
        val historicalBest = workoutSetDao.getHistoricalBestE1RM(exerciseId, workoutTimestamp) ?: 0.0
        val isPR = e1RM > historicalBest && e1RM > 0.0

        return ExerciseSummaryCard(
            exerciseId = exerciseId,
            exerciseName = firstSet.exerciseName,
            muscleGroup = firstSet.muscleGroup,
            exerciseType = firstSet.exerciseType,
            setCount = workingSets.size,
            bestSetWeight = bestSet.weight,
            bestSetReps = bestSet.reps,
            e1RM = e1RM,
            volumeDeltaPercent = volumeDeltaPercent,
            avgRpe = avgRpe,
            isGoldenZone = isGoldenZone,
            isPR = isPR,
            supersetGroupId = firstSet.supersetGroupId
        )
    }

    private fun buildMuscleGroupBars(sets: List<WorkoutSetWithExercise>): List<MuscleGroupBar> {
        val workingSets = sets.filter { it.setType != SetType.WARMUP }
        val volumeByGroup = workingSets
            .groupBy { it.muscleGroup ?: "Other" }
            .mapValues { (_, groupSets) -> groupSets.sumOf { it.weight * it.reps } }
            .filter { it.value > 0.0 }

        if (volumeByGroup.isEmpty()) return emptyList()

        val maxVolume = volumeByGroup.values.max()
        return volumeByGroup.entries
            .sortedByDescending { it.value }
            .map { (group, volume) ->
                MuscleGroupBar(
                    group = group,
                    volume = volume,
                    fraction = (volume / maxVolume).toFloat()
                )
            }
    }

    fun setSessionRating(rating: Int) {
        viewModelScope.launch {
            workoutDao.updateSessionRating(workoutId, rating, System.currentTimeMillis())
            _uiState.update { it.copy(workout = it.workout?.copy(sessionRating = rating)) }
            firestoreSyncManager.pushWorkout(workoutId)
        }
    }

    fun updateNotes(notes: String) {
        viewModelScope.launch {
            val workout = _uiState.value.workout ?: return@launch
            val updated = workout.copy(
                notes = notes.ifBlank { null },
                updatedAt = System.currentTimeMillis()
            )
            workoutDao.updateWorkout(updated)
            _uiState.update { it.copy(workout = updated) }
            firestoreSyncManager.pushWorkout(workoutId)
        }
    }

    /** Reload data after returning from edit mode. */
    fun reload() {
        _uiState.update { it.copy(isLoading = true) }
        loadData()
    }
}
