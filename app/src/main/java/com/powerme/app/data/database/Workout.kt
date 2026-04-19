package com.powerme.app.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workouts",
    foreignKeys = [
        ForeignKey(
            entity = Routine::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["routineId"]),
        Index(value = ["isCompleted", "isArchived", "timestamp"]),  // covers WHERE isCompleted=1 AND isArchived=0 ORDER BY timestamp DESC
        Index(value = ["importBatchId"])  // v45 — fast lookup by import batch for undo
    ]
)
data class Workout(
    @PrimaryKey
    val id: String,                        // UUID string — no autoGenerate; caller pre-generates
    val routineId: String?,
    @ColumnInfo(defaultValue = "NULL")
    val routineName: String? = null,
    val timestamp: Long,
    val durationSeconds: Int,
    val totalVolume: Double,
    val notes: String? = null,
    val isCompleted: Boolean = false,
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 0L,
    val updatedAt: Long = 0L,              // v31 — LWW timestamp for Firestore sync
    val isArchived: Boolean = false,        // v31 — soft delete flag
    @ColumnInfo(defaultValue = "NULL")
    val sessionRating: Int? = null,         // v41 — 1–5 star session rating
    @ColumnInfo(defaultValue = "NULL")
    val source: String? = null,             // v45 — "import" for CSV imports, null for native
    @ColumnInfo(defaultValue = "NULL")
    val importBatchId: String? = null       // v45 — UUID grouping all workouts from one import session
)
