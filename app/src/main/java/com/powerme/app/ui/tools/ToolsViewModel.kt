package com.powerme.app.ui.tools

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.powerme.app.util.AlertType
import com.powerme.app.util.RestTimerNotifier
import com.powerme.app.util.WakeLockManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TimerMode { EMOM, TABATA, STOPWATCH, COUNTDOWN }
enum class TimerPhase { IDLE, WORK, REST }

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
    val countdownInputSeconds: Int = 60,
    val isRunning: Boolean = false,
    // Text fields to avoid integer deletion bug
    val emomRoundSecondsText: String = "60",
    val emomTotalRoundsText: String = "5",
    val workSecondsText: String = "20",
    val restSecondsText: String = "10",
    val totalRoundsText: String = "8",
    val countdownText: String = "60",
    // TABATA skip last rest
    val tabataSkipLastRest: Boolean = false,
    // EMOM skip last rest
    val emomSkipLastRest: Boolean = false,
    // EMOM warning sound
    val emomWarnAtSecondsText: String = "",
    // COUNTDOWN pre-finish alert
    val countdownWarnAtSecondsText: String = ""
)

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val wakeLockManager: WakeLockManager,
    @ApplicationContext context: Context
) : ViewModel() {

    private val restTimerNotifier = RestTimerNotifier(context)

    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()

    private var timerJob: Job? = null

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
    fun updateCountdownText(text: String) {
        _uiState.update { it.copy(countdownText = text, countdownInputSeconds = text.toIntOrNull() ?: it.countdownInputSeconds) }
    }
    fun updateEmomWarnAtSecondsText(text: String) {
        _uiState.update { it.copy(emomWarnAtSecondsText = text) }
    }
    fun updateCountdownWarnAtSecondsText(text: String) {
        _uiState.update { it.copy(countdownWarnAtSecondsText = text) }
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
        val countdown = state.countdownText.toIntOrNull()?.takeIf { it > 0 } ?: state.countdownInputSeconds

        _uiState.update { it.copy(
            emomRoundSeconds = emomRound, emomTotalRounds = emomTotal,
            workSeconds = work, restSeconds = rest, totalRounds = rounds,
            countdownInputSeconds = countdown, isRunning = true
        ) }

        wakeLockManager.acquire()

        timerJob = viewModelScope.launch {
            when (_uiState.value.mode) {
                TimerMode.EMOM -> runEmom()
                TimerMode.TABATA -> runTabata()
                TimerMode.STOPWATCH -> runStopwatch()
                TimerMode.COUNTDOWN -> runCountdown()
            }
        }
    }

    fun pauseTimer() {
        timerJob?.cancel()
        _uiState.update { it.copy(isRunning = false) }
    }

    fun resetTimer() {
        timerJob?.cancel()
        wakeLockManager.release()
        _uiState.update {
            it.copy(
                isRunning = false,
                phase = TimerPhase.IDLE,
                displaySeconds = 0,
                elapsedSeconds = 0,
                currentRound = 0
            )
        }
    }

    private suspend fun runEmom() {
        val totalRounds   = _uiState.value.emomTotalRounds
        val roundDuration = _uiState.value.emomRoundSeconds
        val warnAt        = _uiState.value.emomWarnAtSecondsText.toIntOrNull()

        for (round in 1..totalRounds) {
            _uiState.update { it.copy(phase = TimerPhase.WORK, currentRound = round) }
            restTimerNotifier.triggerAudioAlert(AlertType.ROUND_START)
            var remaining = roundDuration
            var warnedThisRound = false
            while (remaining > 0 && _uiState.value.isRunning) {
                _uiState.update { it.copy(displaySeconds = remaining) }
                if (warnAt != null && remaining == warnAt && !warnedThisRound) {
                    warnedThisRound = true
                    restTimerNotifier.triggerAudioAlert(AlertType.WARNING)
                }
                if (remaining == 2 || remaining == 1) {
                    restTimerNotifier.triggerAudioAlert(AlertType.COUNTDOWN_TICK)
                }
                delay(1000L)
                remaining--
            }
            if (!_uiState.value.isRunning) return
            // No REST phase in EMOM — next round starts immediately
        }
        finishTimer()  // always reached; fires FINISH alert + cleanup
    }

    private suspend fun runTabata() {
        val state        = _uiState.value
        var round        = state.currentRound
        val totalRounds  = state.totalRounds
        val skipLastRest = state.tabataSkipLastRest

        while (round < totalRounds) {
            val isLastRound = round == totalRounds - 1

            // ── WORK phase ───────────────────────────────────────────────────
            _uiState.update { it.copy(phase = TimerPhase.WORK, currentRound = round + 1) }
            restTimerNotifier.triggerAudioAlert(AlertType.ROUND_START)
            var workRemaining = _uiState.value.workSeconds
            while (workRemaining > 0 && _uiState.value.isRunning) {
                _uiState.update { it.copy(displaySeconds = workRemaining) }
                if (workRemaining == 2 || workRemaining == 1) {
                    restTimerNotifier.triggerAudioAlert(AlertType.COUNTDOWN_TICK)
                }
                delay(1000L)
                workRemaining--
            }
            if (!_uiState.value.isRunning) return
            restTimerNotifier.triggerAudioAlert(AlertType.FINISH)

            // ── REST phase ───────────────────────────────────────────────────
            if (!(isLastRound && skipLastRest)) {
                _uiState.update { it.copy(phase = TimerPhase.REST) }
                restTimerNotifier.triggerAudioAlert(AlertType.ROUND_START)
                var restRemaining = _uiState.value.restSeconds
                while (restRemaining > 0 && _uiState.value.isRunning) {
                    _uiState.update { it.copy(displaySeconds = restRemaining) }
                    if (restRemaining == 2 || restRemaining == 1) {
                        restTimerNotifier.triggerAudioAlert(AlertType.COUNTDOWN_TICK)
                    }
                    delay(1000L)
                    restRemaining--
                }
                if (!_uiState.value.isRunning) return
                restTimerNotifier.triggerAudioAlert(AlertType.FINISH)
            }
            round++
        }
        finishTimer()  // overall cleanup; fires one final FINISH alert
    }

    private suspend fun runStopwatch() {
        var elapsed = _uiState.value.elapsedSeconds
        _uiState.update { it.copy(phase = TimerPhase.WORK) }

        while (_uiState.value.isRunning) {
            _uiState.update { it.copy(elapsedSeconds = elapsed) }
            delay(1000L)
            elapsed++
        }
    }

    private suspend fun runCountdown() {
        var remaining = if (_uiState.value.displaySeconds > 0) _uiState.value.displaySeconds
        else _uiState.value.countdownInputSeconds

        val warnAt = _uiState.value.countdownWarnAtSecondsText.toIntOrNull()
        var warnedThisRound = false

        _uiState.update { it.copy(phase = TimerPhase.WORK) }

        while (remaining > 0 && _uiState.value.isRunning) {
            _uiState.update { it.copy(displaySeconds = remaining) }
            if (warnAt != null && remaining == warnAt && !warnedThisRound) {
                warnedThisRound = true
                restTimerNotifier.playWarningBeep()
            }
            if (remaining == 2 || remaining == 1) restTimerNotifier.playWarningBeep()
            delay(1000L)
            remaining--
        }
        if (_uiState.value.isRunning) {
            _uiState.update { it.copy(displaySeconds = 0) }
            finishTimer()
        }
    }

    private fun finishTimer() {
        restTimerNotifier.triggerAudioAlert(AlertType.FINISH)
        wakeLockManager.release()
        _uiState.update { it.copy(isRunning = false, phase = TimerPhase.IDLE) }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        wakeLockManager.release()
    }
}
