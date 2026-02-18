package com.omerhedvat.powerme.util

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.omerhedvat.powerme.BuildConfig
import com.omerhedvat.powerme.actions.ActionBlock
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Logs Gemini API responses for debugging and transparency.
 *
 * Features:
 * - Full response logging in debug builds
 * - Action parsing success/failure tracking
 * - Dev toast notifications for parsed actions
 * - Persistent log file for analysis
 *
 * Usage:
 * ```kotlin
 * val logger = GeminiResponseLogger(context)
 * logger.logResponse(
 *     prompt = userMessage,
 *     response = aiResponse,
 *     parsedActions = actions,
 *     parseErrors = errors
 * )
 * ```
 */
class GeminiResponseLogger(private val context: Context) {

    companion object {
        private const val TAG = "GeminiResponse"
        private const val LOG_FILE_NAME = "gemini_responses.log"
        private const val MAX_LOG_SIZE = 5 * 1024 * 1024 // 5MB
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val logFile: File = File(context.filesDir, LOG_FILE_NAME)

    /**
     * Logs a Gemini API response with parsing results.
     *
     * @param prompt The user's prompt/message
     * @param response The Gemini API response text
     * @param parsedActions List of successfully parsed actions
     * @param parseErrors List of parsing errors (if any)
     * @param showToast Whether to show a dev toast (default: debug builds only)
     */
    fun logResponse(
        prompt: String,
        response: String,
        parsedActions: List<ActionBlock> = emptyList(),
        parseErrors: List<String> = emptyList(),
        showToast: Boolean = BuildConfig.DEBUG
    ) {
        val timestamp = dateFormat.format(Date())

        // Build log entry
        val logEntry = buildString {
            appendLine("=" .repeat(80))
            appendLine("TIMESTAMP: $timestamp")
            appendLine("PROMPT: ${prompt.take(200)}${if (prompt.length > 200) "..." else ""}")
            appendLine("-".repeat(80))
            appendLine("RESPONSE:")
            appendLine(response)
            appendLine("-".repeat(80))
            appendLine("PARSED ACTIONS: ${parsedActions.size}")
            parsedActions.forEachIndexed { index, action ->
                appendLine("  [$index] ${action::class.simpleName}: $action")
            }
            if (parseErrors.isNotEmpty()) {
                appendLine("PARSE ERRORS: ${parseErrors.size}")
                parseErrors.forEach { error ->
                    appendLine("  - $error")
                }
            }
            appendLine("=" .repeat(80))
            appendLine()
        }

        // Log to Logcat (debug builds only)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Gemini Response Logged")
            Log.d(TAG, "Prompt: ${prompt.take(100)}")
            Log.d(TAG, "Response length: ${response.length} chars")
            Log.d(TAG, "Actions parsed: ${parsedActions.size}")
            if (parseErrors.isNotEmpty()) {
                Log.w(TAG, "Parse errors: ${parseErrors.size}")
                parseErrors.forEach { Log.w(TAG, "  - $it") }
            }
            parsedActions.forEach { action ->
                Log.d(TAG, "Action: ${action::class.simpleName} - $action")
            }
        }

        // Write to persistent log file
        writeToLogFile(logEntry)

        // Show dev toast if requested
        if (showToast && parsedActions.isNotEmpty()) {
            showDevToast(parsedActions.size)
        }
    }

    /**
     * Logs a parsing failure.
     *
     * @param response The Gemini response that failed to parse
     * @param exception The exception that occurred during parsing
     */
    fun logParseFailure(response: String, exception: Exception) {
        val timestamp = dateFormat.format(Date())

        val logEntry = buildString {
            appendLine("=" .repeat(80))
            appendLine("TIMESTAMP: $timestamp")
            appendLine("PARSE FAILURE")
            appendLine("-".repeat(80))
            appendLine("RESPONSE:")
            appendLine(response)
            appendLine("-".repeat(80))
            appendLine("EXCEPTION: ${exception.javaClass.simpleName}")
            appendLine("MESSAGE: ${exception.message}")
            appendLine("STACK TRACE:")
            appendLine(exception.stackTraceToString())
            appendLine("=" .repeat(80))
            appendLine()
        }

        if (BuildConfig.DEBUG) {
            Log.e(TAG, "Parse failure", exception)
        }

        writeToLogFile(logEntry)
    }

    /**
     * Retrieves the full log content for analysis.
     *
     * @param maxLines Maximum number of lines to retrieve (default: all)
     * @return Log content as string
     */
    fun getLogContent(maxLines: Int? = null): String {
        return if (logFile.exists()) {
            val allLines = logFile.readLines()
            if (maxLines != null && maxLines < allLines.size) {
                allLines.takeLast(maxLines).joinToString("\n")
            } else {
                allLines.joinToString("\n")
            }
        } else {
            "No logs available"
        }
    }

    /**
     * Clears the log file.
     */
    fun clearLogs() {
        if (logFile.exists()) {
            logFile.delete()
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Logs cleared")
        }
    }

    /**
     * Gets log file statistics.
     *
     * @return LogStats with file size, line count, etc.
     */
    fun getLogStats(): LogStats {
        return if (logFile.exists()) {
            val lines = logFile.readLines()
            val responseCount = lines.count { it.startsWith("TIMESTAMP:") }
            val actionCount = lines.count { it.trim().startsWith("[") && it.contains("]") }
            val errorCount = lines.count { it.contains("PARSE ERRORS:") || it.contains("PARSE FAILURE") }

            LogStats(
                fileSize = logFile.length(),
                lineCount = lines.size,
                responseCount = responseCount,
                actionCount = actionCount,
                errorCount = errorCount,
                lastModified = logFile.lastModified()
            )
        } else {
            LogStats(0, 0, 0, 0, 0, 0)
        }
    }

    /**
     * Writes log entry to file with size management.
     */
    private fun writeToLogFile(entry: String) {
        try {
            // Check file size and rotate if needed
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
                rotateLogs()
            }

            logFile.appendText(entry)
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Failed to write to log file", e)
            }
        }
    }

    /**
     * Rotates log files when size limit is reached.
     */
    private fun rotateLogs() {
        try {
            val archiveFile = File(context.filesDir, "gemini_responses_old.log")

            // Delete old archive if it exists
            if (archiveFile.exists()) {
                archiveFile.delete()
            }

            // Rename current log to archive
            logFile.renameTo(archiveFile)

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Logs rotated (size: ${archiveFile.length()} bytes)")
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Failed to rotate logs", e)
            }
        }
    }

    /**
     * Shows a development toast notification.
     */
    private fun showDevToast(actionCount: Int) {
        try {
            val message = when (actionCount) {
                1 -> "✓ 1 action parsed"
                else -> "✓ $actionCount actions parsed"
            }

            Toast.makeText(
                context,
                message,
                Toast.LENGTH_SHORT
            ).show()
        } catch (e: Exception) {
            // Ignore toast errors (might be called from background thread)
            if (BuildConfig.DEBUG) {
                Log.w(TAG, "Could not show toast", e)
            }
        }
    }

    /**
     * Exports logs to external storage for sharing/analysis.
     *
     * @return File path of exported log, or null if export failed
     */
    fun exportLogs(): String? {
        return try {
            val exportFile = File(context.getExternalFilesDir(null), "powerme_gemini_logs_export.txt")

            if (logFile.exists()) {
                logFile.copyTo(exportFile, overwrite = true)
                exportFile.absolutePath
            } else {
                null
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Failed to export logs", e)
            }
            null
        }
    }
}

/**
 * Statistics about the log file.
 */
data class LogStats(
    val fileSize: Long,
    val lineCount: Int,
    val responseCount: Int,
    val actionCount: Int,
    val errorCount: Int,
    val lastModified: Long
) {
    val fileSizeKB: Double get() = fileSize / 1024.0
    val fileSizeMB: Double get() = fileSizeKB / 1024.0
}
