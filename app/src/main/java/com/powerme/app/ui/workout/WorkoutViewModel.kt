package com.powerme.app.ui.workout

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.data.KeepScreenOnMode
import com.powerme.app.data.RpeMode
import com.powerme.app.data.WorkoutStyle
import com.powerme.app.notification.WorkoutNotificationManager
import com.powerme.app.data.UnitSystem
import com.powerme.app.data.sync.FirestoreSyncManager
import com.powerme.app.health.HealthConnectManager
import com.powerme.app.data.database.Exercise
import com.powerme.app.data.database.ExerciseType
import com.powerme.app.util.UnitConverter
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
import com.powerme.app.analytics.AnalyticsTracker
import com.powerme.app.analytics.BoazPerformanceAnalyzer
import com.powerme.app.data.database.StateHistoryEntry
import com.powerme.app.data.repository.ExerciseRepository
import com.powerme.app.data.repository.WorkoutBootstrap
import com.powerme.app.data.repository.MedicalLedgerRepository
import com.powerme.app.data.repository.StateHistoryRepository
import com.powerme.app.data.repository.WarmupRepository
import com.powerme.app.data.repository.WorkoutRepository
import com.powerme.app.util.ClocksTimerBridge
import com.powerme.app.util.WarmupCalculator
import com.powerme.app.util.RestTimerNotifier
import com.powerme.app.util.TimerSound
import com.powerme.app.util.SurgicalValidator
import com.powerme.app.util.WorkoutTimerService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
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
 * Formats a stored kg value for display in the active workout, in the user's unit system.
 * Strips trailing zeros: 80.0 → "80", 80.5 → "80.5", 32.25 → "32.25".
 * For imperial, converts kg → lbs first.
 */
private fun formatWeight(valueKg: Double, unit: UnitSystem): String =
    UnitConverter.formatWeightRaw(valueKg, unit)

/** Converts a stored RPE Int (×10 scale: 90 = 9.0, 65 = 6.5) to its display string ("9", "6.5"). */
private fun formatGhostRpe(rpe: Int): String {
    val d = rpe / 10.0
    return if (d == d.toLong().toDouble()) d.toLong().toString() else "%.1f".format(d)
}

/**
 * Converts a display-unit weight string back to kg for DB storage.
 * E.g. in IMPERIAL "176.4" → 80.0.
 */
private fun displayStringToKg(displayStr: String, unit: UnitSystem): Double {
    val v = displayStr.toDoubleOrNull() ?: 0.0
    return UnitConverter.inputWeightToKg(v, unit)
}

/**
 * Converts a kg Double to a storage string (clean, no trailing zeros).
 * Used when saving back to setWeightsJson / defaultWeight (always stored in kg).
 */
private fun kgToStorageString(kg: Double): String = when {
    kg == kg.toLong().toDouble() -> kg.toLong().toString()
    kg * 10 == (kg * 10).toLong().toDouble() -> "%.1f".format(kg)
    else -> "%.2f".format(kg)
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
    val ghostWeight: String? = null,      // Previous session weight hint
    val ghostReps: String? = null,        // Previous session reps hint
    val ghostRpe: String? = null,         // Previous session RPE hint
    val ghostTimeSeconds: String? = null, // Previous session time hint (timed exercises)
    val setNotes: String = "",        // Session-specific notes for this set
    val distance: String = "",        // For cardio exercises (km)
    val timeSeconds: String = ""      // For cardio/timed exercises (seconds)
)

data class ExerciseWithSets(
    val exercise: Exercise,
    val sets: List<ActiveSet>,
    val supersetGroupId: String? = null,
    val sessionNote: String? = null,    // volatile — not persisted, resets each session
    val stickyNote: String? = null,     // persisted via RoutineExercise.stickyNote
    val blockId: String? = null         // FK → workout_blocks.id (null for legacy/empty workouts)
)

data class RestTimerState(
    val isActive: Boolean = false,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 0,
    val exerciseId: Long? = null,
    val setOrder: Int? = null,
    val isPaused: Boolean = false,
    val setType: SetType? = null
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

/**
 * Snapshot of pre-edit state captured on entry to live-workout edit mode.
 * cancelEditMode() restores all fields from this snapshot. Null outside live edit.
 */
data class WorkoutEditSnapshot(
    val exercises: List<ExerciseWithSets>,
    val notes: String,
    val restTimeOverrides: Map<String, Int>,
    val hiddenRestSeparators: Set<String>,
    val collapsedExerciseIds: Set<Long>,
    val collapsedWarmupExerciseIds: Set<Long>,
    val deletedSetClipboard: Map<Long, DeletedSetClipboard>,
    val activeSupersetExerciseId: Long?,
    val workoutName: String
)

data class ActiveWorkoutState(
    val isActive: Boolean = false,
    val workoutId: String? = null,
    val routineId: String = "",
    val startTime: Long? = null,
    val exercises: List<ExerciseWithSets> = emptyList(),
    val availableExercises: List<Exercise> = emptyList(),
    val notes: String = "",
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
    val finishedRestSeparators: Set<String> = emptySet(),
    val isEditMode: Boolean = false,
    val editModeSaved: Boolean = false,  // one-shot navigation trigger (like pendingWorkoutSummary)
    val editModeDirty: Boolean = false,
    val showEditGuard: Boolean = false,
    val deletedSetClipboard: Map<Long, DeletedSetClipboard> = emptyMap(),
    val collapsedExerciseIds: Set<Long> = emptySet(),
    val collapsedWarmupExerciseIds: Set<Long> = emptySet(),
    val workoutEditSnapshot: WorkoutEditSnapshot? = null,
    val blocks: List<com.powerme.app.data.database.WorkoutBlock> = emptyList(),
    val activeBlockId: String? = null,
    val functionalBlockState: FunctionalBlockRunnerState? = null,
    val blockAutoFinished: Boolean = false,
    val blockSessionNotes: Map<String, String?> = emptyMap(),
) {
    val exercisesByBlockId: Map<String?, List<ExerciseWithSets>> by lazy { exercises.groupBy { it.blockId } }
}

private fun ActiveWorkoutState.markDirtyIfEditing(): ActiveWorkoutState =
    if (isEditMode && !editModeDirty) copy(editModeDirty = true) else this

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val warmupRepository: WarmupRepository,
    private val workoutDao: WorkoutDao,
    private val workoutSetDao: com.powerme.app.data.database.WorkoutSetDao,
    private val routineExerciseDao: com.powerme.app.data.database.RoutineExerciseDao,
    private val exerciseDao: com.powerme.app.data.database.ExerciseDao,
    private val routineDao: RoutineDao,
    private val routineBlockDao: com.powerme.app.data.database.RoutineBlockDao,
    private val workoutBlockDao: com.powerme.app.data.database.WorkoutBlockDao,
    private val userSettingsDao: UserSettingsDao,
    private val medicalLedgerRepository: MedicalLedgerRepository,
    private val boazPerformanceAnalyzer: BoazPerformanceAnalyzer,
    private val analyticsTracker: AnalyticsTracker,
    private val stateHistoryRepository: StateHistoryRepository,
    private val firestoreSyncManager: FirestoreSyncManager,
    private val clocksTimerBridge: ClocksTimerBridge,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val healthConnectManager: HealthConnectManager,
    private val workoutNotificationManager: WorkoutNotificationManager,
    private val functionalBlockRunner: FunctionalBlockRunner,
    private val wakeLockManager: com.powerme.app.util.WakeLockManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // internal so tests can substitute a mock (mock-maker-inline required).
    internal var restTimerNotifier: RestTimerNotifier = RestTimerNotifier(context)

    private val timerSoundState: StateFlow<TimerSound> = appSettingsDataStore.timerSound
        .stateIn(viewModelScope, SharingStarted.Eagerly, TimerSound.BEEP)

    private val notificationsEnabledState: StateFlow<Boolean> = appSettingsDataStore.notificationsEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val unitSystem: StateFlow<UnitSystem> = appSettingsDataStore.unitSystem
        .stateIn(viewModelScope, SharingStarted.Eagerly, UnitSystem.METRIC)

    /** Current unit system, accessed synchronously for format/convert helpers. */
    private val currentUnit: UnitSystem get() = unitSystem.value

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

    // Captured in finishWorkout(), cleared in cancelWorkout().
    // Used by the nav graph to navigate to WorkoutSummaryScreen after the post-workout sheet dismisses.
    private var _lastFinishedWorkoutId: String? = null
    val lastFinishedWorkoutId: String? get() = _lastFinishedWorkoutId

    private var _lastPendingRoutineSync: RoutineSyncType? = null
    val lastPendingRoutineSync: RoutineSyncType? get() = _lastPendingRoutineSync

    private val timerConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            timerService = (binder as WorkoutTimerService.TimerBinder).getService()
            serviceBound = true
            // Wire notification action button callbacks so the service can invoke ViewModel logic.
            timerService!!.onSkipRestRequested = { skipRestTimer() }
            timerService!!.onFinishWorkoutRequested = { finishWorkout() }
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

    /** One-shot signal: set to "${exerciseId}_${setOrder}" when a set is just completed and rpeMode fires. */
    private val _rpeAutoPopTarget = MutableStateFlow<String?>(null)
    val rpeAutoPopTarget: StateFlow<String?> = _rpeAutoPopTarget.asStateFlow()

    private val rpeMode: StateFlow<RpeMode> = appSettingsDataStore.rpeMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, RpeMode.OFF)

    val workoutStyle: StateFlow<WorkoutStyle> = appSettingsDataStore.workoutStyle
        .stateIn(viewModelScope, SharingStarted.Eagerly, WorkoutStyle.HYBRID)

    private val currentWorkoutStyle: StateFlow<WorkoutStyle> get() = workoutStyle

    val timedSetSetupSeconds: StateFlow<Int> = appSettingsDataStore.timedSetSetupSeconds
        .stateIn(viewModelScope, SharingStarted.Eagerly, 3)

    val keepScreenOnMode: StateFlow<KeepScreenOnMode> = appSettingsDataStore.keepScreenOnMode
        .stateIn(viewModelScope, SharingStarted.Eagerly, KeepScreenOnMode.DURING_WORKOUT)

    private val saveJobs = mutableMapOf<Pair<Long, Int>, Job>()

    /** True only while editing within a live workout (workoutId != null). False during standalone edit. */
    private fun isLiveEdit(): Boolean =
        _workoutState.value.isEditMode && _workoutState.value.workoutId != null

    init {
        loadAvailableExercises()
        rehydrateIfNeeded()
        context.bindService(
            Intent(context, WorkoutTimerService::class.java),
            timerConnection,
            Context.BIND_AUTO_CREATE
        )
        viewModelScope.launch {
            functionalBlockRunner.state.collect { rs ->
                _workoutState.update { it.copy(functionalBlockState = rs) }
            }
        }
    }

    fun consumeRpeAutoPop() {
        _rpeAutoPopTarget.value = null
    }

    // ── Functional block runner facade ───────────────────────────────────────

    fun startFunctionalBlock(blockId: String) {
        viewModelScope.launch {
            val block = workoutBlockDao.getById(blockId) ?: return@launch
            val plan = buildBlockPlan(block) ?: return@launch
            functionalBlockRunner.start(block, plan, onFinish = {
                _workoutState.update { it.copy(blockAutoFinished = true) }
            })
            _workoutState.update { it.copy(activeBlockId = blockId) }
        }
    }

    fun consumeBlockAutoFinished() {
        _workoutState.update { it.copy(blockAutoFinished = false) }
    }

    fun pauseFunctionalBlock() = functionalBlockRunner.pause()
    fun resumeFunctionalBlock() = functionalBlockRunner.resume()

    fun finishFunctionalBlock(
        rounds: Int? = null,
        extraReps: Int? = null,
        finishSeconds: Int? = null,
        rpe: Int? = null,
        perExerciseRpeJson: String? = null,
        notes: String? = null,
    ) {
        viewModelScope.launch {
            functionalBlockRunner.finish(rounds, extraReps, finishSeconds, rpe, perExerciseRpeJson, notes)
            _workoutState.update { it.copy(activeBlockId = null, blockAutoFinished = false) }
        }
    }

    fun abandonFunctionalBlock() {
        functionalBlockRunner.abandon()
        _workoutState.update { it.copy(activeBlockId = null, blockAutoFinished = false) }
    }

    fun appendBlockRoundTap(round: Int, elapsedMs: Long, phase: String? = null, completed: Boolean? = null) {
        viewModelScope.launch {
            functionalBlockRunner.appendRoundTap(round, elapsedMs, phase, completed)
        }
    }

    /** Log the skipped round and advance the EMOM timer to the next round immediately. */
    fun skipEmomRound() {
        val fb = _workoutState.value.functionalBlockState ?: return
        viewModelScope.launch {
            functionalBlockRunner.appendRoundTap(
                round = fb.currentRound,
                elapsedMs = fb.elapsedSeconds * 1000L,
                completed = false,
            )
        }
        functionalBlockRunner.skipCurrentInterval()
    }

    /**
     * Builds a [BlockPlan] for [block] by joining its [type]/plan fields with the routine's
     * recipe rows (reps + holdSeconds live on RoutineExercise).
     */
    private suspend fun buildBlockPlan(block: com.powerme.app.data.database.WorkoutBlock): BlockPlan? {
        val state = _workoutState.value
        val recipeRows = state.exercises
            .filter { it.blockId == block.id }
            .map { ex ->
                val isTimed = ex.exercise.exerciseType == ExerciseType.TIMED
                RecipeRow(
                    exerciseId = ex.exercise.id,
                    exerciseName = ex.exercise.name,
                    reps = if (!isTimed) ex.sets.firstOrNull()?.reps?.toIntOrNull() else null,
                    holdSeconds = if (isTimed) ex.sets.firstOrNull()?.timeSeconds?.toIntOrNull() else null,
                )
            }
        return BlockPlan(
            durationSeconds = block.durationSeconds,
            targetRounds = block.targetRounds,
            emomRoundSeconds = block.emomRoundSeconds,
            tabataWorkSeconds = block.tabataWorkSeconds,
            tabataRestSeconds = block.tabataRestSeconds,
            tabataSkipLastRest = block.tabataSkipLastRest == 1,
            recipe = recipeRows,
        )
    }

    override fun onCleared() {
        super.onCleared()
        timerService?.let {
            it.onSkipRestRequested = null
            it.onFinishWorkoutRequested = null
        }
        serviceCollectionJob?.cancel()
        timerJob?.cancel()
        standaloneTimerJob?.cancel()
        elapsedTimerJob?.cancel()
        if (serviceBound) {
            context.unbindService(timerConnection)
            serviceBound = false
        }
        // Wakelock — runner owns it while active; only release if VM is dying without an active block.
        if (!functionalBlockRunner.isActive.value) wakeLockManager.release()
    }

    private fun launchWorkoutForegroundService(workoutName: String, startTimeMs: Long) {
        val intent = Intent(context, WorkoutTimerService::class.java).apply {
            action = WorkoutNotificationManager.ACTION_START_FOREGROUND
            putExtra(WorkoutNotificationManager.EXTRA_WORKOUT_NAME, workoutName)
            putExtra(WorkoutTimerService.EXTRA_START_TIME, startTimeMs)
        }
        context.startForegroundService(intent)
    }

    private fun stopWorkoutForegroundService() {
        timerService?.stopForegroundAndRemove()
        context.stopService(Intent(context, WorkoutTimerService::class.java))
        workoutNotificationManager.cancelAll()
    }

    private fun updatePersistentNotificationElapsedMode() {
        val state = _workoutState.value
        if (!state.isActive) return
        val notification = workoutNotificationManager.buildPersistentNotification(
            workoutName = state.workoutName,
            startTimeMs = state.startTime ?: System.currentTimeMillis()
        )
        workoutNotificationManager.updateNotification(notification)
    }

    fun minimizeWorkout() {
        Timber.d("WVM MINIMIZE wId=${_workoutState.value.workoutId?.take(8)}")
        _workoutState.update { it.copy(isMinimized = true) }
    }

    fun maximizeWorkout() {
        val s = _workoutState.value
        Timber.d("WVM MAXIMIZE wId=${s.workoutId?.take(8)} isActive=${s.isActive}")
        if (s.isActive) analyticsTracker.logWorkoutResumed(s.workoutId)
        _workoutState.update { it.copy(isMinimized = false) }
    }

    fun logNavTabSelected(tabName: String) {
        analyticsTracker.logNavTabSelected(tabName)
        analyticsTracker.logScreenViewed(tabName)
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
                        restTimerNotifier.playWarningBeep(timerSoundState.value)
                    }
                } else {
                    delay(500)
                }
            }
            if (_workoutState.value.standaloneTimer.remainingSeconds == 0) {
                restTimerNotifier.notifyEnd(sound = timerSoundState.value)
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
                Timber.d("WVM REHYDRATE_STALE purging wId=${activeWorkout.id.take(8)} ageH=${ageMs / 3_600_000}")
                workoutSetDao.deleteSetsForWorkout(activeWorkout.id)
                workoutDao.deleteWorkoutById(activeWorkout.id)
                return@launch
            }

            val dbSets = workoutSetDao.getSetsForWorkout(activeWorkout.id).first()

            // Purge ghost workouts: no sets were ever completed → user opened and closed
            // without doing any real work. Do not restore.
            if (dbSets.none { it.isCompleted }) {
                Timber.d("WVM REHYDRATE_GHOST purging wId=${activeWorkout.id.take(8)}")
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
                            weight = if (ws.weight > 0.0) formatWeight(ws.weight, currentUnit) else "",
                            reps = if (ws.reps > 0) ws.reps.toString() else "",
                            setType = ws.setType,
                            isCompleted = ws.isCompleted
                        )
                    }
                    ExerciseWithSets(exercise = exercise, sets = activeSets)
                }
            }
            val name = activeWorkout.routineName
                ?: activeWorkout.routineId?.let { routineDao.getRoutineById(it)?.name }
                ?: "Workout"
            Timber.d("WVM REHYDRATE_OK wId=${activeWorkout.id.take(8)} exercises=${exercises.size}")
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
            Timber.d("WVM START_BLOCKED_ROUTINE isActive=${_workoutState.value.isActive} isMin=${_workoutState.value.isMinimized} — maximizing")
            maximizeWorkout()
            return
        }
        _lastFinishedWorkoutId = null
        _lastPendingRoutineSync = null
        _workoutState.update { ActiveWorkoutState(availableExercises = it.availableExercises) }
        viewModelScope.launch {
            val bootstrap = workoutRepository.instantiateWorkoutFromRoutine(routineId)
            val routineExercises = routineExerciseDao.getForRoutine(routineId)
            val dbSetsByExercise = bootstrap.workoutSets.groupBy { it.exerciseId }

            // Materialise WorkoutBlock rows from the routine template
            val routineBlocks = routineBlockDao.getBlocksForRoutineOnce(routineId)
            val now = System.currentTimeMillis()
            val routineToWorkoutBlockId = routineBlocks.associate { rb ->
                rb.id to UUID.randomUUID().toString()
            }
            val workoutBlocks = routineBlocks.map { rb ->
                com.powerme.app.data.database.WorkoutBlock(
                    id = routineToWorkoutBlockId.getValue(rb.id),
                    workoutId = bootstrap.workoutId,
                    order = rb.order,
                    type = rb.type,
                    name = rb.name,
                    durationSeconds = rb.durationSeconds,
                    targetRounds = rb.targetRounds,
                    emomRoundSeconds = rb.emomRoundSeconds,
                    tabataWorkSeconds = rb.tabataWorkSeconds,
                    tabataRestSeconds = rb.tabataRestSeconds,
                    tabataSkipLastRest = rb.tabataSkipLastRest,
                    setupSecondsOverride = rb.setupSecondsOverride,
                    warnAtSecondsOverride = rb.warnAtSecondsOverride,
                    updatedAt = now
                )
            }
            workoutBlockDao.upsertAll(workoutBlocks)

            val exercises = routineExercises.mapNotNull { re ->
                val exercise = exerciseRepository.getExerciseById(re.exerciseId) ?: return@mapNotNull null
                val ghostSets = bootstrap.ghostMap[re.exerciseId] ?: emptyList()
                val dbSets = dbSetsByExercise[re.exerciseId] ?: emptyList()
                val ghostByType = ghostSets.groupBy { it.setType }
                val typeCounters = mutableMapOf<SetType, Int>()
                val activeSets = dbSets.map { ws ->
                    val typeIndex = typeCounters.getOrDefault(ws.setType, 0)
                    val ghost = ghostByType[ws.setType]?.getOrNull(typeIndex)
                    typeCounters[ws.setType] = typeIndex + 1
                    val weightStr = if (ws.weight > 0.0) formatWeight(ws.weight, currentUnit) else "0"
                    ActiveSet(
                        id = ws.id,
                        setOrder = ws.setOrder,
                        weight = weightStr,
                        reps = ws.reps.toString(),
                        setType = ws.setType,
                        timeSeconds = ghost?.timeSeconds?.let { if (it > 0) it.toString() else "" }
                            ?: ws.timeSeconds?.let { if (it > 0) it.toString() else "" }
                            ?: "",
                        ghostWeight = ghost?.weight?.let { if (it > 0.0) formatWeight(it, currentUnit) else null },
                        ghostReps = ghost?.reps?.let { if (it > 0) it.toString() else null },
                        ghostRpe = ghost?.rpe?.let { formatGhostRpe(it) },
                        ghostTimeSeconds = ghost?.timeSeconds?.let { if (it > 0) it.toString() else null }
                    )
                }
                val sticky = try { routineExerciseDao.getStickyNote(routineId, re.exerciseId) } catch (_: Exception) { null }
                val workoutBlockId = re.blockId?.let { routineToWorkoutBlockId[it] }
                ExerciseWithSets(exercise = exercise, sets = activeSets, stickyNote = sticky, supersetGroupId = re.supersetGroupId, blockId = workoutBlockId)
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
            val startTime = System.currentTimeMillis()
            _workoutState.update {
                it.copy(
                    isActive = true,
                    workoutId = bootstrap.workoutId,
                    routineId = routineId,
                    startTime = startTime,
                    exercises = exercises,
                    workoutName = name,
                    routineSnapshot = snapshot,
                    blocks = workoutBlocks
                )
            }
            Timber.d("WVM STARTED_ROUTINE wId=${bootstrap.workoutId.take(8)} exercises=${exercises.size} blocks=${workoutBlocks.size}")
            analyticsTracker.logWorkoutStarted(routineId = routineId, exerciseCount = exercises.size)
            startElapsedTimer()
            launchWorkoutForegroundService(name, startTime)
        }
    }

    fun startWorkout(routineId: String = "") {
        if (_workoutState.value.isMinimized || _workoutState.value.isActive) {
            Timber.d("WVM START_BLOCKED isActive=${_workoutState.value.isActive} isMin=${_workoutState.value.isMinimized} — maximizing")
            maximizeWorkout()
            return
        }
        _lastFinishedWorkoutId = null
        _lastPendingRoutineSync = null
        _workoutState.update { ActiveWorkoutState(availableExercises = it.availableExercises) }
        viewModelScope.launch {
            val id = workoutRepository.createEmptyWorkout(routineId.takeIf { it.isNotBlank() })
            val startTime = System.currentTimeMillis()
            _workoutState.update {
                it.copy(
                    isActive = true,
                    workoutId = id,
                    routineId = routineId,
                    startTime = startTime,
                    exercises = emptyList(),
                    workoutName = "Empty Workout"
                )
            }
            Timber.d("WVM STARTED wId=${id.take(8)}")
            analyticsTracker.logWorkoutStarted(routineId = routineId, exerciseCount = 0)
            startElapsedTimer()
            launchWorkoutForegroundService("Empty Workout", startTime)
        }
    }

    /**
     * Starts a workout from a pre-created [WorkoutBootstrap] (produced by AI workout generation).
     * The bootstrap already has the workout and sets persisted in the DB; this method just loads
     * them into the active-workout state so the user can track the session.
     */
    fun startWorkoutFromPlan(bootstrap: WorkoutBootstrap) {
        if (_workoutState.value.isMinimized || _workoutState.value.isActive) {
            Timber.d("WVM START_BLOCKED_PLAN isActive=${_workoutState.value.isActive} isMin=${_workoutState.value.isMinimized} — maximizing")
            maximizeWorkout()
            return
        }
        _lastFinishedWorkoutId = null
        _lastPendingRoutineSync = null
        _workoutState.update { ActiveWorkoutState(availableExercises = it.availableExercises) }
        viewModelScope.launch {
            val dbSetsByExercise = bootstrap.workoutSets.groupBy { it.exerciseId }
            val exercises = dbSetsByExercise.entries.mapNotNull { (exerciseId, dbSets) ->
                val exercise = exerciseRepository.getExerciseById(exerciseId) ?: return@mapNotNull null
                val activeSets = dbSets.sortedBy { it.setOrder }.mapIndexed { i, ws ->
                    val weightStr = if (ws.weight > 0.0) formatWeight(ws.weight, currentUnit) else "0"
                    ActiveSet(
                        id = ws.id,
                        setOrder = ws.setOrder,
                        weight = weightStr,
                        reps = ws.reps.toString(),
                        setType = ws.setType
                    )
                }
                ExerciseWithSets(exercise = exercise, sets = activeSets)
            }
            val startTime = System.currentTimeMillis()
            _workoutState.update {
                it.copy(
                    isActive = true,
                    workoutId = bootstrap.workoutId,
                    routineId = "",
                    startTime = startTime,
                    exercises = exercises,
                    workoutName = "AI Workout"
                )
            }
            Timber.d("WVM STARTED_PLAN wId=${bootstrap.workoutId.take(8)} exercises=${exercises.size}")
            analyticsTracker.logWorkoutStarted(routineId = "ai", exerciseCount = exercises.size)
            startElapsedTimer()
            launchWorkoutForegroundService("AI Workout", startTime)
        }
    }

    fun startEditMode(routineId: String) {
        if (_workoutState.value.isActive && !_workoutState.value.isEditMode) {
            Timber.d("WVM EDIT_BLOCKED isActive=${_workoutState.value.isActive}")
            _workoutState.update { it.copy(showEditGuard = true) }
            return
        }
        // Synchronously clear any stale post-workout state before navigation occurs.
        // startWorkoutFromRoutine() does the same thing — without this, ActiveWorkoutScreen
        // mounts with pendingWorkoutSummary != null and immediately redirects to the old summary.
        _lastFinishedWorkoutId = null
        _lastPendingRoutineSync = null
        _workoutState.update { it.copy(pendingWorkoutSummary = null, pendingRoutineSync = null) }
        viewModelScope.launch {
            _workoutState.update { it.copy(editModeSaved = false) }
            val unit = currentUnit
            val (exercises, blocks, name) = withContext(Dispatchers.IO) {
                val routineBlocks = routineBlockDao.getBlocksForRoutineOnce(routineId)
                // Synthesise in-memory WorkoutBlock objects (same IDs as RoutineBlock — no DB write)
                val syntheticBlocks = routineBlocks.map { rb ->
                    com.powerme.app.data.database.WorkoutBlock(
                        id = rb.id,
                        workoutId = routineId,
                        order = rb.order,
                        type = rb.type,
                        name = rb.name,
                        durationSeconds = rb.durationSeconds,
                        targetRounds = rb.targetRounds,
                        emomRoundSeconds = rb.emomRoundSeconds,
                        tabataWorkSeconds = rb.tabataWorkSeconds,
                        tabataRestSeconds = rb.tabataRestSeconds,
                        tabataSkipLastRest = rb.tabataSkipLastRest,
                        setupSecondsOverride = rb.setupSecondsOverride,
                        warnAtSecondsOverride = rb.warnAtSecondsOverride
                    )
                }
                val routineExercises = routineExerciseDao.getForRoutine(routineId)
                val exList = routineExercises.mapNotNull { re ->
                    val exercise = exerciseRepository.getExerciseById(re.exerciseId) ?: return@mapNotNull null
                    val setTypes = re.setTypesJson.toEditModeSetTypes(re.sets)
                    val kgWeights = re.setWeightsJson.toEditModeValues(re.sets, re.defaultWeight)
                    val repsList = re.setRepsJson.toEditModeValues(re.sets, if (re.reps > 0) re.reps.toString() else "")
                    val activeSets = (1..re.sets).map { i ->
                        // Convert stored kg value → display unit string
                        val kgVal = kgWeights[i - 1].toDoubleOrNull() ?: 0.0
                        val displayWeight = if (kgVal > 0.0) UnitConverter.formatWeightRaw(kgVal, unit) else kgWeights[i - 1]
                        ActiveSet(
                            id = "edit_$i",
                            setOrder = i,
                            weight = displayWeight,
                            reps = repsList[i - 1],
                            setType = setTypes[i - 1]
                        )
                    }
                    val sticky = try { routineExerciseDao.getStickyNote(routineId, re.exerciseId) } catch (_: Exception) { null }
                    ExerciseWithSets(exercise = exercise, sets = activeSets, stickyNote = sticky, supersetGroupId = re.supersetGroupId, blockId = re.blockId)
                }
                val routineName = routineDao.getRoutineById(routineId)?.name ?: "Routine"
                Triple(exList, syntheticBlocks, routineName)
            }
            Timber.d("WVM EDIT_START routineId=${routineId.take(8)}")
            _workoutState.update {
                it.copy(
                    isActive = true,
                    isEditMode = true,
                    routineId = routineId,
                    workoutId = null,
                    startTime = null,
                    exercises = exercises,
                    blocks = blocks,
                    workoutName = name,
                    elapsedSeconds = 0,
                    routineSnapshot = emptyList(),
                    editModeSaved = false,
                    collapsedExerciseIds = emptySet(),
                    hiddenRestSeparators = emptySet(),
                    finishedRestSeparators = emptySet()
                )
            }
        }
    }

    /**
     * Phase B′ — entry point for live-workout edit mode. Called from the pencil icon
     * during an active workout. Captures a pre-edit snapshot for discard, preserves
     * workoutId / routineSnapshot / elapsedTimerJob so the Diff Engine still has its
     * baseline and the session clock keeps ticking. No DB writes.
     */
    fun enterLiveWorkoutEditMode() {
        val s = _workoutState.value
        if (!s.isActive || s.workoutId == null || s.isEditMode) return
        val snapshot = WorkoutEditSnapshot(
            exercises = s.exercises,
            notes = s.notes,
            restTimeOverrides = s.restTimeOverrides,
            hiddenRestSeparators = s.hiddenRestSeparators,
            collapsedExerciseIds = s.collapsedExerciseIds,
            collapsedWarmupExerciseIds = s.collapsedWarmupExerciseIds,
            deletedSetClipboard = s.deletedSetClipboard,
            activeSupersetExerciseId = s.activeSupersetExerciseId,
            workoutName = s.workoutName
        )
        _workoutState.update {
            it.copy(
                isEditMode = true,
                editModeDirty = false,
                editModeSaved = false,
                workoutEditSnapshot = snapshot
            )
        }
        // NOT touched: isActive, workoutId, routineId, routineSnapshot,
        // startTime, elapsedSeconds, restTimer. elapsedTimerJob keeps ticking.
    }

    fun saveRoutineEdits() {
        val state = _workoutState.value
        if (state.workoutId != null) {
            // Live-workout edit: promote any synthetic-ID sets to real workout_sets rows,
            // delete sets that were removed during edit, then exit edit mode in place.
            // No write to routine_exercises — the Diff Engine resolves changes post-workout.
            val snap = state.workoutEditSnapshot
            val wid = state.workoutId
            viewModelScope.launch {
                if (snap != null) {
                    val oldRealIds = snap.exercises.flatMap { ex ->
                        ex.sets.mapNotNull { s ->
                            s.id.takeIf { it.isNotBlank() && !it.startsWith("edit_") }
                        }
                    }.toSet()
                    val newRealIds = state.exercises.flatMap { ex ->
                        ex.sets.mapNotNull { s ->
                            s.id.takeIf { it.isNotBlank() && !it.startsWith("edit_") }
                        }
                    }.toSet()
                    (oldRealIds - newRealIds).forEach { workoutSetDao.deleteSetById(it) }
                }
                val unit = currentUnit
                val promoted = state.exercises.map { ex ->
                    ex.copy(sets = ex.sets.map { s ->
                        if (s.id.isBlank() || s.id.startsWith("edit_")) {
                            val newId = UUID.randomUUID().toString()
                            val ws = WorkoutSet(
                                id = newId,
                                workoutId = wid,
                                exerciseId = ex.exercise.id,
                                setOrder = s.setOrder,
                                weight = displayStringToKg(s.weight, unit),
                                reps = s.reps.toIntOrNull() ?: 0,
                                setType = s.setType
                            )
                            workoutSetDao.insertSet(ws)
                            s.copy(id = newId)
                        } else s
                    })
                }
                _workoutState.update {
                    it.copy(
                        isEditMode = false,
                        editModeDirty = false,
                        editModeSaved = false,
                        workoutEditSnapshot = null,
                        exercises = promoted,
                        snackbarMessage = "Edits staged — will sync after workout"
                    )
                }
            }
            return
        }
        viewModelScope.launch {
            val unit = currentUnit
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
                // Convert first-set display weight → kg string for defaultWeight
                val weight = firstSet?.weight?.let { kgToStorageString(displayStringToKg(it, unit)) } ?: ""
                val setTypesJson = sortedSets.joinToString(",") { it.setType.name }
                // setWeightsJson always stores kg values
                val setWeightsJson = sortedSets.joinToString(",") { kgToStorageString(displayStringToKg(it.weight, unit)) }
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
        val s = _workoutState.value
        val snap = s.workoutEditSnapshot
        if (snap != null) {
            // Live-workout edit: restore pre-edit state; session continues uninterrupted.
            _workoutState.update {
                it.copy(
                    isEditMode = false,
                    editModeDirty = false,
                    editModeSaved = false,
                    workoutEditSnapshot = null,
                    exercises = snap.exercises,
                    notes = snap.notes,
                    restTimeOverrides = snap.restTimeOverrides,
                    hiddenRestSeparators = snap.hiddenRestSeparators,
                    collapsedExerciseIds = snap.collapsedExerciseIds,
                    collapsedWarmupExerciseIds = snap.collapsedWarmupExerciseIds,
                    deletedSetClipboard = snap.deletedSetClipboard,
                    activeSupersetExerciseId = snap.activeSupersetExerciseId,
                    workoutName = snap.workoutName
                )
            }
            return
        }
        // Standalone edit: full reset.
        _workoutState.update {
            ActiveWorkoutState(availableExercises = it.availableExercises)
        }
    }

    fun editModeHasChanges(): Boolean =
        _workoutState.value.isEditMode && _workoutState.value.editModeDirty

    /**
     * Add a mid-workout functional block (created from [DraftBlock] + user-selected exercises).
     * Creates a [WorkoutBlock] DB row then delegates each exercise to [addExercise] with the block id.
     */
    fun addFunctionalBlock(draft: com.powerme.app.ui.workouts.DraftBlock, exercises: List<Exercise>) {
        val workoutId = _workoutState.value.workoutId ?: return
        viewModelScope.launch {
            val blockId = UUID.randomUUID().toString()
            val nextOrder = _workoutState.value.blocks.size
            val block = com.powerme.app.data.database.WorkoutBlock(
                id = blockId,
                workoutId = workoutId,
                order = nextOrder,
                type = draft.type.name,
                name = draft.name,
                durationSeconds = draft.durationSeconds,
                targetRounds = draft.targetRounds,
                emomRoundSeconds = draft.emomRoundSeconds,
                tabataWorkSeconds = draft.tabataWorkSeconds,
                tabataRestSeconds = draft.tabataRestSeconds,
                tabataSkipLastRest = if (draft.tabataSkipLastRest) 1 else null,
                setupSecondsOverride = draft.setupSecondsOverride,
                warnAtSecondsOverride = draft.warnAtSecondsOverride,
            )
            workoutBlockDao.upsertAll(listOf(block))
            _workoutState.update { it.copy(blocks = it.blocks + block) }
            exercises.forEach { exercise -> addExercise(exercise, blockId = blockId) }
        }
    }

    fun addExercise(exercise: Exercise, blockId: String? = null) {
        val currentExercises = _workoutState.value.exercises

        // Check if exercise already exists (skip for functional block re-adds)
        if (blockId == null && currentExercises.any { it.exercise.id == exercise.id }) {
            return
        }

        viewModelScope.launch {
            // Load ghost data from previous session
            val currentTimestamp = System.currentTimeMillis()
            val previousSets = workoutSetDao.getPreviousSessionSets(exercise.id, currentTimestamp)

            // Create initial sets pre-populated from previous session
            val unit = currentUnit
            val initialSets = if (previousSets.isNotEmpty()) {
                previousSets.mapIndexed { index, prevSet ->
                    val weightStr = formatWeight(prevSet.weight, unit)
                    val prevTimeStr = prevSet.timeSeconds?.let { if (it > 0) it.toString() else null }
                    ActiveSet(
                        setOrder = index + 1,
                        weight = weightStr,
                        reps = prevSet.reps.toString(),
                        timeSeconds = prevTimeStr ?: "",
                        ghostWeight = weightStr,
                        ghostReps = prevSet.reps.toString(),
                        ghostRpe = prevSet.rpe?.let { formatGhostRpe(it) },
                        ghostTimeSeconds = prevTimeStr
                    )
                }
            } else {
                if (blockId != null) {
                    // Functional block: prescription defaults (no prior session data)
                    if (exercise.exerciseType == ExerciseType.TIMED) {
                        listOf(ActiveSet(setOrder = 1, timeSeconds = "30"))
                    } else {
                        listOf(ActiveSet(setOrder = 1, reps = "10"))
                    }
                } else {
                    listOf(ActiveSet(setOrder = 1, weight = "0", reps = "0"))
                }
            }

            // Load sticky note from DB (only when a routine is active)
            val routineId = _workoutState.value.routineId
            val sticky = if (routineId.isNotBlank()) {
                try { routineExerciseDao.getStickyNote(routineId, exercise.id) } catch (_: Exception) { null }
            } else null

            // Persist initial sets to DB if a live (non-edit) workout is active (Iron Vault).
            // In edit mode, use synthetic IDs; sets are promoted to real rows on saveRoutineEdits().
            val workoutId = _workoutState.value.workoutId
            val currentIsEditMode = _workoutState.value.isEditMode
            val setsWithIds = if (workoutId != null && !currentIsEditMode) {
                initialSets.map { activeSet ->
                    val setId = UUID.randomUUID().toString()
                    val ws = WorkoutSet(
                        id = setId,
                        workoutId = workoutId,
                        exerciseId = exercise.id,
                        setOrder = activeSet.setOrder,
                        weight = displayStringToKg(activeSet.weight, unit),
                        reps = activeSet.reps.toIntOrNull() ?: 0,
                        setType = activeSet.setType
                    )
                    workoutRepository.createWorkoutSet(ws)
                    activeSet.copy(id = setId)
                }
            } else if (currentIsEditMode) {
                initialSets.mapIndexed { i, activeSet ->
                    activeSet.copy(id = "edit_${System.nanoTime()}_$i")
                }
            } else {
                initialSets
            }

            val newExerciseWithSets = ExerciseWithSets(
                exercise = exercise,
                sets = setsWithIds,
                stickyNote = sticky,
                blockId = blockId
            )

            _workoutState.update {
                it.copy(exercises = it.exercises + newExerciseWithSets).markDirtyIfEditing()
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
            // Insert workout_sets row only for live (non-edit) sessions; in edit mode the
            // set gets a synthetic ID and is promoted to a real row on saveRoutineEdits().
            if (workoutId != null && !state.isEditMode) {
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
                ).markDirtyIfEditing()
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
            state.copy(exercises = updatedExercises).markDirtyIfEditing()
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
            }).markDirtyIfEditing()
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
            }).markDirtyIfEditing()
        }
        if (!_workoutState.value.isEditMode && result is SurgicalValidator.ValidationResult.Valid) {
            debouncedSaveSet(exerciseId, setOrder)
        }
    }

    fun onTimeChanged(exerciseId: Long, setOrder: Int, raw: String) {
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
            val prevFirstGroupTime = groupSets.find { it.setOrder == groupMinOrder }?.timeSeconds ?: ""
            state.copy(exercises = state.exercises.map { e ->
                if (e.exercise.id != exerciseId) return@map e
                e.copy(sets = e.sets.map { set ->
                    when {
                        set.setOrder == setOrder -> {
                            val effectiveTime = when (result) {
                                is SurgicalValidator.ValidationResult.Valid -> raw.trim()
                                is SurgicalValidator.ValidationResult.Empty -> ""
                                else -> return@map set
                            }
                            set.copy(timeSeconds = effectiveTime)
                        }
                        // Cascade within group: first set in group changed → fill group sets that
                        // are blank OR still equal to whatever the first group set had before
                        isFirstInGroup && !set.isCompleted &&
                            (if (isWarmupGroup) set.setType == SetType.WARMUP else set.setType != SetType.WARMUP) &&
                            (set.timeSeconds.isBlank() || set.timeSeconds == prevFirstGroupTime) &&
                            result is SurgicalValidator.ValidationResult.Valid ->
                            set.copy(timeSeconds = raw.trim())
                        else -> set
                    }
                })
            }).markDirtyIfEditing()
        }
        if (!_workoutState.value.isEditMode && result is SurgicalValidator.ValidationResult.Valid) {
            debouncedSaveTimedSet(exerciseId, setOrder)
        }
    }

    private fun debouncedSaveTimedSet(exerciseId: Long, setOrder: Int) {
        saveJobs[exerciseId to setOrder]?.cancel()
        saveJobs[exerciseId to setOrder] = viewModelScope.launch {
            delay(300)
            val set = _workoutState.value.exercises
                .find { it.exercise.id == exerciseId }?.sets?.find { it.setOrder == setOrder }
                ?: return@launch
            if (_workoutState.value.isEditMode || set.id.isBlank() || set.id.startsWith("edit_")) return@launch
            val unit = currentUnit
            val weight = SurgicalValidator.parseDecimal(set.weight)
                .let { if (it is SurgicalValidator.ValidationResult.Valid) UnitConverter.inputWeightToKg(it.value, unit) else 0.0 }
            workoutSetDao.updateTimedSet(set.id, weight, set.timeSeconds.toIntOrNull() ?: 0, set.rpeValue, set.isCompleted)
        }
    }

    private fun debouncedSaveSet(exerciseId: Long, setOrder: Int) {
        saveJobs[exerciseId to setOrder]?.cancel()
        saveJobs[exerciseId to setOrder] = viewModelScope.launch {
            delay(300)
            val set = _workoutState.value.exercises
                .find { it.exercise.id == exerciseId }?.sets?.find { it.setOrder == setOrder }
                ?: return@launch
            if (_workoutState.value.isEditMode || set.id.isBlank() || set.id.startsWith("edit_")) return@launch
            val unit = currentUnit
            val weight = SurgicalValidator.parseDecimal(set.weight)
                .let { if (it is SurgicalValidator.ValidationResult.Valid) UnitConverter.inputWeightToKg(it.value, unit) else null }
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
        var completedSetType = SetType.NORMAL
        _workoutState.update { state ->
            state.copy(exercises = state.exercises.map { ex ->
                if (ex.exercise.id != exerciseId) return@map ex
                ex.copy(sets = ex.sets.map { set ->
                    if (set.setOrder != setOrder) return@map set
                    wasCompleted = set.isCompleted
                    completedSetType = set.setType
                    set.copy(isCompleted = !set.isCompleted)
                })
            }).markDirtyIfEditing()
        }
        if (!isEditMode) {
            val updatedSet = _workoutState.value.exercises
                .find { it.exercise.id == exerciseId }?.sets?.find { it.setOrder == setOrder }
            if (updatedSet != null && updatedSet.id.isNotBlank() && !updatedSet.id.startsWith("edit_")) {
                viewModelScope.launch { workoutSetDao.updateSetCompleted(updatedSet.id, updatedSet.isCompleted) }
            }
            if (!wasCompleted) {
                analyticsTracker.logWorkoutSetConfirmed(exerciseId, completedSetType.name, setOrder)
            }
            if (wasCompleted) {
                stopRestTimer()
            } else {
                val ex = _workoutState.value.exercises.find { it.exercise.id == exerciseId }
                val completedSet = ex?.sets?.find { it.setOrder == setOrder }
                val shouldAutoPop = when (rpeMode.value) {
                    RpeMode.OFF -> false
                    RpeMode.PURE_GYM -> currentWorkoutStyle.value == WorkoutStyle.PURE_GYM
                    RpeMode.PURE_FUNCTIONAL -> currentWorkoutStyle.value == WorkoutStyle.PURE_FUNCTIONAL
                    RpeMode.HYBRID -> true
                }
                if (shouldAutoPop && completedSet?.setType == SetType.NORMAL && completedSet.rpeValue == null) {
                    _rpeAutoPopTarget.value = "${exerciseId}_${setOrder}"
                }
                val nextSet = ex?.sets
                    ?.filter { !it.isCompleted && it.setOrder > setOrder }
                    ?.minByOrNull { it.setOrder }
                val isLastSet = nextSet == null
                if (isLastSet && ex?.exercise?.restAfterLastSet != true) {
                    // No rest timer after last set — collapse warmup rows immediately if this was the last warmup
                    if (completedSetType == SetType.WARMUP) {
                        _workoutState.update { state ->
                            val warmupSets = state.exercises.find { it.exercise.id == exerciseId }
                                ?.sets?.filter { it.setType == SetType.WARMUP } ?: emptyList()
                            if (warmupSets.isNotEmpty() && warmupSets.all { it.isCompleted }) {
                                state.copy(collapsedWarmupExerciseIds = state.collapsedWarmupExerciseIds + exerciseId)
                            } else state
                        }
                    }
                    return
                }
                val override = if (completedSet != null && ex != null) {
                    _workoutState.value.restTimeOverrides["${exerciseId}_${setOrder}"]
                        ?: computeRestDuration(completedSet.setType, nextSet?.setType, ex.exercise)
                } else null
                startRestTimer(exerciseId, setOrder, override, completedSetType)
            }
        }
        // Un-collapse warmup rows when a warmup set is un-completed
        if (!isEditMode && wasCompleted && completedSetType == SetType.WARMUP) {
            _workoutState.update { state ->
                state.copy(collapsedWarmupExerciseIds = state.collapsedWarmupExerciseIds - exerciseId)
            }
        }
    }

    fun stopRestTimer() {
        timerJob?.cancel()
        if (serviceBound && timerService != null) {
            timerService!!.stopTimer()
        }
        workoutNotificationManager.cancelRestDoneNotification()
        _workoutState.update { it.copy(restTimer = RestTimerState()) }
        updatePersistentNotificationElapsedMode()
    }

    fun toggleWarmupsCollapsed(exerciseId: Long) {
        _workoutState.update { state ->
            val updated = if (exerciseId in state.collapsedWarmupExerciseIds) {
                state.collapsedWarmupExerciseIds - exerciseId
            } else {
                state.collapsedWarmupExerciseIds + exerciseId
            }
            state.copy(collapsedWarmupExerciseIds = updated)
        }
    }

    fun updateRpe(exerciseId: Long, setOrder: Int, rpe: Int?) {
        _workoutState.update { state ->
            state.copy(exercises = state.exercises.map { ex ->
                if (ex.exercise.id != exerciseId) return@map ex
                ex.copy(sets = ex.sets.map { set ->
                    if (set.setOrder != setOrder) return@map set
                    set.copy(rpeValue = rpe)
                })
            }).markDirtyIfEditing()
        }
        val set = _workoutState.value.exercises
            .find { it.exercise.id == exerciseId }?.sets?.find { it.setOrder == setOrder }
        if (!_workoutState.value.isEditMode && set != null && set.id.isNotBlank() && !set.id.startsWith("edit_")) {
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
            state.copy(exercises = updatedExercises).markDirtyIfEditing()
        }
        val set = _workoutState.value.exercises
            .find { it.exercise.id == exerciseId }?.sets?.find { it.setOrder == setOrder }
        if (!_workoutState.value.isEditMode && set != null && set.id.isNotBlank() && !set.id.startsWith("edit_")) {
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
            state.copy(exercises = updatedExercises).markDirtyIfEditing()
        }
        val set = _workoutState.value.exercises
            .find { it.exercise.id == exerciseId }?.sets?.find { it.setOrder == setOrder }
        if (!_workoutState.value.isEditMode && set != null && set.id.isNotBlank() && !set.id.startsWith("edit_")) {
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
        val timer = _workoutState.value.restTimer
        // Hoist lookup so setType is available for the timer cancellation check below
        val setToDelete = _workoutState.value.exercises
            .find { it.exercise.id == exerciseId }?.sets?.find { it.setOrder == setOrder }
        // Cancel rest timer if:
        //   (a) timer belongs to the deleted set itself, OR
        //   (b) the deleted set is WARMUP and the timer is for the immediately preceding set
        //       (user skipping an unfinished warmup while its predecessor's rest is running)
        val isPrecedingTimerForWarmup = setToDelete?.setType == SetType.WARMUP &&
            timer.setOrder == setOrder - 1
        if (timer.isActive && timer.exerciseId == exerciseId &&
            (timer.setOrder == setOrder || isPrecedingTimerForWarmup)) {
            stopRestTimer()
        }
        if (!_workoutState.value.isEditMode && setToDelete != null && setToDelete.id.isNotBlank() && !setToDelete.id.startsWith("edit_")) {
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
            // Issue A: re-check warmup collapse state after a warmup set is deleted
            val updatedCollapsed = if (setToDelete?.setType == SetType.WARMUP) {
                val updatedEx = updatedExercises.find { it.exercise.id == exerciseId }
                val remainingWarmups = updatedEx?.sets?.filter { it.setType == SetType.WARMUP } ?: emptyList()
                when {
                    remainingWarmups.isEmpty() -> state.collapsedWarmupExerciseIds - exerciseId
                    remainingWarmups.all { it.isCompleted } -> state.collapsedWarmupExerciseIds + exerciseId
                    else -> state.collapsedWarmupExerciseIds - exerciseId
                }
            } else {
                state.collapsedWarmupExerciseIds
            }
            // Issue B: when a warmup set is deleted, also hide the preceding set's passive rest
            // separator — stopRestTimer() already cancelled the active timer above, but the passive
            // row would otherwise re-appear once the timer state clears.
            val precedingKey = if (setToDelete?.setType == SetType.WARMUP && setOrder > 1) {
                "${exerciseId}_${setOrder - 1}"
            } else null
            val updatedHidden = (if (precedingKey != null) state.hiddenRestSeparators + precedingKey
                                 else state.hiddenRestSeparators) - key
            state.copy(
                exercises = updatedExercises,
                restTimeOverrides = state.restTimeOverrides - key,
                hiddenRestSeparators = updatedHidden,
                collapsedWarmupExerciseIds = updatedCollapsed,
                deletedSetClipboard = if (clipboardEntry != null) {
                    state.deletedSetClipboard + (exerciseId to clipboardEntry)
                } else state.deletedSetClipboard
            ).markDirtyIfEditing()
        }
    }

    /**
     * Hide a rest separator.
     *
     * Active timer: stops the timer (session-only skip — rest duration stays unchanged).
     * Passive separator: also persists restDurationSeconds = 0 to the exercise so the separator
     * does not reappear on the next workout or after an app restart.
     */
    fun deleteRestSeparator(exerciseId: Long, setOrder: Int) {
        val timer = _workoutState.value.restTimer
        val isThisTimerActive = timer.isActive && timer.exerciseId == exerciseId && timer.setOrder == setOrder
        if (isThisTimerActive) {
            stopRestTimer()
        } else if (!_workoutState.value.isEditMode) {
            // Passive separator swiped in a live workout — persist to DB so it doesn't come back
            // next session. We intentionally do NOT mirror restDurationSeconds = 0 into the
            // in-memory exercise, because that would also hide sibling-set separators in the
            // current session (Bug B). The per-set hide is handled by hiddenRestSeparators below.
            viewModelScope.launch {
                try {
                    exerciseDao.updateRestDuration(exerciseId, 0)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        // In edit mode, skip the DAO write entirely so pressing 'X' can fully discard the change
        // (Bug A). The hiddenRestSeparators entry below provides session-scoped per-set hiding.
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
            }).markDirtyIfEditing()
        }
        val updated = _workoutState.value.exercises
            .find { it.exercise.id == exerciseId }?.sets?.find { it.setOrder == setOrder }
        if (!_workoutState.value.isEditMode && updated != null && updated.id.isNotBlank() && !updated.id.startsWith("edit_")) {
            viewModelScope.launch { workoutSetDao.updateSetType(updated.id, setType) }
        }
    }

    fun updateNotes(notes: String) {
        _workoutState.update { it.copy(notes = notes) }
    }

    fun finishWorkout() {
        timerService?.let {
            it.onSkipRestRequested = null
            it.onFinishWorkoutRequested = null
        }
        if (functionalBlockRunner.isActive.value) functionalBlockRunner.abandon()
        stopRestTimer()
        elapsedTimerJob?.cancel()
        viewModelScope.launch {
            try {
                val state = _workoutState.value
                Timber.d("WVM FINISH_BEGIN wId=${state.workoutId?.take(8)} isActive=${state.isActive}")
                if (!state.isActive || state.startTime == null) return@launch

                val endTime = System.currentTimeMillis()
                val durationSeconds = ((endTime - state.startTime) / 1000).toInt()

                // Calculate total volume — always in kg regardless of display unit
                val unit = currentUnit
                var totalVolume = 0.0
                state.exercises.forEach { exerciseWithSets ->
                    exerciseWithSets.sets.forEach { set ->
                        if (set.isCompleted) {
                            val displayWeight = when (val r = SurgicalValidator.parseDecimal(set.weight)) {
                                is SurgicalValidator.ValidationResult.Valid -> r.value
                                else -> SurgicalValidator.parseDecimal(set.ghostWeight ?: "")
                                    .let { if (it is SurgicalValidator.ValidationResult.Valid) it.value else 0.0 }
                            }
                            val weight = UnitConverter.inputWeightToKg(displayWeight, unit)
                            val reps = set.reps.toIntOrNull() ?: 0
                            totalVolume += weight * reps
                        }
                    }
                }

                // Update the existing workout record (created at workout start via Iron Vault)
                val workoutId = state.workoutId ?: return@launch
                val finishedWorkout = Workout(
                    id = workoutId,
                    routineId = state.routineId.takeIf { it.isNotBlank() },
                    routineName = if (state.routineId.isNotBlank()) state.workoutName else null,
                    timestamp = state.startTime!!,
                    durationSeconds = durationSeconds,
                    totalVolume = totalVolume,
                    notes = state.notes.ifBlank { null },
                    isCompleted = true,
                    startTimeMs = state.startTime!!,
                    endTimeMs = endTime,
                    updatedAt = endTime
                )
                workoutDao.updateWorkout(finishedWorkout)
                Timber.d("WVM FINISH_DB_DONE wId=${workoutId.take(8)}")
                // Clean up skeleton rows the user never filled in
                workoutSetDao.deleteIncompleteSetsByWorkout(workoutId)
                // Push to Firestore (fire-and-forget; SDK queues when offline)
                firestoreSyncManager.pushWorkout(workoutId)
                healthConnectManager.writeWorkoutSession(finishedWorkout, state.exercises, state.blocks)

                // Update lastPerformed on the routine (Risk 4 fix)
                if (state.routineId.isNotBlank()) {
                    routineDao.updateLastPerformed(state.routineId, endTime)
                }

                // Boaz Performance Loop — compare planned vs actual
                val report = try {
                    val results = boazPerformanceAnalyzer.compare(workoutId, routineId = state.routineId)
                    boazPerformanceAnalyzer.formatPerformanceReport(results)
                } catch (e: Exception) {
                    Timber.w(e, "Boaz analysis failed")
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
                analyticsTracker.logWorkoutFinished(
                    durationMinutes = durationSeconds / 60,
                    totalSets = completedSetCount,
                    exerciseCount = state.exercises.size
                )
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
                val hasCompletedWorkSets = state.exercises.any { ex ->
                    ex.sets.any { it.isCompleted && it.setType != SetType.WARMUP }
                }
                if (snapshot.isNotEmpty() && state.routineId.isNotBlank()) {
                    val currentExerciseIds = state.exercises.map { it.exercise.id }
                    val snapshotExerciseIds = snapshot.map { it.exerciseId }

                    // Structural: exercise list or order changed, or set count changed
                    val exerciseListChanged = currentExerciseIds != snapshotExerciseIds
                    val setCountChanged = hasCompletedWorkSets && state.exercises.any { ex ->
                        val snap = snapshot.find { it.exerciseId == ex.exercise.id }
                            ?: return@any true  // exercise added mid-workout
                        ex.sets.count { it.isCompleted } != snap.sets
                    }
                    val structuralChange = exerciseListChanged || setCountChanged

                    // Value change: numeric weight/reps differ for completed sets that existed in snapshot
                    val valueChange = hasCompletedWorkSets && state.exercises.any { ex ->
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
                        Timber.d("WVM FINISH_SYNC wId=${workoutId.take(8)} syncType=$syncType")
                        _lastFinishedWorkoutId = workoutId
                        _lastPendingRoutineSync = syncType
                        stopWorkoutForegroundService()
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

                Timber.d("WVM FINISH_OK wId=${workoutId.take(8)}")
                _lastFinishedWorkoutId = workoutId
                _lastPendingRoutineSync = null
                val durationText = if (durationSeconds >= 3600) {
                    "${durationSeconds / 3600}h ${(durationSeconds % 3600) / 60}m"
                } else "${durationSeconds / 60}m"
                stopWorkoutForegroundService()
                workoutNotificationManager.postSummaryNotification(
                    workoutName = summary.workoutName,
                    durationText = durationText,
                    sets = completedSetCount,
                    notificationsEnabled = notificationsEnabledState.value
                )
                _workoutState.update { it.copy(isActive = false, pendingWorkoutSummary = summary) }
            } catch (e: Exception) {
                Timber.e(e, "finishWorkout failed")
                stopWorkoutForegroundService()
                _workoutState.update {
                    ActiveWorkoutState(availableExercises = it.availableExercises)
                }
            }
        }
    }

    fun dismissWorkoutSummary() {
        Timber.d("WVM DISMISS_SUMMARY lastId=${_lastFinishedWorkoutId?.take(8)}")
        _lastFinishedWorkoutId = null
        _lastPendingRoutineSync = null
        _workoutState.update { state ->
            // Guard: if a new workout started before this dispose fires, don't destroy it.
            if (state.isActive) {
                state.copy(pendingWorkoutSummary = null, pendingRoutineSync = null)
            } else {
                ActiveWorkoutState(availableExercises = state.availableExercises)
            }
        }
    }

    fun confirmUpdateRoutineStructure() {
        viewModelScope.launch {
            val state = _workoutState.value
            val unit = currentUnit
            state.exercises.forEach { ex ->
                val completedSets = ex.sets.filter { it.isCompleted }.sortedBy { it.setOrder }
                if (completedSets.isNotEmpty()) {
                    routineExerciseDao.updateSets(state.routineId, ex.exercise.id, completedSets.size)
                    val typesJson = completedSets.joinToString(",") { it.setType.name }
                    val weightsJson = completedSets.joinToString(",") { kgToStorageString(displayStringToKg(it.weight, unit)) }
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
            val unit = currentUnit
            state.exercises.forEach { ex ->
                val completedSets = ex.sets.filter { it.isCompleted }.sortedBy { it.setOrder }
                val firstCompleted = completedSets.firstOrNull() ?: return@forEach
                val reps = firstCompleted.reps.toIntOrNull() ?: return@forEach
                val firstWeightKgStr = kgToStorageString(displayStringToKg(firstCompleted.weight, unit))
                routineExerciseDao.updateRepsAndWeight(
                    state.routineId, ex.exercise.id, reps, firstWeightKgStr
                )
                val typesJson = completedSets.joinToString(",") { it.setType.name }
                val weightsJson = completedSets.joinToString(",") { kgToStorageString(displayStringToKg(it.weight, unit)) }
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
            val unit = currentUnit
            state.exercises.forEach { ex ->
                val completedSets = ex.sets.filter { it.isCompleted }.sortedBy { it.setOrder }
                if (completedSets.isNotEmpty()) {
                    routineExerciseDao.updateSets(state.routineId, ex.exercise.id, completedSets.size)
                }
                val firstCompleted = completedSets.firstOrNull() ?: return@forEach
                val reps = firstCompleted.reps.toIntOrNull() ?: return@forEach
                val firstWeightKgStr = kgToStorageString(displayStringToKg(firstCompleted.weight, unit))
                routineExerciseDao.updateRepsAndWeight(
                    state.routineId, ex.exercise.id, reps, firstWeightKgStr
                )
                val typesJson = completedSets.joinToString(",") { it.setType.name }
                val weightsJson = completedSets.joinToString(",") { kgToStorageString(displayStringToKg(it.weight, unit)) }
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
        Timber.d("WVM RESOLVE_SYNC")
        _workoutState.update { state ->
            state.copy(
                pendingRoutineSync = null,
                pendingWorkoutSummary = state.pendingWorkoutSummary?.copy(pendingRoutineSync = null)
            )
        }
    }

    fun saveWorkoutAsRoutine(workoutId: String, routineName: String) {
        viewModelScope.launch {
            val dbSets = workoutSetDao.getSetsForWorkout(workoutId).first()
                .filter { it.isCompleted }

            val newRoutineId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            routineDao.insertRoutine(Routine(id = newRoutineId, name = routineName, isCustom = true, updatedAt = now))

            val routineExercises = if (dbSets.isNotEmpty()) {
                val grouped = dbSets.groupBy { it.exerciseId }.entries.toList()
                grouped.mapIndexed { index, (exerciseId, exSets) ->
                    val sortedSets = exSets.sortedBy { it.setOrder }
                    val modalReps = exSets.groupingBy { it.reps }.eachCount().maxByOrNull { it.value }?.key ?: 10
                    val modalWeight = sortedSets.firstOrNull()?.weight?.let { kgToStorageString(it) } ?: ""
                    val setTypesJson = sortedSets.joinToString(",") { it.setType.name }
                    val setWeightsJson = sortedSets.joinToString(",") { kgToStorageString(it.weight) }
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
            } else {
                // No completed sets in DB — build from in-memory exercise state (user finished
                // without tapping set completion, or quick workout with no logged sets).
                val exercises = _workoutState.value.exercises
                if (exercises.isEmpty()) return@launch
                val unit = currentUnit
                exercises.mapIndexed { index, exWithSets ->
                    val activeSets = exWithSets.sets
                    val setCount = activeSets.size.coerceAtLeast(1)
                    val modalReps = activeSets.groupingBy { it.reps }.eachCount()
                        .maxByOrNull { it.value }?.key?.toIntOrNull() ?: 10
                    val firstKg = activeSets.firstOrNull()?.let { displayStringToKg(it.weight, unit) } ?: 0.0
                    val setTypesJson = activeSets.joinToString(",") { it.setType.name }
                    val setWeightsJson = activeSets.joinToString(",") { kgToStorageString(displayStringToKg(it.weight, unit)) }
                    val setRepsJson = activeSets.joinToString(",") { it.reps }
                    RoutineExercise(
                        id = UUID.randomUUID().toString(),
                        routineId = newRoutineId,
                        exerciseId = exWithSets.exercise.id,
                        sets = setCount,
                        reps = modalReps,
                        defaultWeight = kgToStorageString(firstKg),
                        restTime = 90,
                        order = index,
                        supersetGroupId = exWithSets.supersetGroupId,
                        setTypesJson = setTypesJson,
                        setWeightsJson = setWeightsJson,
                        setRepsJson = setRepsJson
                    )
                }
            }

            if (routineExercises.isEmpty()) return@launch
            routineExerciseDao.insertAll(routineExercises)
            firestoreSyncManager.pushRoutine(newRoutineId)
            _workoutState.update { it.copy(snackbarMessage = "Saved as \"$routineName\"") }
        }
    }

    fun cancelWorkout() {
        // If a functional block is active, abandon it to stop its timer and release the wakelock
        // before tearing down the rest of the workout.
        if (functionalBlockRunner.isActive.value) {
            functionalBlockRunner.abandon()
        }
        val setsLogged = _workoutState.value.exercises.sumOf { ex -> ex.sets.count { it.isCompleted } }
        Timber.d("WVM CANCEL wId=${_workoutState.value.workoutId?.take(8)} setsLogged=$setsLogged")
        analyticsTracker.logWorkoutCancelled(setsLogged = setsLogged)
        timerService?.let {
            it.onSkipRestRequested = null
            it.onFinishWorkoutRequested = null
        }
        stopRestTimer()
        elapsedTimerJob?.cancel()
        stopWorkoutForegroundService()
        _lastFinishedWorkoutId = null
        _lastPendingRoutineSync = null
        viewModelScope.launch {
            _workoutState.value.workoutId?.let { wid ->
                workoutSetDao.deleteSetsForWorkout(wid)
                workoutDao.deleteWorkoutById(wid)
            }
            // Full reset: also discards any staged live-workout edit snapshot (workoutEditSnapshot → null via default).
            _workoutState.update {
                ActiveWorkoutState(availableExercises = it.availableExercises)
            }
        }
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
    fun timerFinishedFeedback() {
        val settings = settingsState.value ?: com.powerme.app.data.database.UserSettings()
        restTimerNotifier.notifyEnd(
            audioEnabled = settings.restTimerAudioEnabled,
            hapticsEnabled = settings.restTimerHapticsEnabled,
            sound = timerSoundState.value
        )
    }

    fun timerWarningTickFeedback() {
        val settings = settingsState.value ?: com.powerme.app.data.database.UserSettings()
        if (settings.restTimerAudioEnabled) restTimerNotifier.playWarningBeep(timerSoundState.value)
        if (settings.restTimerHapticsEnabled) restTimerNotifier.hapticShortPulse()
    }

    fun timerHalftimeTickFeedback() {
        val settings = settingsState.value ?: com.powerme.app.data.database.UserSettings()
        if (settings.restTimerAudioEnabled) {
            restTimerNotifier.playWarningBeep(timerSoundState.value)
            viewModelScope.launch {
                delay(150L)
                restTimerNotifier.playWarningBeep(timerSoundState.value)
            }
        }
        if (settings.restTimerHapticsEnabled) restTimerNotifier.hapticShortPulse()
    }

    fun setupCountdownTickFeedback() {
        val settings = settingsState.value ?: com.powerme.app.data.database.UserSettings()
        if (settings.restTimerAudioEnabled) restTimerNotifier.playWarningBeep(timerSoundState.value)
        if (settings.restTimerHapticsEnabled) restTimerNotifier.hapticShortPulse()
    }

    private fun onTimerTick(remaining: Int) {
        // Fall back to defaults when the settings row hasn't been created yet.
        val settings = settingsState.value ?: com.powerme.app.data.database.UserSettings()
        if (remaining in 1..3) {
            if (settings.restTimerAudioEnabled) restTimerNotifier.playWarningBeep(timerSoundState.value)
            if (settings.restTimerHapticsEnabled) restTimerNotifier.hapticShortPulse()
        } else if (remaining == 0) {
            // Fire the end beep here (in the tick callback) rather than in onTimerFinish so that
            // it is immune to the service coroutine being cancelled between the loop end and the
            // onFinish dispatch — the tick at 0 always fires before any cleanup.
            restTimerNotifier.notifyEnd(
                audioEnabled = settings.restTimerAudioEnabled,
                hapticsEnabled = settings.restTimerHapticsEnabled,
                sound = timerSoundState.value
            )
        }
        // Keep the persistent notification countdown in sync on every tick.
        val restTimer = _workoutState.value.restTimer
        if (restTimer.isActive && remaining > 0) {
            val exercise = _workoutState.value.exercises.find { it.exercise.id == restTimer.exerciseId }
            if (exercise != null) {
                workoutNotificationManager.updateNotification(
                    workoutNotificationManager.buildRestTimerNotification(
                        workoutName = _workoutState.value.workoutName,
                        exerciseName = exercise.exercise.name,
                        setLabel = "Set ${(restTimer.setOrder ?: 0) + 1}",
                        remainingSeconds = remaining,
                        totalSeconds = restTimer.totalSeconds
                    )
                )
            }
        }
    }

    // Called by the service on the main thread when the countdown reaches zero naturally.
    // The end-beep is already fired in onTimerTick(0); this callback handles state cleanup only.
    private fun onTimerFinish() {
        // Auto-hide the rest separator that just finished so it doesn't linger as a passive row.
        val finishedExerciseId = _workoutState.value.restTimer.exerciseId
        val finishedSetOrder = _workoutState.value.restTimer.setOrder
        val finishedSetType = _workoutState.value.restTimer.setType
        _workoutState.update { state ->
            var updated = state.copy(restTimer = RestTimerState())
            if (finishedExerciseId != null && finishedSetOrder != null) {
                updated = updated.copy(finishedRestSeparators = updated.finishedRestSeparators + "${finishedExerciseId}_${finishedSetOrder}")
            }
            updated
        }
        // Staggered: collapse warmup rows 500ms after the rest separator is hidden.
        if (finishedSetType == SetType.WARMUP && finishedExerciseId != null) {
            viewModelScope.launch {
                delay(500)
                _workoutState.update { state ->
                    val warmupSets = state.exercises.find { it.exercise.id == finishedExerciseId }
                        ?.sets?.filter { it.setType == SetType.WARMUP } ?: emptyList()
                    if (warmupSets.isNotEmpty() && warmupSets.all { it.isCompleted })
                        state.copy(collapsedWarmupExerciseIds = state.collapsedWarmupExerciseIds + finishedExerciseId)
                    else state
                }
            }
        }
        // Post heads-up / watch notification: "Rest Complete — <exercise> Set N"
        val exerciseName = _workoutState.value.exercises
            .find { it.exercise.id == finishedExerciseId }?.exercise?.name
        if (exerciseName != null) {
            val setLabel = "Set ${(finishedSetOrder ?: 0) + 1}"
            workoutNotificationManager.postRestDoneNotification(
                exerciseName = exerciseName,
                setInfo = setLabel,
                notificationsEnabled = notificationsEnabledState.value
            )
        }
        updatePersistentNotificationElapsedMode()
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

    fun startRestTimer(exerciseId: Long, setOrder: Int? = null, overrideSeconds: Int? = null, setType: SetType? = null) {
        // Invariant #4: rest timers are a no-op while a functional block runner is active.
        if (functionalBlockRunner.isActive.value) return
        // If a timer is already running, dismiss it (cancel job + mark separator finished) before
        // starting the new one. Warmup collapse is intentionally not triggered here — the new timer
        // may itself be a warmup timer and will handle collapse when it finishes or is skipped.
        if (_workoutState.value.restTimer.isActive) {
            val prevId = _workoutState.value.restTimer.exerciseId
            val prevOrder = _workoutState.value.restTimer.setOrder
            timerJob?.cancel()
            if (serviceBound && timerService != null) timerService!!.stopTimer()
            _workoutState.update { state ->
                var updated = state.copy(restTimer = RestTimerState())
                if (prevId != null && prevOrder != null) {
                    updated = updated.copy(finishedRestSeparators = updated.finishedRestSeparators + "${prevId}_${prevOrder}")
                }
                updated
            }
        }
        // Cancel any in-process timer (coroutine or service) to prevent double-beep race conditions.
        timerJob?.cancel()
        if (serviceBound && timerService != null) timerService!!.stopTimer()

        val exercise = _workoutState.value.exercises
            .find { it.exercise.id == exerciseId }
            ?.exercise ?: return

        val restDuration = overrideSeconds ?: exercise.restDurationSeconds
        if (restDuration <= 0) return

        analyticsTracker.logRestTimerStarted(restDuration)

        if (serviceBound && timerService != null) {
            // Set identity fields before delegating to service.
            _workoutState.update {
                it.copy(
                    restTimer = RestTimerState(
                        isActive = true,
                        remainingSeconds = restDuration,
                        totalSeconds = restDuration,
                        exerciseId = exerciseId,
                        setOrder = setOrder,
                        setType = setType
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
            // Update the persistent notification to show rest countdown.
            val exerciseName = exercise.name
            val setLabel = "Set ${(setOrder ?: 0) + 1}"
            workoutNotificationManager.updateNotification(
                workoutNotificationManager.buildRestTimerNotification(
                    workoutName = _workoutState.value.workoutName,
                    exerciseName = exerciseName,
                    setLabel = setLabel,
                    remainingSeconds = restDuration,
                    totalSeconds = restDuration
                )
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
                        setOrder = setOrder,
                        setType = setType
                    )
                )
            }
            timerJob = viewModelScope.launch {
                for (i in restDuration downTo 0) {
                    _workoutState.update { it.copy(restTimer = it.restTimer.copy(remainingSeconds = i)) }
                    val settings = settingsState.value ?: com.powerme.app.data.database.UserSettings()
                    if (i in 1..3) {
                        if (settings.restTimerAudioEnabled) restTimerNotifier.playWarningBeep(timerSoundState.value)
                        if (settings.restTimerHapticsEnabled) restTimerNotifier.hapticShortPulse()
                    }
                    if (i == 0) {
                        restTimerNotifier.notifyEnd(
                            audioEnabled = settings.restTimerAudioEnabled,
                            hapticsEnabled = settings.restTimerHapticsEnabled,
                            sound = timerSoundState.value
                        )
                    }
                    if (i > 0) delay(1000)
                }
                // Auto-hide the rest separator that just finished.
                _workoutState.update { state ->
                    var updated = state.copy(restTimer = RestTimerState())
                    if (setOrder != null) {
                        updated = updated.copy(finishedRestSeparators = updated.finishedRestSeparators + "${exerciseId}_${setOrder}")
                    }
                    updated
                }
                // Staggered: collapse warmup rows 500ms after the rest separator is hidden.
                if (setType == SetType.WARMUP) {
                    delay(500)
                    _workoutState.update { state ->
                        val warmupSets = state.exercises.find { it.exercise.id == exerciseId }
                            ?.sets?.filter { it.setType == SetType.WARMUP } ?: emptyList()
                        if (warmupSets.isNotEmpty() && warmupSets.all { it.isCompleted })
                            state.copy(collapsedWarmupExerciseIds = state.collapsedWarmupExerciseIds + exerciseId)
                        else state
                    }
                }
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
        if (!_workoutState.value.restTimer.isActive) return
        val remainingSeconds = _workoutState.value.restTimer.remainingSeconds
        analyticsTracker.logRestTimerSkipped(remainingSeconds)
        timerJob?.cancel()
        if (serviceBound && timerService != null) {
            timerService!!.stopTimer()
        }
        val skippedSetType = _workoutState.value.restTimer.setType
        val skippedExerciseId = _workoutState.value.restTimer.exerciseId
        val skippedSetOrder = _workoutState.value.restTimer.setOrder
        _workoutState.update { state ->
            var updated = state.copy(restTimer = RestTimerState())
            if (skippedExerciseId != null && skippedSetOrder != null) {
                updated = updated.copy(finishedRestSeparators = updated.finishedRestSeparators + "${skippedExerciseId}_${skippedSetOrder}")
            }
            updated
        }
        // Staggered: collapse warmup rows 500ms after the rest separator is hidden.
        if (skippedSetType == SetType.WARMUP && skippedExerciseId != null) {
            viewModelScope.launch {
                delay(500)
                _workoutState.update { state ->
                    val warmupSets = state.exercises.find { it.exercise.id == skippedExerciseId }
                        ?.sets?.filter { it.setType == SetType.WARMUP } ?: emptyList()
                    if (warmupSets.isNotEmpty() && warmupSets.all { it.isCompleted })
                        state.copy(collapsedWarmupExerciseIds = state.collapsedWarmupExerciseIds + skippedExerciseId)
                    else state
                }
            }
        }
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
            ).markDirtyIfEditing()
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
            ).markDirtyIfEditing()
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
            ).markDirtyIfEditing()
        }
    }

    /** Remove an exercise from the active workout list. */
    fun removeExercise(exerciseId: Long) {
        _workoutState.update { state ->
            state.copy(
                exercises = state.exercises.filter { it.exercise.id != exerciseId },
                snackbarMessage = "Exercise removed"
            ).markDirtyIfEditing()
        }
    }

    /** Remove a functional block and all its exercises from the active workout. */
    fun removeBlock(blockId: String) {
        _workoutState.update { state ->
            state.copy(
                exercises = state.exercises.filter { it.blockId != blockId },
                blocks = state.blocks.filter { it.id != blockId },
                snackbarMessage = "Block removed"
            ).markDirtyIfEditing()
        }
    }

    /** Update plan parameters of a functional block. Only meaningful before the block has started. */
    fun updateBlock(
        blockId: String,
        durationSeconds: Int?,
        targetRounds: Int?,
        emomRoundSeconds: Int?,
        tabataWorkSeconds: Int?,
        tabataRestSeconds: Int?,
        tabataSkipLastRest: Boolean? = null,
        setupSecondsOverride: Int? = null,
        warnAtSecondsOverride: Int? = null,
        name: String? = null
    ) {
        _workoutState.update { state ->
            state.copy(
                blocks = state.blocks.map { block ->
                    if (block.id == blockId) block.copy(
                        durationSeconds = durationSeconds,
                        targetRounds = targetRounds,
                        emomRoundSeconds = emomRoundSeconds,
                        tabataWorkSeconds = tabataWorkSeconds,
                        tabataRestSeconds = tabataRestSeconds,
                        tabataSkipLastRest = tabataSkipLastRest?.let { if (it) 1 else 0 } ?: block.tabataSkipLastRest,
                        setupSecondsOverride = setupSecondsOverride,
                        warnAtSecondsOverride = warnAtSecondsOverride,
                        name = name ?: block.name
                    ) else block
                }
            ).markDirtyIfEditing()
        }
    }

    /** Replace an exercise, keeping all its existing sets. */
    fun replaceExercise(oldId: Long, newExercise: Exercise) {
        _workoutState.update { state ->
            state.copy(
                exercises = state.exercises.map { ex ->
                    if (ex.exercise.id == oldId) ex.copy(
                        exercise = newExercise,
                        sets = ex.sets.map { s -> ActiveSet(setOrder = s.setOrder, setType = s.setType) },
                        sessionNote = null,
                        stickyNote = null
                    ) else ex
                }
            ).markDirtyIfEditing()
        }
    }

    /** Update a volatile session note for the exercise (lost when workout ends). */
    fun updateExerciseSessionNote(exerciseId: Long, note: String?) {
        _workoutState.update { state ->
            state.copy(
                exercises = state.exercises.map { ex ->
                    if (ex.exercise.id == exerciseId) ex.copy(sessionNote = note) else ex
                }
            ).markDirtyIfEditing()
        }
    }

    /** Update a volatile session note for a functional block (lost when workout ends). */
    fun updateBlockSessionNote(blockId: String, note: String?) {
        _workoutState.update { it.copy(blockSessionNotes = it.blockSessionNotes + (blockId to note)) }
    }

    /** Update a sticky note for an exercise — persisted to DB via RoutineExercise.stickyNote. */
    fun updateExerciseStickyNote(exerciseId: Long, note: String?) {
        viewModelScope.launch {
            try {
                val routineId = _workoutState.value.routineId
                // Skip DAO write during live-workout edit — snapshot restore handles revert.
                if (routineId.isNotBlank() && !isLiveEdit()) {
                    routineExerciseDao.updateStickyNote(routineId, exerciseId, note)
                }
                _workoutState.update { state ->
                    state.copy(
                        exercises = state.exercises.map { ex ->
                            if (ex.exercise.id == exerciseId) ex.copy(stickyNote = note) else ex
                        }
                    ).markDirtyIfEditing()
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
                // Skip DAO write during live-workout edit — snapshot restore handles revert.
                if (!isLiveEdit()) exerciseDao.updateRestDuration(exerciseId, seconds)
                _workoutState.update { state ->
                    state.copy(
                        exercises = state.exercises.map { ex ->
                            if (ex.exercise.id == exerciseId)
                                ex.copy(exercise = ex.exercise.copy(restDurationSeconds = seconds))
                            else ex
                        }
                    ).markDirtyIfEditing()
                }
            } catch (e: Exception) {
                _workoutState.update { it.copy(snackbarMessage = "Failed to update rest timer") }
            }
        }
    }

    /** Update all three rest timer durations (work, warmup, drop) and restAfterLastSet for an exercise and persist. */
    fun updateExerciseRestTimers(exerciseId: Long, workSeconds: Int, warmupSeconds: Int, dropSeconds: Int, restAfterLastSet: Boolean) {
        viewModelScope.launch {
            try {
                // Skip DAO write during live-workout edit — snapshot restore handles revert.
                if (!isLiveEdit()) exerciseDao.updateRestTimers(exerciseId, workSeconds, warmupSeconds, dropSeconds, restAfterLastSet)
                val prefix = "${exerciseId}_"
                _workoutState.update { state ->
                    state.copy(
                        exercises = state.exercises.map { ex ->
                            if (ex.exercise.id == exerciseId)
                                ex.copy(exercise = ex.exercise.copy(
                                    restDurationSeconds = workSeconds,
                                    warmupRestSeconds = warmupSeconds,
                                    dropSetRestSeconds = dropSeconds,
                                    restAfterLastSet = restAfterLastSet
                                ))
                            else ex
                        },
                        hiddenRestSeparators = state.hiddenRestSeparators.filterNot { it.startsWith(prefix) }.toSet()
                    ).markDirtyIfEditing()
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
        if (serviceBound && timerService != null) timerService!!.stopTimer()
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
                    if (i in 1..3) {
                        if (settings.restTimerAudioEnabled) restTimerNotifier.playWarningBeep(timerSoundState.value)
                        if (settings.restTimerHapticsEnabled) restTimerNotifier.hapticShortPulse()
                    }
                    if (i == 0) {
                        restTimerNotifier.notifyEnd(
                            audioEnabled = settings.restTimerAudioEnabled,
                            hapticsEnabled = settings.restTimerHapticsEnabled,
                            sound = timerSoundState.value
                        )
                    }
                    if (i > 0) delay(1000)
                }
                _workoutState.update { it.copy(restTimer = RestTimerState()) }
            }
        }
    }

    /** Returns true if the exercise already has any WARMUP-type sets. */
    fun hasWarmupSets(exerciseId: Long): Boolean =
        _workoutState.value.exercises
            .find { it.exercise.id == exerciseId }
            ?.sets?.any { it.setType == SetType.WARMUP } == true

    /**
     * Returns the average weight of non-empty NORMAL sets for the exercise,
     * or null if all work sets are empty.
     */
    fun getWorkSetWeight(exerciseId: Long): Double? {
        val sets = _workoutState.value.exercises
            .find { it.exercise.id == exerciseId }
            ?.sets?.filter { it.setType == SetType.NORMAL } ?: return null
        val weights = sets.mapNotNull { it.weight.toDoubleOrNull()?.takeIf { w -> w > 0 } }
        return if (weights.isEmpty()) null else weights.average()
    }

    /**
     * Returns true when the exercise qualifies for smart warmup generation:
     * must be STRENGTH type and have a valid equipment type (or be Bodyweight with loaded sets).
     */
    fun canAddSmartWarmups(exerciseId: Long): Boolean {
        val entry = _workoutState.value.exercises.find { it.exercise.id == exerciseId } ?: return false
        if (entry.exercise.exerciseType.name != "STRENGTH") return false
        val equip = entry.exercise.equipmentType
        if (equip == "Bodyweight") {
            // Only available if at least one set has a non-zero weight
            return entry.sets.any { it.weight.toDoubleOrNull()?.let { w -> w > 0 } == true }
        }
        return WarmupCalculator.equipmentToWarmupParams(equip, currentUnit) != null
    }

    /**
     * Computes and prepends smart warmup sets for the given exercise.
     *
     * @param workingWeight explicit working weight (used when work sets are empty and user entered it)
     * @param workingReps   explicit working reps (used together with [workingWeight])
     * @param fillWorkSets  if true, fills empty NORMAL sets with [workingWeight]/[workingReps]
     */
    fun addSmartWarmups(
        exerciseId: Long,
        workingWeight: Double? = null,
        workingReps: Int? = null,
        fillWorkSets: Boolean = false
    ) {
        viewModelScope.launch {
            try {
                val exerciseEntry = _workoutState.value.exercises
                    .find { it.exercise.id == exerciseId } ?: return@launch

                val equip = exerciseEntry.exercise.equipmentType
                val params = if (equip == "Bodyweight") {
                    WarmupCalculator.bodyweightLoadedParams(currentUnit)
                } else {
                    WarmupCalculator.equipmentToWarmupParams(equip, currentUnit) ?: return@launch
                }

                // Resolve working weight: explicit arg → average of existing work sets
                val resolvedWeight = workingWeight
                    ?: exerciseEntry.sets
                        .filter { it.setType == SetType.NORMAL }
                        .mapNotNull { it.weight.toDoubleOrNull()?.takeIf { w -> w > 0 } }
                        .average().takeIf { !it.isNaN() }

                if (resolvedWeight == null || resolvedWeight <= 0) {
                    _workoutState.update { it.copy(snackbarMessage = "Set working weight first") }
                    return@launch
                }

                val warmupData = WarmupCalculator.computeWarmupSets(resolvedWeight, params)
                if (warmupData.isEmpty()) {
                    _workoutState.update { it.copy(snackbarMessage = "Working weight too light for warmup sets") }
                    return@launch
                }

                _workoutState.update { state ->
                    val currentSets = state.exercises.find { it.exercise.id == exerciseId }?.sets ?: return@update state

                    // Optionally fill empty NORMAL sets (treat blank or "0" as empty)
                    val updatedNormalSets = if (fillWorkSets && workingWeight != null) {
                        currentSets.map { set ->
                            val isEmptySet = set.setType == SetType.NORMAL &&
                                (set.weight.isBlank() || set.weight.trim().toDoubleOrNull() == 0.0)
                            if (isEmptySet) {
                                set.copy(
                                    weight = formatWeight(workingWeight, currentUnit),
                                    reps = workingReps?.toString() ?: set.reps
                                )
                            } else set
                        }
                    } else currentSets

                    // Remove existing warmup sets, keep everything else
                    val nonWarmupSets = updatedNormalSets.filter { it.setType != SetType.WARMUP }

                    // Build new warmup ActiveSets
                    val newWarmupSets = warmupData.mapIndexed { idx, warmup ->
                        ActiveSet(
                            setOrder = idx + 1,
                            setType = SetType.WARMUP,
                            weight = formatWeight(warmup.weight, currentUnit),
                            reps = warmup.reps.toString()
                        )
                    }

                    // Shift non-warmup sets
                    val shiftedSets = nonWarmupSets.mapIndexed { idx, set ->
                        set.copy(setOrder = newWarmupSets.size + idx + 1)
                    }

                    state.copy(
                        exercises = state.exercises.map { ex ->
                            if (ex.exercise.id == exerciseId) {
                                ex.copy(sets = newWarmupSets + shiftedSets)
                            } else ex
                        }
                    ).markDirtyIfEditing()
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

    /** Toggle an exercise in/out of the superset candidate set.
     *  Functional block exercises (AMRAP/RFT/EMOM/TABATA) are silently ignored — they cannot be supersetted. */
    fun toggleSupersetCandidate(exerciseId: Long) {
        _workoutState.update { state ->
            val ex = state.exercises.find { it.exercise.id == exerciseId }
            val block = state.blocks.find { it.id == ex?.blockId }
            if (block != null && block.type != "STRENGTH") return@update state
            val updated = if (exerciseId in state.supersetCandidateIds) {
                state.supersetCandidateIds - exerciseId
            } else {
                state.supersetCandidateIds + exerciseId
            }
            state.copy(supersetCandidateIds = updated)
        }
    }

    /** Commit the current candidate set as a superset group.
     *  Clears [supersetCandidateIds] so the user can immediately select another group,
     *  but keeps [isSupersetSelectMode] true until the user explicitly taps Done. */
    fun commitSupersetSelection() {
        val candidates = _workoutState.value.supersetCandidateIds
        if (candidates.size >= 2) {
            pairAsSuperset(candidates)
            _workoutState.update { it.copy(supersetCandidateIds = emptySet()) }
        }
        // Mode stays active — user taps Done to exit Organize Mode.
    }

    /** Ungroup selected exercises from their superset(s).
     *  If fewer than 2 members remain in a group after removal, the entire group is dissolved.
     *  Clears [supersetCandidateIds] but keeps [isSupersetSelectMode] true. */
    fun ungroupSelectedExercises() {
        val candidates = _workoutState.value.supersetCandidateIds
        if (candidates.isEmpty()) return
        _workoutState.update { state ->
            val affectedGroups = state.exercises
                .filter { it.exercise.id in candidates && it.supersetGroupId != null }
                .mapNotNull { it.supersetGroupId }
                .toSet()
            var updated = state.exercises.map { ex ->
                if (ex.exercise.id in candidates) ex.copy(supersetGroupId = null) else ex
            }
            for (groupId in affectedGroups) {
                val remaining = updated.count { it.supersetGroupId == groupId }
                if (remaining < 2) {
                    updated = updated.map { ex ->
                        if (ex.supersetGroupId == groupId) ex.copy(supersetGroupId = null) else ex
                    }
                }
            }
            state.copy(exercises = updated, supersetCandidateIds = emptySet()).markDirtyIfEditing()
        }
    }

    /** Exit Organize Mode (superset-select mode), discarding any unconfirmed selection. */
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
            state.copy(exercises = list).markDirtyIfEditing()
        }
    }

    /**
     * Reorder an organize-mode list item by LazyList keys.
     * Handles both individual exercise items (Long key = exercise ID) and
     * functional block items (String key = "org_block_<blockId>").
     * Functional blocks move atomically — all their exercises relocate together.
     *
     * The reorderable library calls this with adjacent swaps, so [fromKey] and [toKey]
     * represent the two items exchanging positions. Direction (forward/backward) is inferred
     * from whether the target appears after or before the source in the flat exercises list.
     */
    fun reorderOrganizeItem(fromKey: Any, toKey: Any) {
        _workoutState.update { state ->
            val list = state.exercises.toMutableList()
            val fromBlockId = (fromKey as? String)?.takeIf { it.startsWith("org_block_") }?.removePrefix("org_block_")
            val toBlockId = (toKey as? String)?.takeIf { it.startsWith("org_block_") }?.removePrefix("org_block_")
            when {
                fromBlockId != null -> {
                    // Moving an entire functional block — move all its exercises atomically
                    val blockExercises = list.filter { it.blockId == fromBlockId }
                    val firstBlockIdx = list.indexOfFirst { it.blockId == fromBlockId }
                    val targetIdx = when {
                        toBlockId != null -> list.indexOfFirst { it.blockId == toBlockId }
                        toKey is Long -> list.indexOfFirst { it.exercise.id == toKey }
                        else -> -1
                    }
                    if (targetIdx < 0) return@update state
                    val draggingForward = targetIdx > firstBlockIdx
                    list.removeAll { it.blockId == fromBlockId }
                    val insertAt = when {
                        toBlockId != null -> {
                            val idx = list.indexOfFirst { it.blockId == toBlockId }
                            if (idx < 0) list.size
                            else if (draggingForward) idx + list.count { it.blockId == toBlockId }
                            else idx
                        }
                        toKey is Long -> {
                            val idx = list.indexOfFirst { it.exercise.id == toKey }
                            if (idx < 0) list.size
                            else if (draggingForward) idx + 1
                            else idx
                        }
                        else -> list.size
                    }.coerceAtMost(list.size)
                    list.addAll(insertAt, blockExercises)
                }
                toBlockId != null -> {
                    // Moving a strength/unblocked exercise past a functional block
                    val exId = fromKey as? Long ?: return@update state
                    val fromIdx = list.indexOfFirst { it.exercise.id == exId }
                    if (fromIdx < 0) return@update state
                    val targetFirstIdx = list.indexOfFirst { it.blockId == toBlockId }
                    if (targetFirstIdx < 0) return@update state
                    val draggingForward = targetFirstIdx > fromIdx
                    val item = list.removeAt(fromIdx)
                    val insertAt = if (draggingForward) {
                        val idx = list.indexOfFirst { it.blockId == toBlockId }
                        if (idx >= 0) idx + list.count { it.blockId == toBlockId } else list.size
                    } else {
                        list.indexOfFirst { it.blockId == toBlockId }.let { if (it < 0) list.size else it }
                    }.coerceAtMost(list.size)
                    list.add(insertAt, item)
                }
                else -> {
                    // Both are individual exercises
                    val fromId = fromKey as? Long ?: return@update state
                    val toId = toKey as? Long ?: return@update state
                    val fromIdx = list.indexOfFirst { it.exercise.id == fromId }
                    val toIdx = list.indexOfFirst { it.exercise.id == toId }
                    if (fromIdx >= 0 && toIdx >= 0) {
                        val item = list.removeAt(fromIdx)
                        list.add(toIdx, item)
                    }
                }
            }
            // Keep blocks list in sync with the new exercises order so normal-mode rendering
            // (which iterates workoutState.blocks) matches the organize-mode drag result.
            val orderedBlockIds = list.mapNotNull { it.blockId }.distinct()
            val reorderedBlocks = orderedBlockIds.mapNotNull { id -> state.blocks.find { it.id == id } } +
                state.blocks.filter { b -> orderedBlockIds.none { it == b.id } }
            state.copy(exercises = list, blocks = reorderedBlocks).markDirtyIfEditing()
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
            val exercise = exerciseRepository.getAllExercises().first()
                .find { it.id == exerciseId } ?: return@launch

            val updatedExercise = exercise.copy(setupNotes = notes.ifBlank { null })
            // Skip DAO write during live-workout edit — snapshot restore handles revert.
            if (!isLiveEdit()) exerciseRepository.updateExercise(updatedExercise)

            // Update local state
            _workoutState.update { state ->
                val updatedExercises = state.exercises.map { exerciseWithSets ->
                    if (exerciseWithSets.exercise.id == exerciseId) {
                        exerciseWithSets.copy(exercise = updatedExercise)
                    } else {
                        exerciseWithSets
                    }
                }
                state.copy(exercises = updatedExercises).markDirtyIfEditing()
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
            state.copy(exercises = updatedExercises).markDirtyIfEditing()
        }
    }

}

