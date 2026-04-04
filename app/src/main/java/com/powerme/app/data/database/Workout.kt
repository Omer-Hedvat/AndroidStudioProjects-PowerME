package com.powerme.app.data.database

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
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val routineId: Long?,
    val timestamp: Long,
    val durationSeconds: Int,
    val totalVolume: Double,
    val notes: String? = null,
    val isCompleted: Boolean = false,
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 0L
)
