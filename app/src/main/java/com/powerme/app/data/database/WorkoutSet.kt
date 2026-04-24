package com.powerme.app.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "workout_sets",
    foreignKeys = [
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Exercise::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["workoutId"]),
        Index(value = ["exerciseId"]),
        Index(value = ["workoutId", "exerciseId"]),                // covers SELECT DISTINCT workoutId, exerciseId
        Index(value = ["workoutId", "isCompleted", "setType"])     // covering index for setCount aggregation
    ]
)
data class WorkoutSet(
    @PrimaryKey
    val id: String,                        // UUID string — no autoGenerate; caller pre-generates
    val workoutId: String,
    val exerciseId: Long,                  // exercises table not migrated — stays Long
    val setOrder: Int,
    val weight: Double,
    val reps: Int,
    val rpe: Int? = null,
    val setType: SetType = SetType.NORMAL,
    val setNotes: String? = null,      // Session-specific notes
    val distance: Double? = null,      // For cardio exercises (km)
    val timeSeconds: Int? = null,      // For cardio/timed exercises
    val startTime: Long? = null,       // epoch ms — set started (for TUT)
    val endTime: Long? = null,         // epoch ms — set ended (for TUT)
    val restDuration: Int? = null,     // seconds between this and next set
    val supersetGroupId: String? = null, // UUID shared by paired superset exercises
    val isCompleted: Boolean = false,  // Iron Vault: persisted completion state
    @ColumnInfo(defaultValue = "NULL")
    val blockId: String? = null        // FK → workout_blocks.id (v51)
)
