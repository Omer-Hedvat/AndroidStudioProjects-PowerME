package com.omerhedvat.powerme.data.database

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
        Index(value = ["exerciseId"])
    ]
)
data class WorkoutSet(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
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
    val restDuration: Int? = null      // seconds between this and next set
)
