package com.powerme.app.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "warmup_log",
    foreignKeys = [
        ForeignKey(
            entity = Workout::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["workoutId"])]
)
data class WarmupLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val workoutId: Long? = null,
    val exerciseName: String,
    val timestamp: Long,
    val targetJoint: TargetJoint,
    val durationSeconds: Int? = null,
    val reps: Int? = null
)
