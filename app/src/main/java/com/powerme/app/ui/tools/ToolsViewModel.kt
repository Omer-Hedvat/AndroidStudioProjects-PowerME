package com.powerme.app.ui.tools

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powerme.app.data.AppSettingsDataStore
import com.powerme.app.util.AlertType
import com.powerme.app.util.ClocksTimerBridge
import com.powerme.app.util.RestTimerNotifier
import com.powerme.app.util.TimerSound
import com.powerme.app.util.WakeLockManager
import com.powerme.app.util.timer.TimerEngine
import com.powerme.app.util.timer.TimerEngineImpl
import com.powerme.app.util.timer.TimerEngineState
import com.powerme.app.util.timer.TimerPhase
import com.powerme.app.util.timer.TimerSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TimerMode { EMOM, TABATA, STOPWATCH, COUNTDOWN }

/**
 * Returns the warn-at threshold in seconds, or null if warn should not fire.
 *
 * - text.isBlank() → auto mode: floor(duration / 2); suppressed (null) when that value <= 3
 * - text.isNotBlank() → manual mode: text.toInt() if valid (> 0 && < duration), else null
 * - duration <= 0 → null always
 */
internal fun resolveWarnAt(text: String, duration: Int): Int? {
    if (duration <= 0) return null
    return if (text.isBlank()) {
        val auto = duration / 2
        if (auto <= 3) null else auto
    } else {
        val v = text.toIntOrNull() ?: return null
        if (v > 0 && v < duration) v else null
    }
}

data class ToolsUiState(
    val mode: TimerMode = TimerMode.EMOM,
    val phase: TimerPhase = TimerPhase.IDLE,
    val displaySeconds: Int = 0,
    val elapsedSeconds: Int = 0,
    val currentRound: Int = 0,
    val totalRounds: Int = 8,
    val workSeconds: Int = 20,
    val restSeconds: Int = 10,
    val emomRoundSeconds: Int = 60,
    val emomTotalRounds: Int = 5,
    val countdownMinutes: Int = 1,
    val countdownSeconds: Int = 0,
    val isRunning: Boolean = false,
    // Text fields to avoid integer deletion bug
    val emomRoundSecondsText: String = "60",
    val emomTotalRoundsText: String = "5",
    val workSecondsText: String = "20",
    val restSecondsText: String = "10",
    val totalRoundsText: String = "8",
    // TABATA skip last rest
    val tabataSkipLastRest: Boolean = false,
    // EMOM skip last rest
    val emomSkipLastRest: Boolean = false,
    // Warn-before-finish fields (blank = auto half-time mode)
    val tabataWorkWarnText: String = "",
    val tabataRestWarnText: String = "",
    val emomWarnText: String = "",
    val countdownWarnText: String = "",
    // Setup time — preparation countdown before main timer starts (shared across all modes)
    val setupSeconds: Int = 0,
    val setupSecondsText: String = "0",
    val setupSecondsRemaining: Int = 0,
    val tickEpochMs: Long = 0L
)

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val wakeLockManager: WakeLockManager,
    private val clocksTimerBridge: ClocksTimerBridge,
    private val appSettingsDataStore: AppSettingsDataStore,
    @ApplicationContext context: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val restTimerNotifier = RestTimerNotifier(context)

    private val timerSoundState: StateFlow<TimerSound> = appSettingsDataStore.timerSound
        .stateIn(viewModelScope, SharingStarted.Eagerly, TimerSound.BEEP)

    private val initialMode: TimerMode = savedStateHandle.get<String>("mode")
        ?.let { runCatching { TimerMode.valueOf(it) }.getOrNull() }
        ?: TimerMode.EMOM

    private val _uiState = MutableStateFlow(ToolsUiState(mode = initialMode))
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null
    private var observerJob: Job? = null
    private var engine: TimerEngineImpl? = null

    fun setMode(mode: TimerMode) {
        if (_uiState.value.isRunning) return
        _uiState.update { it.copy(mode = mode, phase = TimerPhase.IDLE, displaySeconds = 0, elapsedSeconds = 0, currentRound = 0) }
    }

    // Text-based update functions (allow full deletion)
    fun updateEmomRoundSecondsText(text: String) {
        _uiState.update { it.copy(emomRoundSecondsText = text, emomRoundSeconds = text.toIntOrNull() ?: it.emomRoundSeconds) }
    }
    fun updateEmomTotalRoundsText(text: String) {
        _uiState.update { it.copy(emomTotalRoundsText = text, emomTotalRounds = text.toIntOrNull() ?: it.emomTotalRounds) }
    }
    fun updateWorkSecondsText(text: String) {
        _uiState.update { it.copy(workSecondsText = text, workSeconds = text.toIntOrNull() ?: it.workSeconds) }
    }
    fun updateRestSecondsText(text: String) {
        _uiState.update { it.copy(restSecondsText = text, restSeconds = text.toIntOrNull() ?: it.restSeconds) }
    }
    fun updateTotalRoundsText(text: String) {
        _uiState.update { it.copy(totalRoundsText = text, totalRounds = text.toIntOrNull() ?: it.totalRounds) }
    }
    fun updateTabataWorkWarnText(text: String) { _uiState.update { it.copy(tabataWorkWarnText = text) } }
    fun resetTabataWorkWarn()                  { _uiState.update { it.copy(tabataWorkWarnText = "") } }
    fun updateTabataRestWarnText(text: String) { _uiState.update { it.copy(tabataRestWarnText = text) } }
    fun resetTabataRestWarn()                  { _uiState.update { it.copy(tabataRestWarnText = "") } }
    fun updateEmomWarnText(text: String)       { _uiState.update { it.copy(emomWarnText = text) } }
    fun resetEmomWarn()                        { _uiState.update { it.copy(emomWarnText = "") } }
    fun updateCountdownWarnText(text: String)  { _uiState.update { it.copy(countdownWarnText = text) } }
    fun resetCountdownWarn()                   { _uiState.update { it.copy(countdownWarnText = "") } }
    fun updateSetupSecondsText(text: String) {
        _uiState.update { it.copy(setupSecondsText = text, setupSeconds = text.toIntOrNull()?.coerceAtLeast(0) ?: it.setupSeconds) }
    }
    fun updateCountdownMinutes(minutes: Int) {
        _uiState.update { it.copy(countdownMinutes = minutes.coerceIn(0, 59)) }
    }
    fun updateCountdownSeconds(seconds: Int) {
        _uiState.update { it.copy(countdownSeconds = seconds.coerceIn(0, 59)) }
    }
    fun setCountdownPreset(totalSeconds: Int) {
        _uiState.update { it.copy(countdownMinutes = totalSeconds / 60, countdownSeconds = totalSeconds % 60) }
    }
    fun toggleEmomSkipLastRest() {
        _uiState.update { it.copy(emomSkipLastRest = !it.emomSkipLastRest) }
    }

    // Legacy Int update functions (kept for compatibility)
    fun updateWorkSeconds(seconds: Int) { _uiState.update { it.copy(workSeconds = seconds, workSecondsText = seconds.toString()) } }
    fun updateRestSeconds(seconds: Int) { _uiState.update { it.copy(restSeconds = seconds, restSecondsText = seconds.toString()) } }
    fun updateTotalRounds(rounds: Int) { _uiState.update { it.copy(totalRounds = rounds, totalRoundsText = rounds.toString()) } }
    fun updateEmomRoundSeconds(seconds: Int) { _uiState.update { it.copy(emomRoundSeconds = seconds, emomRoundSecondsText = seconds.toString()) } }
    fun updateEmomTotalRounds(rounds: Int) { _uiState.update { it.copy(emomTotalRounds = rounds, emomTotalRoundsText = rounds.toString()) } }
    fun toggleTabataSkipLastRest() {
        _uiState.update { it.copy(tabataSkipLastRest = !it.tabataSkipLastRest) }
    }

    fun startTimer() {
        if (_uiState.value.isRunning) return
        val state = _uiState.value

        // Validate config: parse text fields before starting
        val emomRound = state.emomRoundSecondsText.toIntOrNull()?.takeIf { it > 0 } ?: state.emomRoundSeconds
        val emomTotal = state.emomTotalRoundsText.toIntOrNull()?.takeIf { it > 0 } ?: state.emomTotalRounds
        val work = state.workSecondsText.toIntOrNull()?.takeIf { it > 0 } ?: state.workSeconds
        val rest = state.restSecondsText.toIntOrNull()?.takeIf { it > 0 } ?: state.restSeconds
        val rounds = state.totalRoundsText.toIntOrNull()?.takeIf { it > 0 } ?: state.totalRounds
        val countdownTotal = (state.countdownMinutes * 60 + state.countdownSeconds).takeIf { it > 0 } ?: 60
        val (cdMins, cdSecs) = countdownTotal / 60 to countdownTotal % 60
        val setup = state.setupSecondsText.toIntOrNull()?.coerceAtLeast(0) ?: state.setupSeconds

        _uiState.update {
            it.copy(
                emomRoundSeconds = emomRound, emomTotalRounds = emomTotal,
                workSeconds = work, restSeconds = rest, totalRounds = rounds,
                countdownMinutes = cdMins, countdownSeconds = cdSecs,
                setupSeconds = setup, isRunning = true
            )
        }

        wakeLockManager.acquire()

        // Resume paused engine instead of starting fresh
        val existingEngine = engine
        if (existingEngine != null && state.phase != TimerPhase.IDLE) {
            existingEngine.resume()
            return
        }

        val spec = buildSpec(state.mode, emomRound, emomTotal, work, rest, rounds, countdownTotal, state)
        val newEngine = TimerEngineImpl(restTimerNotifier) { timerSoundState.value }
        engine = newEngine

        observerJob?.cancel()
        observerJob = viewModelScope.launch {
            var prevWasRunning = false
            newEngine.state.collect { engineState ->
                val wasRunning = prevWasRunning
                prevWasRunning = engineState.isRunning
                syncEngineState(engineState)
                when {
                    engineState.isRunning ->
                        clocksTimerBridge.update(engineState.displaySeconds, engineState.phaseTotalSeconds)
                    wasRunning -> {
                        clocksTimerBridge.clear()
                        if (engineState.phase == TimerPhase.IDLE) wakeLockManager.release()
                    }
                }
            }
        }

        timerJob = viewModelScope.launch { newEngine.run(spec, setup) }
    }

    private fun buildSpec(
        mode: TimerMode,
        emomRound: Int, emomTotal: Int,
        work: Int, rest: Int, rounds: Int,
        countdownTotal: Int,
        state: ToolsUiState
    ): TimerSpec = when (mode) {
        TimerMode.EMOM -> TimerSpec.Emom(
            totalDurationSeconds = emomRound * emomTotal,
            intervalSeconds = emomRound,
            warnAtSeconds = resolveWarnAt(state.emomWarnText, emomRound)
        )
        TimerMode.TABATA -> TimerSpec.Tabata(
            workSeconds = work,
            restSeconds = rest,
            rounds = rounds,
            workWarnAtSeconds = resolveWarnAt(state.tabataWorkWarnText, work),
            restWarnAtSeconds = resolveWarnAt(state.tabataRestWarnText, rest),
            skipLastRest = state.tabataSkipLastRest
        )
        TimerMode.COUNTDOWN -> TimerSpec.Countdown(
            durationSeconds = countdownTotal,
            warnAtSeconds = resolveWarnAt(state.countdownWarnText, countdownTotal)
        )
        TimerMode.STOPWATCH -> TimerSpec.Stopwatch
    }

    private fun syncEngineState(engineState: TimerEngineState) {
        _uiState.update { uiState ->
            uiState.copy(
                phase = engineState.phase,
                currentRound = engineState.currentRound,
                displaySeconds = engineState.displaySeconds,
                elapsedSeconds = engineState.elapsedSeconds,
                tickEpochMs = engineState.tickEpochMs,
                isRunning = engineState.isRunning,
                setupSecondsRemaining = if (engineState.phase == TimerPhase.SETUP) engineState.displaySeconds else 0
            )
        }
    }

    fun pauseTimer() {
        engine?.pause()
        clocksTimerBridge.clear()
    }

    fun resetTimer() {
        timerJob?.cancel()
        observerJob?.cancel()
        engine?.stop()
        engine = null
        clocksTimerBridge.clear()
        wakeLockManager.release()
        _uiState.update {
            it.copy(
                isRunning = false,
                phase = TimerPhase.IDLE,
                displaySeconds = 0,
                elapsedSeconds = 0,
                currentRound = 0,
                setupSecondsRemaining = 0,
                tickEpochMs = 0L
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        observerJob?.cancel()
        clocksTimerBridge.clear()
        wakeLockManager.release()
    }
}
