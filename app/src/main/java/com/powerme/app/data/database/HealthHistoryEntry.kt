package com.powerme.app.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class HealthHistoryType(val displayName: String, val shortLabel: String = displayName) {
    INJURY("Injury"),
    SURGERY("Surgery"),
    CONDITION("Condition", "Cond."),
    RESTRICTION("Restriction", "Restrict."),
    OTHER("Other")
}

enum class HealthHistorySeverity(val displayName: String) {
    MILD("Mild"),
    MODERATE("Moderate"),
    SEVERE("Severe"),
    RESOLVED("Resolved")
}

@Entity(tableName = "health_history_entries")
data class HealthHistoryEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val type: String = HealthHistoryType.INJURY.name,
    val title: String,
    val bodyRegion: String? = null,
    val severity: String = HealthHistorySeverity.MODERATE.name,
    val startDate: Long? = null,         // epoch millis
    val resolvedDate: Long? = null,      // epoch millis; non-null when RESOLVED
    val notes: String? = null,
    val affectedExerciseIds: String? = null, // comma-separated exercise IDs (future)
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val isArchived: Int = 0,
    @ColumnInfo(defaultValue = "")
    val firestoreId: String = "",
    @ColumnInfo(defaultValue = "0")
    val lastModifiedAt: Long = 0L
)
