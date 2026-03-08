package com.omerhedvat.powerme.ui.workout

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omerhedvat.powerme.data.AppSettingsDataStore
import com.omerhedvat.powerme.data.database.Exercise
import com.omerhedvat.powerme.data.database.RoutineDao
import com.omerhedvat.powerme.data.database.SetType
import com.omerhedvat.powerme.data.database.TargetJoint
import com.omerhedvat.powerme.data.database.UserSettingsDao
import com.omerhedvat.powerme.data.database.Workout
import com.omerhedvat.powerme.data.database.WorkoutDao
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
import com.omerhedvat.powerme.util.PlateCalculator
import com.omerhedvat.powerme.util.RestTimerNotifier
import com.omerhedvat.powerme.util.SurgicalValidator
import com.omerhedvat.powerme.util.WorkoutTimerService
import com.omerhedvat.powerme.warmup.WarmupPrescription
import com.omerhedvat.powerme.warmup.WarmupService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
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
    val sets: List<ActiveSet>,
    val supersetGroupId: String? = null,
    val sessionNote: String? = null,    // volatile — not persisted, resets each session
    val stickyNote: String? = null      // persisted via RoutineExercise.stickyNote
)

data class RestTimerState(
    val isActive: Boolean = false,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 0
)

data class WorkoutSummary(
    val workoutName: String,
    val durationSeconds: Int,
    val totalVolume: Double,
    val setCount: Int,
    val exerciseNames: List<String>
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
    val restTimer: RestTimerState = RestTimerState(),
    val activeSupersetExerciseId: Long? = null,
    val isSupersetSelectMode: Boolean = false,
    val supersetCandidateIds: Set<Long> = emptySet(),
    val snackbarMessage: String? = null,
    val workoutName: String = "Workout",
    val elapsedSeconds: Int = 0,
    val pendingWorkoutSummary: WorkoutSummary? = null
)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val warmupRepository: WarmupRepository,
    private val warmupService: WarmupService,
    private val workoutDao: WorkoutDao,
    private val workoutSetDao: com.omerhedvat.powerme.data.database.WorkoutSetDao,
    private val routineExerciseDao: com.omerhedvat.powerme.data.database.RoutineExerciseDao,
    private val routineDao: RoutineDao,
    private val userSettingsDao: UserSettingsDao,
    private val medicalLedgerRepository: MedicalLedgerRepository,
    private val boazPerformanceAnalyzer: BoazPerformanceAnalyzer,
    private val stateHistoryRepository: StateHistoryRepository,
    private val appSettingsDataStore: AppSettingsDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val restTimerNotifier = RestTimerNotifier(context)

    // --- WorkoutTimerService binding ---
    private var timerService: WorkoutTimerService? = null
    private var serviceBound = false
    private var serviceCollectionJob: kotlinx.coroutines.Job? = null

    private val timerConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            timerService = (binder as WorkoutTimerService.TimerBinder).getService()
            serviceBound = true
            // Mirror the service's live countdown into ViewModel state so the UI
            // automatically reflects ticks even across Activity recreations.
            serviceCollectionJob?.cancel()
            serviceCollectionJob = viewModelScope.launch {
                timerService!!.timerState.collect { serviceState ->
                    if (serviceState.isRunning) {
                        _workoutState.update { state ->
                            state.copy(
                                restTimer = RestTimerState(
                                    isActive = true,
                                    remainingSeconds = serviceState.remainingSeconds,
                                    totalSeconds = serviceState.totalSeconds
                                )
                            )
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            serviceCollectionJob?.cancel()
            timerService = null
            serviceBound = false
        }
    }

    private val _workoutState = MutableStateFlow(ActiveWorkoutState())
    val workoutState: StateFlow<ActiveWorkoutState> = _workoutState.asStateFlow()

    private val _medicalDoc = MutableStateFlow<MedicalRestrictionsDoc?>(null)
    val medicalDoc: StateFlow<MedicalRestrictionsDoc?> = _medicalDoc.asStateFlow()

    val keepScreenOn: StateFlow<Boolean> = appSettingsDataStore.keepScreenOn
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val availablePlates: StateFlow<List<Double>> = userSettingsDao.getSettings()
        .map { settings -> PlateCalculator.parseAvailablePlates(settings?.availablePlates ?: "0.5,1.25,2.5,5,10,15,20,25") }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val saveJobs = mutableMapOf<Pair<Long, Int>, Job>()

    init {
        loadAvailableExercises()
        loadMedicalDoc()
        rehydrateIfNeeded()
        context.bindService(
            Intent(context, WorkoutTimerService::class.java),
            timerConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onCleared() {
        super.onCleared()
        serviceCollectionJob?.cancel()
        timerJob?.cancel()
        elapsedTimerJob?.cancel()
        if (serviceBound) {
            context.unbindService(timerConnection)
            serviceBound = false
        }
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

    private fun rehydrateIfNeeded() {
        viewModelScope.launch {
            val activeWorkout = workoutDao.getActiveWorkout() ?: return@launch
            val dbSets = workoutSetDao.getSetsForWorkout(activeWorkout.id).first()
            val groupedByExercise = dbSets.groupBy { it.exerciseId }
            val exercises = groupedByExercise.keys.mapNotNull { exerciseId ->
                exerciseRepository.getExerciseById(exerciseId)?.let { exercise ->
                    val activeSets = groupedByExercise[exerciseId]!!.map { ws ->
                        ActiveSet(
                            id = ws.id,
                            setOrder = ws.setOrder,
                            weight = if (ws.weight > 0.0) ws.weight.toString() else "",
                            reps = if (ws.reps > 0) ws.reps.toString() else "",
                            setType = ws.setType,
                            isCompleted = ws.isCompleted
                        )
                    }
                    ExerciseWithSets(exercise = exercise, sets = activeSets)
                }
            }
            val name = activeWorkout.routineId?.let { routineDao.getRoutineById(it)?.name } ?: "Workout"
            _workoutState.update {
                it.copy(
                    isActive = true,
                    workoutId = activeWorkout.id,
                    routineId = activeWorkout.routineId ?: 0L,
                    startTime = activeWorkout.timestamp,
                    exercises = exercises,
                    workoutName = name
                )
            }
            startElapsedTimer()
        }
    }

    fun startWorkoutFromRoutine(routineId: Long) {
        viewModelScope.launch {
            val bootstrap = workoutRepository.instantiateWorkoutFromRoutine(routineId)
            val routineExercises = routineExerciseDao.getForRoutine(routineId)
            val dbSetsByExercise = bootstrap.workoutSets.groupBy { it.exerciseId }
            val exercises = routineExercises.mapNotNull { re ->
                val exercise = exerciseRepository.getExerciseById(re.exerciseId) ?: return@mapNotNull null
                val ghostSets = bootstrap.ghostMap[re.exerciseId] ?: emptyList()
                val dbSets = dbSetsByExercise[re.exerciseId] ?: emptyList()
                val activeSets = dbSets.mapIndexed { i, ws ->
                    val ghost = ghostSets.getOrNull(i)
                    ActiveSet(
                        id = ws.id,
                        setOrder = ws.setOrder,
                        ghostWeight = ghost?.weight?.let { if (it > 0.0) it.toString() else null },
                        ghostReps = ghost?.reps?.let { if (it > 0) it.toString() else null }
                    )
                }
                val sticky = try { routineExerciseDao.getStickyNote(routineId, re.exerciseId) } catch (_: Exception) { null }
                ExerciseWithSets(exercise = exercise, sets = activeSets, stickyNote = sticky)
            }
            val name = routineDao.getRoutineById(routineId)?.name ?: "Workout"
            _workoutState.update {
                it.copy(
                    isActive = true, workoutId = bootstrap.workoutId,
                    routineId = routineId, startTime = System.currentTimeMillis(),
                    exercises = exercises, workoutName = name
                )
            }
            startElapsedTimer()
        }
    }

    fun startWorkout(routineId: Long = 0L) {
        viewModelScope.launch {
            val id = workoutRepository.createEmptyWorkout(routineId.takeIf { it > 0L })
            _workoutState.update {
                it.copy(
                    isActive = true,
                    workoutId = id,
                    routineId = routineId,
                    startTime = System.currentTimeMillis(),
                    exercises = emptyList(),
                    workoutName = "Empty Workout"
                )
            }
            startElapsedTimer()
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

            // Load sticky note from DB (only when a routine is active)
            val routineId = _workoutState.value.routineId
            val sticky = if (routineId > 0L) {
                try { routineExerciseDao.getStickyNote(routineId, exercise.id) } catch (_: Exception) { null }
            } else null

            // Persist initial sets to DB if a workout is active (Iron Vault)
            val workoutId = _workoutState.value.workoutId
            val setsWithIds = if (workoutId != null) {
                initialSets.map { activeSet ->
                    val ws = WorkoutSet(
                        workoutId = workoutId,
                        exerciseId = exercise.id,
                        setOrder = activeSet.setOrder,
                        weight = 0.0,
                        reps = 0,
                        setType = activeSet.setType
                    )
                    val dbId = workoutRepository.createWorkoutSet(ws)
                    activeSet.copy(id = dbId)
                }
            } else {
                initialSets
            }

            val newExerciseWithSets = ExerciseWithSets(
                exercise = exercise,
                sets = setsWithIds,
                stickyNote = sticky
            )

            _workoutState.update {
                it.copy(exercises = currentExercises + newExerciseWithSets)
            }
        }
    }

    fun addSet(exerciseId: Long) {
        viewModelScope.launch {
            val state = _workoutState.value
            val currentExercise = state.exercises.find { it.exercise.id == exerciseId } ?: return@launch
            val newSetOrder = currentExercise.sets.size + 1
            var newSet = ActiveSet(setOrder = newSetOrder)

            val workoutId = state.workoutId
            if (workoutId != null) {
                val ws = WorkoutSet(
                    workoutId = workoutId,
                    exerciseId = exerciseId,
                    setOrder = newSetOrder,
                    weight = 0.0,
                    reps = 0
                )
                val dbId = workoutSetDao.insertSet(ws)
                newSet = newSet.copy(id = dbId)
            }

            _workoutState.update { s ->
                s.copy(exercises = s.exercises.map { ex ->
                    if (ex.exercise.id == exerciseId) ex.copy(sets = ex.sets + newSet) else ex
                })
            }
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

    fun onWeightChanged(exerciseId: Long, setOrder: Int, raw: String) {
        val result = SurgicalValidator.parseDecimal(raw)
        if (result is SurgicalValidator.ValidationResult.Invalid) return  // discard silently
        _workoutState.update { state ->
            state.copy(exercises = state.exercises.map { ex ->
                if (ex.exercise.id != exerciseId) return@map ex
                ex.copy(sets = ex.sets.map { set ->
                    if (set.setOrder != setOrder) return@map set
                    val effectiveWeight = when (result) {
                        is SurgicalValidator.ValidationResult.Valid -> raw.trim()
                        is SurgicalValidator.ValidationResult.Empty -> set.ghostWeight ?: ""
                        else -> return@map set  // Invalid already returned above
                    }
                    set.copy(weight = effectiveWeight)  // no isCompleted side-effect
                })
            })
        }
        if (result is SurgicalValidator.ValidationResult.Valid) {
            debouncedSaveSet(exerciseId, setOrder)
        }
    }

    fun onRepsChanged(exerciseId: Long, setOrder: Int, raw: String) {
        val result = SurgicalValidator.parseReps(raw)
        if (result is SurgicalValidator.ValidationResult.Invalid) return  // discard silently
        _workoutState.update { state ->
            state.copy(exercises = state.exercises.map { ex ->
                if (ex.exercise.id != exerciseId) return@map ex
                ex.copy(sets = ex.sets.map { set ->
                    if (set.setOrder != setOrder) return@map set
                    val effectiveReps = when (result) {
                        is SurgicalValidator.ValidationResult.Valid -> raw.trim()
                        is SurgicalValidator.ValidationResult.Empty -> set.ghostReps ?: ""
                        else -> return@map set  // Invalid already returned above
                    }
                    set.copy(reps = effectiveReps)  // no isCompleted side-effect
                })
            })
        }
        if (result is SurgicalValidator.ValidationResult.Valid) {
            debouncedSaveSet(exerciseId, setOrder)
        }
    }

    private fun debouncedSaveSet(exerciseId: Long, setOrder: Int) {
        saveJobs[exerciseId to setOrder]?.cancel()
        saveJobs[exerciseId to setOrder] = viewModelScope.launch {
            delay(300)
            val set = _workoutState.value.exercises
                .find { it.exercise.id == exerciseId }?.sets?.find { it.setOrder == setOrder }
                ?: return@launch
            if (set.id <= 0L) return@launch
            val weight = SurgicalValidator.parseDecimal(set.weight)
                .let { if (it is SurgicalValidator.ValidationResult.Valid) it.value else null }
                ?: return@launch
            val reps = set.reps.toIntOrNull() ?: return@launch
            workoutSetDao.updateWeightReps(set.id, weight, reps)
        }
    }

    /**
     * V-button handler — the ONLY place isCompleted is set to true.
     * Gated behind dual SurgicalValidator checks; starts rest timer only on confirmed completion.
     */
    fun completeSet(exerciseId: Long, setOrder: Int) {
        _workoutState.update { state ->
            state.copy(exercises = state.exercises.map { ex ->
                if (ex.exercise.id != exerciseId) return@map ex
                ex.copy(sets = ex.sets.map { set ->
                    if (set.setOrder != setOrder) return@map set
                    val weightOk = SurgicalValidator.parseDecimal(set.weight) is SurgicalValidator.ValidationResult.Valid
                    val repsOk = SurgicalValidator.parseReps(set.reps) is SurgicalValidator.ValidationResult.Valid
                    if (!weightOk || !repsOk) return@map set  // silently reject incomplete sets
                    set.copy(isCompleted = true)
                })
            })
        }
        // Persist completion to DB and start timer only if actually completed
        val completedSet = _workoutState.value.exercises
            .find { it.exercise.id == exerciseId }?.sets?.find { it.setOrder == setOrder }
        if (completedSet?.isCompleted == true) {
            if (completedSet.id > 0L) {
                viewModelScope.launch { workoutSetDao.updateSetCompleted(completedSet.id, true) }
            }
            startRestTimer(exerciseId)
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
        elapsedTimerJob?.cancel()
        viewModelScope.launch {
            try {
                val state = _workoutState.value
                if (!state.isActive || state.startTime == null) return@launch

                val endTime = System.currentTimeMillis()
                val durationSeconds = ((endTime - state.startTime) / 1000).toInt()

                // Calculate total volume
                var totalVolume = 0.0
                state.exercises.forEach { exerciseWithSets ->
                    exerciseWithSets.sets.forEach { set ->
                        if (set.isCompleted) {
                            val weight = when (val r = SurgicalValidator.parseDecimal(set.weight)) {
                                is SurgicalValidator.ValidationResult.Valid -> r.value
                                else -> SurgicalValidator.parseDecimal(set.ghostWeight ?: "")
                                    .let { if (it is SurgicalValidator.ValidationResult.Valid) it.value else 0.0 }
                            }
                            val reps = set.reps.toIntOrNull() ?: 0
                            totalVolume += weight * reps
                        }
                    }
                }

                // Update the existing workout record (created at workout start via Iron Vault)
                val workoutId = state.workoutId ?: return@launch
                workoutDao.updateWorkout(
                    Workout(
                        id = workoutId,
                        routineId = state.routineId.takeIf { it > 0L },
                        timestamp = state.startTime!!,
                        durationSeconds = durationSeconds,
                        totalVolume = totalVolume,
                        notes = state.notes.ifBlank { null },
                        isCompleted = true
                    )
                )
                // Clean up skeleton rows the user never filled in
                workoutSetDao.deleteIncompleteSetsByWorkout(workoutId)

                // Update lastPerformed on the routine (Risk 4 fix)
                if (state.routineId > 0L) {
                    routineDao.updateLastPerformed(state.routineId, endTime)
                }

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

                val completedSetCount = state.exercises.sumOf { ex -> ex.sets.count { it.isCompleted } }
                val exerciseNames = state.exercises.map { it.exercise.name }
                _workoutState.update {
                    it.copy(
                        isActive = false,
                        pendingWorkoutSummary = WorkoutSummary(
                            workoutName = state.workoutName,
                            durationSeconds = durationSeconds,
                            totalVolume = totalVolume,
                            setCount = completedSetCount,
                            exerciseNames = exerciseNames
                        )
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("WorkoutViewModel", "finishWorkout failed", e)
                _workoutState.update {
                    ActiveWorkoutState(availableExercises = it.availableExercises)
                }
            }
        }
    }

    fun dismissWorkoutSummary() {
        _workoutState.update {
            ActiveWorkoutState(availableExercises = it.availableExercises)
        }
    }

    fun cancelWorkout() {
        elapsedTimerJob?.cancel()
        viewModelScope.launch {
            _workoutState.value.workoutId?.let { wid ->
                workoutSetDao.deleteSetsForWorkout(wid)
                workoutDao.deleteWorkoutById(wid)
            }
            _workoutState.update {
                ActiveWorkoutState(availableExercises = it.availableExercises)
            }
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
    private var elapsedTimerJob: kotlinx.coroutines.Job? = null

    private fun startElapsedTimer() {
        elapsedTimerJob?.cancel()
        elapsedTimerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _workoutState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
            }
        }
    }

    // Called by the service on the main thread for every countdown tick.
    private fun onTimerTick(remaining: Int) {
        if (remaining == 2 || remaining == 1) {
            viewModelScope.launch {
                val settings = userSettingsDao.getSettingsOnce()
                if (settings?.restTimerAudioEnabled == true) restTimerNotifier.playWarningBeep()
            }
        }
    }

    // Called by the service on the main thread when the countdown reaches zero naturally.
    private fun onTimerFinish() {
        viewModelScope.launch {
            val settings = userSettingsDao.getSettingsOnce()
            if (settings != null) {
                restTimerNotifier.notifyEnd(
                    audioEnabled = settings.restTimerAudioEnabled,
                    hapticsEnabled = settings.restTimerHapticsEnabled
                )
            }
            _workoutState.update { it.copy(restTimer = RestTimerState()) }
        }
    }

    fun startRestTimer(exerciseId: Long) {
        // Guard: cancel any in-process coroutine to prevent double-beep race conditions.
        timerJob?.cancel()

        val exercise = _workoutState.value.exercises
            .find { it.exercise.id == exerciseId }
            ?.exercise ?: return

        val restDuration = exercise.restDurationSeconds

        if (serviceBound && timerService != null) {
            // Delegate to the foreground service so the timer survives backgrounding.
            // The service's StateFlow is mirrored to _workoutState by timerConnection.
            timerService!!.startTimer(
                totalSeconds = restDuration,
                onTick = ::onTimerTick,
                onFinish = ::onTimerFinish
            )
        } else {
            // Fallback: in-process coroutine (service not yet bound).
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
                    _workoutState.update { it.copy(restTimer = it.restTimer.copy(remainingSeconds = i)) }
                    if (i == 2 || i == 1) {
                        val settings = userSettingsDao.getSettingsOnce()
                        if (settings?.restTimerAudioEnabled == true) restTimerNotifier.playWarningBeep()
                    }
                    if (i == 0) {
                        val settings = userSettingsDao.getSettingsOnce()
                        if (settings != null) {
                            restTimerNotifier.notifyEnd(
                                audioEnabled = settings.restTimerAudioEnabled,
                                hapticsEnabled = settings.restTimerHapticsEnabled
                            )
                        }
                    }
                    if (i > 0) delay(1000)
                }
                _workoutState.update { it.copy(restTimer = RestTimerState()) }
            }
        }
    }

    fun addTimeToTimer(seconds: Int) {
        val currentTimer = _workoutState.value.restTimer
        if (!currentTimer.isActive) return
        val newRemaining = currentTimer.remainingSeconds + seconds
        if (serviceBound && timerService != null) {
            // Restart the service timer with the extended duration.
            timerService!!.startTimer(
                totalSeconds = newRemaining,
                onTick = ::onTimerTick,
                onFinish = ::onTimerFinish
            )
        } else {
            _workoutState.update {
                it.copy(
                    restTimer = currentTimer.copy(
                        remainingSeconds = newRemaining,
                        totalSeconds = currentTimer.totalSeconds + seconds
                    )
                )
            }
        }
    }

    fun skipRestTimer() {
        timerJob?.cancel()
        if (serviceBound && timerService != null) {
            timerService!!.stopTimer()
        }
        _workoutState.update { it.copy(restTimer = RestTimerState()) }
    }

    /** Pair two exercises as a superset by assigning a shared UUID groupId. */
    fun pairAsSuperset(exerciseId1: Long, exerciseId2: Long) {
        val groupId = UUID.randomUUID().toString()
        _workoutState.update { state ->
            state.copy(
                exercises = state.exercises.map { ex ->
                    if (ex.exercise.id == exerciseId1 || ex.exercise.id == exerciseId2) {
                        ex.copy(supersetGroupId = groupId)
                    } else ex
                },
                activeSupersetExerciseId = exerciseId1
            )
        }
    }

    /** Remove an exercise from its superset. If only one remains, clear that one too. */
    fun removeFromSuperset(exerciseId: Long) {
        val groupId = _workoutState.value.exercises
            .find { it.exercise.id == exerciseId }?.supersetGroupId ?: return
        val partnersAfterRemoval = _workoutState.value.exercises
            .filter { it.supersetGroupId == groupId && it.exercise.id != exerciseId }
        _workoutState.update { state ->
            state.copy(
                exercises = state.exercises.map { ex ->
                    when {
                        ex.exercise.id == exerciseId -> ex.copy(supersetGroupId = null)
                        partnersAfterRemoval.size < 2 && ex.supersetGroupId == groupId -> ex.copy(supersetGroupId = null)
                        else -> ex
                    }
                },
                activeSupersetExerciseId = if (state.activeSupersetExerciseId == exerciseId) null else state.activeSupersetExerciseId
            )
        }
    }

    /** Pair N exercises as a superset by assigning a shared UUID groupId. */
    fun pairAsSuperset(exerciseIds: Set<Long>) {
        val groupId = UUID.randomUUID().toString()
        _workoutState.update { state ->
            state.copy(
                exercises = state.exercises.map { ex ->
                    if (ex.exercise.id in exerciseIds) ex.copy(supersetGroupId = groupId) else ex
                },
                activeSupersetExerciseId = exerciseIds.firstOrNull()
            )
        }
    }

    /** Remove an exercise from the active workout list. */
    fun removeExercise(exerciseId: Long) {
        _workoutState.update { state ->
            state.copy(exercises = state.exercises.filter { it.exercise.id != exerciseId })
        }
    }

    /** Replace an exercise, keeping all its existing sets. */
    fun replaceExercise(oldId: Long, newExercise: Exercise) {
        _workoutState.update { state ->
            state.copy(
                exercises = state.exercises.map { ex ->
                    if (ex.exercise.id == oldId) ex.copy(exercise = newExercise) else ex
                }
            )
        }
    }

    /** Update a volatile session note for the exercise (lost when workout ends). */
    fun updateExerciseSessionNote(exerciseId: Long, note: String?) {
        _workoutState.update { state ->
            state.copy(
                exercises = state.exercises.map { ex ->
                    if (ex.exercise.id == exerciseId) ex.copy(sessionNote = note) else ex
                }
            )
        }
    }

    /** Update a sticky note for an exercise — persisted to DB via RoutineExercise.stickyNote. */
    fun updateExerciseStickyNote(exerciseId: Long, note: String?) {
        viewModelScope.launch {
            try {
                val routineId = _workoutState.value.routineId
                if (routineId > 0L) {
                    routineExerciseDao.updateStickyNote(routineId, exerciseId, note)
                }
                _workoutState.update { state ->
                    state.copy(
                        exercises = state.exercises.map { ex ->
                            if (ex.exercise.id == exerciseId) ex.copy(stickyNote = note) else ex
                        }
                    )
                }
            } catch (e: Exception) {
                _workoutState.update { it.copy(snackbarMessage = "Failed to save sticky note") }
            }
        }
    }

    /** Update the rest timer duration for an exercise and persist it to the exercise record. */
    fun updateExerciseRestTimer(exerciseId: Long, seconds: Int) {
        viewModelScope.launch {
            try {
                val exercise = _workoutState.value.exercises
                    .find { it.exercise.id == exerciseId }?.exercise ?: return@launch
                val updated = exercise.copy(restDurationSeconds = seconds)
                exerciseRepository.updateExercise(updated)
                _workoutState.update { state ->
                    state.copy(
                        exercises = state.exercises.map { ex ->
                            if (ex.exercise.id == exerciseId) ex.copy(exercise = updated) else ex
                        }
                    )
                }
            } catch (e: Exception) {
                _workoutState.update { it.copy(snackbarMessage = "Failed to update rest timer") }
            }
        }
    }

    /** Add 3 WARMUP-type sets to the exercise (prepended before working sets). */
    fun addWarmupSetsToExercise(exerciseId: Long) {
        viewModelScope.launch {
            try {
                val exerciseEntry = _workoutState.value.exercises
                    .find { it.exercise.id == exerciseId } ?: return@launch
                val warmupSets = (1..3).map { i ->
                    ActiveSet(setOrder = i, setType = SetType.WARMUP)
                }
                // Shift existing sets up by 3
                val shifted = exerciseEntry.sets.mapIndexed { idx, set ->
                    set.copy(setOrder = idx + 4)
                }
                _workoutState.update { state ->
                    state.copy(
                        exercises = state.exercises.map { ex ->
                            if (ex.exercise.id == exerciseId) {
                                ex.copy(sets = warmupSets + shifted)
                            } else ex
                        }
                    )
                }
            } catch (e: Exception) {
                _workoutState.update { it.copy(snackbarMessage = "Failed to add warmup sets") }
            }
        }
    }

    /** Enter multi-select mode for creating a superset across N exercises. */
    fun enterSupersetSelectMode() {
        _workoutState.update { it.copy(isSupersetSelectMode = true, supersetCandidateIds = emptySet()) }
    }

    /** Toggle an exercise in/out of the superset candidate set. */
    fun toggleSupersetCandidate(exerciseId: Long) {
        _workoutState.update { state ->
            val updated = if (exerciseId in state.supersetCandidateIds) {
                state.supersetCandidateIds - exerciseId
            } else {
                state.supersetCandidateIds + exerciseId
            }
            state.copy(supersetCandidateIds = updated)
        }
    }

    /** Commit the current candidate set as a superset group and exit select mode. */
    fun commitSupersetSelection() {
        val candidates = _workoutState.value.supersetCandidateIds
        if (candidates.size >= 2) {
            pairAsSuperset(candidates)
        }
        exitSupersetSelectMode()
    }

    /** Cancel superset select mode without making any changes. */
    fun exitSupersetSelectMode() {
        _workoutState.update { it.copy(isSupersetSelectMode = false, supersetCandidateIds = emptySet()) }
    }

    /** Move an exercise from [fromIndex] to [toIndex] in the exercises list. */
    fun reorderExercise(fromIndex: Int, toIndex: Int) {
        _workoutState.update { state ->
            val list = state.exercises.toMutableList()
            if (fromIndex in list.indices && toIndex in list.indices) {
                val item = list.removeAt(fromIndex)
                list.add(toIndex, item)
            }
            state.copy(exercises = list)
        }
    }

    /** Clear the current snackbar message after it has been shown. */
    fun clearSnackbarMessage() {
        _workoutState.update { it.copy(snackbarMessage = null) }
    }

    /** After completing a set in a superset, flip to the partner exercise. */
    fun advanceSupersetTurn(exerciseId: Long) {
        val groupId = _workoutState.value.exercises
            .find { it.exercise.id == exerciseId }?.supersetGroupId ?: return
        val partner = _workoutState.value.exercises
            .firstOrNull { it.supersetGroupId == groupId && it.exercise.id != exerciseId }
            ?: return
        _workoutState.update { it.copy(activeSupersetExerciseId = partner.exercise.id) }
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

    /** Cycle the SetType for a set: NORMAL → WARMUP → FAILURE → DROP → NORMAL */
    fun cycleSetType(exerciseId: Long, setOrder: Int) {
        val next: (SetType) -> SetType = { cur ->
            when (cur) {
                SetType.NORMAL  -> SetType.WARMUP
                SetType.WARMUP  -> SetType.FAILURE
                SetType.FAILURE -> SetType.DROP
                SetType.DROP    -> SetType.NORMAL
            }
        }
        _workoutState.update { state ->
            state.copy(exercises = state.exercises.map { ex ->
                if (ex.exercise.id != exerciseId) return@map ex
                ex.copy(sets = ex.sets.map { set ->
                    if (set.setOrder != setOrder) return@map set
                    set.copy(setType = next(set.setType))
                })
            })
        }
        val updated = _workoutState.value.exercises
            .find { it.exercise.id == exerciseId }?.sets?.find { it.setOrder == setOrder }
        if (updated != null && updated.id > 0L) {
            viewModelScope.launch { workoutSetDao.updateSetType(updated.id, updated.setType) }
        }
    }
}
