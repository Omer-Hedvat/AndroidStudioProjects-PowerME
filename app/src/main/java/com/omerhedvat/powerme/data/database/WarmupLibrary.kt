package com.omerhedvat.powerme.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "warmup_library")
data class WarmupLibrary(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val exerciseName: String,
    val group: String, // Spine, Shoulder, Elbow, Hips, Ankles, Movement
    val targetArea: String,
    val committeeNote: String
)
