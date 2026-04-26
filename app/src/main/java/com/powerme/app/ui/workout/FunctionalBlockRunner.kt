package com.powerme.app.ui.workout

import com.powerme.app.data.database.WorkoutBlock
import com.powerme.app.data.database.WorkoutBlockDao
import com.powerme.app.util.WakeLockManager
import com.powerme.app.util.timer.TimerEngine
import com.powerme.app.util.timer.TimerSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton runner for AMRAP / RFT / EMOM / TABATA blocks.
 *
 * Owns the shared [TimerEngine] while a block is active and acts as the formal soft mutex
 * (via [isActive]) preventing concurrent timers (rest timer, Tools EMOM, etc.). Wakelock is
 * acquired on start and released on finish/abandon.
 *
 * Resume-from-kill: [resumeFromKill] reconciles wall-clock elapsed via [WorkoutBlock.runStartMs]
 * and seeds [TimerEngine.resumeAt] mid-interval.
 */
@Singleton
class FunctionalBlockRunner @Inject constructor(
    private val timerEngine: TimerEngine,
    private val workoutBlockDao: WorkoutBlockDao,
    private val wakeLockManager: WakeLockManager,
) {
    // Main dispatcher — orchestration only; TimerEngine's delay(1000L) is non-blocking.
    // Tests substitute via Dispatchers.setMain(testDispatcher).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val tapMutex = Mutex()

    private val _state = MutableStateFlow<FunctionalBlockRunnerState?>(null)
    val state: StateFlow<FunctionalBlockRunnerState?> = _state.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private var runJob: Job? = null
    private var stateMirrorJob: Job? = null
    private var currentBlockId: String? = null
    private var currentPlan: BlockPlan? = null
    private var currentBlockType: String? = null
    private var currentBlockName: String? = null
    private var roundTapCount: Int = 0

    /** Start a fresh block. Acquires wakelock, sets runStartMs (idempotent), launches timer. */
    suspend fun start(block: WorkoutBlock, plan: BlockPlan, onFinish: suspend () -> Unit = {}) {
        if (_isActive.value) return  // already running

        wakeLockManager.acquire()
        if (block.runStartMs == null) {
            workoutBlockDao.setRunStart(block.id, System.currentTimeMillis())
        }

        currentBlockId = block.id
        currentBlockType = block.type
        currentBlockName = block.name
        currentPlan = plan
        roundTapCount = 0

        val spec = mapToTimerSpec(block)
        emitInitialState()
        _isActive.value = true

        startMirror()
        runJob = scope.launch {
            timerEngine.run(spec, setupSeconds = block.setupSecondsOverride ?: 3)
            onFinish()
        }
    }

    /** Resume after process kill. elapsedSeconds derived from runStartMs wall-clock. */
    suspend fun resumeFromKill(block: WorkoutBlock, plan: BlockPlan) {
        if (_isActive.value) return
        val startMs = block.runStartMs ?: return

        val elapsed = ((System.currentTimeMillis() - startMs) / 1000L).toInt().coerceAtLeast(0)
        wakeLockManager.acquire()

        currentBlockId = block.id
        currentBlockType = block.type
        currentBlockName = block.name
        currentPlan = plan
        roundTapCount = parseTapCount(block.roundTapLogJson)

        val spec = mapToTimerSpec(block)
        emitInitialState()
        _isActive.value = true

        startMirror()
        runJob = scope.launch {
            timerEngine.resumeAt(spec, elapsedSeconds = elapsed, setupSeconds = 0)
        }
    }

    fun pause() {
        if (!_isActive.value) return
        timerEngine.pause()
    }

    fun resume() {
        if (!_isActive.value) return
        timerEngine.resume()
    }

    /** Persist results, stop timer, release wakelock, clear state. Validates Invariant #9. */
    suspend fun finish(
        rounds: Int? = null,
        extraReps: Int? = null,
        finishSeconds: Int? = null,
        rpe: Int? = null,
        perExerciseRpeJson: String? = null,
        notes: String? = null,
    ) {
        require(rpe == null || perExerciseRpeJson == null) {
            "Invariant #9: rpe and perExerciseRpeJson are mutually exclusive"
        }
        val blockId = currentBlockId ?: return

        // Read current tap log to persist (DAO writes append separately, but include in saveResult for atomicity)
        val tapLog = workoutBlockDao.getById(blockId)?.roundTapLogJson

        workoutBlockDao.saveResult(
            id = blockId,
            r = rounds,
            er = extraReps,
            ft = finishSeconds,
            rpe = rpe,
            perExJson = perExerciseRpeJson,
            tapLog = tapLog,
            notes = notes,
            ts = System.currentTimeMillis(),
        )
        teardown()
    }

    /** Stop without persisting. Leaves runStartMs intact for resume-from-kill. */
    fun abandon() {
        if (!_isActive.value) return
        teardown()
    }

    /** Skip the current EMOM interval — causes the active round loop to break early. */
    fun skipCurrentInterval() {
        if (!_isActive.value) return
        timerEngine.skipInterval()
    }

    /** Append a round-tap log entry. Serialized via [tapMutex] to avoid lost writes. */
    suspend fun appendRoundTap(round: Int, elapsedMs: Long, phase: String? = null, completed: Boolean? = null) {
        val blockId = currentBlockId ?: return
        tapMutex.withLock {
            val entry = buildString {
                append("{\"round\":").append(round)
                append(",\"elapsedMs\":").append(elapsedMs)
                if (phase != null) append(",\"phase\":\"").append(phase).append("\"")
                if (completed != null) append(",\"completed\":").append(completed)
                append("}")
            }
            workoutBlockDao.appendRoundTap(blockId, entry, System.currentTimeMillis())
            roundTapCount++
            _state.update { it?.copy(roundTapCount = roundTapCount) }
        }
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private fun teardown() {
        runJob?.cancel()
        runJob = null
        stateMirrorJob?.cancel()
        stateMirrorJob = null
        timerEngine.stop()
        wakeLockManager.release()
        _isActive.value = false
        _state.value = null
        currentBlockId = null
        currentPlan = null
        currentBlockType = null
        currentBlockName = null
        roundTapCount = 0
    }

    private fun startMirror() {
        stateMirrorJob?.cancel()
        stateMirrorJob = scope.launch {
            timerEngine.state.collect { ts ->
                val plan = currentPlan ?: return@collect
                val blockId = currentBlockId ?: return@collect
                val type = currentBlockType ?: return@collect
                val capRemaining = plan.durationSeconds?.let { cap ->
                    if (type == "RFT") (cap - ts.elapsedSeconds).coerceAtLeast(0) else null
                }
                _state.update { current ->
                    FunctionalBlockRunnerState(
                        blockId = blockId,
                        blockType = type,
                        blockName = currentBlockName,
                        plan = plan,
                        timerPhase = ts.phase,
                        displaySeconds = ts.displaySeconds,
                        elapsedSeconds = ts.elapsedSeconds,
                        currentRound = ts.currentRound,
                        totalRounds = ts.totalRounds,
                        phaseTotalSeconds = ts.phaseTotalSeconds,
                        isRunning = ts.isRunning,
                        isPaused = !ts.isRunning && _isActive.value,
                        roundTapCount = current?.roundTapCount ?: roundTapCount,
                        capRemainingSeconds = capRemaining,
                    )
                }
            }
        }
    }

    private fun emitInitialState() {
        val plan = currentPlan ?: return
        val blockId = currentBlockId ?: return
        val type = currentBlockType ?: return
        _state.value = FunctionalBlockRunnerState(
            blockId = blockId,
            blockType = type,
            blockName = currentBlockName,
            plan = plan,
            timerPhase = com.powerme.app.util.timer.TimerPhase.IDLE,
            displaySeconds = 0,
            elapsedSeconds = 0,
            currentRound = 0,
            totalRounds = 0,
            phaseTotalSeconds = 0,
            isRunning = false,
            isPaused = false,
            roundTapCount = roundTapCount,
            capRemainingSeconds = null,
        )
    }

    private fun mapToTimerSpec(block: WorkoutBlock): TimerSpec = when (block.type) {
        "AMRAP" -> TimerSpec.Amrap(durationSeconds = block.durationSeconds!!)
        "RFT" -> TimerSpec.Rft(
            targetRounds = block.targetRounds!!,
            capSeconds = block.durationSeconds,
        )
        "EMOM" -> {
            val interval = block.emomRoundSeconds ?: 60
            val override = block.warnAtSecondsOverride
            TimerSpec.Emom(
                totalDurationSeconds = block.durationSeconds!!,
                intervalSeconds = interval,
                warnAtSeconds = override ?: 10,
                warnAtSeconds2 = if (override == null && interval > 20) 30 else null,
            )
        }
        "TABATA" -> {
            val workSecs = block.tabataWorkSeconds!!
            TimerSpec.Tabata(
                workSeconds = workSecs,
                restSeconds = block.tabataRestSeconds!!,
                rounds = block.targetRounds ?: 8,
                workWarnAtSeconds = block.warnAtSecondsOverride ?: resolveWarnAt(workSecs),
                skipLastRest = block.tabataSkipLastRest == 1,
            )
        }
        else -> error("FunctionalBlockRunner cannot run block type ${block.type}")
    }

    /** Default warn threshold: half the interval, capped at 10s, minimum 1s. */
    private fun resolveWarnAt(intervalSeconds: Int): Int =
        (intervalSeconds / 2).coerceIn(1, 10)

    private fun parseTapCount(json: String?): Int {
        if (json.isNullOrBlank()) return 0
        // count occurrences of `"round":` — simple, robust to JSON whitespace
        var count = 0
        var idx = 0
        while (true) {
            val next = json.indexOf("\"round\"", idx)
            if (next < 0) break
            count++
            idx = next + 1
        }
        return count
    }
}
