package com.powerme.app.data.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

/**
 * Unit tests for PreMigrationValidator.
 *
 * Tests the migration validation safety system:
 * - Snapshot capture
 * - Post-migration validation
 * - Data loss detection
 *
 * Success Criteria: All tests must pass before proceeding to Phase 1.3.
 */
class PreMigrationValidatorTest {

    private lateinit var validator: PreMigrationValidator
    private lateinit var mockContext: Context
    private lateinit var mockDatabase: SupportSQLiteDatabase

    @Before
    fun setup() {
        mockContext = mock(Context::class.java)
        mockDatabase = mock(SupportSQLiteDatabase::class.java)

        // Mock SharedPreferences
        val mockSharedPrefs = mock(android.content.SharedPreferences::class.java)
        val mockEditor = mock(android.content.SharedPreferences.Editor::class.java)

        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockSharedPrefs)
        `when`(mockSharedPrefs.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
        `when`(mockEditor.putInt(anyString(), anyInt())).thenReturn(mockEditor)
        `when`(mockEditor.remove(anyString())).thenReturn(mockEditor)

        validator = PreMigrationValidator(mockContext)
    }

    /**
     * Test Case 1: Snapshot Capture
     * Verify that snapshot correctly captures table counts
     */
    @Test
    fun testSnapshotCapture() {
        // Mock table counts
        val mockWorkoutsCursor = mock(android.database.Cursor::class.java)
        `when`(mockWorkoutsCursor.moveToFirst()).thenReturn(true)
        `when`(mockWorkoutsCursor.getInt(0)).thenReturn(42)
        `when`(mockDatabase.query("SELECT COUNT(*) FROM workouts")).thenReturn(mockWorkoutsCursor)

        val mockExercisesCursor = mock(android.database.Cursor::class.java)
        `when`(mockExercisesCursor.moveToFirst()).thenReturn(true)
        `when`(mockExercisesCursor.getInt(0)).thenReturn(50)
        `when`(mockDatabase.query("SELECT COUNT(*) FROM exercises")).thenReturn(mockExercisesCursor)

        // Capture snapshot
        val snapshot = validator.captureSnapshot(mockDatabase, 7)

        // Verify
        assertEquals("Version should be 7", 7, snapshot.version)
        assertEquals("Workout count should be 42", 42, snapshot.workoutCount)
        assertEquals("Exercise count should be 50", 50, snapshot.exerciseCount)
        assertTrue("Timestamp should be recent", System.currentTimeMillis() - snapshot.timestamp < 1000)
    }

    /**
     * Test Case 2: MigrationSnapshot Serialization
     * Verify that snapshots can be serialized/deserialized correctly
     */
    @Test
    fun testSnapshotSerialization() {
        val originalSnapshot = MigrationSnapshot(
            version = 8,
            timestamp = System.currentTimeMillis(),
            workoutCount = 100,
            workoutSetCount = 500,
            exerciseCount = 50,
            routineCount = 10,
            chatMessageCount = 200,
            healthStatsCount = 30,
            warmupLogCount = 75,
            healthConnectSyncCount = 15
        )

        // Serialize
        val json = kotlinx.serialization.json.Json
        val jsonString = json.encodeToString(MigrationSnapshot.serializer(), originalSnapshot)

        // Deserialize
        val deserializedSnapshot = json.decodeFromString(MigrationSnapshot.serializer(), jsonString)

        // Verify all fields match
        assertEquals(originalSnapshot.version, deserializedSnapshot.version)
        assertEquals(originalSnapshot.timestamp, deserializedSnapshot.timestamp)
        assertEquals(originalSnapshot.workoutCount, deserializedSnapshot.workoutCount)
        assertEquals(originalSnapshot.workoutSetCount, deserializedSnapshot.workoutSetCount)
        assertEquals(originalSnapshot.exerciseCount, deserializedSnapshot.exerciseCount)
        assertEquals(originalSnapshot.routineCount, deserializedSnapshot.routineCount)
        assertEquals(originalSnapshot.chatMessageCount, deserializedSnapshot.chatMessageCount)
        assertEquals(originalSnapshot.healthStatsCount, deserializedSnapshot.healthStatsCount)
        assertEquals(originalSnapshot.warmupLogCount, deserializedSnapshot.warmupLogCount)
        assertEquals(originalSnapshot.healthConnectSyncCount, deserializedSnapshot.healthConnectSyncCount)
    }

    /**
     * Test Case 3: Validation Pass - No Data Loss
     * Verify that validation passes when row counts are preserved
     */
    @Test
    fun testValidationPass_NoDataLoss() {
        // Create snapshot
        val snapshot = MigrationSnapshot(
            version = 7,
            timestamp = System.currentTimeMillis(),
            workoutCount = 100,
            workoutSetCount = 500,
            exerciseCount = 50,
            routineCount = 10,
            chatMessageCount = 200,
            healthStatsCount = 30,
            warmupLogCount = 75,
            healthConnectSyncCount = 15
        )

        // Mock post-migration counts (same as pre-migration)
        setupMockCounts(100, 500, 50, 10, 200, 30, 75, 15)

        // Validate
        val isValid = validator.validatePostMigration(mockDatabase, snapshot, 8)

        // Should pass - no data loss
        assertTrue("Validation should pass when counts match", isValid)
    }

    /**
     * Test Case 4: Validation Pass - Increased Counts
     * Verify that validation passes when row counts increase (e.g., seeded data)
     */
    @Test
    fun testValidationPass_IncreasedCounts() {
        val snapshot = MigrationSnapshot(
            version = 7,
            timestamp = System.currentTimeMillis(),
            workoutCount = 100,
            workoutSetCount = 500,
            exerciseCount = 50,
            routineCount = 10,
            chatMessageCount = 200,
            healthStatsCount = 30,
            warmupLogCount = 75,
            healthConnectSyncCount = 15
        )

        // Mock post-migration counts (increased - e.g., seeded exercises)
        setupMockCounts(100, 500, 150, 10, 200, 30, 75, 15) // +100 exercises

        // Validate
        val isValid = validator.validatePostMigration(mockDatabase, snapshot, 8)

        // Should pass - counts can increase
        assertTrue("Validation should pass when counts increase", isValid)
    }

    /**
     * Test Case 5: Validation Fail - Data Loss Detected
     * Verify that validation fails when row counts decrease
     */
    @Test
    fun testValidationFail_DataLoss() {
        val snapshot = MigrationSnapshot(
            version = 7,
            timestamp = System.currentTimeMillis(),
            workoutCount = 100,
            workoutSetCount = 500,
            exerciseCount = 50,
            routineCount = 10,
            chatMessageCount = 200,
            healthStatsCount = 30,
            warmupLogCount = 75,
            healthConnectSyncCount = 15
        )

        // Mock post-migration counts (DECREASED - data loss!)
        setupMockCounts(95, 500, 50, 10, 200, 30, 75, 15) // Lost 5 workouts!

        // Validate
        val isValid = validator.validatePostMigration(mockDatabase, snapshot, 8)

        // Should fail - data loss detected
        assertFalse("Validation should fail when counts decrease", isValid)
    }

    /**
     * Test Case 6: Validation with New Tables
     * Verify that validation handles new tables gracefully
     */
    @Test
    fun testValidationWithNewTables() {
        val snapshot = MigrationSnapshot(
            version = 7,
            timestamp = System.currentTimeMillis(),
            workoutCount = 100,
            workoutSetCount = 500,
            exerciseCount = 50,
            routineCount = 10,
            chatMessageCount = 200,
            healthStatsCount = 0, // Table didn't exist
            warmupLogCount = 0,   // Table didn't exist
            healthConnectSyncCount = 0 // Table didn't exist
        )

        // Mock post-migration counts (new tables now have data)
        setupMockCounts(100, 500, 50, 10, 200, 30, 75, 15)

        // Validate with allowNewTables = true
        val isValid = validator.validatePostMigration(mockDatabase, snapshot, 8, allowNewTables = true)

        // Should pass - new tables are allowed
        assertTrue("Validation should pass when new tables are added", isValid)
    }

    /**
     * Helper: Setup mock cursors for all table counts
     */
    private fun setupMockCounts(
        workouts: Int,
        workoutSets: Int,
        exercises: Int,
        routines: Int,
        chatMessages: Int,
        healthStats: Int,
        warmupLogs: Int,
        healthConnectSyncs: Int
    ) {
        setupMockCursor("workouts", workouts)
        setupMockCursor("workout_sets", workoutSets)
        setupMockCursor("exercises", exercises)
        setupMockCursor("routines", routines)
        setupMockCursor("chat_messages", chatMessages)
        setupMockCursor("health_stats", healthStats)
        setupMockCursor("warmup_log", warmupLogs)
        setupMockCursor("health_connect_sync", healthConnectSyncs)
    }

    /**
     * Helper: Setup a mock cursor for a specific table
     */
    private fun setupMockCursor(tableName: String, count: Int) {
        val cursor = mock(android.database.Cursor::class.java)
        `when`(cursor.moveToFirst()).thenReturn(true)
        `when`(cursor.getInt(0)).thenReturn(count)
        `when`(mockDatabase.query("SELECT COUNT(*) FROM $tableName")).thenReturn(cursor)
    }
}
