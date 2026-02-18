package com.omerhedvat.powerme.util

import android.content.Context
import com.omerhedvat.powerme.actions.ActionBlock
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*
import java.io.File

/**
 * Unit tests for GeminiResponseLogger.
 *
 * Tests logging functionality:
 * - Response logging
 * - Parse failure tracking
 * - Log file management
 * - Statistics generation
 *
 * Success Criteria: All 8 tests must pass before Phase 1.4 is complete.
 */
class GeminiResponseLoggerTest {

    private lateinit var logger: GeminiResponseLogger
    private lateinit var mockContext: Context
    private lateinit var tempLogFile: File

    @Before
    fun setup() {
        mockContext = mock(Context::class.java)

        // Create temp directory for test logs
        val tempDir = createTempDir("gemini_logs_test")
        tempLogFile = File(tempDir, "gemini_responses.log")

        `when`(mockContext.filesDir).thenReturn(tempDir)
        `when`(mockContext.getExternalFilesDir(null)).thenReturn(tempDir)

        logger = GeminiResponseLogger(mockContext)
    }

    /**
     * Test Case 1: Basic Response Logging
     */
    @Test
    fun testBasicResponseLogging() {
        val prompt = "Set current weight to 92kg"
        val response = "I'll update that for you.\n```json\n{\"action\": \"update_weight\", \"weightKg\": 92.0}\n```"
        val actions = listOf(ActionBlock.UpdateWeight(92.0))

        logger.logResponse(
            prompt = prompt,
            response = response,
            parsedActions = actions,
            showToast = false
        )

        // Verify log file was created
        assertTrue("Log file should exist", tempLogFile.exists())

        // Verify content
        val content = tempLogFile.readText()
        assertTrue("Should contain prompt", content.contains("Set current weight"))
        assertTrue("Should contain response", content.contains("I'll update that"))
        assertTrue("Should contain action count", content.contains("PARSED ACTIONS: 1"))
    }

    /**
     * Test Case 2: Multiple Actions Logging
     */
    @Test
    fun testMultipleActionsLogging() {
        val actions = listOf(
            ActionBlock.UpdateWeight(95.0),
            ActionBlock.SwitchGym("Home"),
            ActionBlock.UpdateInjury("LOWER_BACK", 7, "Sharp pain")
        )

        logger.logResponse(
            prompt = "Test prompt",
            response = "Test response",
            parsedActions = actions,
            showToast = false
        )

        val content = tempLogFile.readText()
        assertTrue("Should log 3 actions", content.contains("PARSED ACTIONS: 3"))
        assertTrue("Should log UpdateWeight", content.contains("UpdateWeight"))
        assertTrue("Should log SwitchGym", content.contains("SwitchGym"))
        assertTrue("Should log UpdateInjury", content.contains("UpdateInjury"))
    }

    /**
     * Test Case 3: Parse Error Logging
     */
    @Test
    fun testParseErrorLogging() {
        val errors = listOf(
            "Malformed JSON at line 5",
            "Missing required field: weightKg"
        )

        logger.logResponse(
            prompt = "Test",
            response = "Invalid response",
            parsedActions = emptyList(),
            parseErrors = errors,
            showToast = false
        )

        val content = tempLogFile.readText()
        assertTrue("Should contain error count", content.contains("PARSE ERRORS: 2"))
        assertTrue("Should contain first error", content.contains("Malformed JSON"))
        assertTrue("Should contain second error", content.contains("Missing required field"))
    }

    /**
     * Test Case 4: Parse Failure Exception Logging
     */
    @Test
    fun testParseFailureLogging() {
        val exception = IllegalArgumentException("Invalid action type")

        logger.logParseFailure(
            response = "Bad response data",
            exception = exception
        )

        val content = tempLogFile.readText()
        assertTrue("Should contain failure marker", content.contains("PARSE FAILURE"))
        assertTrue("Should contain exception type", content.contains("IllegalArgumentException"))
        assertTrue("Should contain exception message", content.contains("Invalid action type"))
    }

    /**
     * Test Case 5: Log Stats Generation
     */
    @Test
    fun testLogStatsGeneration() {
        // Log multiple responses
        repeat(3) { i ->
            logger.logResponse(
                prompt = "Prompt $i",
                response = "Response $i",
                parsedActions = listOf(ActionBlock.UpdateWeight(90.0 + i)),
                showToast = false
            )
        }

        val stats = logger.getLogStats()

        assertTrue("File size should be > 0", stats.fileSize > 0)
        assertTrue("Line count should be > 0", stats.lineCount > 0)
        assertEquals("Should have 3 responses", 3, stats.responseCount)
        assertTrue("Should have 3 actions", stats.actionCount >= 3)
        assertTrue("Last modified should be recent", System.currentTimeMillis() - stats.lastModified < 5000)
    }

    /**
     * Test Case 6: Clear Logs
     */
    @Test
    fun testClearLogs() {
        // Create log entry
        logger.logResponse(
            prompt = "Test",
            response = "Test",
            parsedActions = emptyList(),
            showToast = false
        )

        assertTrue("Log file should exist", tempLogFile.exists())

        // Clear logs
        logger.clearLogs()

        assertFalse("Log file should be deleted", tempLogFile.exists())
    }

    /**
     * Test Case 7: Get Log Content
     */
    @Test
    fun testGetLogContent() {
        val prompt = "Test prompt with unique identifier XYZ123"
        val response = "Test response with unique identifier ABC789"

        logger.logResponse(
            prompt = prompt,
            response = response,
            parsedActions = emptyList(),
            showToast = false
        )

        val content = logger.getLogContent()

        assertTrue("Content should contain prompt", content.contains("XYZ123"))
        assertTrue("Content should contain response", content.contains("ABC789"))
    }

    /**
     * Test Case 8: Export Logs
     */
    @Test
    fun testExportLogs() {
        // Create log entry
        logger.logResponse(
            prompt = "Export test",
            response = "Export test response",
            parsedActions = emptyList(),
            showToast = false
        )

        val exportPath = logger.exportLogs()

        assertNotNull("Export path should not be null", exportPath)
        assertTrue("Export file should exist", File(exportPath!!).exists())

        val exportedContent = File(exportPath).readText()
        assertTrue("Exported content should match", exportedContent.contains("Export test"))
    }

    /**
     * Test Case 9: Long Response Handling
     */
    @Test
    fun testLongResponseHandling() {
        val longPrompt = "A".repeat(10000)
        val longResponse = "B".repeat(50000)

        logger.logResponse(
            prompt = longPrompt,
            response = longResponse,
            parsedActions = emptyList(),
            showToast = false
        )

        assertTrue("Should handle long responses", tempLogFile.exists())
        val content = tempLogFile.readText()
        assertTrue("Should contain response", content.contains("RESPONSE:"))
    }

    /**
     * Test Case 10: Empty Stats for Non-existent Log
     */
    @Test
    fun testEmptyStatsForNonExistentLog() {
        // Don't create any logs
        val stats = logger.getLogStats()

        assertEquals("File size should be 0", 0L, stats.fileSize)
        assertEquals("Line count should be 0", 0, stats.lineCount)
        assertEquals("Response count should be 0", 0, stats.responseCount)
        assertEquals("Action count should be 0", 0, stats.actionCount)
        assertEquals("Error count should be 0", 0, stats.errorCount)
    }
}
