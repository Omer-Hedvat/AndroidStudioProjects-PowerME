package com.powerme.app.util

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TimerServiceState(
    val isRunning: Boolean = false,
    val remainingSeconds: Int = 0,
    val totalSeconds: Int = 0
)

/**
 * Bound service that hosts the active countdown timer coroutine.
 * Kept alive by the ViewModel's ServiceConnection (BIND_AUTO_CREATE) for the duration
 * of the workout — no foreground promotion needed.
 *
 * Clients bind via [TimerBinder] and subscribe to [timerState] for tick updates.
 */
class WorkoutTimerService : Service() {

    inner class TimerBinder : Binder() {
        fun getService(): WorkoutTimerService = this@WorkoutTimerService
    }

    private val binder = TimerBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var timerJob: Job? = null

    private val _timerState = MutableStateFlow(TimerServiceState())
    val timerState: StateFlow<TimerServiceState> = _timerState.asStateFlow()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        serviceScope.cancel()
    }

    /**
     * Start a countdown from [totalSeconds].
     * @param onTick Called on the main thread each second with the remaining seconds.
     * @param onFinish Called on the main thread when the timer reaches zero.
     */
    fun startTimer(
        totalSeconds: Int,
        onTick: ((Int) -> Unit)? = null,
        onFinish: (() -> Unit)? = null
    ) {
        timerJob?.cancel()
        _timerState.value = TimerServiceState(
            isRunning = true,
            remainingSeconds = totalSeconds,
            totalSeconds = totalSeconds
        )

        timerJob = serviceScope.launch {
            for (remaining in totalSeconds downTo 0) {
                _timerState.value = _timerState.value.copy(remainingSeconds = remaining)
                withContext(Dispatchers.Main) { onTick?.invoke(remaining) }
                if (remaining > 0) delay(1000L)
            }
            _timerState.value = TimerServiceState()
            withContext(Dispatchers.Main) { onFinish?.invoke() }
        }
    }

    /** Stop the timer immediately. */
    fun stopTimer() {
        timerJob?.cancel()
        _timerState.value = TimerServiceState()
    }

    /**
     * Pause the timer and return the remaining seconds at the moment of pause.
     * The caller is responsible for resuming via [startTimer] with the returned value.
     */
    fun pauseTimer(): Int {
        val remaining = _timerState.value.remainingSeconds
        timerJob?.cancel()
        _timerState.value = _timerState.value.copy(isRunning = false)
        return remaining
    }
}
