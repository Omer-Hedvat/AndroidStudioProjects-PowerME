package com.omerhedvat.powerme.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "routine_exercises",
    foreignKeys = [
        ForeignKey(entity = Routine::class,  parentColumns = ["id"], childColumns = ["routineId"],  onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index(value = ["routineId"]), Index(value = ["exerciseId"])]
)
data class RoutineExercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routineId: Long,
    val exerciseId: Long,
    val sets: Int = 3,
    val reps: Int = 10,
    val restTime: Int = 90,          // seconds
    val order: Int = 0,              // display order within routine
    val supersetGroupId: String? = null,
    val stickyNote: String? = null
)
