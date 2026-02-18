package com.omerhedvat.powerme.ui.workout

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omerhedvat.powerme.data.database.Exercise
import com.omerhedvat.powerme.data.database.SetType
import com.omerhedvat.powerme.data.database.TargetJoint
import com.omerhedvat.powerme.data.database.UserSettingsDao
import com.omerhedvat.powerme.data.database.Workout
import com.omerhedvat.powerme.data.database.WorkoutSet
import com.omerhedvat.powerme.data.database.WarmupLog
import com.omerhedvat.powerme.analytics.BoazPerformanceAnalyzer
import com.omerhedvat.powerme.data.database.StateHistoryEntry
import com.omerhedvat.powerme.data.repository.ExerciseRepository
import com.omerhedvat.powerme.data.repository.MedicalLedgerRepository
import com.omerhedvat.powerme.data.repository.StateHistoryRepository
import com.omerhedvat.powerme.data.repository.WarmupRepository
import com.omerhedvat.powerme.data.repository.WorkoutRepository
import com.omerhedvat.powerme.ui.chat.MedicalRestrictionsDoc
import com.omerhedvat.powerme.util.RestTimerNotifier
import com.omerhedvat.powerme.warmup.WarmupPrescription
import com.omerhedvat.powerme.warmup.WarmupService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ActiveSet(
    val id: Long = 0,
    val setOrder: Int,
    val weight: String = "",
    val reps: String = "",
    val rpe: String = "",
    val setType: SetType = SetType.NORMAL,
    val isCompleted: Boolean = false,
    val ghostWeight: String? = null,  // Previous session weight hint
    val ghostReps: String? = null,    // Previous session reps hint
    val ghostRpe: String? = null,     // Previous session RPE hint
    val setNotes: String = "",        // Session-specific notes for this set
    val distance: String = "",        // For cardio exercises (km)
    val timeSeconds: String = ""      // For cardio/timed exercises (seconds)
)

data class ExerciseWithSets(
    val exercise: Exercise,
    val sets: List<ActiveSet>
)

data class RestTimerState(
    val isActive: Boolean = false,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 0
)

data class ActiveWorkoutState(
    val isActive: Boolean = false,
    val workoutId: Long? = null,
    val routineId: Long = 0L,
    val startTime: Long? = null,
    val exercises: List<ExerciseWithSets> = emptyList(),
    val availableExercises: List<Exercise> = emptyList(),
    val notes: String = "",
    val warmupPrescription: WarmupPrescription? = null,
    val isLoadingWarmup: Boolean = false,
    val warmupCompleted: Boolean = false,
    val restTimer: RestTimerState = RestTimerState()
)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val warmupRepository: WarmupRepository,
    private val warmupService: WarmupService,
    private val workoutSetDao: com.omerhedvat.powerme.data.database.WorkoutSetDao,
    private val userSettingsDao: UserSettingsDao,
    private val medicalLedgerRepository: MedicalLedgerRepository,
    private val boazPerformanceAnalyzer: BoazPerformanceAnalyzer,
    private val stateHistoryRepository: StateHistoryRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val restTimerNotifier = RestTimerNotifier(context)
    private var hasNotifiedAt5Seconds = false

    private val _workoutState = MutableStateFlow(ActiveWorkoutState())
    val workoutState: StateFlow<ActiveWorkoutState> = _workoutState.asStateFlow()

    private val _medicalDoc = MutableStateFlow<MedicalRestrictionsDoc?>(null)
    val medicalDoc: StateFlow<MedicalRestrictionsDoc?> = _medicalDoc.asStateFlow()

    init {
        loadAvailableExercises()
        loadMedicalDoc()
    }

    private fun loadMedicalDoc() {
        viewModelScope.launch {
            _medicalDoc.value = medicalLedgerRepository.getRestrictionsDoc()
        }
    }

    private fun loadAvailableExercises() {
        viewModelScope.launch {
            exerciseRepository.getAllExercises().collect { exercises ->
                _workoutState.update { it.copy(availableExercises = exercises) }
            }
        }
    }

    fun startWorkout(routineId: Long = 0L) {
        _workoutState.update {
            it.copy(
                isActive = true,
                routineId = routineId,
                startTime = System.currentTimeMillis(),
                exercises = emptyList()
            )
        }
    }

    fun addExercise(exercise: Exercise) {
        val currentExercises = _workoutState.value.exercises

        // Check if exercise already exists
        if (currentExercises.any { it.exercise.id == exercise.id }) {
            return
        }

        viewModelScope.launch {
            // Load ghost data from previous session
            val currentTimestamp = System.currentTimeMillis()
            val previousSets = workoutSetDao.getPreviousSessionSets(exercise.id, currentTimestamp)

            // Create initial sets with ghost data
            val initialSets = if (previousSets.isNotEmpty()) {
                previousSets.mapIndexed { index, prevSet ->
                    ActiveSet(
                        setOrder = index + 1,
                        ghostWeight = prevSet.weight.toString(),
                        ghostReps = prevSet.reps.toString(),
                        ghostRpe = prevSet.rpe?.toString()
                    )
                }
            } else {
                listOf(ActiveSet(setOrder = 1))
            }

            val newExerciseWithSets = ExerciseWithSets(
                exercise = exercise,
                sets = initialSets
            )

            _workoutState.update {
                it.copy(exercises = currentExercises + newExerciseWithSets)
            }
        }
    }

    fun addSet(exerciseId: Long) {
        _workoutState.update { state ->
            val updatedExercises = state.exercises.map { exerciseWithSets ->
                if (exerciseWithSets.exercise.id == exerciseId) {
                    val newSetOrder = exerciseWithSets.sets.size + 1
                    val newSet = ActiveSet(setOrder = newSetOrder)
                    exerciseWithSets.copy(sets = exerciseWithSets.sets + newSet)
                } else {
                    exerciseWithSets
                }
            }
            state.copy(exercises = updatedExercises)
        }
    }

    fun updateSet(exerciseId: Long, setOrder: Int, weight: String, reps: String, rpe: String) {
        _workoutState.update { state ->
            val updatedExercises = state.exercises.map { exerciseWithSets ->
                if (exerciseWithSets.exercise.id == exerciseId) {
                    val updatedSets = exerciseWithSets.sets.map { set ->
                        if (set.setOrder == setOrder) {
                            set.copy(
                                weight = weight,
                                reps = reps,
                                rpe = rpe,
                                isCompleted = weight.isNotBlank() && reps.isNotBlank()
                            )
                        } else {
                            set
                        }
                    }
                    exerciseWithSets.copy(sets = updatedSets)
                } else {
                    exerciseWithSets
                }
            }
            state.copy(exercises = updatedExercises)
        }
    }

    fun updateCardioSet(exerciseId: Long, setOrder: Int, distance: String, timeSeconds: String, rpe: String) {
        _workoutState.update { state ->
            val updatedExercises = state.exercises.map { exerciseWithSets ->
                if (exerciseWithSets.exercise.id == exerciseId) {
                    val updatedSets = exerciseWithSets.sets.map { set ->
                        if (set.setOrder == setOrder) {
                            set.copy(
                                distance = distance,
                                timeSeconds = timeSeconds,
                                rpe = rpe,
                                isCompleted = distance.isNotBlank() && timeSeconds.isNotBlank()
                            )
                        } else {
                            set
                        }
                    }
                    exerciseWithSets.copy(sets = updatedSets)
                } else {
                    exerciseWithSets
                }
            }
            state.copy(exercises = updatedExercises)
        }
    }

    fun updateTimedSet(exerciseId: Long, setOrder: Int, timeSeconds: String, rpe: String) {
        _workoutState.update { state ->
            val updatedExercises = state.exercises.map { exerciseWithSets ->
                if (exerciseWithSets.exercise.id == exerciseId) {
                    val updatedSets = exerciseWithSets.sets.map { set ->
                        if (set.setOrder == setOrder) {
                            set.copy(
                                timeSeconds = timeSeconds,
                                rpe = rpe,
                                isCompleted = timeSeconds.isNotBlank()
                            )
                        } else {
                            set
                        }
                    }
                    exerciseWithSets.copy(sets = updatedSets)
                } else {
                    exerciseWithSets
                }
            }
            state.copy(exercises = updatedExercises)
        }
    }

    fun deleteSet(exerciseId: Long, setOrder: Int) {
        _workoutState.update { state ->
            val updatedExercises = state.exercises.map { exerciseWithSets ->
                if (exerciseWithSets.exercise.id == exerciseId) {
                    val updatedSets = exerciseWithSets.sets
                        .filter { it.setOrder != setOrder }
                        .mapIndexed { index, set -> set.copy(setOrder = index + 1) }
                    exerciseWithSets.copy(sets = updatedSets)
                } else {
                    exerciseWithSets
                }
            }
            state.copy(exercises = updatedExercises)
        }
    }

    fun updateNotes(notes: String) {
        _workoutState.update { it.copy(notes = notes) }
    }

    fun finishWorkout() {
        viewModelScope.launch {
            val state = _workoutState.value
            if (!state.isActive || state.startTime == null) return@launch

            val endTime = System.currentTimeMillis()
            val durationSeconds = ((endTime - state.startTime) / 1000).toInt()

            // Calculate total volume
            var totalVolume = 0.0
            state.exercises.forEach { exerciseWithSets ->
                exerciseWithSets.sets.forEach { set ->
                    if (set.isCompleted) {
                        val weight = set.weight.toDoubleOrNull() ?: 0.0
                        val reps = set.reps.toIntOrNull() ?: 0
                        totalVolume += weight * reps
                    }
                }
            }

            // Create workout entry
            val workout = Workout(
                routineId = state.routineId,
                timestamp = endTime,
                durationSeconds = durationSeconds,
                totalVolume = totalVolume,
                notes = state.notes.ifBlank { null }
            )

            val workoutId = workoutRepository.insertWorkout(workout)

            // Create workout sets
            val workoutSets = mutableListOf<WorkoutSet>()
            state.exercises.forEach { exerciseWithSets ->
                exerciseWithSets.sets.forEach { set ->
                    if (set.isCompleted) {
                        workoutSets.add(
                            WorkoutSet(
                                workoutId = workoutId,
                                exerciseId = exerciseWithSets.exercise.id,
                                setOrder = set.setOrder,
                                weight = set.weight.toDoubleOrNull() ?: 0.0,
                                reps = set.reps.toIntOrNull() ?: 0,
                                rpe = set.rpe.toIntOrNull(),
                                setType = set.setType,
                                setNotes = set.setNotes.ifBlank { null },
                                distance = set.distance.toDoubleOrNull(),
                                timeSeconds = set.timeSeconds.toIntOrNull()
                            )
                        )
                    }
                }
            }

            workoutRepository.insertSets(workoutSets)

            // Boaz Performance Loop — compare planned vs actual
            val report = try {
                val results = boazPerformanceAnalyzer.compare(workoutId, routineId = state.routineId)
                boazPerformanceAnalyzer.formatPerformanceReport(results)
            } catch (e: Exception) {
                android.util.Log.w("WorkoutViewModel", "Boaz analysis failed", e)
                null
            }

            if (!report.isNullOrBlank()) {
                stateHistoryRepository.insertEntry(
                    StateHistoryEntry(
                        documentType = "PERFORMANCE",
                        operation = "UPDATE",
                        previousValueJson = "",
                        newValueJson = report,
                        changeReason = "Post-workout Boaz analysis — planned vs actual"
                    )
                )
            }

            // Reset state
            _workoutState.update {
                ActiveWorkoutState(availableExercises = it.availableExercises)
            }
        }
    }

    fun cancelWorkout() {
        _workoutState.update {
            ActiveWorkoutState(availableExercises = it.availableExercises)
        }
    }

    fun requestWarmup() {
        viewModelScope.launch {
            try {
                _workoutState.update { it.copy(isLoadingWarmup = true) }

                // Get recent warmups
                val recentWarmups = warmupRepository.getLast10Warmups()

                // Determine routine name based on exercises (simplified)
                val routineName = "Routine A" // Could be determined from exercise selection

                // Get main exercises from current workout
                val mainExercises = _workoutState.value.exercises.map { it.exercise.name }

                // User injury profile — loaded from MedicalLedger at runtime
                val injuryProfile = emptyList<String>()

                // Generate warmup prescription
                val prescription = warmupService.generateWarmup(
                    routineName = routineName,
                    injuryProfile = injuryProfile,
                    recentWarmups = recentWarmups,
                    mainExercises = mainExercises.ifEmpty { listOf("General warmup") }
                )

                _workoutState.update {
                    it.copy(
                        warmupPrescription = prescription,
                        isLoadingWarmup = false
                    )
                }
            } catch (e: Exception) {
                _workoutState.update { it.copy(isLoadingWarmup = false) }
            }
        }
    }

    fun logWarmupAsPerformed() {
        viewModelScope.launch {
            val prescription = _workoutState.value.warmupPrescription ?: return@launch
            val currentTime = System.currentTimeMillis()

            val warmupLogs = prescription.exercises.map { exercise ->
                WarmupLog(
                    workoutId = _workoutState.value.workoutId,
                    exerciseName = exercise.name,
                    timestamp = currentTime,
                    targetJoint = warmupService.parseTargetJoint(exercise.targetJoint),
                    durationSeconds = exercise.duration,
                    reps = exercise.reps
                )
            }

            warmupRepository.insertWarmups(warmupLogs)

            _workoutState.update {
                it.copy(
                    warmupCompleted = true,
                    warmupPrescription = null
                )
            }
        }
    }

    fun dismissWarmup() {
        _workoutState.update { it.copy(warmupPrescription = null) }
    }

    private var timerJob: kotlinx.coroutines.Job? = null

    fun startRestTimer(exerciseId: Long) {
        // Cancel any existing timer
        timerJob?.cancel()
        hasNotifiedAt5Seconds = false

        // Find the exercise to get rest duration
        val exercise = _workoutState.value.exercises
            .find { it.exercise.id == exerciseId }
            ?.exercise ?: return

        val restDuration = exercise.restDurationSeconds

        _workoutState.update {
            it.copy(
                restTimer = RestTimerState(
                    isActive = true,
                    remainingSeconds = restDuration,
                    totalSeconds = restDuration
                )
            )
        }

        timerJob = viewModelScope.launch {
            for (i in restDuration downTo 0) {
                _workoutState.update {
                    it.copy(
                        restTimer = it.restTimer.copy(remainingSeconds = i)
                    )
                }

                // Trigger notification at 5 seconds remaining
                if (i == 5 && !hasNotifiedAt5Seconds) {
                    hasNotifiedAt5Seconds = true
                    val settings = userSettingsDao.getSettingsOnce()
                    if (settings != null) {
                        restTimerNotifier.notifyUser(
                            audioEnabled = settings.restTimerAudioEnabled,
                            hapticsEnabled = settings.restTimerHapticsEnabled
                        )
                    }
                }

                if (i > 0) {
                    delay(1000)
                }
            }
            // Timer finished
            _workoutState.update {
                it.copy(restTimer = RestTimerState())
            }
        }
    }

    fun addTimeToTimer(seconds: Int) {
        val currentTimer = _workoutState.value.restTimer
        if (currentTimer.isActive) {
            val newRemaining = currentTimer.remainingSeconds + seconds
            val newTotal = currentTimer.totalSeconds + seconds
            _workoutState.update {
                it.copy(
                    restTimer = currentTimer.copy(
                        remainingSeconds = newRemaining,
                        totalSeconds = newTotal
                    )
                )
            }
        }
    }

    fun skipRestTimer() {
        timerJob?.cancel()
        hasNotifiedAt5Seconds = false
        _workoutState.update {
            it.copy(restTimer = RestTimerState())
        }
    }

    fun updateSetupNotes(exerciseId: Long, notes: String) {
        viewModelScope.launch {
            // Update exercise in database
            val exercise = exerciseRepository.getAllExercises().first()
                .find { it.id == exerciseId } ?: return@launch

            val updatedExercise = exercise.copy(setupNotes = notes.ifBlank { null })
            exerciseRepository.updateExercise(updatedExercise)

            // Update local state
            _workoutState.update { state ->
                val updatedExercises = state.exercises.map { exerciseWithSets ->
                    if (exerciseWithSets.exercise.id == exerciseId) {
                        exerciseWithSets.copy(exercise = updatedExercise)
                    } else {
                        exerciseWithSets
                    }
                }
                state.copy(exercises = updatedExercises)
            }
        }
    }

    fun updateSetNotes(exerciseId: Long, setOrder: Int, notes: String) {
        _workoutState.update { state ->
            val updatedExercises = state.exercises.map { exerciseWithSets ->
                if (exerciseWithSets.exercise.id == exerciseId) {
                    val updatedSets = exerciseWithSets.sets.map { set ->
                        if (set.setOrder == setOrder) {
                            set.copy(setNotes = notes)
                        } else {
                            set
                        }
                    }
                    exerciseWithSets.copy(sets = updatedSets)
                } else {
                    exerciseWithSets
                }
            }
            state.copy(exercises = updatedExercises)
        }
    }
}
