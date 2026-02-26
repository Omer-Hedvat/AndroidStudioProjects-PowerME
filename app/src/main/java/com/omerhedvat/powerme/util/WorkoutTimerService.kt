package com.omerhedvat.powerme.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
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
 * ForegroundService that hosts the active countdown timer coroutine.
 * Prevents Android from killing the timer when the app is backgrounded.
 *
 * Clients bind via [TimerBinder] and subscribe to [timerState] for tick updates.
 * The foreground notification is posted on [startTimer] and removed on [stopTimer].
 */
class WorkoutTimerService : Service() {

    companion object {
        const val CHANNEL_ID = "timer_channel"
        const val NOTIFICATION_ID = 1001
    }

    inner class TimerBinder : Binder() {
        fun getService(): WorkoutTimerService = this@WorkoutTimerService
    }

    private val binder = TimerBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var timerJob: Job? = null

    private val _timerState = MutableStateFlow(TimerServiceState())
    val timerState: StateFlow<TimerServiceState> = _timerState.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

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
        startForeground(NOTIFICATION_ID, buildNotification(totalSeconds))

        timerJob = serviceScope.launch {
            for (remaining in totalSeconds downTo 0) {
                _timerState.value = _timerState.value.copy(remainingSeconds = remaining)
                updateNotification(remaining)
                withContext(Dispatchers.Main) { onTick?.invoke(remaining) }
                if (remaining > 0) delay(1000L)
            }
            _timerState.value = TimerServiceState()
            withContext(Dispatchers.Main) { onFinish?.invoke() }
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    /** Stop the timer immediately and remove the foreground notification. */
    fun stopTimer() {
        timerJob?.cancel()
        _timerState.value = TimerServiceState()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Workout Timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows remaining rest time during your workout"
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(remaining: Int): Notification {
        val min = remaining / 60
        val sec = remaining % 60
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Rest Timer")
            .setContentText("Remaining: %02d:%02d".format(min, sec))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(remaining: Int) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(remaining))
    }
}
