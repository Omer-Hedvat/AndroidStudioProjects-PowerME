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
    indices = [Index(value = ["routineId"])]
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
    val isArchived: Boolean = false        // v31 — soft delete flag
)
