package com.powerme.app.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettings(
    @PrimaryKey
    val id: Long = 1, // Singleton settings
    val availablePlates: String = "0.5,1.25,2.5,5,10,15,20,25", // Comma-separated kg values
    val restTimerAudioEnabled: Boolean = true,
    val restTimerHapticsEnabled: Boolean = true,
    val language: String = "Hebrew",
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0L // Epoch ms, set on every mutation (v35)
)
