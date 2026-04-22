package com.powerme.app.util

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import timber.log.Timber

/**
 * Timber tree for release builds. Routes WARN+ logs to Crashlytics breadcrumbs and
 * records ERROR-level throwables as non-fatal exceptions so they appear in the
 * Crashlytics console without crashing the app.
 */
class CrashlyticsTree : Timber.Tree() {

    private val crashlytics = FirebaseCrashlytics.getInstance()

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority < Log.WARN) return
        crashlytics.log("${priorityLabel(priority)}/$tag: $message")
        if (t != null && priority >= Log.ERROR) {
            crashlytics.recordException(t)
        }
    }

    private fun priorityLabel(priority: Int) = when (priority) {
        Log.WARN   -> "W"
        Log.ERROR  -> "E"
        Log.ASSERT -> "A"
        else       -> "?"
    }
}
