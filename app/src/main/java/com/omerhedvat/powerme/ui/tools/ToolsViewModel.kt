package com.omerhedvat.powerme.ui.tools

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omerhedvat.powerme.util.RestTimerNotifier
import com.omerhedvat.powerme.util.WakeLockManager
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
    val emomMinutes: Int = 3,
    val countdownInputSeconds: Int = 60,
    val isRunning: Boolean = false
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

    fun updateWorkSeconds(seconds: Int) { _uiState.update { it.copy(workSeconds = seconds) } }
    fun updateRestSeconds(seconds: Int) { _uiState.update { it.copy(restSeconds = seconds) } }
    fun updateTotalRounds(rounds: Int) { _uiState.update { it.copy(totalRounds = rounds) } }
    fun updateEmomMinutes(minutes: Int) { _uiState.update { it.copy(emomMinutes = minutes) } }
    fun updateCountdownSeconds(seconds: Int) { _uiState.update { it.copy(countdownInputSeconds = seconds) } }

    fun startTimer() {
        if (_uiState.value.isRunning) return
        wakeLockManager.acquire()
        _uiState.update { it.copy(isRunning = true) }

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
        val totalSeconds = _uiState.value.emomMinutes * 60
        var elapsed = _uiState.value.elapsedSeconds

        while (elapsed < totalSeconds) {
            val secondsInMinute = elapsed % 60
            val remaining = 60 - secondsInMinute

            if (secondsInMinute == 0) {
                restTimerNotifier.notifyUser(audioEnabled = true, hapticsEnabled = true)
                _uiState.update { it.copy(phase = TimerPhase.WORK, currentRound = elapsed / 60 + 1) }
            }

            _uiState.update { it.copy(displaySeconds = remaining, elapsedSeconds = elapsed) }
            delay(1000L)
            elapsed++
        }
        finishTimer()
    }

    private suspend fun runTabata() {
        val state = _uiState.value
        var round = state.currentRound
        val totalRounds = state.totalRounds

        while (round < totalRounds) {
            // WORK phase
            _uiState.update { it.copy(phase = TimerPhase.WORK, currentRound = round + 1) }
            restTimerNotifier.notifyUser(audioEnabled = true, hapticsEnabled = true)
            var workRemaining = _uiState.value.workSeconds
            while (workRemaining > 0 && _uiState.value.isRunning) {
                _uiState.update { it.copy(displaySeconds = workRemaining) }
                delay(1000L)
                workRemaining--
            }
            if (!_uiState.value.isRunning) return

            // REST phase
            _uiState.update { it.copy(phase = TimerPhase.REST) }
            restTimerNotifier.notifyUser(audioEnabled = true, hapticsEnabled = true)
            var restRemaining = _uiState.value.restSeconds
            while (restRemaining > 0 && _uiState.value.isRunning) {
                _uiState.update { it.copy(displaySeconds = restRemaining) }
                delay(1000L)
                restRemaining--
            }
            if (!_uiState.value.isRunning) return

            round++
        }
        finishTimer()
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

        _uiState.update { it.copy(phase = TimerPhase.WORK) }

        while (remaining > 0 && _uiState.value.isRunning) {
            _uiState.update { it.copy(displaySeconds = remaining) }
            delay(1000L)
            remaining--
        }
        if (_uiState.value.isRunning) {
            _uiState.update { it.copy(displaySeconds = 0) }
            finishTimer()
        }
    }

    private fun finishTimer() {
        restTimerNotifier.notifyUser(audioEnabled = true, hapticsEnabled = true)
        wakeLockManager.release()
        _uiState.update { it.copy(isRunning = false, phase = TimerPhase.IDLE) }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        wakeLockManager.release()
    }
}
