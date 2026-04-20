package com.powerme.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.powerme.app.util.WorkoutTimerService

/**
 * Handles notification action button taps (Skip Rest, Finish Workout).
 * Forwards the action to WorkoutTimerService, which then delegates to WorkoutViewModel
 * via the registered callback.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val serviceIntent = Intent(context, WorkoutTimerService::class.java).apply {
            action = intent.action
        }
        context.startService(serviceIntent)
    }
}
