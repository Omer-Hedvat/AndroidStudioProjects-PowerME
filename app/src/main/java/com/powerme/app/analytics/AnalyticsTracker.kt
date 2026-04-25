package com.powerme.app.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin injectable wrapper around Firebase Analytics.
 *
 * All key workout lifecycle and navigation events are logged here so that
 * beta testers' action trails can be replayed in the Firebase console to
 * diagnose bugs that do not crash the app.
 *
 * Note: weight, reps, and RPE values are intentionally omitted from all events
 * to protect tester privacy.
 */
@Singleton
class AnalyticsTracker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val analytics: FirebaseAnalytics = Firebase.analytics

    fun logWorkoutStarted(routineId: String, exerciseCount: Int) {
        val source = if (routineId.isBlank()) "quick" else if (routineId == "ai") "ai" else "routine"
        Timber.i("workout_started source=$source exercises=$exerciseCount")
        analytics.logEvent("workout_started", Bundle().apply {
            putString("source", source)
            putInt("exercise_count", exerciseCount)
        })
    }

    fun logWorkoutFinished(durationMinutes: Int, totalSets: Int, exerciseCount: Int) {
        Timber.i("workout_finished duration=${durationMinutes}m sets=$totalSets exercises=$exerciseCount")
        analytics.logEvent("workout_finished", Bundle().apply {
            putInt("duration_minutes", durationMinutes)
            putInt("total_sets", totalSets)
            putInt("exercise_count", exerciseCount)
        })
    }

    fun logWorkoutCancelled(setsLogged: Int) {
        Timber.i("workout_cancelled sets_logged=$setsLogged")
        analytics.logEvent("workout_cancelled", Bundle().apply {
            putInt("sets_logged", setsLogged)
        })
    }

    fun logRestTimerStarted(durationSeconds: Int) {
        Timber.d("rest_timer_started duration=${durationSeconds}s")
        analytics.logEvent("rest_timer_started", Bundle().apply {
            putInt("duration_seconds", durationSeconds)
        })
    }

    fun logRestTimerSkipped(remainingSeconds: Int) {
        Timber.d("rest_timer_skipped remaining=${remainingSeconds}s")
        analytics.logEvent("rest_timer_skipped", Bundle().apply {
            putInt("remaining_seconds", remainingSeconds)
        })
    }

    fun logWorkoutSetConfirmed(exerciseId: Long, setType: String, setIndex: Int) {
        Timber.d("workout_set_confirmed exId=$exerciseId type=$setType idx=$setIndex")
        analytics.logEvent("workout_set_confirmed", Bundle().apply {
            putLong("exercise_id", exerciseId)
            putString("set_type", setType)
            putInt("set_index", setIndex)
        })
    }

    fun logWorkoutResumed(workoutId: String?) {
        Timber.i("workout_resumed wId=${workoutId?.take(8)}")
        analytics.logEvent("workout_resumed", Bundle().apply {
            if (workoutId != null) putString("workout_id", workoutId)
        })
    }

    fun logScreenViewed(screenName: String) {
        Timber.d("screen_viewed screen=$screenName")
        analytics.logEvent("screen_viewed", Bundle().apply {
            putString("screen_name", screenName)
        })
    }

    fun logNavTabSelected(tabName: String) {
        Timber.i("nav_tab_selected tab=$tabName")
        analytics.logEvent("nav_tab_selected", Bundle().apply {
            putString("tab_name", tabName)
        })
    }

    fun logAiGeneration(backend: String, exerciseCount: Int) {
        Timber.i("ai_generation backend=$backend exercises=$exerciseCount")
        analytics.logEvent("ai_generation", Bundle().apply {
            putString("backend", backend)
            putInt("exercise_count", exerciseCount)
        })
    }

    fun logSynonymSaved(rawName: String, resolvedExerciseName: String) {
        Timber.i("synonym_saved raw=$rawName resolved=$resolvedExerciseName")
        analytics.logEvent("synonym_saved", Bundle().apply {
            putString("raw_name", rawName)
            putString("resolved_exercise", resolvedExerciseName)
        })
    }
}
