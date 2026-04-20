package com.powerme.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.powerme.app.MainActivity
import com.powerme.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages all workout-related notification channels, builders, and posting.
 *
 * Two channels:
 *  - CHANNEL_ACTIVE: LOW importance, ongoing persistent notification while workout is active.
 *  - CHANNEL_REST: HIGH importance, heads-up notification when rest timer ends.
 *
 * Audio/haptic feedback for timer events is intentionally NOT done here — RestTimerNotifier
 * already handles that. Both channels have sound/vibration disabled to prevent double-beeping.
 */
@Singleton
class WorkoutNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ACTIVE = "powerme_workout_active"
        const val CHANNEL_REST = "powerme_rest_timer"
        const val NOTIFICATION_ID_PERSISTENT = 1001
        const val NOTIFICATION_ID_REST_DONE = 1002

        const val ACTION_SKIP_REST = "com.powerme.app.ACTION_SKIP_REST"
        const val ACTION_FINISH_WORKOUT = "com.powerme.app.ACTION_FINISH_WORKOUT"
        const val ACTION_START_FOREGROUND = "com.powerme.app.ACTION_START_FOREGROUND"
        const val EXTRA_WORKOUT_NAME = "extra_workout_name"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val mainActivityIntent: PendingIntent by lazy {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private val skipRestIntent: PendingIntent by lazy {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_SKIP_REST
        }
        PendingIntent.getBroadcast(
            context, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private val finishWorkoutIntent: PendingIntent by lazy {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_FINISH_WORKOUT
        }
        PendingIntent.getBroadcast(
            context, 2, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun createChannels() {
        val activeChannel = NotificationChannel(
            CHANNEL_ACTIVE,
            "Active Workout",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows workout progress while exercising"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }

        val restChannel = NotificationChannel(
            CHANNEL_REST,
            "Rest Timer Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when rest period ends"
            enableVibration(false)
            setSound(null, null)
        }

        notificationManager.createNotificationChannel(activeChannel)
        notificationManager.createNotificationChannel(restChannel)
    }

    /**
     * Builds the persistent foreground notification shown while a workout is active.
     * Uses a Chronometer so elapsed time updates without per-second notify() calls.
     */
    fun buildPersistentNotification(
        workoutName: String,
        startTimeMs: Long = System.currentTimeMillis(),
        exerciseName: String? = null
    ): Notification {
        val content = exerciseName ?: "Workout in progress"
        return NotificationCompat.Builder(context, CHANNEL_ACTIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(workoutName)
            .setContentText(content)
            .setContentIntent(mainActivityIntent)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setWhen(startTimeMs)
            .setSilent(true)
            .addAction(0, "Finish Workout", finishWorkoutIntent)
            .build()
    }

    /**
     * Builds an updated persistent notification showing rest timer countdown.
     */
    fun buildRestTimerNotification(
        workoutName: String,
        exerciseName: String,
        setLabel: String,
        remainingSeconds: Int,
        totalSeconds: Int
    ): Notification {
        val mins = remainingSeconds / 60
        val secs = remainingSeconds % 60
        val countdown = if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
        return NotificationCompat.Builder(context, CHANNEL_ACTIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(workoutName)
            .setContentText("Rest $countdown — $setLabel of $exerciseName")
            .setContentIntent(mainActivityIntent)
            .setOngoing(true)
            .setProgress(totalSeconds, remainingSeconds, false)
            .setSilent(true)
            .addAction(0, "Skip Rest", skipRestIntent)
            .addAction(0, "Finish Workout", finishWorkoutIntent)
            .build()
    }

    /**
     * Posts a heads-up notification when the rest timer ends and the next set is ready.
     * Auto-cancels after 10 seconds. Bridges to WearOS/Galaxy Watch automatically.
     */
    fun postRestDoneNotification(
        exerciseName: String,
        setInfo: String,
        notificationsEnabled: Boolean
    ) {
        if (!notificationsEnabled) return
        val notification = NotificationCompat.Builder(context, CHANNEL_REST)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Rest Complete")
            .setContentText("$exerciseName — $setInfo")
            .setContentIntent(mainActivityIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(10_000)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(NOTIFICATION_ID_REST_DONE, notification)
    }

    /**
     * Posts a summary notification when the workout is finished.
     */
    fun postSummaryNotification(
        workoutName: String,
        durationText: String,
        sets: Int,
        notificationsEnabled: Boolean
    ) {
        if (!notificationsEnabled) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ACTIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Workout Complete")
            .setContentText("$workoutName — $durationText, $sets sets")
            .setContentIntent(mainActivityIntent)
            .setAutoCancel(true)
            .setSilent(true)
            .build()
        notificationManager.notify(NOTIFICATION_ID_PERSISTENT, notification)
    }

    fun updateNotification(notification: Notification) {
        notificationManager.notify(NOTIFICATION_ID_PERSISTENT, notification)
    }

    fun cancelRestDoneNotification() {
        notificationManager.cancel(NOTIFICATION_ID_REST_DONE)
    }

    fun cancelAll() {
        notificationManager.cancel(NOTIFICATION_ID_PERSISTENT)
        notificationManager.cancel(NOTIFICATION_ID_REST_DONE)
    }
}
