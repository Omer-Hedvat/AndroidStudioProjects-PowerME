package com.powerme.app.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the single rolling "Delta Summary" for War Room session memory.
 *
 * Each summary is a ≤150-word compact snapshot written by Gemini at session end.
 * It replaces the full chat history for the next session, preventing context
 * window degradation over long-running conversations.
 *
 * Storage: SharedPreferences (no DB migration needed — single rolling value)
 */
@Singleton
class SessionSummaryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val PREFS_NAME = "session_memory"
        private const val KEY_SUMMARY = "delta_summary"
        private const val KEY_SUMMARY_TIMESTAMP = "summary_timestamp"
    }

    private val prefs get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Returns the stored delta summary, or null if none exists. */
    fun getSummary(): String? = prefs.getString(KEY_SUMMARY, null)

    /** Returns the epoch-ms timestamp when the summary was last saved. */
    fun getSummaryTimestamp(): Long = prefs.getLong(KEY_SUMMARY_TIMESTAMP, 0L)

    /** Persists a new delta summary. Overwrites any previous summary. */
    fun saveSummary(summary: String) {
        prefs.edit()
            .putString(KEY_SUMMARY, summary.trim())
            .putLong(KEY_SUMMARY_TIMESTAMP, System.currentTimeMillis())
            .apply()
    }

    /** Returns true if a summary exists. */
    fun hasSummary(): Boolean = getSummary()?.isNotBlank() == true

    /** Clears the stored summary (e.g. on manual reset). */
    fun clearSummary() {
        prefs.edit()
            .remove(KEY_SUMMARY)
            .remove(KEY_SUMMARY_TIMESTAMP)
            .apply()
    }
}
