package com.powerme.app.data.database

import android.content.Context
import android.content.SharedPreferences
import timber.log.Timber
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*

/**
 * Validates database migrations to ensure zero data loss.
 *
 * Strategy:
 * 1. Capture snapshot of row counts before migration
 * 2. Perform migration
 * 3. Validate row counts after migration
 * 4. Rollback if validation fails
 *
 * Usage:
 * ```kotlin
 * val validator = PreMigrationValidator(context)
 * val snapshot = validator.captureSnapshot(database)
 * // ... migration happens ...
 * val isValid = validator.validatePostMigration(database, snapshot)
 * if (!isValid) validator.rollbackIfNeeded()
 * ```
 */
class PreMigrationValidator(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        "migration_snapshots",
        Context.MODE_PRIVATE
    )

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    companion object {
        private const val TAG = "PreMigrationValidator"
        private const val KEY_LAST_SNAPSHOT = "last_snapshot"
        private const val KEY_LAST_SNAPSHOT_VERSION = "last_snapshot_version"
    }

    /**
     * Captures a snapshot of the database state before migration.
     *
     * @param database The SupportSQLiteDatabase instance
     * @param fromVersion The current database version
     * @return MigrationSnapshot containing row counts for all tables
     */
    fun captureSnapshot(database: SupportSQLiteDatabase, fromVersion: Int): MigrationSnapshot {
        Timber.d("Capturing snapshot for version $fromVersion")

        val snapshot = MigrationSnapshot(
            version = fromVersion,
            timestamp = System.currentTimeMillis(),
            workoutCount = getTableCount(database, "workouts"),
            workoutSetCount = getTableCount(database, "workout_sets"),
            exerciseCount = getTableCount(database, "exercises"),
            routineCount = getTableCount(database, "routines"),
            healthStatsCount = getTableCount(database, "health_stats"),
            warmupLogCount = getTableCount(database, "warmup_log"),
            healthConnectSyncCount = getTableCount(database, "health_connect_sync")
        )

        // Save snapshot to SharedPreferences for recovery
        saveSnapshot(snapshot)

        Timber.d("Snapshot captured: $snapshot")
        return snapshot
    }

    /**
     * Validates database state after migration.
     *
     * @param database The migrated database
     * @param snapshot The pre-migration snapshot
     * @param toVersion The target database version
     * @param allowNewTables If true, allows new tables with zero rows
     * @return true if validation passes, false otherwise
     */
    fun validatePostMigration(
        database: SupportSQLiteDatabase,
        snapshot: MigrationSnapshot,
        toVersion: Int,
        allowNewTables: Boolean = true
    ): Boolean {
        Timber.d("Validating migration from v${snapshot.version} to v$toVersion")

        val postSnapshot = MigrationSnapshot(
            version = toVersion,
            timestamp = System.currentTimeMillis(),
            workoutCount = getTableCount(database, "workouts"),
            workoutSetCount = getTableCount(database, "workout_sets"),
            exerciseCount = getTableCount(database, "exercises"),
            routineCount = getTableCount(database, "routines"),
            healthStatsCount = getTableCount(database, "health_stats"),
            warmupLogCount = getTableCount(database, "warmup_log"),
            healthConnectSyncCount = getTableCount(database, "health_connect_sync")
        )

        // Validate critical tables (should never lose data)
        val validationResults = mutableListOf<ValidationResult>()

        validationResults.add(validateTable("workouts", snapshot.workoutCount, postSnapshot.workoutCount))
        validationResults.add(validateTable("workout_sets", snapshot.workoutSetCount, postSnapshot.workoutSetCount))
        validationResults.add(validateTable("exercises", snapshot.exerciseCount, postSnapshot.exerciseCount))
        validationResults.add(validateTable("routines", snapshot.routineCount, postSnapshot.routineCount))
        // Optional tables (may be added in new versions)
        if (!allowNewTables || snapshot.healthStatsCount > 0) {
            validationResults.add(validateTable("health_stats", snapshot.healthStatsCount, postSnapshot.healthStatsCount))
        }
        if (!allowNewTables || snapshot.warmupLogCount > 0) {
            validationResults.add(validateTable("warmup_log", snapshot.warmupLogCount, postSnapshot.warmupLogCount))
        }
        if (!allowNewTables || snapshot.healthConnectSyncCount > 0) {
            validationResults.add(validateTable("health_connect_sync", snapshot.healthConnectSyncCount, postSnapshot.healthConnectSyncCount))
        }

        val allValid = validationResults.all { it.isValid }

        if (allValid) {
            Timber.i("✅ Migration validation PASSED (v${snapshot.version} → v$toVersion)")
            clearSnapshot() // Clear old snapshot on success
        } else {
            Timber.e("❌ Migration validation FAILED (v${snapshot.version} → v$toVersion)")
            validationResults.filter { !it.isValid }.forEach { result ->
                Timber.e("  - ${result.tableName}: Expected ${result.expectedCount}, Found ${result.actualCount}")
            }
        }

        return allValid
    }

    /**
     * Attempts to rollback the database by deleting the database file.
     *
     * WARNING: This is a destructive operation. Only call if migration validation fails
     * and you have a backup strategy in place.
     *
     * @return true if rollback was initiated, false otherwise
     */
    fun rollbackIfNeeded(): Boolean {
        val lastSnapshot = getLastSnapshot()

        if (lastSnapshot == null) {
            Timber.w("No snapshot found for rollback")
            return false
        }

        Timber.w("⚠️ ROLLBACK INITIATED - Deleting database to force recreation from backup")
        Timber.w("Last valid snapshot: $lastSnapshot")

        try {
            // Close all database connections
            // Note: In production, you'd want to ensure Room database is closed first
            val dbFile = context.getDatabasePath("powerme_database")

            if (dbFile.exists()) {
                val deleted = dbFile.delete()
                if (deleted) {
                    Timber.i("Database file deleted successfully")

                    // Also delete journal files
                    val shmFile = context.getDatabasePath("powerme_database-shm")
                    val walFile = context.getDatabasePath("powerme_database-wal")
                    shmFile?.delete()
                    walFile?.delete()

                    return true
                } else {
                    Timber.e("Failed to delete database file")
                    return false
                }
            } else {
                Timber.w("Database file not found")
                return false
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during rollback")
            return false
        }
    }

    /**
     * Gets the row count for a specific table.
     *
     * @param database The database instance
     * @param tableName The table name
     * @return Row count, or 0 if table doesn't exist
     */
    private fun getTableCount(database: SupportSQLiteDatabase, tableName: String): Int {
        return try {
            val cursor = database.query("SELECT COUNT(*) FROM $tableName")
            cursor.use {
                if (it.moveToFirst()) {
                    it.getInt(0)
                } else {
                    0
                }
            }
        } catch (e: Exception) {
            // Table might not exist yet
            Timber.d("Table '$tableName' not found (might be new): ${e.message}")
            0
        }
    }

    /**
     * Validates that a table's row count hasn't decreased.
     *
     * @param tableName The table name
     * @param expectedCount The expected minimum count
     * @param actualCount The actual count after migration
     * @return ValidationResult
     */
    private fun validateTable(
        tableName: String,
        expectedCount: Int,
        actualCount: Int
    ): ValidationResult {
        val isValid = actualCount >= expectedCount

        return ValidationResult(
            tableName = tableName,
            expectedCount = expectedCount,
            actualCount = actualCount,
            isValid = isValid
        )
    }

    /**
     * Saves snapshot to SharedPreferences for recovery.
     */
    private fun saveSnapshot(snapshot: MigrationSnapshot) {
        try {
            val snapshotJson = json.encodeToString(snapshot)
            prefs.edit()
                .putString(KEY_LAST_SNAPSHOT, snapshotJson)
                .putInt(KEY_LAST_SNAPSHOT_VERSION, snapshot.version)
                .apply()
        } catch (e: Exception) {
            Timber.e(e, "Failed to save snapshot")
        }
    }

    /**
     * Retrieves the last saved snapshot.
     */
    private fun getLastSnapshot(): MigrationSnapshot? {
        return try {
            val snapshotJson = prefs.getString(KEY_LAST_SNAPSHOT, null)
            if (snapshotJson != null) {
                json.decodeFromString<MigrationSnapshot>(snapshotJson)
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to retrieve snapshot")
            null
        }
    }

    /**
     * Clears the saved snapshot after successful migration.
     */
    private fun clearSnapshot() {
        prefs.edit()
            .remove(KEY_LAST_SNAPSHOT)
            .remove(KEY_LAST_SNAPSHOT_VERSION)
            .apply()
    }

    /**
     * Generates a human-readable migration report.
     */
    fun generateMigrationReport(
        snapshot: MigrationSnapshot,
        database: SupportSQLiteDatabase,
        toVersion: Int
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val report = StringBuilder()

        report.appendLine("=== PowerME Migration Report ===")
        report.appendLine("Migration: v${snapshot.version} → v$toVersion")
        report.appendLine("Snapshot Time: ${dateFormat.format(Date(snapshot.timestamp))}")
        report.appendLine("")
        report.appendLine("PRE-MIGRATION COUNTS:")
        report.appendLine("  Workouts: ${snapshot.workoutCount}")
        report.appendLine("  Workout Sets: ${snapshot.workoutSetCount}")
        report.appendLine("  Exercises: ${snapshot.exerciseCount}")
        report.appendLine("  Routines: ${snapshot.routineCount}")
        report.appendLine("  Health Stats: ${snapshot.healthStatsCount}")
        report.appendLine("  Warmup Logs: ${snapshot.warmupLogCount}")
        report.appendLine("  Health Connect Syncs: ${snapshot.healthConnectSyncCount}")
        report.appendLine("")
        report.appendLine("POST-MIGRATION COUNTS:")
        report.appendLine("  Workouts: ${getTableCount(database, "workouts")}")
        report.appendLine("  Workout Sets: ${getTableCount(database, "workout_sets")}")
        report.appendLine("  Exercises: ${getTableCount(database, "exercises")}")
        report.appendLine("  Routines: ${getTableCount(database, "routines")}")
        report.appendLine("  Health Stats: ${getTableCount(database, "health_stats")}")
        report.appendLine("  Warmup Logs: ${getTableCount(database, "warmup_log")}")
        report.appendLine("  Health Connect Syncs: ${getTableCount(database, "health_connect_sync")}")
        report.appendLine("")
        report.appendLine("Status: ${if (validatePostMigration(database, snapshot, toVersion, allowNewTables = true)) "✅ PASSED" else "❌ FAILED"}")

        return report.toString()
    }
}

/**
 * Snapshot of database state at a specific version.
 */
@Serializable
data class MigrationSnapshot(
    val version: Int,
    val timestamp: Long,
    val workoutCount: Int,
    val workoutSetCount: Int,
    val exerciseCount: Int,
    val routineCount: Int,
    val healthStatsCount: Int,
    val warmupLogCount: Int,
    val healthConnectSyncCount: Int
)

/**
 * Result of validating a single table.
 */
private data class ValidationResult(
    val tableName: String,
    val expectedCount: Int,
    val actualCount: Int,
    val isValid: Boolean
)
