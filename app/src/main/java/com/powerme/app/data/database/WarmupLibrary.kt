package com.powerme.app.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "warmup_library")
data class WarmupLibrary(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val exerciseName: String,
    val group: String, // Spine, Shoulder, Elbow, Hips, Ankles, Movement
    val targetArea: String,
    val committeeNote: String,
    @ColumnInfo(defaultValue = "")
    val syncId: String = UUID.randomUUID().toString() // Stable cross-device identity for Firestore (v35)
)
