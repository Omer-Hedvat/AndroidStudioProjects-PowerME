package com.powerme.app.ui.workout

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powerme.app.data.sync.FirestoreSyncManager
import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.Routine
import com.powerme.app.data.database.RoutineDao
import com.powerme.app.data.database.RoutineExercise
import com.powerme.app.data.database.SetType
import com.powerme.app.data.database.TargetJoint
import com.powerme.app.data.database.UserSettingsDao
import com.powerme.app.data.database.Workout
import com.powerme.app.data.database.WorkoutDao
import com.powerme.app.data.database.WorkoutSet
import com.powerme.app.data.database.WarmupLog
import com.powerme.app.analytics.BoazPerformanceAnalyzer
import com.powerme.app.data.database.StateHistoryEntry
import com.powerme.app.data.repository.ExerciseRepository
import com.powerme.app.data.repository.MedicalLedgerRepository
import com.powerme.app.data.repository.StateHistoryRepository
import com.powerme.app.data.repository.WarmupRepository
import com.powerme.app.data.repository.WorkoutRepository
import com.powerme.app.ui.chat.MedicalRestrictionsDoc
import com.powerme.app.util.ClocksTimerBridge
import com.powerme.app.util.RestTimerNotifier
import com.powerme.app.util.SurgicalValidator
import com.powerme.app.util.WorkoutTimerService
import com.powerme.app.warmup.WarmupPrescription
import com.powerme.app.warmup.WarmupService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

/**
 * Deserializes a comma-separated setTypesJson string into a list of SetType values.
 * If the string is empty or shorter than [count], missing entries default to NORMAL.
 */
private fun String.toEditModeSetTypes(count: Int): List<SetType> {
    if (isEmpty()) return List(count) { SetType.NORMAL }
    val parts = split(",")
    return List(count) { i ->
        parts.getOrNull(i)?.let { runCatching { SetType.valueOf(it) }.getOrNull() } ?: SetType.NORMAL
    }
}

/**
 * Formats a weight Double for display: strips trailing zeros, preserves up to 2 decimal places.
 * 80.0 → "80", 80.5 → "80.5", 32.25 → "32.25"
 */
private fun formatWeight(value: Double): String = when {
    value == value.toLong().toDouble() -> value.toLong().toString()
    value * 10 == (value * 10).toLong().toDouble() -> "%.1f".format(value)
    else -> "%.2f".format(value)
}

/**
 * Deserializes a comma-separated values string (weights or reps) into a list of strings.
 * If the string is empty or shorter than [count], missing entries fall back to [default].
 */
private fun String.toEditModeValues(count: Int, default: String): List<String> {
    if (isEmpty()) return List(count) { default }
    val parts = split(",")
    return List(count) { i -> parts.getOrNull(i)?.takeIf { it.isNotBlank() } ?: default }
}

data class ActiveSet(
    val id: String = "",
    val setOrder: Int,
    val weight: String = "",
    val reps: String = "",
    val rpe: String = "",
    val rpeValue: Int? = null,        // Badge-style RPE (Int×10: 60=6.0, 65=6.5, …, 100=10.0)
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
    val totalSeconds: Int = 0,
    val exerciseId: Long? = null,
    val setOrder: Int? = null,
    val isPaused: Boolean = false
)

data class WorkoutSummary(
    val workoutName: String,
    val durationSeconds: Int,
    val totalVolume: Double,
    val setCount: Int,
    val exerciseNames: List<String>,
    val pendingRoutineSync: RoutineSyncType? = null
)

/** Captured at workout start from the routine template — used to detect changes at finish. */
data class RoutineExerciseSnapshot(
    val exerciseId: Long,
    val sets: Int,
    val perSetWeights: List<String>,
    val perSetReps: List<Int>
)

enum class RoutineSyncType { STRUCTURE, VALUES, BOTH }

data class DeletedSetClipboard(
    val weight: String,
    val reps: String,
    val setType: SetType
)

data class ActiveWorkoutState(
    val isActive: Boolean = false,
    val workoutId: String? = null,
    val routineId: String = "",
    val startTime: Long? = null,
    val exercises: List<ExerciseWithSets> = emptyList(),
    val availableExercises: List<Exercise> = emptyList(),
    val notes: String = "",
    val warmupPrescription: WarmupPrescription? = null,
    val isLoadingWarmup: Boolean = false,
    val warmupCompleted: Boolean = false,
    val restTimer: RestTimerState = RestTimerState(),
    val standaloneTimer: RestTimerState = RestTimerState(),
    val isMinimized: Boolean = false,
    val activeSupersetExerciseId: Long? = null,
    val isSupersetSelectMode: Boolean = false,
    val supersetCandidateIds: Set<Long> = emptySet(),
    val snackbarMessage: String? = null,
    val workoutName: String = "Workout",
    val elapsedSeconds: Int = 0,
    val pendingWorkoutSummary: WorkoutSummary? = null,
    val restTimeOverrides: Map<String, Int> = emptyMap(),
    val routineSnapshot: List<RoutineExerciseSnapshot> = emptyList(),
    val pendingRoutineSync: RoutineSyncType? = null,
    val hiddenRestSeparators: Set<String> = emptySet(),
    val isEditMode: Boolean = false,
    val editModeSaved: Boolean = false,  // one-shot navigation trigger (like pendingWorkoutSummary)
    val showEditGuard: Boolean = false,
    val deletedSetClipboard: Map<Long, DeletedSetClipboard> = emptyMap(),
    val collapsedExerciseIds: Set<Long> = emptySet()
)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val warmupRepository: WarmupRepository,
    private val warmupService: WarmupService,
    private val workoutDao: WorkoutDao,
    private val workoutSetDao: com.powerme.app.data.database.WorkoutSetDao,
    private val routineExerciseDao: com.powerme.app.data.database.RoutineExerciseDao,
    private val exerciseDao: com.powerme.app.data.database.ExerciseDao,
    private val routineDao: RoutineDao,
    private val userSettingsDao: UserSettingsDao,
    private val medicalLedgerRepository: MedicalLedgerRepository,
    private val boazPerformanceAnalyzer: BoazPerformanceAnalyzer,
    private val stateHistoryRepository: StateHistoryRepository,
    private val firestoreSyncManager: FirestoreSyncManager,
    private val clocksTimerBridge: ClocksTimerBridge,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val restTimerNotifier = RestTimerNotifier(context)

    val clocksTimerState: StateFlow<ClocksTimerBridge.Snapshot?> = clocksTimerBridge.state

    private val settingsState: StateFlow<com.powerme.app.data.database.UserSettings?> =
        userSettingsDao.getSettings()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = null
            )

    // --- WorkoutTimerService binding ---
    private var timerService: WorkoutTimerService? = null
    private var serviceBound = false
    private var serviceCollectionJob: kotlinx.coroutines.Job? = null
    private var standaloneTimerJob: Job? = null

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
                                restTimer = state.restTimer.copy(
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
        standaloneTimerJob?.cancel()
        elapsedTimerJob?.cancel()
        if (serviceBound) {
            context.unbindService(timerConnection)
            serviceBound = false
        }
    }

    fun minimizeWorkout() {
        _workoutState.update { it.copy(isMinimized = true) }
    }

    fun maximizeWorkout() {
        _workoutState.update { it.copy(isMinimized = false) }
    }

    fun startStandaloneTimer(seconds: Int) {
        standaloneTimerJob?.cancel()
        _workoutState.update { it.copy(
            standaloneTimer = RestTimerState(
                isActive = true,
                remainingSeconds = seconds,
                totalSeconds = seconds
            )
        ) }
        
        standaloneTimerJob = viewModelScope.launch {
            while (_workoutState.value.standaloneTimer.remainingSeconds > 0) {
                if (!_workoutState.value.standaloneTimer.isPaused) {
                    delay(1000)
                    _workoutState.update { it.copy(
                        standaloneTimer = it.standaloneTimer.copy(
                            remainingSeconds = it.standaloneTimer.remainingSeconds - 1
                        )
                    ) }
                    if (_workoutState.value.standaloneTimer.remainingSeconds in 1..2) {
                        restTimerNotifier.playWarningBeep()
                    }
                } else {
                    delay(500)
                }
            }
            if (_workoutState.value.standaloneTimer.remainingSeconds == 0) {
                restTimerNotifier.notifyEnd()
                stopStandaloneTimer()
            }
        }
    }

    fun pauseStandaloneTimer() {
        _workoutState.update { it.copy(
            standaloneTimer = it.standaloneTimer.copy(isPaused = true)
        ) }
    }

    fun resumeStandaloneTimer() {
        _workoutState.update { it.copy(
            standaloneTimer = it.standaloneTimer.copy(isPaused = false)
        ) }
    }

    fun stopStandaloneTimer() {
        standaloneTimerJob?.cancel()
        _workoutState.update { it.copy(standaloneTimer = RestTimerState()) }
    }

    fun addTimeToStandaloneTimer(seconds: Int) {
        _workoutState.update { it.copy(
            standaloneTimer = it.standaloneTimer.copy(
                remainingSeconds = it.standaloneTimer.remainingSeconds + seconds,
                totalSeconds = it.standaloneTimer.totalSeconds + seconds
            )
        ) }
    }

    fun subtractFromStandaloneTimer(seconds: Int) {
        _workoutState.update { it.copy(
            standaloneTimer = it.standaloneTimer.copy(
                remainingSeconds = (it.standaloneTimer.remainingSeconds - seconds).coerceAtLeast(0)
            )
        ) }
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

            // Purge orphaned workouts: pre-v30 rows (startTimeMs=0) or sessions
            // abandoned more than 24 hours ago are stale and should not be restored.
            val ageMs = System.currentTimeMillis() - activeWorkout.startTimeMs
            val isStale = activeWorkout.startTimeMs == 0L || ageMs > 24 * 60 * 60 * 1000L
            if (isStale) {
                workoutSetDao.deleteSetsForWorkout(activeWorkout.id)
                workoutDao.deleteWorkoutById(activeWorkout.id)
                return@launch
            }

            val dbSets = workoutSetDao.getSetsForWorkout(activeWorkout.id).first()

            // Purge ghost workouts: no sets were ever completed → user opened and closed
            // without doing any real work. Do not restore.
            if (dbSets.none { it.isCompleted }) {
                workoutSetDao.deleteSetsForWorkout(activeWorkout.id)
                workoutDao.deleteWorkoutById(activeWorkout.id)
                return@launch
            }

            val groupedByExercise = dbSets.groupBy { it.exerciseId }
            val exercises = groupedByExercise.keys.mapNotNull { exerciseId ->
                exerciseRepository.getExerciseById(exerciseId)?.let { exercise ->
                    val activeSets = groupedByExercise[exerciseId]!!.map { ws ->
                        ActiveSet(
                            id = ws.id,
                            setOrder = ws.setOrder,
                            weight = if (ws.weight > 0.0) formatWeight(ws.weight) else "",
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
                    routineId = activeWorkout.routineId ?: "",
                    startTime = activeWorkout.timestamp,
                    exercises = exercises,
                    workoutName = name,
                    editModeSaved = false
                )
            }
            startElapsedTimer()
        }
    }

    fun startWorkoutFromRoutine(routineId: String) {
        if (_workoutState.value.isMinimized || _workoutState.value.isActive) {
            maximizeWorkout()
            return
        }
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
                    val weightStr = if (ws.weight > 0.0) formatWeight(ws.weight) else "0"
                    ActiveSet(
                        id = ws.id,
                        setOrder = ws.setOrder,
                        weight = weightStr,
                        reps = ws.reps.toString(),
                        setType = ws.setType,
                        ghostWeight = ghost?.weight?.let { if (it > 0.0) formatWeight(it) else null },
                        ghostReps = ghost?.reps?.let { if (it > 0) it.toString() else null }
                    )
                }
                val sticky = try { routineExerciseDao.getStickyNote(routineId, re.exerciseId) } catch (_: Exception) { null }
                ExerciseWithSets(exercise = exercise, sets = activeSets, stickyNote = sticky, supersetGroupId = re.supersetGroupId)
            }
            val name = routineDao.getRoutineById(routineId)?.name ?: "Workout"
            val snapshot = routineExercises.map { re ->
                val weights = re.setWeightsJson.split(",").map { it.trim() }
                    .takeIf { it.size == re.sets && it.all { w -> w.isNotBlank() } }
                    ?: List(re.sets) { re.defaultWeight }
                val reps = re.setRepsJson.split(",").mapNotNull { it.trim().toIntOrNull() }
                    .takeIf { it.size == re.sets }
                    ?: List(re.sets) { re.reps }
                RoutineExerciseSnapshot(
                    exerciseId = re.exerciseId,
                    sets = re.sets,
                    perSetWeights = weights,
                    perSetReps = reps
                )
            }
            _workoutState.update {
                it.copy(
                    isActive = true, workoutId = bootstrap.workoutId,
                    routineId = routineId, startTime = System.currentTimeMillis(),
                    exercises = exercises, workoutName = name,
                    routineSnapshot = snapshot, editModeSaved = false
                )
            }
            startElapsedTimer()
        }
    }

    fun startWorkout(routineId: String = "") {
        if (_workoutState.value.isMinimized || _workoutState.value.isActive) {
            maximizeWorkout()
            return
        }
        viewModelScope.launch {
            val id = workoutRepository.createEmptyWorkout(routineId.takeIf { it.isNotBlank() })
            _workoutState.update {
                it.copy(
                    isActive = true,
                    workoutId = id,
                    routineId = routineId,
                    startTime = System.currentTimeMillis(),
                    exercises = emptyList(),
                    workoutName = "Empty Workout",
                    editModeSaved = false
                )
            }
            startElapsedTimer()
        }
    }

    fun startEditMode(routineId: String) {
        if (_workoutState.value.isActive && !_workoutState.value.isEditMode) {
            _workoutState.update { it.copy(showEditGuard = true) }
            return
        }
        viewModelScope.launch {
            _workoutState.update { it.copy(editModeSaved = false) }
            val (exercises, name) = withContext(Dispatchers.IO) {
                val routineExercises = routineExerciseDao.getForRoutine(routineId)
                val exList = routineExercises.mapNotNull { re ->
                    val exercise = exerciseRepository.getExerciseById(re.exerciseId) ?: return@mapNotNull null
                    val setTypes = re.setTypesJson.toEditModeSetTypes(re.sets)
                    val weights = re.setWeightsJson.toEditModeValues(re.sets, re.defaultWeight)
                    val repsList = re.setRepsJson.toEditModeValues(re.sets, if (re.reps > 0) re.reps.toString() else "")
                    val activeSets = (1..re.sets).map { i ->
                        ActiveSet(
                            id = "edit_$i",
                            setOrder = i,
                            weight = weights[i - 1],
                            reps = repsList[i - 1],
                            setType = setTypes[i - 1]
                        )
                    }
                    val sticky = try { routineExerciseDao.getStickyNote(routineId, re.exerciseId) } catch (_: Exception) { null }
                    ExerciseWithSets(exercise = exercise, sets = activeSets, stickyNote = sticky, supersetGroupId = re.supersetGroupId)
                }
                val routineName = routineDao.getRoutineById(routineId)?.name ?: "Routine"
                Pair(exList, routineName)
            }
            _workoutState.update {
                it.copy(
                    isActive = true,
                    isEditMode = true,
                    routineId = routineId,
                    workoutId = null,
                    startTime = null,
                    exercises = exercises,
                    workoutName = name,
                    elapsedSeconds = 0,
                    routineSnapshot = emptyList(),
                    editModeSaved = false,
                    collapsedExerciseIds = emptySet()
                )
            }
        }
    }

    fun saveRoutineEdits() {
        viewModelScope.launch {
            val state = _workoutState.value
            val routineId = state.routineId
            val originalIds = routineExerciseDao.getForRoutine(routineId).map { it.exerciseId }.toSet()
            val currentIds = state.exercises.map { it.exercise.id }.toSet()

            // Delete removed exercises
            for (exerciseId in originalIds - currentIds) {
                routineExerciseDao.deleteByRoutineAndExercise(routineId, exerciseId)
            }

            // Update or insert exercises
            state.exercises.forEachIndexed { index, ex ->
                val exerciseId = ex.exercise.id
                val sortedSets = ex.sets.sortedBy { it.setOrder }
                val firstSet = sortedSets.firstOrNull()
                // defaultWeight / reps kept in sync for Diff Engine compatibility
                val reps = firstSet?.reps?.toIntOrNull() ?: 0
                val weight = firstSet?.weight ?: ""
                val setTypesJson = sortedSets.joinToString(",") { it.setType.name }
                val setWeightsJson = sortedSets.joinToString(",") { it.weight }
                val setRepsJson = sortedSets.joinToString(",") { it.reps }
                if (exerciseId in originalIds) {
                    routineExerciseDao.updateSets(routineId, exerciseId, ex.sets.size)
                    routineExerciseDao.updateRepsAndWeight(routineId, exerciseId, reps, weight)
                    routineExerciseDao.updateSetTypesJson(routineId, exerciseId, setTypesJson)
                    routineExerciseDao.updateSetWeightsAndReps(routineId, exerciseId, setWeightsJson, setRepsJson)
                    routineExerciseDao.updateSupersetGroupId(routineId, exerciseId, ex.supersetGroupId)
                } else {
                    routineExerciseDao.insert(
                        RoutineExercise(
                            id = UUID.randomUUID().toString(),
                            routineId = routineId,
                            exerciseId = exerciseId,
                            sets = ex.sets.size,
                            reps = reps,
                            restTime = 90,
                            order = index,
                            defaultWeight = weight,
                            supersetGroupId = ex.supersetGroupId,
                            setTypesJson = setTypesJson,
                            setWeightsJson = setWeightsJson,
                            setRepsJson = setRepsJson
                        )
                    )
                }
            }
            // Bump updatedAt and push routine to Firestore
            val now = System.currentTimeMillis()
            val existing = routineDao.getRoutineById(routineId)
            if (existing != null) {
                routineDao.updateRoutine(existing.copy(updatedAt = now))
            }
            firestoreSyncManager.pushRoutine(routineId)

            _workoutState.update {
                ActiveWorkoutState(
                    availableExercises = it.availableExercises,
                    editModeSaved = true
                )
            }
        }
    }

    fun cancelEditMode() {
        _workoutState.update {
            ActiveWorkoutState(availableExercises = it.availableExercises)
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

            // Create initial sets pre-populated from previous session
            val initialSets = if (previousSets.isNotEmpty()) {
                previousSets.mapIndexed { index, prevSet ->
                    val weightStr = prevSet.weight.let {
                        if (it == it.toLong().toDouble()) it.toLong().toString() else "%.1f".format(it)
                    }
                    ActiveSet(
                        setOrder = index + 1,
                        weight = weightStr,
                        reps = prevSet.reps.toString(),
                        ghostWeight = prevSet.weight.toString(),
                        ghostReps = prevSet.reps.toString(),
                        ghostRpe = prevSet.rpe?.toString()
                    )
                }
            } else {
                listOf(ActiveSet(setOrder = 1, weight = "0", reps = "0"))
            }

            // Load sticky note from DB (only when a routine is active)
            val routineId = _workoutState.value.routineId
            val sticky = if (routineId.isNotBlank()) {
                try { routineExerciseDao.getStickyNote(routineId, exercise.id) } catch (_: Exception) { null }
            } else null

            // Persist initial sets to DB if a workout is active (Iron Vault)
            val workoutId = _workoutState.value.workoutId
            val setsWithIds = if (workoutId != null) {
                initialSets.map { activeSet ->
                    val setId = UUID.randomUUID().toString()
                    val ws = WorkoutSet(
                        id = setId,
                        workoutId = workoutId,
                        exerciseId = exercise.id,
                        setOrder = activeSet.setOrder,
                        weight = activeSet.weight.toDoubleOrNull() ?: 0.0,
                        reps = activeSet.reps.toIntOrNull() ?: 0,
                        setType = activeSet.setType
                    )
                    workoutRepository.createWorkoutSet(ws)
                    activeSet.copy(id = setId)
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
            val clipboard = state.deletedSetClipboard[exerciseId]
            val newWeightStr: String
            val newRepsStr: String
            val newSetType: SetType
            if (clipboard != null) {
                newWeightStr = clipboard.weight
                newRepsStr = clipboard.reps
                newSetType = clipboard.setType
            } else {
                val lastSet = currentExercise.sets.lastOrNull()
                newWeightStr = lastSet?.weight ?: ""
                newRepsStr = lastSet?.reps ?: ""
                newSetType = SetType.NORMAL
            }
            val newSetId = if (state.isEditMode) "edit_${System.nanoTime()}" else UUID.randomUUID().toString()
            var newSet = ActiveSet(
                id = newSetId,
                setOrder = newSetOrder,
                weight = newWeightStr,
                reps = newRepsStr,
                setType = newSetType
            )

            val workoutId = state.workoutId
            if (workoutId != null) {
                val ws = WorkoutSet(
                    id = newSetId,
                    workoutId = workoutId,
                    exerciseId = exerciseId,
                    setOrder = newSetOrder,
                    weight = newWeightStr.toDoubleOrNull() ?: 0.0,
                    reps = newRepsStr.toIntOrNull() ?: 0,
                    setType = newSetType
                )
                workoutSetDao.insertSet(ws)
            }

            _workoutState.update { s ->
                s.copy(
                    exercises = s.exercises.map { ex ->
                        if (ex.exercise.id == exerciseId) ex.copy(sets = ex.sets + newSet) else ex
                    },
                    deletedSetClipboard = if (clipboard != null) s.deletedSetClipboard - exerciseId else s.deletedSetClipboard
                )
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
            val ex = state.exercises.find { it.exercise.id == exerciseId }
            val changedSetType = ex?.sets?.find { it.setOrder == setOrder }?.setType ?: SetType.NORMAL
            val isWarmupGroup = changedSetType == SetType.WARMUP
            // Cascade only within the same type group (warmup→warmup, work→work)
            val groupSets = ex?.sets?.filter { s ->
                if (isWarmupGroup) s.setType == SetType.WARMUP else s.setType != SetType.WARMUP
            } ?: emptyList()
            val groupMinOrder = groupSets.minOfOrNull { it.setOrder } ?: setOrder
            val isFirstInGroup = setOrder == groupMinOrder
            val prevFirstGroupWeight = groupSets.find { it.setOrder == groupMinOrder }?.weight ?: ""
            state.copy(exercises = state.exercises.map { e ->
                if (e.exercise.id != exerciseId) return@map e
                e.copy(sets = e.sets.map { set ->
                    when {
                        set.setOrder == setOrder -> {
                            val effectiveWeight = when (result) {
                                is SurgicalValidator.ValidationResult.Valid -> raw.trim()
                                is SurgicalValidator.ValidationResult.Empty -> set.ghostWeight ?: ""
                                else -> return@map set
                            }
                            set.copy(weight = effectiveWeight)
                        }
                        // Cascade within group: first set in group changed → fill group sets that
                        // are blank OR still equal to whatever the first group set had before
                        isFirstInGroup && !set.isCompleted &&
                            (if (isWarmupGroup) set.setType == SetType.WARMUP else set.setType != SetType.WARMUP) &&
                            (set.weight.isBlank() || set.weight == prevFirstGroupWeight) &&
                            result is SurgicalValidator.ValidationResult.Valid ->
                            set.copy(weight = raw.trim())
                        else -> set
                    }
                })
            })
        }
        if (!_workoutState.value.isEditMode && result is SurgicalValidator.ValidationResult.Valid) {
            debouncedSaveSet(exerciseId, setOrder)
        }
    }

    fun onRepsChanged(exerciseId: Long, setOrder: Int, raw: String) {
        val result = SurgicalValidator.parseReps(raw)
        if (result is SurgicalValidator.ValidationResult.Invalid) return  // discard silently
        _workoutState.update { state ->
            val ex = state.exercises.find { it.exercise.id == exerciseId }
            val changedSetType = ex?.sets?.find { it.setOrder == setOrder }?.setType ?: SetType.NORMAL
            val isWarmupGroup = changedSetType == SetType.WARMUP
            // Cascade only within the same type group (warmup→warmup, work→work)
            val groupSets = ex?.sets?.filter { s ->
                if (isWarmupGroup) s.setType == SetType.WARMUP else s.setType != SetType.WARMUP
            } ?: emptyList()
            val groupMinOrder = groupSets.minOfOrNull { it.setOrder } ?: setOrder
            val isFirstInGroup = setOrder == groupMinOrder
            val prevFirstGroupReps = groupSets.find { it.setOrder == groupMinOrder }?.reps ?: ""
            state.copy(exercises = state.exercises.map { e ->
                if (e.exercise.id != exerciseId) return@map e
                e.copy(sets = e.sets.map { set ->
                    when {
                        set.setOrder == setOrder -> {
                            val effectiveReps = when (result) {
                                is SurgicalValidator.ValidationResult.Valid -> raw.trim()
                                is SurgicalValidator.ValidationResult.Empty -> set.ghostReps ?: ""
                                else -> return@map set
                            }
                            set.copy(reps = effectiveReps)
                        }
                        // Cascade within group: first set in group changed → fill group sets that
                        // are blank OR still equal to whatever the first group set had before
                        isFirstInGroup && !set.isCompleted &&
                            (if (isWarmupGroup) set.setType == SetType.WARMUP else set.setType != SetType.WARMUP) &&
                            (set.reps.isBlank() || set.reps == prevFirstGroupReps) &&
                            result is SurgicalValidator.ValidationResult.Valid ->
                            set.copy(reps = raw.trim())
                        else -> set
                    }
                })
            })
        }
        if (!_workoutState.value.isEditMode && result is SurgicalValidator.ValidationResult.Valid) {
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
            if (set.id.isBlank() || set.id.startsWith("edit_")) return@launch
            val weight = SurgicalValidator.parseDecimal(set.weight)
                .let { if (it is SurgicalValidator.ValidationResult.Valid) it.value else null }
                ?: return@launch
            val reps = set.reps.toIntOrNull() ?: return@launch
            workoutSetDao.updateWeightReps(set.id, weight, reps)
        }
    }

    /**
     * V-button handler — toggles isCompleted. Starts rest timer on completion, stops it on un-complete.
     */
    fun completeSet(exerciseId: Long, setOrder: Int) {
        val isEditMode = _workoutState.value.isEditMode
        var wasCompleted = false
        _workoutState.update { state ->
            state.copy(exercises = state.exercises.map { ex ->
                if (ex.exercise.id != exerciseId) return@map ex
                ex.copy(sets = ex.sets.map { set ->
                    if (set.setOrder != setOrder) return@map set
                    wasCompleted = set.isCompleted
                    set.copy(isCompleted = !set.isCompleted)
                })
            })
        }
        if (!isEditMode) {
            val updatedSet = _workoutState.value.exercises
                .find { it.exercise.id == exerciseId }?.sets?.find { it.setOrder == setOrder }
            if (updatedSet != null && updatedSet.id.isNotBlank() && !updatedSet.id.startsWith("edit_")) {
                viewModelScope.launch { workoutSetDao.updateSetCompleted(updatedSet.id, updatedSet.isCompleted) }
            }
            if (wasCompleted) {
                stopRestTimer()
            } else {
                val ex = _workoutState.value.exercises.find { it.exercise.id == exerciseId }
                val completedSet = ex?.sets?.find { it.setOrder == setOrder }
                val nextSet = ex?.sets
                    ?.filter { !it.isCompleted && it.setOrder > setOrder }
                    ?.minByOrNull { it.setOrder }
                val override = if (completedSet != null && ex != null) {
                    _workoutState.value.restTimeOverrides["${exerciseId}_${setOrder}"]
                        ?: computeRestDuration(completedSet.setType, nextSet?.setType, ex.exercise)
                } else null
                startRestTimer(exerciseId, setOrder, override)
            }
        }
    }

    fun stopRestTimer() {
        timerJob?.cancel()
        if (serviceBound && timerService != null) {
            timerService!!.stopTimer()
        }
        _workoutState.update { it.copy(restTimer = RestTimerState()) }
    }

    fun updateRpe(exerciseId: Long, setOrder: Int, rpe: Int?) {
        _workoutState.update { state ->
            state.copy(exercises = state.exercises.map { ex ->
                if (ex.exercise.id != exerciseId) return@map ex
                ex.copy(sets = ex.sets.map { set ->
                    if (set.setOrder != setOrder) return@map set
                    set.copy(rpeValue = rpe)
                })
            })
        }
        val set = _workoutState.value.exercises
            .find { it.exercise.id == exerciseId }?.sets?.find { it.setOrder == setOrder }
        if (set != null && set.id.isNotBlank() && !set.id.startsWith("edit_")) {
            viewModelScope.launch { workoutSetDao.updateRpe(set.id, rpe) }
        }
    }

    fun updateCardioSet(exerciseId: Long, setOrder: Int, distance: String, timeSeconds: String, rpe: String, rpeValue: Int? = null, completed: Boolean = distance.isNotBlank() && timeSeconds.isNotBlank()) {
        _workoutState.update { state ->
            val updatedExercises = state.exercises.map { exerciseWithSets ->
                if (exerciseWithSets.exercise.id == exerciseId) {
                    val updatedSets = exerciseWithSets.sets.map { set ->
                        if (set.setOrder == setOrder) {
                            set.copy(
                                distance = distance,
                                timeSeconds = timeSeconds,
                                rpe = rpe,
                                rpeValue = rpeValue ?: set.rpeValue,
                                isCompleted = completed
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
        val set = _workoutState.value.exercises
            .find { it.exercise.id == exerciseId }?.sets?.find { it.setOrder == setOrder }
        if (set != null && set.id.isNotBlank() && !set.id.startsWith("edit_")) {
            viewModelScope.launch {
                workoutSetDao.updateCardioSet(
                    set.id,
                    distance.toDoubleOrNull() ?: 0.0,
                    timeSeconds.toIntOrNull() ?: 0,
                    rpeValue ?: set.rpeValue,
                    completed
                )
            }
        }
    }

    fun updateTimedSet(exerciseId: Long, setOrder: Int, weight: String, timeSeconds: String, rpe: String, rpeValue: Int? = null, completed: Boolean = timeSeconds.isNotBlank()) {
        _workoutState.update { state ->
            val updatedExercises = state.exercises.map { exerciseWithSets ->
                if (exerciseWithSets.exercise.id == exerciseId) {
                    val updatedSets = exerciseWithSets.sets.map { set ->
                        if (set.setOrder == setOrder) {
                            set.copy(
                                weight = weight,
                                timeSeconds = timeSeconds,
                                rpe = rpe,
                                rpeValue = rpeValue ?: set.rpeValue,
                                isCompleted = completed
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
        val set = _workoutState.value.exercises
            .find { it.exercise.id == exerciseId }?.sets?.find { it.setOrder == setOrder }
        if (set != null && set.id.isNotBlank() && !set.id.startsWith("edit_")) {
            viewModelScope.launch {
                workoutSetDao.updateTimedSet(
                    set.id,
                    weight.toDoubleOrNull() ?: 0.0,
                    timeSeconds.toIntOrNull() ?: 0,
                    rpeValue ?: set.rpeValue,
                    completed
                )
            }
        }
    }

    fun deleteSet(exerciseId: Long, setOrder: Int) {
        // Cancel rest timer if it belongs to this set
        val timer = _workoutState.value.restTimer
        if (timer.isActive && timer.exerciseId == exerciseId && timer.setOrder == setOrder) {
            stopRestTimer()
        }
        val setToDelete = _workoutState.value.exercises
            .find { it.exercise.id == exerciseId }?.sets?.find { it.setOrder == setOrder }
        if (setToDelete != null && setToDelete.id.isNotBlank() && !setToDelete.id.startsWith("edit_")) {
            viewModelScope.launch { workoutSetDao.deleteSetById(setToDelete.id) }
        }
        // Save clipboard entry before deletion
        val clipboardEntry = setToDelete?.let {
            DeletedSetClipboard(weight = it.weight, reps = it.reps, setType = it.setType)
        }
        val key = "${exerciseId}_${setOrder}"
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
            state.copy(
                exercises = updatedExercises,
                restTimeOverrides = state.restTimeOverrides - key,
                hiddenRestSeparators = state.hiddenRestSeparators - key,
                deletedSetClipboard = if (clipboardEntry != null) {
                    state.deletedSetClipboard + (exerciseId to clipboardEntry)
                } else state.deletedSetClipboard
            )
        }
    }

    /** Hide a rest separator for this session (in-memory only). Cancels timer if it belongs to it. */
    fun deleteRestSeparator(exerciseId: Long, setOrder: Int) {
        val timer = _workoutState.value.restTimer
        if (timer.isActive && timer.exerciseId == exerciseId && timer.setOrder == setOrder) {
            stopRestTimer()
        }
        val key = "${exerciseId}_${setOrder}"
        _workoutState.update { it.copy(hiddenRestSeparators = it.hiddenRestSeparators + key) }
    }

    /** Set the type for a set directly and persist to DB. */
    fun selectSetType(exerciseId: Long, setOrder: Int, setType: SetType) {
        _workoutState.update { state ->
            state.copy(exercises = state.exercises.map { ex ->
                if (ex.exercise.id != exerciseId) return@map ex
                ex.copy(sets = ex.sets.map { set ->
                    if (set.setOrder != setOrder) return@map set
                    set.copy(setType = setType)
                })
            })
        }
        val updated = _workoutState.value.exercises
            .find { it.exercise.id == exerciseId }?.sets?.find { it.setOrder == setOrder }
        if (updated != null && updated.id.isNotBlank() && !updated.id.startsWith("edit_")) {
            viewModelScope.launch { workoutSetDao.updateSetType(updated.id, setType) }
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

                // Calculate total volume — all completed sets included
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
                        routineId = state.routineId.takeIf { it.isNotBlank() },
                        timestamp = state.startTime!!,
                        durationSeconds = durationSeconds,
                        totalVolume = totalVolume,
                        notes = state.notes.ifBlank { null },
                        isCompleted = true,
                        startTimeMs = state.startTime!!,
                        endTimeMs = endTime,
                        updatedAt = endTime
                    )
                )
                // Clean up skeleton rows the user never filled in
                workoutSetDao.deleteIncompleteSetsByWorkout(workoutId)
                // Push to Firestore (fire-and-forget; SDK queues when offline)
                firestoreSyncManager.pushWorkout(workoutId)

                // Update lastPerformed on the routine (Risk 4 fix)
                if (state.routineId.isNotBlank()) {
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

                val completedSetCount = state.exercises.sumOf { ex ->
                    ex.sets.count { it.isCompleted }
                }
                val exerciseNames = state.exercises
                    .filter { ex -> ex.sets.any { it.isCompleted } }
                    .map { it.exercise.name }
                val summary = WorkoutSummary(
                    workoutName = state.workoutName,
                    durationSeconds = durationSeconds,
                    totalVolume = totalVolume,
                    setCount = completedSetCount,
                    exerciseNames = exerciseNames
                )

                // Routine sync: detect changes vs snapshot captured at workout start
                val snapshot = state.routineSnapshot
                if (snapshot.isNotEmpty() && state.routineId.isNotBlank()) {
                    val currentExerciseIds = state.exercises.map { it.exercise.id }
                    val snapshotExerciseIds = snapshot.map { it.exerciseId }

                    // Structural: exercise list or order changed, or set count changed
                    val exerciseListChanged = currentExerciseIds != snapshotExerciseIds
                    val setCountChanged = state.exercises.any { ex ->
                        val snap = snapshot.find { it.exerciseId == ex.exercise.id }
                            ?: return@any true  // exercise added mid-workout
                        ex.sets.count { it.isCompleted } != snap.sets
                    }
                    val structuralChange = exerciseListChanged || setCountChanged

                    // Value change: numeric weight/reps differ for sets that existed in the snapshot
                    // (added sets are structural changes, not value changes)
                    val valueChange = state.exercises.any { ex ->
                        val snap = snapshot.find { it.exerciseId == ex.exercise.id } ?: return@any false
                        ex.sets.filter { it.isCompleted }.take(snap.sets).mapIndexed { i, set ->
                            val snapWeight = snap.perSetWeights.getOrNull(i) ?: ""
                            val snapReps = snap.perSetReps.getOrNull(i) ?: 0
                            val repsChanged = (set.reps.toIntOrNull() ?: 0) != snapReps
                            val weightChanged = (set.weight.toDoubleOrNull() ?: 0.0) != (snapWeight.toDoubleOrNull() ?: 0.0)
                            repsChanged || weightChanged
                        }.any { it }
                    }

                    val syncType = when {
                        structuralChange && valueChange -> RoutineSyncType.BOTH
                        structuralChange               -> RoutineSyncType.STRUCTURE
                        valueChange                    -> RoutineSyncType.VALUES
                        else                           -> null
                    }
                    if (syncType != null) {
                        _workoutState.update {
                            it.copy(
                                isActive = false,
                                pendingRoutineSync = syncType,
                                pendingWorkoutSummary = summary.copy(pendingRoutineSync = syncType)
                            )
                        }
                        return@launch
                    }
                }

                _workoutState.update { it.copy(isActive = false, pendingWorkoutSummary = summary) }
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

    fun confirmUpdateRoutineStructure() {
        viewModelScope.launch {
            val state = _workoutState.value
            state.exercises.forEach { ex ->
                val completedSets = ex.sets.filter { it.isCompleted }.sortedBy { it.setOrder }
                if (completedSets.isNotEmpty()) {
                    routineExerciseDao.updateSets(state.routineId, ex.exercise.id, completedSets.size)
                    val typesJson = completedSets.joinToString(",") { it.setType.name }
                    val weightsJson = completedSets.joinToString(",") { it.weight }
                    val repsJson = completedSets.joinToString(",") { it.reps }
                    routineExerciseDao.updateSetTypesJson(state.routineId, ex.exercise.id, typesJson)
                    routineExerciseDao.updateSetWeightsAndReps(state.routineId, ex.exercise.id, weightsJson, repsJson)
                    routineExerciseDao.updateSupersetGroupId(state.routineId, ex.exercise.id, ex.supersetGroupId)
                }
            }
            val routine = routineDao.getRoutineById(state.routineId)
            if (routine != null) {
                routineDao.updateRoutine(routine.copy(updatedAt = System.currentTimeMillis()))
                firestoreSyncManager.pushRoutine(state.routineId)
            }
            resolveRoutineSync()
            _workoutState.update { it.copy(snackbarMessage = "Routine structure updated") }
        }
    }

    fun confirmUpdateRoutineValues() {
        viewModelScope.launch {
            val state = _workoutState.value
            state.exercises.forEach { ex ->
                val completedSets = ex.sets.filter { it.isCompleted }.sortedBy { it.setOrder }
                val firstCompleted = completedSets.firstOrNull() ?: return@forEach
                val reps = firstCompleted.reps.toIntOrNull() ?: return@forEach
                routineExerciseDao.updateRepsAndWeight(
                    state.routineId, ex.exercise.id, reps, firstCompleted.weight
                )
                val typesJson = completedSets.joinToString(",") { it.setType.name }
                val weightsJson = completedSets.joinToString(",") { it.weight }
                val repsJson = completedSets.joinToString(",") { it.reps }
                routineExerciseDao.updateSetTypesJson(state.routineId, ex.exercise.id, typesJson)
                routineExerciseDao.updateSetWeightsAndReps(state.routineId, ex.exercise.id, weightsJson, repsJson)
                routineExerciseDao.updateSupersetGroupId(state.routineId, ex.exercise.id, ex.supersetGroupId)
            }
            val routine = routineDao.getRoutineById(state.routineId)
            if (routine != null) {
                routineDao.updateRoutine(routine.copy(updatedAt = System.currentTimeMillis()))
                firestoreSyncManager.pushRoutine(state.routineId)
            }
            resolveRoutineSync()
            _workoutState.update { it.copy(snackbarMessage = "Routine defaults updated") }
        }
    }

    fun confirmUpdateBoth() {
        viewModelScope.launch {
            val state = _workoutState.value
            state.exercises.forEach { ex ->
                val completedSets = ex.sets.filter { it.isCompleted }.sortedBy { it.setOrder }
                if (completedSets.isNotEmpty()) {
                    routineExerciseDao.updateSets(state.routineId, ex.exercise.id, completedSets.size)
                }
                val firstCompleted = completedSets.firstOrNull() ?: return@forEach
                val reps = firstCompleted.reps.toIntOrNull() ?: return@forEach
                routineExerciseDao.updateRepsAndWeight(
                    state.routineId, ex.exercise.id, reps, firstCompleted.weight
                )
                val typesJson = completedSets.joinToString(",") { it.setType.name }
                val weightsJson = completedSets.joinToString(",") { it.weight }
                val repsJson = completedSets.joinToString(",") { it.reps }
                routineExerciseDao.updateSetTypesJson(state.routineId, ex.exercise.id, typesJson)
                routineExerciseDao.updateSetWeightsAndReps(state.routineId, ex.exercise.id, weightsJson, repsJson)
                routineExerciseDao.updateSupersetGroupId(state.routineId, ex.exercise.id, ex.supersetGroupId)
            }
            val routine = routineDao.getRoutineById(state.routineId)
            if (routine != null) {
                routineDao.updateRoutine(routine.copy(updatedAt = System.currentTimeMillis()))
                firestoreSyncManager.pushRoutine(state.routineId)
            }
            resolveRoutineSync()
            _workoutState.update { it.copy(snackbarMessage = "Routine structure and defaults updated") }
        }
    }

    fun dismissRoutineSync() = resolveRoutineSync()

    private fun resolveRoutineSync() {
        _workoutState.update { state ->
            state.copy(
                pendingRoutineSync = null,
                pendingWorkoutSummary = state.pendingWorkoutSummary?.copy(pendingRoutineSync = null)
            )
        }
    }

    fun saveWorkoutAsRoutine(routineName: String) {
        val workoutId = _workoutState.value.workoutId ?: return
        viewModelScope.launch {
            val sets = workoutSetDao.getSetsForWorkout(workoutId).first()
                .filter { it.isCompleted }
            if (sets.isEmpty()) return@launch
            val newRoutineId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            routineDao.insertRoutine(Routine(id = newRoutineId, name = routineName, isCustom = true, updatedAt = now))
            val grouped = sets.groupBy { it.exerciseId }.entries.toList()
            val routineExercises = grouped.mapIndexed { index, (exerciseId, exSets) ->
                val sortedSets = exSets.sortedBy { it.setOrder }
                val modalReps = exSets.groupingBy { it.reps }.eachCount().maxByOrNull { it.value }?.key ?: 10
                val modalWeight = sortedSets.firstOrNull()?.weight?.let { formatWeight(it) } ?: ""
                val setTypesJson = sortedSets.joinToString(",") { it.setType.name }
                val setWeightsJson = sortedSets.joinToString(",") { formatWeight(it.weight) }
                val setRepsJson = sortedSets.joinToString(",") { it.reps.toString() }
                RoutineExercise(
                    id = UUID.randomUUID().toString(),
                    routineId = newRoutineId,
                    exerciseId = exerciseId,
                    sets = exSets.size,
                    reps = modalReps,
                    defaultWeight = modalWeight,
                    restTime = 90,
                    order = index,
                    supersetGroupId = exSets.first().supersetGroupId,
                    setTypesJson = setTypesJson,
                    setWeightsJson = setWeightsJson,
                    setRepsJson = setRepsJson
                )
            }
            routineExerciseDao.insertAll(routineExercises)
            firestoreSyncManager.pushRoutine(newRoutineId)
            _workoutState.update { it.copy(snackbarMessage = "Saved as \"$routineName\"") }
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
        // Fall back to defaults when the settings row hasn't been created yet.
        val settings = settingsState.value ?: com.powerme.app.data.database.UserSettings()
        if (remaining == 2 || remaining == 1) {
            if (settings.restTimerAudioEnabled) restTimerNotifier.playWarningBeep()
            if (settings.restTimerHapticsEnabled) restTimerNotifier.hapticShortPulse()
        }
    }

    // Called by the service on the main thread when the countdown reaches zero naturally.
    private fun onTimerFinish() {
        val settings = settingsState.value ?: com.powerme.app.data.database.UserSettings()
        restTimerNotifier.notifyEnd(
            audioEnabled = settings.restTimerAudioEnabled,
            hapticsEnabled = settings.restTimerHapticsEnabled
        )
        _workoutState.update { it.copy(restTimer = RestTimerState()) }
    }

    /**
     * Returns the rest duration in seconds for the transition between two set types.
     * Uses per-exercise warmupRestSeconds and dropSetRestSeconds (configurable via "Set Rest Timers").
     */
    private fun computeRestDuration(completed: SetType, next: SetType?, exercise: com.powerme.app.data.database.Exercise): Int = when (completed) {
        SetType.DROP    -> exercise.dropSetRestSeconds
        SetType.FAILURE -> exercise.restDurationSeconds
        SetType.WARMUP  -> if (next == SetType.WARMUP) exercise.warmupRestSeconds else exercise.restDurationSeconds
        SetType.NORMAL  -> if (next == SetType.DROP) exercise.dropSetRestSeconds else exercise.restDurationSeconds
    }

    fun startRestTimer(exerciseId: Long, setOrder: Int? = null, overrideSeconds: Int? = null) {
        // Guard: cancel any in-process coroutine to prevent double-beep race conditions.
        timerJob?.cancel()

        val exercise = _workoutState.value.exercises
            .find { it.exercise.id == exerciseId }
            ?.exercise ?: return

        val restDuration = overrideSeconds ?: exercise.restDurationSeconds

        if (serviceBound && timerService != null) {
            // Set identity fields before delegating to service.
            _workoutState.update {
                it.copy(
                    restTimer = RestTimerState(
                        isActive = true,
                        remainingSeconds = restDuration,
                        totalSeconds = restDuration,
                        exerciseId = exerciseId,
                        setOrder = setOrder
                    )
                )
            }
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
                        totalSeconds = restDuration,
                        exerciseId = exerciseId,
                        setOrder = setOrder
                    )
                )
            }
            timerJob = viewModelScope.launch {
                for (i in restDuration downTo 0) {
                    _workoutState.update { it.copy(restTimer = it.restTimer.copy(remainingSeconds = i)) }
                    val settings = settingsState.value ?: com.powerme.app.data.database.UserSettings()
                    if (i == 2 || i == 1) {
                        if (settings.restTimerAudioEnabled) restTimerNotifier.playWarningBeep()
                        if (settings.restTimerHapticsEnabled) restTimerNotifier.hapticShortPulse()
                    }
                    if (i == 0) {
                        restTimerNotifier.notifyEnd(
                            audioEnabled = settings.restTimerAudioEnabled,
                            hapticsEnabled = settings.restTimerHapticsEnabled
                        )
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
            state.copy(
                exercises = state.exercises.filter { it.exercise.id != exerciseId },
                snackbarMessage = "Exercise removed"
            )
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
                if (routineId.isNotBlank()) {
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
                exerciseDao.updateRestDuration(exerciseId, seconds)
                _workoutState.update { state ->
                    state.copy(
                        exercises = state.exercises.map { ex ->
                            if (ex.exercise.id == exerciseId)
                                ex.copy(exercise = ex.exercise.copy(restDurationSeconds = seconds))
                            else ex
                        }
                    )
                }
            } catch (e: Exception) {
                _workoutState.update { it.copy(snackbarMessage = "Failed to update rest timer") }
            }
        }
    }

    /** Update all three rest timer durations (work, warmup, drop) for an exercise and persist. */
    fun updateExerciseRestTimers(exerciseId: Long, workSeconds: Int, warmupSeconds: Int, dropSeconds: Int) {
        viewModelScope.launch {
            try {
                exerciseDao.updateRestTimers(exerciseId, workSeconds, warmupSeconds, dropSeconds)
                _workoutState.update { state ->
                    state.copy(
                        exercises = state.exercises.map { ex ->
                            if (ex.exercise.id == exerciseId)
                                ex.copy(exercise = ex.exercise.copy(
                                    restDurationSeconds = workSeconds,
                                    warmupRestSeconds = warmupSeconds,
                                    dropSetRestSeconds = dropSeconds
                                ))
                            else ex
                        }
                    )
                }
            } catch (e: Exception) {
                _workoutState.update { it.copy(snackbarMessage = "Failed to update rest timers") }
            }
        }
    }

    /** Store a per-set rest time override (falls back to exercise default when removed). */
    fun updateLocalRestTime(exerciseId: Long, setOrder: Int, newSeconds: Int) {
        _workoutState.update {
            it.copy(restTimeOverrides = it.restTimeOverrides + ("${exerciseId}_${setOrder}" to newSeconds))
        }
    }

    /** Remove a per-set rest time override, reverting to the exercise default. */
    fun deleteLocalRestTime(exerciseId: Long, setOrder: Int) {
        _workoutState.update {
            it.copy(restTimeOverrides = it.restTimeOverrides - "${exerciseId}_${setOrder}")
        }
    }

    /** Pause the active rest timer and store remaining seconds in state. */
    fun pauseRestTimer() {
        val timer = _workoutState.value.restTimer
        if (!timer.isActive || timer.isPaused) return
        val remaining = if (serviceBound && timerService != null) {
            timerService!!.pauseTimer()
        } else {
            timerJob?.cancel()
            timer.remainingSeconds
        }
        _workoutState.update { it.copy(restTimer = it.restTimer.copy(isPaused = true, remainingSeconds = remaining)) }
    }

    /** Resume a paused rest timer from where it left off. */
    fun resumeRestTimer() {
        val timer = _workoutState.value.restTimer
        if (!timer.isPaused) return
        val exerciseId = timer.exerciseId ?: return
        _workoutState.update { it.copy(restTimer = it.restTimer.copy(isPaused = false)) }
        startRestTimerWithDuration(exerciseId, timer.setOrder, timer.remainingSeconds)
    }

    /**
     * Start (or restart) the rest timer for [exerciseId] with an explicit [durationSeconds],
     * bypassing the exercise's default rest duration.
     */
    private fun startRestTimerWithDuration(exerciseId: Long, setOrder: Int?, durationSeconds: Int) {
        timerJob?.cancel()
        if (serviceBound && timerService != null) {
            _workoutState.update {
                it.copy(
                    restTimer = RestTimerState(
                        isActive = true,
                        remainingSeconds = durationSeconds,
                        totalSeconds = durationSeconds,
                        exerciseId = exerciseId,
                        setOrder = setOrder
                    )
                )
            }
            timerService!!.startTimer(
                totalSeconds = durationSeconds,
                onTick = ::onTimerTick,
                onFinish = ::onTimerFinish
            )
        } else {
            _workoutState.update {
                it.copy(
                    restTimer = RestTimerState(
                        isActive = true,
                        remainingSeconds = durationSeconds,
                        totalSeconds = durationSeconds,
                        exerciseId = exerciseId,
                        setOrder = setOrder
                    )
                )
            }
            timerJob = viewModelScope.launch {
                for (i in durationSeconds downTo 0) {
                    _workoutState.update { it.copy(restTimer = it.restTimer.copy(remainingSeconds = i)) }
                    val settings = settingsState.value ?: com.powerme.app.data.database.UserSettings()
                    if (i == 2 || i == 1) {
                        if (settings.restTimerAudioEnabled) restTimerNotifier.playWarningBeep()
                        if (settings.restTimerHapticsEnabled) restTimerNotifier.hapticShortPulse()
                    }
                    if (i == 0) {
                        restTimerNotifier.notifyEnd(
                            audioEnabled = settings.restTimerAudioEnabled,
                            hapticsEnabled = settings.restTimerHapticsEnabled
                        )
                    }
                    if (i > 0) delay(1000)
                }
                _workoutState.update { it.copy(restTimer = RestTimerState()) }
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

    /** Enter multi-select mode for creating a superset across N exercises.
     *  If the triggering exercise is already part of a superset group, pre-selects that group
     *  so the user can modify it. */
    fun enterSupersetSelectMode(fromExerciseId: Long? = null) {
        val state = _workoutState.value
        val preSelected: Set<Long> = if (fromExerciseId != null) {
            val groupId = state.exercises.find { it.exercise.id == fromExerciseId }?.supersetGroupId
            if (groupId != null) {
                state.exercises.filter { it.supersetGroupId == groupId }.map { it.exercise.id }.toSet()
            } else setOf(fromExerciseId)
        } else emptySet()
        _workoutState.update { it.copy(isSupersetSelectMode = true, supersetCandidateIds = preSelected) }
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

    fun collapseAll() {
        _workoutState.update { state ->
            val allIds = state.exercises.map { it.exercise.id }.toSet()
            state.copy(collapsedExerciseIds = allIds)
        }
    }

    fun collapseAllExcept(exerciseId: Long) {
        _workoutState.update { state ->
            val allIds = state.exercises.map { it.exercise.id }.toSet()
            state.copy(collapsedExerciseIds = allIds - exerciseId)
        }
    }

    fun toggleCollapsed(exerciseId: Long) {
        _workoutState.update { state ->
            val current = state.collapsedExerciseIds
            state.copy(
                collapsedExerciseIds = if (exerciseId in current) current - exerciseId else current + exerciseId
            )
        }
    }

    /** Clear the current snackbar message after it has been shown. */
    fun clearSnackbarMessage() {
        _workoutState.update { it.copy(snackbarMessage = null) }
    }

    /** Alias used by ActiveWorkoutScreen snackbar LaunchedEffect. */
    fun clearSnackbar() = clearSnackbarMessage()

    /** Clear the edit guard dialog (shown when user tries to edit a routine while workout is active). */
    fun clearEditGuard() {
        _workoutState.update { it.copy(showEditGuard = false) }
    }

    /** Show the "Workout in Progress" guard dialog without entering edit mode. */
    fun triggerEditGuard() {
        _workoutState.update { it.copy(showEditGuard = true) }
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

}

