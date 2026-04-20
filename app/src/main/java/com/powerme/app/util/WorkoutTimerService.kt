package com.powerme.app.util

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.powerme.app.notification.WorkoutNotificationManager
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
 * Bound foreground service that hosts the active countdown timer coroutine.
 * Promoted to foreground on workout start so the timer survives app backgrounding.
 *
 * Clients bind via [TimerBinder] and subscribe to [timerState] for tick updates.
 * WorkoutViewModel sets [onSkipRestRequested] and [onFinishWorkoutRequested] after binding
 * so notification action buttons can invoke ViewModel functions.
 */
class WorkoutTimerService : Service() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkoutTimerServiceEntryPoint {
        fun workoutNotificationManager(): WorkoutNotificationManager
    }

    inner class TimerBinder : Binder() {
        fun getService(): WorkoutTimerService = this@WorkoutTimerService
    }

    private val binder = TimerBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var timerJob: Job? = null

    private val _timerState = MutableStateFlow(TimerServiceState())
    val timerState: StateFlow<TimerServiceState> = _timerState.asStateFlow()

    /** Set by WorkoutViewModel after binding. Called when user taps "Skip Rest" in notification. */
    var onSkipRestRequested: (() -> Unit)? = null

    /** Set by WorkoutViewModel after binding. Called when user taps "Finish Workout" in notification. */
    var onFinishWorkoutRequested: (() -> Unit)? = null

    private lateinit var notificationManager: WorkoutNotificationManager

    override fun onCreate() {
        super.onCreate()
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WorkoutTimerServiceEntryPoint::class.java
        )
        notificationManager = entryPoint.workoutNotificationManager()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            WorkoutNotificationManager.ACTION_START_FOREGROUND -> {
                val workoutName = intent.getStringExtra(WorkoutNotificationManager.EXTRA_WORKOUT_NAME) ?: "Workout"
                val startTime = intent.getLongExtra(EXTRA_START_TIME, System.currentTimeMillis())
                startForeground(
                    WorkoutNotificationManager.NOTIFICATION_ID_PERSISTENT,
                    notificationManager.buildPersistentNotification(workoutName, startTime)
                )
            }
            WorkoutNotificationManager.ACTION_SKIP_REST -> {
                onSkipRestRequested?.invoke()
            }
            WorkoutNotificationManager.ACTION_FINISH_WORKOUT -> {
                onFinishWorkoutRequested?.invoke()
            }
        }
        return START_NOT_STICKY
    }

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

    /** Remove the foreground notification and demote back to background service. */
    fun stopForegroundAndRemove() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    companion object {
        const val EXTRA_START_TIME = "extra_start_time"
    }
}
