package com.powerme.app.util.timer

import com.powerme.app.util.AlertType
import com.powerme.app.util.RestTimerNotifier
import com.powerme.app.util.TimerSound
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update

interface TimerEngine {
    val state: StateFlow<TimerEngineState>
    suspend fun run(spec: TimerSpec, setupSeconds: Int = 0)
    fun pause()
    fun resume()
    fun stop()
    fun addSeconds(delta: Int)
}

class TimerEngineImpl(
    private val notifier: RestTimerNotifier,
    private val getSound: () -> TimerSound
) : TimerEngine {

    private val _state = MutableStateFlow(TimerEngineState())
    override val state: StateFlow<TimerEngineState> = _state.asStateFlow()

    private val _isPaused = MutableStateFlow(false)

    override suspend fun run(spec: TimerSpec, setupSeconds: Int) {
        _isPaused.value = false
        _state.update { it.copy(isRunning = true, phase = TimerPhase.IDLE) }
        try {
            if (!runSetup(setupSeconds)) return
            when (spec) {
                is TimerSpec.Emom      -> runEmom(spec)
                is TimerSpec.Tabata    -> runTabata(spec)
                is TimerSpec.Countdown -> runCountdown(spec)
                is TimerSpec.Stopwatch -> runStopwatch()
                is TimerSpec.Amrap     -> runAmrap(spec)
                is TimerSpec.Rft       -> runRft(spec)
            }
            notifier.triggerAudioAlert(AlertType.FINISH, getSound())
        } finally {
            _state.update { it.copy(isRunning = false, phase = TimerPhase.IDLE, tickEpochMs = 0L) }
        }
    }

    override fun pause() {
        _isPaused.value = true
        _state.update { it.copy(isRunning = false) }
    }

    override fun resume() {
        _isPaused.value = false
        _state.update { it.copy(isRunning = true) }
    }

    override fun stop() {
        _isPaused.value = false
        _state.value = TimerEngineState()
    }

    override fun addSeconds(delta: Int) {
        _state.update { it.copy(displaySeconds = (it.displaySeconds + delta).coerceAtLeast(0)) }
    }

    private suspend fun awaitNotPaused() {
        _isPaused.first { !it }
    }

    private suspend fun runSetup(setupSeconds: Int): Boolean {
        if (setupSeconds <= 0) return true
        _state.update { it.copy(phase = TimerPhase.SETUP, phaseTotalSeconds = setupSeconds) }
        var remaining = setupSeconds
        while (remaining > 0) {
            awaitNotPaused()
            _state.update { it.copy(displaySeconds = remaining) }
            notifier.triggerAudioAlert(AlertType.COUNTDOWN_TICK, getSound())
            delay(1000L)
            remaining--
        }
        _state.update { it.copy(displaySeconds = 0) }
        notifier.triggerAudioAlert(AlertType.ROUND_START, getSound())
        return true
    }

    private suspend fun runEmom(spec: TimerSpec.Emom) {
        val totalRounds = spec.totalDurationSeconds / spec.intervalSeconds
        _state.update {
            it.copy(phase = TimerPhase.WORK, totalRounds = totalRounds, phaseTotalSeconds = spec.intervalSeconds)
        }
        for (round in 1..totalRounds) {
            _state.update { it.copy(currentRound = round) }
            notifier.triggerAudioAlert(AlertType.ROUND_START, getSound())
            var remaining = spec.intervalSeconds
            var warnedThisRound = false
            while (remaining > 0) {
                awaitNotPaused()
                val elapsed = (round - 1) * spec.intervalSeconds + (spec.intervalSeconds - remaining)
                _state.update {
                    it.copy(
                        displaySeconds = remaining,
                        elapsedSeconds = elapsed,
                        tickEpochMs = System.currentTimeMillis()
                    )
                }
                if (spec.warnAtSeconds != null && remaining == spec.warnAtSeconds && !warnedThisRound) {
                    warnedThisRound = true
                    notifier.triggerAudioAlert(AlertType.WARNING, getSound())
                }
                if (remaining in 1..3) notifier.triggerAudioAlert(AlertType.COUNTDOWN_TICK, getSound())
                delay(1000L)
                remaining--
            }
        }
    }

    private suspend fun runTabata(spec: TimerSpec.Tabata) {
        _state.update { it.copy(totalRounds = spec.rounds) }
        for (round in 0 until spec.rounds) {
            val isLastRound = round == spec.rounds - 1

            _state.update {
                it.copy(phase = TimerPhase.WORK, currentRound = round + 1, phaseTotalSeconds = spec.workSeconds)
            }
            notifier.triggerAudioAlert(AlertType.ROUND_START, getSound())
            var workRemaining = spec.workSeconds
            var warnedWork = false
            while (workRemaining > 0) {
                awaitNotPaused()
                _state.update { it.copy(displaySeconds = workRemaining, tickEpochMs = System.currentTimeMillis()) }
                if (spec.workWarnAtSeconds != null && workRemaining == spec.workWarnAtSeconds && !warnedWork) {
                    warnedWork = true
                    notifier.triggerAudioAlert(AlertType.WARNING, getSound())
                }
                if (workRemaining in 1..3) notifier.triggerAudioAlert(AlertType.COUNTDOWN_TICK, getSound())
                delay(1000L)
                workRemaining--
            }
            notifier.triggerAudioAlert(AlertType.FINISH, getSound())

            if (!(isLastRound && spec.skipLastRest)) {
                _state.update { it.copy(phase = TimerPhase.REST, phaseTotalSeconds = spec.restSeconds) }
                notifier.triggerAudioAlert(AlertType.ROUND_START, getSound())
                var restRemaining = spec.restSeconds
                var warnedRest = false
                while (restRemaining > 0) {
                    awaitNotPaused()
                    _state.update { it.copy(displaySeconds = restRemaining, tickEpochMs = System.currentTimeMillis()) }
                    if (spec.restWarnAtSeconds != null && restRemaining == spec.restWarnAtSeconds && !warnedRest) {
                        warnedRest = true
                        notifier.triggerAudioAlert(AlertType.WARNING, getSound())
                    }
                    if (restRemaining in 1..3) notifier.triggerAudioAlert(AlertType.COUNTDOWN_TICK, getSound())
                    delay(1000L)
                    restRemaining--
                }
                notifier.triggerAudioAlert(AlertType.FINISH, getSound())
            }
        }
    }

    private suspend fun runCountdown(spec: TimerSpec.Countdown) {
        _state.update {
            it.copy(phase = TimerPhase.WORK, totalRounds = 0, phaseTotalSeconds = spec.durationSeconds)
        }
        var remaining = spec.durationSeconds
        var warned = false
        while (remaining > 0) {
            awaitNotPaused()
            _state.update { it.copy(displaySeconds = remaining, tickEpochMs = System.currentTimeMillis()) }
            if (spec.warnAtSeconds != null && remaining == spec.warnAtSeconds && !warned) {
                warned = true
                notifier.triggerAudioAlert(AlertType.WARNING, getSound())
            }
            if (remaining in 1..3) notifier.triggerAudioAlert(AlertType.COUNTDOWN_TICK, getSound())
            delay(1000L)
            remaining--
        }
        _state.update { it.copy(displaySeconds = 0) }
    }

    private suspend fun runStopwatch() {
        _state.update { it.copy(phase = TimerPhase.WORK, totalRounds = 0, phaseTotalSeconds = 0) }
        var elapsed = _state.value.elapsedSeconds
        while (true) {
            awaitNotPaused()
            _state.update {
                it.copy(elapsedSeconds = elapsed, displaySeconds = elapsed, tickEpochMs = System.currentTimeMillis())
            }
            delay(1000L)
            elapsed++
        }
    }

    private suspend fun runAmrap(spec: TimerSpec.Amrap) {
        _state.update { it.copy(phase = TimerPhase.WORK, totalRounds = 0, phaseTotalSeconds = spec.durationSeconds) }
        var remaining = spec.durationSeconds
        while (remaining > 0) {
            awaitNotPaused()
            _state.update { it.copy(displaySeconds = remaining, tickEpochMs = System.currentTimeMillis()) }
            if (remaining in 1..3) notifier.triggerAudioAlert(AlertType.COUNTDOWN_TICK, getSound())
            delay(1000L)
            remaining--
        }
        _state.update { it.copy(displaySeconds = 0) }
    }

    private suspend fun runRft(spec: TimerSpec.Rft) {
        _state.update { it.copy(phase = TimerPhase.WORK, totalRounds = spec.targetRounds, phaseTotalSeconds = 0) }
        var elapsed = 0
        val cap = spec.capSeconds
        while (true) {
            awaitNotPaused()
            _state.update {
                it.copy(elapsedSeconds = elapsed, displaySeconds = elapsed, tickEpochMs = System.currentTimeMillis())
            }
            delay(1000L)
            elapsed++
            if (cap != null && elapsed >= cap) break
        }
    }
}
